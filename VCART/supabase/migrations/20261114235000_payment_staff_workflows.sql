-- VCART payment attempts, refund processing, and staff accounts.
-- Apply only after reviewing this migration against the target database.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.refund_requests
        GROUP BY order_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate refund requests exist; review them before adding one-refund-per-order uniqueness.';
    END IF;
END;
$$;

ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE public.users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('CUSTOMER', 'ADMIN', 'STAFF'));

ALTER TABLE public.staff_members
    ADD COLUMN IF NOT EXISTS user_id BIGINT
    REFERENCES public.users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_members_user_id
    ON public.staff_members(user_id)
    WHERE user_id IS NOT NULL;

UPDATE public.staff_members s
SET user_id = u.id
FROM public.users u
WHERE s.user_id IS NULL
  AND u.role = 'STAFF'
  AND LOWER(u.email) = LOWER(s.email);

CREATE TABLE IF NOT EXISTS public.payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES public.orders(id),
    provider VARCHAR(40) NOT NULL
        CHECK (provider IN ('MOCK', 'RAZORPAY', 'STRIPE', 'LEGACY')),
    provider_reference VARCHAR(160) UNIQUE,
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(160);

INSERT INTO public.payments
    (order_id, provider, idempotency_key, amount, currency, status)
SELECT o.id,
       'LEGACY',
       'legacy-order-' || o.id::TEXT,
       o.total_amount,
       'INR',
       CASE o.payment_status
           WHEN 'PAID' THEN 'SUCCEEDED'
           WHEN 'FAILED' THEN 'FAILED'
           WHEN 'REFUNDED' THEN 'REFUNDED'
           ELSE 'PENDING'
       END
FROM public.orders o
ON CONFLICT (order_id) DO NOTHING;

ALTER TABLE public.refund_requests
    ADD COLUMN IF NOT EXISTS payment_id BIGINT
    REFERENCES public.payments(id);

ALTER TABLE public.refund_requests
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);

ALTER TABLE public.refund_requests
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(160);

UPDATE public.refund_requests r
SET payment_id = p.id
FROM public.payments p
WHERE r.payment_id IS NULL
  AND p.order_id = r.order_id;

UPDATE public.refund_requests
SET idempotency_key = 'legacy-refund-' || id::TEXT
WHERE idempotency_key IS NULL;

ALTER TABLE public.refund_requests
    ALTER COLUMN payment_id SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE public.refund_requests
    DROP CONSTRAINT IF EXISTS refund_requests_status_check;

ALTER TABLE public.refund_requests
    ADD CONSTRAINT refund_requests_status_check
    CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'PROCESSED'));

UPDATE public.refund_requests
SET status = 'APPROVED',
    admin_note = CONCAT_WS(
        E'\n',
        NULLIF(admin_note, ''),
        'Legacy PROCESSED label did not represent a provider transfer; verify before processing.'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PROCESSED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_requests_order_id
    ON public.refund_requests(order_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_requests_idempotency_key
    ON public.refund_requests(idempotency_key);

CREATE INDEX IF NOT EXISTS idx_payments_status
    ON public.payments(status);
