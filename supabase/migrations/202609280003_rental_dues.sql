-- Persistent ride debt: overdue/overtime amounts the wallet could not cover
-- at return. Previously shown once in a popup and then forgotten; now every
-- uncollected poisha is recorded here, blocks the next booking until cleared,
-- and auto-settles when money next appears. No cap on accrual (the deterrent
-- stays unbounded); collection, not forgiveness, clears debt.
-- Style follows existing migrations: lowercase, if not exists, revoke from anon/authenticated.

-- 1. Dues ledger: at most one row per rental (unique), states track lifecycle.
create table if not exists public.rental_dues (
    id text primary key,
    rental_id uuid not null unique references public.rentals(id),
    user_id text not null,
    amount_poisha integer not null check (amount_poisha > 0),
    paid_poisha integer not null default 0 check (paid_poisha >= 0),
    state text not null default 'UNPAID'
        check (state in ('UNPAID', 'PARTIAL', 'PAID', 'WAIVED')),
    reason text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (paid_poisha <= amount_poisha)
);
create index if not exists rental_dues_user_state_idx
    on public.rental_dues (user_id, state);

-- 2. Keep updated_at honest on state moves.
create or replace function public.touch_rental_due_updated_at() returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
    new.updated_at := now();
    return new;
end;
$$;
drop trigger if exists rental_dues_touch on public.rental_dues;
create trigger rental_dues_touch before update on public.rental_dues
for each row execute function public.touch_rental_due_updated_at();

-- 3. RLS posture matches the other service tables: app role only.
alter table public.rental_dues enable row level security;
revoke all on public.rental_dues from anon, authenticated;
