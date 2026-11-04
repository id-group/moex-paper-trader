package ru.moex.papertrader.core.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Результат матчинга заявки по стакану.
 *
 * <p>{@link #fills} — исполнения по возрастанию цены стакана; {@link #remainderAction} говорит, что
 * делать с остатком: оставить активным (лимитка ждёт), снять (маркет-остаток) или отклонить заявку
 * целиком.
 */
public record MatchResult(
    List<MatchFill> fills, RemainderAction remainderAction, String rejectReason) {

  /** Судьба неисполненного остатка. */
  public enum RemainderAction {
    /** Остаток остаётся активным (лимитная заявка ждёт рынок). */
    KEEP,
    /** Остаток снимается (маркет-заявка исполнена частично). */
    CANCEL,
    /** Заявка отклоняется целиком. */
    REJECT
  }

  public MatchResult {
    fills = List.copyOf(Objects.requireNonNull(fills, "fills"));
    Objects.requireNonNull(remainderAction, "remainderAction");
    if (remainderAction == RemainderAction.REJECT
        && (rejectReason == null || rejectReason.isBlank())) {
      throw new IllegalArgumentException("Причина отклонения обязательна");
    }
  }

  public static MatchResult none() {
    return new MatchResult(List.of(), RemainderAction.KEEP, null);
  }

  public static MatchResult reject(String reason) {
    return new MatchResult(
        List.of(), RemainderAction.REJECT, Objects.requireNonNull(reason, "reason"));
  }

  public boolean rejected() {
    return remainderAction == RemainderAction.REJECT;
  }

  public Optional<String> rejectionReason() {
    return Optional.ofNullable(rejectReason);
  }

  /** Суммарный объём запланированных исполнений. */
  public long quantity() {
    return fills.stream().mapToLong(MatchFill::quantity).sum();
  }
}
