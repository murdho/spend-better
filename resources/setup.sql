CREATE TABLE transactions (
    id SERIAL PRIMARY KEY,
    date DATE NOT NULL,
    other TEXT,
    amount DECIMAL(15, 2) NOT NULL,
    description TEXT,
    currency TEXT,
    filename TEXT,
    category TEXT
);
