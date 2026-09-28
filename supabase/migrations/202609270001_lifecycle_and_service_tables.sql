-- Phase 2 lifecycle + service tables (P-014, P-015).
-- Idempotent: safe on a fresh DB (202609250001..202609260001 applied) and on the
-- live DB (tables/columns below may already exist via LiveMigrationExecutor).
-- Style follows the existing migrations: lowercase, if not exists, revoke from anon/authenticated.

-- 1. profiles.role: add TECHNICIAN (P-031). Both DBs were built from the same
-- migrations so the default PG check name profiles_role_check is deterministic.
alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles add constraint profiles_role_check check (role in ('STUDENT', 'TECHNICIAN', 'ADMIN'));

-- 2. cycles.availability_status: add QUARANTINE + RETIRED (P-042).
alter table public.cycles drop constraint if exists cycles_availability_status_check;
alter table public.cycles add constraint cycles_availability_status_check check (availability_status in ('AVAILABLE', 'RENTED', 'MAINTENANCE', 'QUARANTINE', 'RETIRED'));

-- 3. cycles retirement columns (P-042, P-032).
alter table public.cycles add column if not exists retired_at timestamptz;
alter table public.cycles add column if not exists retirement_reason text;

-- 4. rentals drift columns (P-015): code reads/writes due_at + final_amount_poisha,
-- neither of which exists in 202609250003. dropoff_hub + overdue_fine_poisha are new.
alter table public.rentals add column if not exists due_at timestamptz;
alter table public.rentals add column if not exists final_amount_poisha integer check (final_amount_poisha >= 0);
alter table public.rentals add column if not exists dropoff_hub text;
alter table public.rentals add column if not exists overdue_fine_poisha integer not null default 0 check (overdue_fine_poisha >= 0);

-- backfill due_at from started_at + requested_minutes (concatenation with null
-- yields null, so un-backfillable rows simply stay null and keep the column nullable).
update public.rentals
set due_at = started_at + (requested_minutes || ' minutes')::interval
where due_at is null and started_at is not null and requested_minutes is not null;

-- set not null only when no nulls remain, so this still applies cleanly on live
-- data that could not be backfilled.
do $$ begin
    if not exists (select 1 from public.rentals where due_at is null) then
        alter table public.rentals alter column due_at set not null;
    else
        raise notice 'rentals.due_at left nullable: rows with null due_at remain';
    end if;
end $$;

-- 5. maintenance_tickets (P-014, P-047). id is client-generated TICK-... text.
-- NOTE: live DDL created cycle_id as text; fresh DBs use uuid + FK per this schema.
-- The Java code binds UUID strings, so both shapes accept current traffic.
create table if not exists public.maintenance_tickets (
    id text primary key,
    cycle_id uuid not null references public.cycles(id),
    reported_by_user_id text not null,
    assigned_to_user_id text,
    issue_category text not null,
    priority text not null default 'ROUTINE' check (priority in ('URGENT', 'HIGH', 'ROUTINE')),
    description text not null,
    status text not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    reported_at timestamptz not null default now(),
    resolved_at timestamptz,
    resolved_by_user_id text,
    release_decision text check (release_decision in ('RETURN_TO_SERVICE', 'QUARANTINE', 'RETIRE')),
    technician_notes text not null default '',
    repair_cost_poisha integer not null default 0 check (repair_cost_poisha >= 0),
    estimated_downtime_minutes integer check (estimated_downtime_minutes >= 0),
    check ((status = 'RESOLVED' and resolved_at is not null) or (status <> 'RESOLVED'))
);
create index if not exists maintenance_tickets_status_reported_idx on public.maintenance_tickets (status, reported_at desc);

-- 6. wallets (P-014). user_id is text: the code binds profile UUIDs as strings.
-- NOTE: live DDL defaulted balance_poisha to 2000; this schema defaults to 0 and
-- the approval path inserts the explicit 2000 welcome bonus, so behaviour is unchanged.
create table if not exists public.wallets (
    user_id text primary key,
    balance_poisha integer not null default 0 check (balance_poisha >= 0),
    updated_at timestamptz not null default now()
);

-- 7. wallet_transactions (P-014, P-022). Column names mirror WalletService
-- recordTransactionDirect exactly. The (user_id, reference_code) unique key is
-- the idempotency predicate for charges/fines.
create table if not exists public.wallet_transactions (
    id text primary key,
    user_id text not null,
    amount_poisha integer not null,
    transaction_type text not null,
    balance_after_poisha integer not null,
    timestamp timestamptz not null default now(),
    description text not null default '',
    reference_code text,
    unique (user_id, reference_code)
);
create index if not exists wallet_transactions_user_time_idx on public.wallet_transactions (user_id, timestamp desc);

-- 8. pending_registrations (P-014, P-054). Columns mirror exactly what
-- SupabaseCampusRepository submit/approve/reject/changePassword reads and writes
-- (id bound as uuid via ?::uuid). verification_status is the status column.
create table if not exists public.pending_registrations (
    id uuid primary key default gen_random_uuid(),
    full_name text not null,
    student_roll text not null,
    department text not null,
    email text not null,
    phone text not null,
    password_hash text not null,
    verification_status text not null default 'PENDING_APPROVAL',
    created_at timestamptz not null default now()
);
create unique index if not exists pending_registrations_email_idx on public.pending_registrations (lower(email));

alter table public.maintenance_tickets enable row level security;
alter table public.wallets enable row level security;
alter table public.wallet_transactions enable row level security;
alter table public.pending_registrations enable row level security;
revoke all on public.maintenance_tickets, public.wallets, public.wallet_transactions, public.pending_registrations from anon, authenticated;
