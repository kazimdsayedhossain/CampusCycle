-- Live alignment: adapt repo RPC names to the live Supabase schema.
-- Live tables use legacy columns (rate_cards.minimum/interval_*, rentals.due_at/currency,
-- payment_records.currency, audit_events.object_type/object_id without details).
-- This migration is additive except for 3 junk SUSPENDED test cycles. Payments stay UNPAID.

-- 1. Remove junk test cycles (no rentals reference them; rentals only use Blue Commuter 01).
delete from public.cycles where label in ('CC-CARGO02', 'CC-PENDING03', 'CC-TEST001');

-- 1b. Drop legacy RPCs whose return types changed (recreated below).
drop function if exists public.pending_cycle_reviews();
drop function if exists public.my_support_conversations();
drop function if exists public.support_message_history(uuid);
drop function if exists public.is_admin();
drop function if exists public.public_cycle_catalog();
drop function if exists public.my_cycle_listings();
drop function if exists public.my_rental_history();
drop function if exists public.dispute_queue();
drop function if exists public.register_cycle(text, text, text, text, double precision, double precision, text, text);
drop function if exists public.review_cycle(uuid, boolean, text);
drop function if exists public.start_rental(uuid, integer, uuid);
drop function if exists public.return_rental(uuid);
drop function if exists public.open_dispute(uuid, text);
drop function if exists public.resolve_dispute(uuid, boolean, text);
drop function if exists public.create_support_conversation(text, text);
drop function if exists public.post_support_message(uuid, text);
drop function if exists public.map_cycle_locations();

-- 2. Admin check + audit helper (live audit_events has no details column).
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path to '' as $$
    select exists (select 1 from public.profiles where id = auth.uid() and role = 'ADMIN');
$$;

create or replace function public.write_audit(p_object_type text, p_object_id uuid, p_action text)
returns void language sql security definer set search_path to '' as $$
    insert into public.audit_events(actor_id, action, object_type, object_id)
    values (auth.uid(), p_action, p_object_type, p_object_id);
$$;

-- 3. Public catalog (no owner PII; excludes own cycles only when signed in).
create or replace function public.public_cycle_catalog()
returns table(cycle_id uuid, owner_id uuid, label text, cycle_type text, physical_condition text,
              pickup_point text, latitude double precision, longitude double precision, description text)
language sql stable security definer set search_path to '' as $$
    select c.id, c.owner_id, c.label, c.cycle_type, c.physical_condition, c.pickup_point,
           c.latitude, c.longitude, coalesce(c.description, '')
    from public.cycles c
    where c.review_status = 'APPROVED' and c.availability_status = 'AVAILABLE'
      and (auth.uid() is null or c.owner_id is distinct from auth.uid())
    order by c.label;
$$;

create or replace function public.my_cycle_listings()
returns table(cycle_id uuid, label text, cycle_type text, pickup_point text,
              review_status text, availability_status text, created_at timestamptz)
language sql stable security definer set search_path to '' as $$
    select c.id, c.label, c.cycle_type, c.pickup_point, c.review_status, c.availability_status, c.registered_at
    from public.cycles c where c.owner_id = auth.uid() order by c.registered_at desc;
$$;

create or replace function public.my_rental_history()
returns table(rental_id uuid, cycle_label text, pickup_point text, requested_minutes integer,
              quoted_amount_poisha bigint, state text, started_at timestamptz, returned_at timestamptz,
              payment_state text)
language sql stable security definer set search_path to '' as $$
    select r.id, c.label, c.pickup_point, r.requested_minutes, r.quoted_amount_poisha,
           r.state, r.started_at, r.returned_at, coalesce(p.state, 'UNPAID')
    from public.rentals r
    join public.cycles c on c.id = r.cycle_id
    left join public.payment_records p on p.rental_id = r.id
    where r.renter_id = auth.uid() order by r.started_at desc;
$$;

create or replace function public.pending_cycle_reviews()
returns table(cycle_id uuid, label text, cycle_type text, physical_condition text,
              pickup_point text, description text, created_at timestamptz)
language plpgsql stable security definer set search_path to '' as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    return query select c.id, c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                        coalesce(c.description, ''), c.registered_at
                 from public.cycles c where c.review_status = 'PENDING_REVIEW' order by c.registered_at;
end;
$$;

create or replace function public.dispute_queue()
returns table(dispute_id uuid, rental_id uuid, reason text, state text, created_at timestamptz)
language plpgsql stable security definer set search_path to '' as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    return query select d.id, d.rental_id, d.reason, d.state, d.created_at
                 from public.disputes d where d.state in ('OPEN', 'UNDER_REVIEW') order by d.created_at;
end;
$$;

create or replace function public.my_support_conversations()
returns table(conversation_id uuid, subject text, state text, assigned_admin_name text, updated_at timestamptz)
language plpgsql stable security definer set search_path to '' as $$
begin
    if public.is_admin() then
        return query select c.id, c.subject, c.state,
                            (select p.display_name from public.profiles p where p.id = c.assigned_admin_id),
                            c.updated_at
                     from public.support_conversations c order by c.updated_at desc;
    end if;
    return query select c.id, c.subject, c.state,
                        (select p.display_name from public.profiles p where p.id = c.assigned_admin_id),
                        c.updated_at
                 from public.support_conversations c where c.student_id = auth.uid() order by c.updated_at desc;
end;
$$;

create or replace function public.support_message_history(p_conversation_id uuid)
returns table(message_id uuid, sender_name text, sender_role text, body text, created_at timestamptz)
language plpgsql stable security definer set search_path to '' as $$
begin
    if not exists (select 1 from public.support_conversations c
                   where c.id = p_conversation_id
                     and (c.student_id = auth.uid() or public.is_admin())) then
        raise exception 'conversation access denied';
    end if;
    return query select m.id, p.display_name, p.role, m.body, m.created_at
                 from public.support_messages m join public.profiles p on p.id = m.sender_id
                 where m.conversation_id = p_conversation_id order by m.created_at;
end;
$$;

-- 4. Writes. Payments always UNPAID (no provider in pilot).
create or replace function public.register_cycle(p_label text, p_cycle_type text, p_condition text,
    p_pickup_point text, p_latitude double precision, p_longitude double precision,
    p_description text, p_owner_phone text)
returns table(cycle_id uuid)
language plpgsql security definer set search_path to '' as $$
declare v_cycle_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    insert into public.cycles(owner_id, label, cycle_type, physical_condition, pickup_point,
                              latitude, longitude, description, owner_phone, review_status, availability_status)
    values (auth.uid(), trim(p_label), p_cycle_type, p_condition, trim(p_pickup_point),
            p_latitude, p_longitude, trim(coalesce(p_description, '')), trim(p_owner_phone),
            'PENDING_REVIEW', 'AVAILABLE')
    returning id into v_cycle_id;
    perform public.write_audit('cycle', v_cycle_id, 'REGISTERED');
    return query select v_cycle_id;
end;
$$;

create or replace function public.review_cycle(p_cycle_id uuid, p_approve boolean, p_reason text default null)
returns void
language plpgsql security definer set search_path to '' as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    if not p_approve and char_length(trim(coalesce(p_reason, ''))) < 10 then
        raise exception 'rejection reason required';
    end if;
    update public.cycles set review_status = case when p_approve then 'APPROVED' else 'REJECTED' end,
                             updated_at = now()
    where id = p_cycle_id and review_status = 'PENDING_REVIEW';
    if not found then raise exception 'cycle is not awaiting review'; end if;
    perform public.write_audit('cycle', p_cycle_id, case when p_approve then 'APPROVED' else 'REJECTED' end);
end;
$$;

create or replace function public.start_rental(p_cycle_id uuid, p_requested_minutes integer, p_idempotency_key uuid)
returns table(rental_id uuid, quoted_amount_poisha bigint, state text)
language plpgsql security definer set search_path to '' as $$
declare v_cycle public.cycles%rowtype; v_rate public.rate_cards%rowtype;
        v_amount bigint; v_rental_id uuid; v_state text;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    select r.id, r.quoted_amount_poisha, r.state into v_rental_id, v_amount, v_state
    from public.rentals r where r.renter_id = auth.uid() and r.idempotency_key = p_idempotency_key;
    if found then return query select v_rental_id, v_amount, v_state; return; end if;
    select * into v_cycle from public.cycles where id = p_cycle_id for update;
    if not found or v_cycle.review_status <> 'APPROVED' or v_cycle.availability_status <> 'AVAILABLE' then
        raise exception 'cycle unavailable';
    end if;
    if v_cycle.owner_id = auth.uid() then raise exception 'cannot rent own cycle'; end if;
    select * into v_rate from public.rate_cards
    where active_from <= now() and (active_until is null or active_until > now())
    order by version desc limit 1;
    if not found then raise exception 'no active rate card'; end if;
    if p_requested_minutes < v_rate.minimum_minutes or p_requested_minutes > 180 then
        raise exception 'invalid rental duration';
    end if;
    v_amount := public.calculate_fare_poisha(v_rate.version, p_requested_minutes);
    insert into public.rentals(cycle_id, renter_id, requested_minutes, due_at, rate_card_version,
                               quoted_amount_poisha, currency, idempotency_key, state)
    values (p_cycle_id, auth.uid(), p_requested_minutes, now() + make_interval(mins => p_requested_minutes),
            v_rate.version, v_amount, v_rate.currency, p_idempotency_key, 'ACTIVE')
    returning id into v_rental_id;
    update public.cycles set availability_status = 'RENTED', is_available = false, updated_at = now(),
                             version = version + 1 where id = p_cycle_id;
    insert into public.payment_records(rental_id, amount_poisha, currency, state)
    values (v_rental_id, v_amount, v_rate.currency, 'UNPAID');
    perform public.write_audit('rental', v_rental_id, 'STARTED');
    return query select v_rental_id, v_amount, 'ACTIVE'::text;
end;
$$;

create or replace function public.return_rental(p_rental_id uuid)
returns void
language plpgsql security definer set search_path to '' as $$
declare v_rental public.rentals%rowtype;
begin
    select * into v_rental from public.rentals where id = p_rental_id for update;
    if not found then raise exception 'rental not found'; end if;
    if v_rental.renter_id <> auth.uid() then raise exception 'rental access denied'; end if;
    if v_rental.state = 'RETURNED' then return; end if;
    if v_rental.state <> 'ACTIVE' then raise exception 'rental cannot be returned'; end if;
    update public.rentals set state = 'RETURNED', returned_at = now(), updated_at = now() where id = p_rental_id;
    update public.cycles set availability_status = 'AVAILABLE', is_available = true, updated_at = now(),
                             version = version + 1 where id = v_rental.cycle_id;
    perform public.write_audit('rental', p_rental_id, 'RETURNED');
end;
$$;

create or replace function public.open_dispute(p_rental_id uuid, p_reason text)
returns table(dispute_id uuid)
language plpgsql security definer set search_path to '' as $$
declare v_dispute_id uuid;
begin
    if not exists (select 1 from public.rentals
                   where id = p_rental_id and renter_id = auth.uid() and state = 'RETURNED') then
        raise exception 'rental is not eligible for dispute';
    end if;
    insert into public.disputes(rental_id, opened_by, reason)
    values (p_rental_id, auth.uid(), trim(p_reason)) returning id into v_dispute_id;
    update public.rentals set state = 'DISPUTED', updated_at = now() where id = p_rental_id;
    perform public.write_audit('dispute', v_dispute_id, 'OPENED');
    return query select v_dispute_id;
end;
$$;

create or replace function public.resolve_dispute(p_dispute_id uuid, p_resolve boolean, p_resolution text)
returns void
language plpgsql security definer set search_path to '' as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    if char_length(trim(coalesce(p_resolution, ''))) < 10 then raise exception 'resolution required'; end if;
    update public.disputes set state = case when p_resolve then 'RESOLVED' else 'REJECTED' end,
                              updated_at = now()
    where id = p_dispute_id and state in ('OPEN', 'UNDER_REVIEW');
    if not found then raise exception 'dispute cannot be resolved'; end if;
    perform public.write_audit('dispute', p_dispute_id, case when p_resolve then 'RESOLVED' else 'REJECTED' end);
end;
$$;

create or replace function public.create_support_conversation(p_subject text, p_message text)
returns table(conversation_id uuid)
language plpgsql security definer set search_path to '' as $$
declare v_conversation_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    insert into public.support_conversations(student_id, subject)
    values (auth.uid(), trim(p_subject)) returning id into v_conversation_id;
    insert into public.support_messages(conversation_id, sender_id, body)
    values (v_conversation_id, auth.uid(), trim(p_message));
    perform public.write_audit('support', v_conversation_id, 'OPENED');
    return query select v_conversation_id;
end;
$$;

create or replace function public.post_support_message(p_conversation_id uuid, p_body text)
returns void
language plpgsql security definer set search_path to '' as $$
declare v_conversation public.support_conversations%rowtype;
begin
    select * into v_conversation from public.support_conversations where id = p_conversation_id for update;
    if not found then raise exception 'conversation unavailable'; end if;
    if v_conversation.student_id <> auth.uid() and not public.is_admin() then
        raise exception 'conversation access denied';
    end if;
    insert into public.support_messages(conversation_id, sender_id, body)
    values (p_conversation_id, auth.uid(), trim(p_body));
    update public.support_conversations
    set assigned_admin_id = case when public.is_admin() then auth.uid() else assigned_admin_id end,
        updated_at = now() where id = p_conversation_id;
end;
$$;

create or replace function public.map_cycle_locations()
returns table(cycle_id uuid, label text, pickup_point text, latitude double precision, longitude double precision)
language sql stable security definer set search_path to '' as $$
    select c.id, c.label, c.pickup_point, c.latitude, c.longitude
    from public.cycles c
    where c.review_status = 'APPROVED' and c.availability_status = 'AVAILABLE'
      and c.latitude between -90 and 90 and c.longitude between -180 and 180
    order by c.label;
$$;

-- 5. Grants: authenticated for app RPCs, anon+authenticated for public map.
revoke all on function public.is_admin() from public;
revoke all on function public.write_audit(text, uuid, text) from public;
grant execute on function public.public_cycle_catalog(), public.my_cycle_listings(),
    public.my_rental_history(), public.pending_cycle_reviews(), public.dispute_queue(),
    public.my_support_conversations(), public.support_message_history(uuid),
    public.register_cycle(text, text, text, text, double precision, double precision, text, text),
    public.review_cycle(uuid, boolean, text), public.start_rental(uuid, integer, uuid),
    public.return_rental(uuid), public.open_dispute(uuid, text), public.resolve_dispute(uuid, boolean, text),
    public.create_support_conversation(text, text), public.post_support_message(uuid, text)
    to authenticated;
grant execute on function public.map_cycle_locations() to anon, authenticated;
