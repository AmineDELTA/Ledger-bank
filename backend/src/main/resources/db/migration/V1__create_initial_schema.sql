CREATE TABLE IF NOT EXISTS accounts (
    id UUID NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    holder_name VARCHAR(255) NOT NULL,
    current_balance NUMERIC(19, 4) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number)
);

CREATE TABLE IF NOT EXISTS transaction_headers (
    id UUID NOT NULL,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_transaction_headers PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID NOT NULL,
    transaction_header_id UUID NOT NULL,
    account_id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction_header FOREIGN KEY (transaction_header_id) REFERENCES transaction_headers (id)
);

CREATE TABLE IF NOT EXISTS idempotent_request (
    idempotency_key VARCHAR(255) NOT NULL,
    response_status_code INTEGER,
    response_body TEXT,
    CONSTRAINT pk_idempotent_request PRIMARY KEY (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_header_id
    ON ledger_entries (transaction_header_id);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id
    ON ledger_entries (account_id);

INSERT INTO accounts (id, account_number, holder_name, current_balance)
VALUES ('11111111-1111-1111-1111-111111111111', 'ACC-1001', 'Mohamed Sender', 500.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, account_number, holder_name, current_balance)
VALUES ('22222222-2222-2222-2222-222222222222', 'ACC-1002', 'Amine Receiver', 0.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO transaction_headers (id, timestamp, description)
VALUES ('99999999-9999-9999-9999-999999999999', CURRENT_TIMESTAMP, 'System Initial Funding')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_entries (id, transaction_header_id, account_id, type, amount)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', '99999999-9999-9999-9999-999999999999', '11111111-1111-1111-1111-111111111111', 'CREDIT', 500.00)
ON CONFLICT (id) DO NOTHING;
