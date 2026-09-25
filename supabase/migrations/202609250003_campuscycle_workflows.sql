-- Core rental, dispute, and support workflows. Apply only through the Supabase migration tool.

create table public.rate_cards (
    id uuid primary key default gen_random_uuid(),
    version integer not null unique check (version > 0),
    base_minutes smallint not null check (base_minutes between 1 and 180),
    base_charge_poisha integer not null check (base_charge_poisha >= 0),
    extra_block_minutes smallint not null check (extra_block_minutes between 1 and 180),
    extra_block_charge_poisha integer not null check (extra_block_charge_poisha >= 0),
    maximum_minutes smallint not null check (maximum_minutes between base_minutes and 720),
    active_from timestamptz not null,
    active_until timestamptz,
    created_at timestamptz not null default now(),
    check (active_until is null or active_until > active_from)
);

create table public.rentals (
    id uuid primary key default gen_random_uuid(),
    cycle_id uuid not null references public.cycles(id),
    renter_id uuid not null references public.profiles(id),
    rate_card_id uuid not null references public.rate_cards(id),
    rate_card_version integer not null,
    quoted_amount_poisha integer not null check (quoted_amount_poisha >= 0),
    requested_minutes smallint not null check (requested_minutes between 1 and 720),
    state text not null default 'ACTIVE' check (state in ('ACTIVE', 'RETURNED', 'CANCELLED', 'DISPUTED', 'CLOSED')),
    started_at timestamptz not null default now(),
    returned_at timestamptz,
    idempotency_key uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (renter_id, idempotency_key),
    check ((state = 'ACTIVE' and returned_at is null) or (state <> 'ACTIVE' and returned_at is not null))
);

create unique index rentals_one_active_rental_per_renter_idx on public.rentals (renter_id) where state = 'ACTIVE';
create unique index rentals_one_active_rental_per_cycle_idx on public.rentals (cycle_id) where state = 'ACTIVE';
create index rentals_renter_history_idx on public.rentals (renter_id, created_at desc);

create table public.payment_records (
    id uuid primary key default gen_random_uuid(),
    rental_id uuid not null unique references public.rentals(id),
    amount_poisha integer not null check (amount_poisha >= 0),
    state text not null default 'UNPAID' check (state in ('UNPAID', 'PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    provider_reference text unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.disputes (
    id uuid primary key default gen_random_uuid(),
    rental_id uuid not null unique references public.rentals(id),
    opened_by uuid not null references public.profiles(id),
    reason text not null check (char_length(trim(reason)) between 10 and 2000),
    state text not null default 'OPEN' check (state in ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED')),
    resolution text check (resolution is null or char_length(trim(resolution)) between 10 and 2000),
    assigned_admin_id uuid references public.profiles(id),
    resolved_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((state in ('RESOLVED', 'REJECTED') and resolution is not null and resolved_at is not null)
        or (state in ('OPEN', 'UNDER_REVIEW') and resolution is null and resolved_at is null))
);
create index disputes_open_queue_idx on public.disputes (state, created_at) where state in ('OPEN', 'UNDER_REVIEW');

create table public.support_conversations (
    id uuid primary key default gen_random_uuid(),
    student_id uuid not null references public.profiles(id),
    subject text not null check (char_length(trim(subject)) between 3 and 160),
    state text not null default 'OPEN' check (state in ('OPEN', 'CLOSED')),
    assigned_admin_id uuid references public.profiles(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index support_student_idx on public.support_conversations (student_id, updated_at desc);
create index support_open_queue_idx on public.support_conversations (state, updated_at desc) where state = 'OPEN';

create table public.support_messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.support_conversations(id) on delete cascade,
    sender_id uuid not null references public.profiles(id),
    body text not null check (char_length(trim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);
create index support_messages_timeline_idx on public.support_messages (conversation_id, created_at);

create table public.audit_events (
    id bigint generated always as identity primary key,
    actor_id uuid references public.profiles(id),
    entity_type text not null check (entity_type in ('CYCLE', 'RENTAL', 'DISPUTE', 'SUPPORT')),
    entity_id uuid not null,
    action text not null check (char_length(action) between 2 and 80),
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index audit_events_entity_idx on public.audit_events (entity_type, entity_id, created_at desc);

alter table public.rate_cards enable row level security;
alter table public.rentals enable row level security;
alter table public.payment_records enable row level security;
alter table public.disputes enable row level security;
alter table public.support_conversations enable row level security;
alter table public.support_messages enable row level security;
alter table public.audit_events enable row level security;
revoke all on public.rate_cards, public.rentals, public.payment_records, public.disputes, public.support_conversations, public.support_messages, public.audit_events from anon, authenticated;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public, pg_temp as $$
    select exists (select 1 from public.profiles where id = auth.uid() and role = 'ADMIN');
$$;

create or replace function public.write_audit(p_entity_type text, p_entity_id uuid, p_action text, p_details jsonb default '{}'::jsonb)
returns void language sql security definer set search_path = public, pg_temp as $$
    insert into public.audit_events(actor_id, entity_type, entity_id, action, details)
    values (auth.uid(), p_entity_type, p_entity_id, p_action, coalesce(p_details, '{}'::jsonb));
$$;

create or replace function public.create_profile_for_auth_user()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare safe_name text;
begin
    safe_name := coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), nullif(split_part(new.email, '@', 1), ''), 'KUET Student');
    if char_length(safe_name) < 2 then safe_name := 'KUET Student'; end if;
    insert into public.profiles(id, display_name) values (new.id, left(safe_name, 100));
    return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.create_profile_for_auth_user();

insert into public.rate_cards(version, base_minutes, base_charge_poisha, extra_block_minutes, extra_block_charge_poisha, maximum_minutes, active_from)
values (1, 15, 2000, 15, 1000, 180, now()) on conflict (version) do nothing;

create or replace function public.public_cycle_catalog()
returns table(cycle_id uuid, label text, cycle_type text, physical_condition text, pickup_point text, latitude double precision, longitude double precision, description text)
language sql stable security definer set search_path = public, pg_temp as $$
    select id, label, cycle_type, physical_condition, pickup_point, latitude, longitude, description
    from public.cycles where review_status = 'APPROVED' and availability_status = 'AVAILABLE' order by created_at desc;
$$;

create or replace function public.my_cycle_listings()
returns table(cycle_id uuid, label text, cycle_type text, pickup_point text, review_status text, availability_status text, created_at timestamptz)
language sql stable security definer set search_path = public, pg_temp as $$
    select id, label, cycle_type, pickup_point, review_status, availability_status, created_at
    from public.cycles where owner_id = auth.uid() order by created_at desc;
$$;

create or replace function public.my_rental_history()
returns table(rental_id uuid, cycle_label text, pickup_point text, requested_minutes smallint, quoted_amount_poisha integer, state text, started_at timestamptz, returned_at timestamptz, payment_state text)
language sql stable security definer set search_path = public, pg_temp as $$
    select r.id, c.label, c.pickup_point, r.requested_minutes, r.quoted_amount_poisha, r.state, r.started_at, r.returned_at, p.state
    from public.rentals r join public.cycles c on c.id = r.cycle_id join public.payment_records p on p.rental_id = r.id
    where r.renter_id = auth.uid() order by r.created_at desc;
$$;

create or replace function public.pending_cycle_reviews()
returns table(cycle_id uuid, label text, cycle_type text, physical_condition text, pickup_point text, description text, created_at timestamptz)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    return query select id, label, cycle_type, physical_condition, pickup_point, description, created_at
    from public.cycles where review_status = 'PENDING_REVIEW' order by created_at;
end;
$$;

create or replace function public.dispute_queue()
returns table(dispute_id uuid, rental_id uuid, renter_name text, cycle_label text, reason text, state text, created_at timestamptz)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    return query select d.id, d.rental_id, p.display_name, c.label, d.reason, d.state, d.created_at
    from public.disputes d join public.profiles p on p.id = d.opened_by join public.rentals r on r.id = d.rental_id join public.cycles c on c.id = r.cycle_id
    where d.state in ('OPEN', 'UNDER_REVIEW') order by d.created_at;
end;
$$;

create or replace function public.my_support_conversations()
returns table(conversation_id uuid, subject text, state text, assigned_admin_name text, updated_at timestamptz)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
    if public.is_admin() then
        return query select c.id, c.subject, c.state, p.display_name, c.updated_at
        from public.support_conversations c left join public.profiles p on p.id = c.assigned_admin_id order by c.updated_at desc;
    end if;
    return query select c.id, c.subject, c.state, p.display_name, c.updated_at
    from public.support_conversations c left join public.profiles p on p.id = c.assigned_admin_id
    where c.student_id = auth.uid() order by c.updated_at desc;
end;
$$;

create or replace function public.support_message_history(p_conversation_id uuid)
returns table(message_id uuid, sender_name text, sender_role text, body text, created_at timestamptz)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
    if not exists (select 1 from public.support_conversations c where c.id = p_conversation_id and (c.student_id = auth.uid() or public.is_admin())) then
        raise exception 'conversation access denied';
    end if;
    return query select m.id, p.display_name, p.role, m.body, m.created_at
    from public.support_messages m join public.profiles p on p.id = m.sender_id
    where m.conversation_id = p_conversation_id order by m.created_at;
end;
$$;

create or replace function public.register_cycle(p_label text, p_cycle_type text, p_condition text, p_pickup_point text, p_latitude double precision, p_longitude double precision, p_description text, p_owner_phone text)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_cycle_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    insert into public.cycles(owner_id, label, cycle_type, physical_condition, pickup_point, latitude, longitude, description, owner_phone)
    values (auth.uid(), trim(p_label), p_cycle_type, p_condition, trim(p_pickup_point), p_latitude, p_longitude, trim(coalesce(p_description, '')), trim(p_owner_phone)) returning id into v_cycle_id;
    perform public.write_audit('CYCLE', v_cycle_id, 'REGISTERED');
    return v_cycle_id;
end;
$$;

create or replace function public.review_cycle(p_cycle_id uuid, p_approve boolean, p_reason text default null)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    if not p_approve and char_length(trim(coalesce(p_reason, ''))) < 10 then raise exception 'rejection reason required'; end if;
    update public.cycles set review_status = case when p_approve then 'APPROVED' else 'REJECTED' end, updated_at = now()
    where id = p_cycle_id and review_status = 'PENDING_REVIEW';
    if not found then raise exception 'cycle is not awaiting review'; end if;
    perform public.write_audit('CYCLE', p_cycle_id, case when p_approve then 'APPROVED' else 'REJECTED' end, jsonb_build_object('reason', p_reason));
end;
$$;

create or replace function public.start_rental(p_cycle_id uuid, p_requested_minutes smallint, p_idempotency_key uuid)
returns table(rental_id uuid, quoted_amount_poisha integer, state text)
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_cycle public.cycles%rowtype; v_rate public.rate_cards%rowtype; v_amount integer; v_rental_id uuid; v_state text;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    select r.id, r.quoted_amount_poisha, r.state into v_rental_id, v_amount, v_state from public.rentals r where r.renter_id = auth.uid() and r.idempotency_key = p_idempotency_key;
    if found then return query select v_rental_id, v_amount, v_state; return; end if;
    select * into v_cycle from public.cycles where id = p_cycle_id for update;
    if not found or v_cycle.review_status <> 'APPROVED' or v_cycle.availability_status <> 'AVAILABLE' then raise exception 'cycle unavailable'; end if;
    if v_cycle.owner_id = auth.uid() then raise exception 'cannot rent own cycle'; end if;
    select * into v_rate from public.rate_cards where active_from <= now() and (active_until is null or active_until > now()) order by version desc limit 1;
    if not found then raise exception 'no active rate card'; end if;
    if p_requested_minutes < v_rate.base_minutes or p_requested_minutes > v_rate.maximum_minutes then raise exception 'invalid rental duration'; end if;
    v_amount := v_rate.base_charge_poisha + greatest(0, ceil((p_requested_minutes - v_rate.base_minutes)::numeric / v_rate.extra_block_minutes)::integer) * v_rate.extra_block_charge_poisha;
    insert into public.rentals(cycle_id, renter_id, rate_card_id, rate_card_version, quoted_amount_poisha, requested_minutes, idempotency_key)
    values (p_cycle_id, auth.uid(), v_rate.id, v_rate.version, v_amount, p_requested_minutes, p_idempotency_key) returning id into v_rental_id;
    update public.cycles set availability_status = 'RENTED', updated_at = now() where id = p_cycle_id;
    insert into public.payment_records(rental_id, amount_poisha) values (v_rental_id, v_amount);
    perform public.write_audit('RENTAL', v_rental_id, 'STARTED');
    return query select v_rental_id, v_amount, 'ACTIVE'::text;
end;
$$;

create or replace function public.return_rental(p_rental_id uuid)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare v_rental public.rentals%rowtype;
begin
    select * into v_rental from public.rentals where id = p_rental_id for update;
    if not found then raise exception 'rental not found'; end if;
    if v_rental.renter_id <> auth.uid() then raise exception 'rental access denied'; end if;
    if v_rental.state = 'RETURNED' then return; end if;
    if v_rental.state <> 'ACTIVE' then raise exception 'rental cannot be returned'; end if;
    update public.rentals set state = 'RETURNED', returned_at = now(), updated_at = now() where id = p_rental_id;
    update public.cycles set availability_status = 'AVAILABLE', updated_at = now() where id = v_rental.cycle_id;
    perform public.write_audit('RENTAL', p_rental_id, 'RETURNED');
end;
$$;

create or replace function public.open_dispute(p_rental_id uuid, p_reason text)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_dispute_id uuid;
begin
    if not exists (select 1 from public.rentals where id = p_rental_id and renter_id = auth.uid() and state = 'RETURNED') then raise exception 'rental is not eligible for dispute'; end if;
    insert into public.disputes(rental_id, opened_by, reason) values (p_rental_id, auth.uid(), trim(p_reason)) returning id into v_dispute_id;
    perform public.write_audit('DISPUTE', v_dispute_id, 'OPENED');
    return v_dispute_id;
end;
$$;

create or replace function public.resolve_dispute(p_dispute_id uuid, p_resolve boolean, p_resolution text)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_admin() then raise exception 'administrator access required'; end if;
    if char_length(trim(coalesce(p_resolution, ''))) < 10 then raise exception 'resolution required'; end if;
    update public.disputes set state = case when p_resolve then 'RESOLVED' else 'REJECTED' end, resolution = trim(p_resolution), assigned_admin_id = auth.uid(), resolved_at = now(), updated_at = now()
    where id = p_dispute_id and state in ('OPEN', 'UNDER_REVIEW');
    if not found then raise exception 'dispute cannot be resolved'; end if;
    perform public.write_audit('DISPUTE', p_dispute_id, case when p_resolve then 'RESOLVED' else 'REJECTED' end);
end;
$$;

create or replace function public.create_support_conversation(p_subject text, p_message text)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_conversation_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    insert into public.support_conversations(student_id, subject) values (auth.uid(), trim(p_subject)) returning id into v_conversation_id;
    insert into public.support_messages(conversation_id, sender_id, body) values (v_conversation_id, auth.uid(), trim(p_message));
    perform public.write_audit('SUPPORT', v_conversation_id, 'OPENED');
    return v_conversation_id;
end;
$$;

create or replace function public.post_support_message(p_conversation_id uuid, p_body text)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare v_conversation public.support_conversations%rowtype;
begin
    select * into v_conversation from public.support_conversations where id = p_conversation_id for update;
    if not found or v_conversation.state <> 'OPEN' then raise exception 'conversation unavailable'; end if;
    if v_conversation.student_id <> auth.uid() and not public.is_admin() then raise exception 'conversation access denied'; end if;
    insert into public.support_messages(conversation_id, sender_id, body) values (p_conversation_id, auth.uid(), trim(p_body));
    update public.support_conversations set assigned_admin_id = case when public.is_admin() then auth.uid() else assigned_admin_id end, updated_at = now() where id = p_conversation_id;
end;
$$;

revoke all on function public.is_admin(), public.write_audit(text, uuid, text, jsonb) from public;
grant execute on function public.public_cycle_catalog(), public.my_cycle_listings(), public.my_rental_history(), public.pending_cycle_reviews(), public.dispute_queue(), public.my_support_conversations(), public.support_message_history(uuid), public.register_cycle(text, text, text, text, double precision, double precision, text, text), public.review_cycle(uuid, boolean, text), public.start_rental(uuid, smallint, uuid), public.return_rental(uuid), public.open_dispute(uuid, text), public.resolve_dispute(uuid, boolean, text), public.create_support_conversation(text, text), public.post_support_message(uuid, text) to authenticated;
