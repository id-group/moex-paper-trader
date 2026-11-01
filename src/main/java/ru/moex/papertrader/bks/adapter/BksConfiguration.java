package ru.moex.papertrader.bks.adapter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Регистрация настроек адаптера БКС. */
@Configuration
@EnableConfigurationProperties(BksProperties.class)
public class BksConfiguration {}
