-- Минимальная схема персистентности этапа 3 (решение владельца): ровно семь таблиц.
-- Деньги — NUMERIC(19, 2) (масштаб Money.SCALE), цены и средние цены — NUMERIC(19, 6)
-- (масштаб VirtualPortfolio.AVERAGE_PRICE_SCALE). Время — только TIMESTAMP WITH TIME ZONE.

CREATE TABLE accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Денежные средства и блокировки по валютам: одна строка на пару (счёт, валюта).
CREATE TABLE account_balances (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    currency VARCHAR(8) NOT NULL,
    cash NUMERIC(19, 2) NOT NULL DEFAULT 0,
    locked NUMERIC(19, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_account_balances_account_currency UNIQUE (account_id, currency)
);

-- Инструменты: доменная идентичность — пара (ticker, class_code).
CREATE TABLE instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL,
    class_code VARCHAR(32) NOT NULL,
    type VARCHAR(32) NOT NULL,
    short_name VARCHAR(255),
    display_name VARCHAR(255),
    isin VARCHAR(32),
    lot_size NUMERIC(19, 6) NOT NULL,
    minimum_step NUMERIC(19, 6) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    CONSTRAINT uq_instruments_ticker_class_code UNIQUE (ticker, class_code)
);

-- Заявки: биржевой идентификатор order_id — естественный ключ, order_num уникален.
CREATE TABLE orders (
    order_id VARCHAR(64) PRIMARY KEY,
    order_num BIGINT NOT NULL,
    client_order_id UUID NOT NULL,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    instrument_id BIGINT NOT NULL REFERENCES instruments (id),
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(32) NOT NULL,
    time_in_force VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    price NUMERIC(19, 6),
    client_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    executed_quantity BIGINT NOT NULL DEFAULT 0,
    average_price NUMERIC(19, 6),
    CONSTRAINT uq_orders_order_num UNIQUE (order_num),
    CONSTRAINT uq_orders_client_order_id UNIQUE (client_order_id)
);

-- Сделки: доменный Fill четырёхполевой, ссылки на заявку/счёт/инструмент добавлены слоем infra.
CREATE TABLE fills (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES orders (order_id),
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    instrument_id BIGINT NOT NULL REFERENCES instruments (id),
    price NUMERIC(19, 6) NOT NULL,
    quantity BIGINT NOT NULL,
    commission NUMERIC(19, 2) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Позиции: одна строка на пару (счёт, инструмент); количество со знаком (short — отрицательное).
CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    instrument_id BIGINT NOT NULL REFERENCES instruments (id),
    quantity BIGINT NOT NULL,
    average_price NUMERIC(19, 6) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_positions_account_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT ck_positions_quantity_non_zero CHECK (quantity <> 0),
    CONSTRAINT ck_positions_average_price_non_negative CHECK (average_price >= 0)
);

-- Ежедневный NAV: один снапшот на пару (счёт, торговый день); holdings — JSON-массив
-- NavSnapshot.Holding (ticker, quantity, closingPrice), чтобы восстановить срез без потерь.
CREATE TABLE nav_daily (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    nav_date DATE NOT NULL,
    as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    cash NUMERIC(19, 2) NOT NULL,
    commission NUMERIC(19, 2) NOT NULL,
    variation_margin NUMERIC(19, 2) NOT NULL,
    market_value NUMERIC(19, 2) NOT NULL,
    nav NUMERIC(19, 2) NOT NULL,
    holdings VARCHAR(4000) NOT NULL DEFAULT '[]',
    CONSTRAINT uq_nav_daily_account_date UNIQUE (account_id, nav_date)
);
