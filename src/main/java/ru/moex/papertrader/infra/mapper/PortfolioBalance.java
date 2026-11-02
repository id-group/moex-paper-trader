package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;

/** Состояние денежных средств виртуального портфеля по одной валюте. */
public record PortfolioBalance(String currency, BigDecimal cash, BigDecimal locked) {}
