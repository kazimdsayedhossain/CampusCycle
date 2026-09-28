-- Database-authoritative fares (P-016, P-024). Client-supplied quoted/final amounts
-- are recomputed here, so a tampered client cannot settle a ride for 1 poisha.
-- Fare mirrors TariffService: base 2000 for the first 15 min + 1000 per extra
-- 15-min block, minutes clamped to [15, 180], students minus round(total * 0.25).
--
-- Checkpoints vs TariffService.quotePoisha/subsidyPoisha (truncation and round
-- agree on every checkpoint: all totals are multiples of 1000, hence divisible by 4):
--   15 min: admin 2000,           student 2000 - 500  = 1500
--   30 min: admin 2000 + 1000 = 3000, student 3000 - 750  = 2250
--   45 min: admin 2000 + 2000 = 4000, student 4000 - 1000 = 3000
--  180 min: admin 2000 + 11*1000 = 13000, student 13000 - 3250 = 9750

create or replace function public.enforce_rental_fare() returns trigger
language plpgsql set search_path = public, pg_temp as $$
declare
    v_rate public.rate_cards%rowtype;
    v_role text;
    v_req integer;
    v_total integer;
    v_actual integer;
    v_actual_fare integer;
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
        if new.due_at is null then
            new.due_at := new.started_at + (v_req || ' minutes')::interval;
        end if;
        return new;
    elsif tg_op = 'UPDATE' then
        -- settle exactly once, on the ACTIVE -> RETURNED transition; other updates pass through.
        if new.state = 'RETURNED' and old.state is distinct from 'RETURNED' then
            if new.returned_at is null or new.started_at is null then
                new.final_amount_poisha := new.quoted_amount_poisha;
                return new;
            end if;
            v_actual := greatest(15, least(180, ceil(extract(epoch from (new.returned_at - new.started_at)) / 60.0)::integer));
            v_actual_fare := 2000 + ((v_actual - 15 + 14) / 15) * 1000;
            if v_role = 'STUDENT' then
                v_actual_fare := v_actual_fare - round(v_actual_fare * 0.25)::integer;
            end if;
            new.final_amount_poisha := greatest(new.quoted_amount_poisha, v_actual_fare);
        end if;
        return new;
    end if;
    return new;
end;
$$;

drop trigger if exists rentals_fare_authority on public.rentals;
create trigger rentals_fare_authority before insert or update on public.rentals for each row execute function public.enforce_rental_fare();
