-- Схема этапа 6: отчётность, метки стратегий и опорная цена сделки.
-- strategy_id — необязательное расширение контракта (заголовок X-Strategy-Id);
-- reference_price — цена стакана до проскальзывания, NULL у сделок до этапа 6.

ALTER TABLE orders ADD COLUMN strategy_id VARCHAR(64);
ALTER TABLE fills ADD COLUMN reference_price NUMERIC(19, 6);

-- Отчётность читает сделки счёта за период и группирует их по стратегии.
CREATE INDEX idx_orders_account_strategy ON orders (account_id, strategy_id);
CREATE INDEX idx_fills_account_executed_at ON fills (account_id, executed_at);

-- Сохранённый отчёт: payload — канонический JSON ReportPayload (кривая NAV, метрики, журнал).
CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    label VARCHAR(128),
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT ck_reports_period CHECK (period_to >= period_from)
);

CREATE INDEX idx_reports_account_generated_at ON reports (account_id, generated_at);

-- Результат сравнения с внешним бэктестом: вход и таблица отклонений в payload.
CREATE TABLE backtest_results (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT REFERENCES reports (id) ON DELETE SET NULL,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (account_id),
    label VARCHAR(128) NOT NULL,
    threshold NUMERIC(9, 6) NOT NULL,
    passed BOOLEAN NOT NULL,
    compared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL
);

CREATE INDEX idx_backtest_results_account_compared_at
    ON backtest_results (account_id, compared_at);
