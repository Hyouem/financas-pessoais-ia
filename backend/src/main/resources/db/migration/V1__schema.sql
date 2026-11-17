CREATE TABLE users (
  id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE accounts (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,initial_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user ON accounts(user_id);
CREATE TABLE categories (
  id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,kind VARCHAR(10) NOT NULL CHECK(kind IN ('INCOME','EXPENSE'))
);
CREATE INDEX idx_categories_user ON categories(user_id);
CREATE TABLE recurrences (
  id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  account_id UUID NOT NULL REFERENCES accounts(id),category_id UUID NOT NULL REFERENCES categories(id),
  amount NUMERIC(18,2) NOT NULL CHECK(amount>0),kind VARCHAR(10) NOT NULL CHECK(kind IN ('INCOME','EXPENSE')),
  note VARCHAR(200) NOT NULL DEFAULT '',day_of_month SMALLINT NOT NULL CHECK(day_of_month BETWEEN 1 AND 31),enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE transactions (
  id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  account_id UUID NOT NULL REFERENCES accounts(id),category_id UUID NOT NULL REFERENCES categories(id),
  amount NUMERIC(18,2) NOT NULL CHECK(amount>0),kind VARCHAR(10) NOT NULL CHECK(kind IN ('INCOME','EXPENSE')),
  note VARCHAR(200) NOT NULL DEFAULT '',occurred_on DATE NOT NULL,
  recurrence_id UUID REFERENCES recurrences(id),period_key CHAR(7),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(recurrence_id,period_key)
);
CREATE INDEX idx_transactions_user_date ON transactions(user_id,occurred_on);
CREATE TABLE budgets (
  id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category_id UUID NOT NULL REFERENCES categories(id),month_key CHAR(7) NOT NULL,
  limit_amount NUMERIC(18,2) NOT NULL CHECK(limit_amount>0),UNIQUE(user_id,category_id,month_key)
);
