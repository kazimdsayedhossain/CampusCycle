-- Public map projection. It deliberately returns only approved, available cycles and no owner data.
create or replace function public.map_cycle_locations()
returns table(
    cycle_id uuid,
    label text,
    pickup_point text,
    latitude double precision,
    longitude double precision
)
language sql
stable
security definer
set search_path = ''
as $$
    select c.id, c.label, c.pickup_point, c.latitude, c.longitude
    from public.cycles c
    where c.review_status = 'APPROVED'
      and c.availability_status = 'AVAILABLE'
      and c.latitude between -90 and 90
      and c.longitude between -180 and 180
    order by c.created_at desc;
$$;

revoke all on function public.map_cycle_locations() from public;
grant execute on function public.map_cycle_locations() to anon, authenticated;
