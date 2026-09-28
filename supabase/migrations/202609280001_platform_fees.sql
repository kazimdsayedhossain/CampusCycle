-- Platform commission + owner payout on settled fares (earnings feature).
-- Fee rule (mirrors TariffService.platformFeePoisha / ownerPayoutPoisha):
--   fee    = LEAST(ROUND(final * 0.05), 150), 0 when final <= 0
--   payout = GREATEST(final - fee, 0)
-- Columns are NULLABLE by design: pre-fee rows keep NULL (forward-only, no
-- backfill) and readers treat NULL as 0. Java Math.round(double) and Postgres
-- ROUND(numeric) agree (half-up) on all non-negative inputs.
-- Style follows existing migrations: lowercase, if not exists, revoke from anon/authenticated.

-- 1. Stored settlement split on rentals.
alter table public.rentals add column if not exists platform_fee_poisha integer check (platform_fee_poisha >= 0);
alter table public.rentals add column if not exists owner_payout_poisha integer check (owner_payout_poisha >= 0);

-- 2. Settled-row aggregate index for owner + platform earnings reads.
create index if not exists rentals_settled_earnings_idx
    on public.rentals (state, returned_at desc)
    where state = 'RETURNED';

-- 3. Authoritative computation inside the existing fare trigger: on the
-- ACTIVE -> RETURNED transition, after final_amount_poisha is settled, derive
-- the split from it. INSERT path leaves both NULL (fee exists only on settlement).
create or replace function public.enforce_rental_fare() returns trigger
language plpgsql set search_path = public, pg_temp as $$
declare
    v_rate public.rate_cards%rowtype;
    v_role text;
    v_req integer;
    v_total integer;
    v_actual integer;
    v_actual_fare integer;
    v_fee integer;
begin
    -- renter role drives the student subsidy; unknown profile means full fare.
    select p.role into v_role from public.profiles p where p.id = new.renter_id;
    if v_role is null then
        v_role := '';
    end if;

    if tg_op = 'INSERT' then
        -- active rate card gates the booking; fall back to latest version, and if
        -- the table is empty leave the row untouched so clean-DB seeds keep working.
        select * into v_rate from public.rate_cards
        where active_from <= now() and (active_until is null or active_until > now())
        order by version desc limit 1;
        if not found then
            select * into v_rate from public.rate_cards order by version desc limit 1;
        end if;
        if not found then
            raise notice 'enforce_rental_fare: no rate card present, leaving client-supplied fare untouched';
            return new;
        end if;

        v_req := greatest(15, least(180, new.requested_minutes));
        v_total := 2000 + ((v_req - 15 + 14) / 15) * 1000;
        if v_role = 'STUDENT' then
            v_total := v_total - round(v_total * 0.25)::integer;
        end if;
        new.quoted_amount_poisha := v_total;
        new.final_amount_poisha := null;
        new.platform_fee_poisha := null;
        new.owner_payout_poisha := null;
        if new.due_at is null then
            new.due_at := new.started_at + (v_req || ' minutes')::interval;
        end if;
        return new;
    elsif tg_op = 'UPDATE' then
        -- settle exactly once, on the ACTIVE -> RETURNED transition; other updates pass through.
        if new.state = 'RETURNED' and old.state is distinct from 'RETURNED' then
            if new.returned_at is null or new.started_at is null then
                new.final_amount_poisha := new.quoted_amount_poisha;
            else
                v_actual := greatest(15, least(180, ceil(extract(epoch from (new.returned_at - new.started_at)) / 60.0)::integer));
                v_actual_fare := 2000 + ((v_actual - 15 + 14) / 15) * 1000;
                if v_role = 'STUDENT' then
                    v_actual_fare := v_actual_fare - round(v_actual_fare * 0.25)::integer;
                end if;
                new.final_amount_poisha := greatest(new.quoted_amount_poisha, v_actual_fare);
            end if;
            -- platform split from the settled fare (never from client input).
            if new.final_amount_poisha is null or new.final_amount_poisha <= 0 then
                new.platform_fee_poisha := 0;
                new.owner_payout_poisha := 0;
            else
                v_fee := least(round(new.final_amount_poisha * 0.05)::integer, 150);
                new.platform_fee_poisha := greatest(v_fee, 0);
                new.owner_payout_poisha := greatest(new.final_amount_poisha - new.platform_fee_poisha, 0);
            end if;
        end if;
        return new;
    end if;
    return new;
end;
$$;

drop trigger if exists rentals_fare_authority on public.rentals;
create trigger rentals_fare_authority before insert or update on public.rentals for each row execute function public.enforce_rental_fare();

-- 4. RLS posture matches the other service tables: app role only.
alter table public.rentals enable row level security;
revoke all on public.rentals from anon, authenticated;
