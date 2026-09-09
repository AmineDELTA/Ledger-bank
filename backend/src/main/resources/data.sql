INSERT INTO accounts (id, holderName) 
VALUES ('11111111-1111-1111-1111-111111111111', 'Mohamed Sender')
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, holderName) 
VALUES ('22222222-2222-2222-2222-222222222222', 'Amine Receiver')
ON CONFLICT (id) DO NOTHING;

INSERT INTO transaction_headers (id, description)
VALUES ('99999999-9999-9999-9999-999999999999', 'System Initial Funding')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_entries (id, account_id, amount, transaction_header_id, type)
VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 
    '11111111-1111-1111-1111-111111111111', 
    500.00, 
    '99999999-9999-9999-9999-999999999999', 
    'CREDIT'
) ON CONFLICT (id) DO NOTHING;