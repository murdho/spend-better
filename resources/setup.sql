CREATE FUNCTION calc_transaction_hash(date DATE, other TEXT, amount DECIMAL, description TEXT, currency TEXT)
RETURNS TEXT AS
$$
SELECT md5(
    concat_ws(
        ':',
        'date', date,
        'other', other,
        'amount', amount,
        'description', description,
        'currency', currency
    )
)
$$
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE;

CREATE TABLE imports
(
    id          SERIAL PRIMARY KEY,
    bank        TEXT NOT NULL,
    filename    TEXT NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE transactions
(
    id          SERIAL PRIMARY KEY,
    category    TEXT,
    date        DATE,
    other       TEXT,
    amount      DECIMAL(15, 2) NOT NULL,
    description TEXT,
    currency    TEXT,
    import_id   INTEGER NOT NULL REFERENCES imports(id),
    hash        TEXT NOT NULL GENERATED ALWAYS AS (calc_transaction_hash(date, other, amount, description, currency)) STORED
);

CREATE VIEW duplicate_transactions AS
(
    SELECT
        dupl.*,
        orig.id AS original_id
    FROM transactions orig
    JOIN transactions dupl ON dupl.hash = orig.hash AND dupl.import_id != orig.import_id AND dupl.id > orig.id
);
