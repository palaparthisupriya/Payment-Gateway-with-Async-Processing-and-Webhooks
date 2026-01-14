-- 1. Merchants Table
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    webhook_url TEXT,
    webhook_secret VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Payments Table
CREATE TABLE payments (
    id VARCHAR(64) PRIMARY KEY, -- Format: pay_ + 16 chars
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount INTEGER NOT NULL, -- In cents/paise
    currency VARCHAR(3) DEFAULT 'INR',
    status VARCHAR(20) DEFAULT 'pending', -- pending, success, failed
    method VARCHAR(20), -- card, upi
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Refunds Table (Deliverable 2 Requirement)
CREATE TABLE refunds (
    id VARCHAR(64) PRIMARY KEY, -- Format: rfnd_ + 16 chars
    payment_id VARCHAR(64) NOT NULL REFERENCES payments(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount INTEGER NOT NULL,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- 4. Webhook Logs Table (For Async Delivery & Retries)
CREATE TABLE webhook_logs (
    id SERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    event VARCHAR(50) NOT NULL, -- payment.success, payment.failed, refund.processed
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    attempts INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    response_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Idempotency Keys Table (Prevent Duplicate Requests)
CREATE TABLE idempotency_keys (
    key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    response_payload JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (key, merchant_id)
);

-- Seed a test merchant for your evaluation
INSERT INTO merchants (id, name, email, webhook_url, webhook_secret)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'Test Merchant', 'test@example.com', 'http://webhook.site/test', 'whsec_test_abc123');