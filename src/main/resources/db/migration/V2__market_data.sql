-- Схема рыночных данных этапа 4: снапшоты RECORD/REPLAY и торговый календарь.
-- Снапшоты пишет декоратор RECORD, читает REPLAY: payload — JSON-представление доменного
-- MarketDataSnapshot (QuoteSnapshot / OrderBookEvent / TradeEvent), поэтому отдельные колонки
-- котировок, стакана и сделок не нужны. Время — только TIMESTAMP WITH TIME ZONE (UTC).
-- Торговый календарь — кэш расписания MOEX ISS: одна строка на (движок, рынок, режим, дата).

-- Поток рыночных данных: тип события (QUOTE/ORDER_BOOK/TRADE), инструмент и момент события.
CREATE TABLE market_data_snapshots (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMP WITH TIME ZONE NOT NULL,
    kind VARCHAR(16) NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    class_code VARCHAR(8) NOT NULL,
    payload JSONB NOT NULL
);

-- Индекс таймлайна: выборка снапшотов инструмента за окно [from, to] по возрастанию ts.
CREATE INDEX idx_market_data_snapshots_lookup
    ON market_data_snapshots (ticker, class_code, ts);

-- Торговый календарь: is_trading = false для выходных и неторговых дней; окно сессии в UTC
-- заполнено только для торговых дней.
CREATE TABLE trading_calendar (
    id BIGSERIAL PRIMARY KEY,
    engine VARCHAR(16) NOT NULL,
    market VARCHAR(16) NOT NULL,
    board VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    is_trading BOOLEAN NOT NULL,
    open_utc TIMESTAMP WITH TIME ZONE,
    close_utc TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_trading_calendar_engine_market_board_date
        UNIQUE (engine, market, board, trade_date)
);
