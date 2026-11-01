package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Заявка, принятая от клиента, до валидации и присвоения биржевого идентификатора. */
public record NewOrder(
    UUID clientOrderId,
    String ticker,
    String classCode,
    Side side,
    OrderType orderType,
    TimeInForce timeInForce,
    long quantity,
    BigDecimal price) {}
