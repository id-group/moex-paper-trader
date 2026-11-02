package ru.moex.papertrader;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * База интеграционных тестов, которым нужен PostgreSQL 18 из Testcontainers.
 *
 * <p>Контейнер — синглтон на JVM: при per-class жизненном цикле Testcontainers останавливает
 * контейнер после каждого тестового класса и запускает новый на другом порту, а Spring кэширует
 * контекст между классами — DataSource указывал бы на уже остановленный контейнер. Поэтому
 * {@code @Testcontainers} и {@code @Container} здесь не используются: контейнер стартует один раз в
 * статическом блоке и живёт до завершения JVM.
 */
public abstract class PostgresContainerSupport {

  @ServiceConnection
  private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18");

  static {
    POSTGRESQL.start();
  }

  /**
   * JDBC-URL контейнера — для тестов, которые поднимают контекст вручную, без @ServiceConnection.
   */
  protected static String jdbcUrl() {
    return POSTGRESQL.getJdbcUrl();
  }

  /** Имя пользователя контейнера для ручного запуска контекста. */
  protected static String jdbcUsername() {
    return POSTGRESQL.getUsername();
  }

  /** Пароль контейнера для ручного запуска контекста. */
  protected static String jdbcPassword() {
    return POSTGRESQL.getPassword();
  }
}
