-- Idempotency arbiter for the wallet ledger: ON CONFLICT (user_id, reference_code).
-- Live databases built by LiveMigrationExecutor predate the unique constraint
-- declared in 202609270001 (CREATE TABLE IF NOT EXISTS skipped the existing
-- table), so every ON CONFLICT insert on wallet_transactions fails there —
-- including the pre-existing WalletService ledger path, not just payouts.
-- This backfills the constraint. Idempotent and re-runnable.
-- Style follows existing migrations: lowercase, guarded, revoke-neutral (no
-- privilege changes; table grants already cover the app role).

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'wallet_transactions'
          AND column_name = 'reference_code'
    ) THEN
        RAISE NOTICE 'wallet_transactions.reference_code absent: skipping ledger constraint backfill';
        RETURN;
    END IF;

    -- 1. Remove exact-key duplicates (keep earliest row per key). NULL
    -- reference codes never conflict in Postgres and are left alone.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'wallet_transactions'
          AND column_name = 'timestamp'
    ) THEN
        DELETE FROM public.wallet_transactions t
        USING (
            SELECT id FROM (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY user_id, reference_code
                    ORDER BY "timestamp" NULLS LAST, id
                ) AS rn
                FROM public.wallet_transactions
                WHERE reference_code IS NOT NULL
            ) s WHERE rn > 1
        ) d WHERE t.id = d.id;
    ELSE
        DELETE FROM public.wallet_transactions t
        USING (
            SELECT id FROM (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY user_id, reference_code
                    ORDER BY id
                ) AS rn
                FROM public.wallet_transactions
                WHERE reference_code IS NOT NULL
            ) s WHERE rn > 1
        ) d WHERE t.id = d.id;
    END IF;

    -- 2. The arbiter both ON CONFLICT (user_id, reference_code) sites need.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'wallet_tx_user_ref_uidx'
    ) THEN
        CREATE UNIQUE INDEX wallet_tx_user_ref_uidx
            ON public.wallet_transactions (user_id, reference_code);
    END IF;
END
$$;
