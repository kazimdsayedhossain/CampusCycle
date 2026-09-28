-- Least-privilege app role (P-004). The app connects as campuscycle_app with a
-- password set out-of-band by the operator and stored in .env as
-- SUPABASE_DB_USER / SUPABASE_DB_PASSWORD; the postgres superuser credential
-- previously committed to source must be rotated and removed. anon and
-- authenticated remain revoked on all tables (see migrations 202609250001,
-- 202609250003, 202609270001), so this role is the only writer by design.
-- Per-user RLS via RPCs using auth.uid() / is_admin() is the recommended next
-- step; it is documented here, not implemented: these policies grant the app
-- role full-row access and per-user scoping still lives in the Java layer.

do $$ begin
    if not exists (select from pg_roles where rolname = 'campuscycle_app') then
        create role campuscycle_app nologin;
    end if;
end $$;

grant connect on database postgres to campuscycle_app;
grant usage on schema public to campuscycle_app;
grant select, insert, update, delete on all tables in schema public to campuscycle_app;
alter default privileges in schema public grant select, insert, update, delete on tables to campuscycle_app;
-- audit_events.id is an identity sequence: inserts need sequence usage too.
grant usage, select on all sequences in schema public to campuscycle_app;
alter default privileges in schema public grant usage, select on sequences to campuscycle_app;

drop policy if exists campuscycle_app_full on public.profiles;
create policy campuscycle_app_full on public.profiles for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.cycles;
create policy campuscycle_app_full on public.cycles for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.rate_cards;
create policy campuscycle_app_full on public.rate_cards for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.rentals;
create policy campuscycle_app_full on public.rentals for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.payment_records;
create policy campuscycle_app_full on public.payment_records for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.disputes;
create policy campuscycle_app_full on public.disputes for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.support_conversations;
create policy campuscycle_app_full on public.support_conversations for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.support_messages;
create policy campuscycle_app_full on public.support_messages for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.audit_events;
create policy campuscycle_app_full on public.audit_events for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.maintenance_tickets;
create policy campuscycle_app_full on public.maintenance_tickets for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.wallets;
create policy campuscycle_app_full on public.wallets for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.wallet_transactions;
create policy campuscycle_app_full on public.wallet_transactions for all to campuscycle_app using (true) with check (true);
drop policy if exists campuscycle_app_full on public.pending_registrations;
create policy campuscycle_app_full on public.pending_registrations for all to campuscycle_app using (true) with check (true);
