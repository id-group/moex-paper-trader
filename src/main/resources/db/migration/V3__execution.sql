-- Схема этапа 5: срок действия GTD и журнал переходов жизненного цикла заявки.
-- expiry_date относится только к timeInForce=GTD; для DAY/FOK колонка остаётся NULL.
ALTER TABLE orders ADD COLUMN expiry_date DATE;

-- Журнал событий заявки: каждое изменение статуса фиксируется отдельной строкой.
-- previous_status = NULL только у CREATED; quantity/price описывают объём события
-- (исполненная часть для fill, остаток для отмены/экспирации), reason — причину отказа.
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES orders (order_id),
    event_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    quantity BIGINT,
    price NUMERIC(19, 6),
    reason VARCHAR(512),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Журнал читается по заявке в хронологическом порядке; id разрешает одинаковые метки времени.
CREATE INDEX idx_order_events_order_lookup ON order_events (order_id, occurred_at, id);
