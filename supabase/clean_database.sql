-- CampusCycle Supabase Database Clean Script
-- Run in Supabase SQL Editor
-- Deletes ALL user accounts, cycles, rentals, wallets, tickets, registrations, disputes, support, audit
-- Preserves: schema, indexes, functions, RLS policies, rate_cards seed (v1)

-- ============================================================
-- 1. Disable RLS temporarily (required for TRUNCATE on RLS tables)
-- ============================================================
ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.cycles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.rentals DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_records DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.disputes DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_conversations DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_messages DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_events DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.maintenance_tickets DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallets DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_transactions DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.pending_registrations DISABLE ROW LEVEL SECURITY;

-- ============================================================
-- 2. Truncate data tables (CASCADE handles FK dependencies)
-- ============================================================
TRUNCATE TABLE 
    public.wallet_transactions,
    public.wallets,
    public.maintenance_tickets,
    public.audit_events,
    public.support_messages,
    public.support_conversations,
    public.disputes,
    public.payment_records,
    public.rentals,
    public.cycles,
    public.pending_registrations,
    public.profiles
    RESTART IDENTITY CASCADE;

-- ============================================================
-- 3. Keep rate_cards seed data (version 1: BDT 20/15min + BDT 10/15min)
-- ============================================================
-- If you want to reset rate_cards too, uncomment:
-- TRUNCATE TABLE public.rate_cards RESTART IDENTITY CASCADE;
-- INSERT INTO public.rate_cards(version, base_minutes, base_charge_poisha, extra_block_minutes, extra_block_charge_poisha, maximum_minutes, active_from)
-- VALUES (1, 15, 2000, 15, 1000, 180, now()) ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 4. Re-enable RLS
-- ============================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rentals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.maintenance_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pending_registrations ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 5. Clean Supabase Auth users (run separately in Dashboard or via Admin API)
-- ============================================================
-- Option A: Supabase Dashboard → Authentication → Users → Select all → Delete
-- Option B: Admin API (requires SUPABASE_SERVICE_ROLE_KEY)
-- curl -X DELETE "https://<project>.supabase.co/auth/v1/admin/users/<user-id>" \
--   -H "apikey: <service-role-key>" -H "Authorization: Bearer <service-role-key>"
-- Option C: SQL (only works if users were created via raw SQL, not recommended)
-- DELETE FROM auth.users WHERE email NOT LIKE '%@supabase.io'; -- be careful!

-- ============================================================
-- 6. Verify cleanup (all counts should be 0 except rate_cards)
-- ============================================================
SELECT 'profiles' AS table_name, COUNT(*) FROM public.profiles
UNION ALL SELECT 'cycles', COUNT(*) FROM public.cycles
UNION ALL SELECT 'rentals', COUNT(*) FROM public.rentals
UNION ALL SELECT 'wallets', COUNT(*) FROM public.wallets
UNION ALL SELECT 'wallet_transactions', COUNT(*) FROM public.wallet_transactions
UNION ALL SELECT 'maintenance_tickets', COUNT(*) FROM public.maintenance_tickets
UNION ALL SELECT 'pending_registrations', COUNT(*) FROM public.pending_registrations
UNION ALL SELECT 'disputes', COUNT(*) FROM public.disputes
UNION ALL SELECT 'support_conversations', COUNT(*) FROM public.support_conversations
UNION ALL SELECT 'audit_events', COUNT(*) FROM public.audit_events
UNION ALL SELECT 'rate_cards (seed)', COUNT(*) FROM public.rate_cards;