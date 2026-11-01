package ru.moex.papertrader.bks.api;

import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** Проверки параметров пагинации, общие для поиска заявок и сделок. */
final class BksPagination {

  private BksPagination() {}

  static void validate(int page, int size) {
    if (page < 0) {
      throw new InvalidRequestException("page должен быть >= 0");
    }
    if (size < 1 || size > 100) {
      throw new InvalidRequestException("size должен быть в диапазоне 1..100");
    }
  }
}
