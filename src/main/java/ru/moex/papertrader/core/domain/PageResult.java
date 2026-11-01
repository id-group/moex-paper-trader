package ru.moex.papertrader.core.domain;

import java.util.List;

/** Страница результатов поиска. */
public record PageResult<T>(List<T> items, long totalRecords, int totalPages) {

  public PageResult {
    items = List.copyOf(items);
  }

  public static <T> PageResult<T> empty() {
    return new PageResult<>(List.of(), 0, 0);
  }
}
