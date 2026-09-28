-- Payment method and cash collection on rentals.
-- Closes the Dock Pay gap: cash-at-hub rides had no durable record of how the
-- ride was paid, so the return flow could neither collect cash nor cap the owner
-- payout by money actually received.
--
-- Settlement model (owner paid at return, platform holds the fare in escrow):
--   * CAMPUS_PAY  - quoted fare taken from the wallet at booking, overtime/fine
--                   taken at return; the owner is credited the settled payout.
--   * DOCK_PAY    - nothing is taken at booking; the hub confirms the cash taken
--                   at return, the owner is credited from that cash only, and
--                   anything short of the settled fare becomes a rental due.
--
-- payment_method is NULLABLE by design: pre-migration rows keep NULL and the
-- application treats NULL as CAMPUS_PAY. Style matches existing migrations.

-- 1. Durable payment method + cash taken, on the rental itself.
--    Defaulted to CAMPUS_PAY so every pre-migration row stays valid: the old app
--    had no cash collection path, so no stored ride was actually settled in cash.
alter table public.rentals add column if not exists payment_method text
    default 'CAMPUS_PAY' check (payment_method in ('CAMPUS_PAY', 'DOCK_PAY'));
alter table public.rentals add column if not exists cash_collected_poisha integer
    check (cash_collected_poisha >= 0);

-- 2. Backfill rides that are still in flight. Booking records the payment row
--    UNPAID for a dock ride and PAID for Campus Pay, and only the return path
--    flips UNPAID to PAID, so an ACTIVE rental still carries that signal.
--    Returned rows keep the CAMPUS_PAY default.
update public.rentals r
set payment_method = case when p.state = 'UNPAID' then 'DOCK_PAY' else 'CAMPUS_PAY' end
from public.payment_records p
where p.rental_id = r.id
  and r.state = 'ACTIVE';

-- 3. Cash-at-hub audit read: dock rides settled with a cash amount.
create index if not exists rentals_cash_settlement_idx
    on public.rentals (payment_method, returned_at desc)
    where state = 'RETURNED';

-- 4. RLS posture matches the other service tables: app role only.
alter table public.rentals enable row level security;
revoke all on public.rentals from anon, authenticated;
