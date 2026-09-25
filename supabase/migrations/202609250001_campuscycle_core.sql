create extension if not exists pgcrypto;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null check (char_length(trim(display_name)) between 2 and 100),
    role text not null default 'STUDENT' check (role in ('STUDENT', 'ADMIN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.cycles (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles(id),
    label text not null check (char_length(trim(label)) between 2 and 100),
    cycle_type text not null check (cycle_type in ('CITY_BIKE', 'ROAD_BIKE', 'ELECTRIC_BIKE', 'CARGO_BIKE')),
    physical_condition text not null check (physical_condition in ('EXCELLENT', 'GOOD', 'FAIR')),
    pickup_point text not null check (char_length(trim(pickup_point)) between 2 and 180),
    latitude double precision,
    longitude double precision,
    description text not null default '' check (char_length(description) <= 2000),
    owner_phone text not null check (owner_phone ~ '^\\+?8801[3-9][0-9]{8}$'),
    review_status text not null default 'PENDING_REVIEW' check (review_status in ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    availability_status text not null default 'AVAILABLE' check (availability_status in ('AVAILABLE', 'RENTED', 'MAINTENANCE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((latitude is null and longitude is null) or (latitude between -90 and 90 and longitude between -180 and 180))
);

create index cycles_public_map_idx on public.cycles (review_status, availability_status, created_at desc)
    where latitude is not null and longitude is not null;

alter table public.profiles enable row level security;
alter table public.cycles enable row level security;

revoke all on public.profiles, public.cycles from anon, authenticated;
