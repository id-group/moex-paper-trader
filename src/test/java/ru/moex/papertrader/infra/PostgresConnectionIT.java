package ru.moex.papertrader.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;

/** Дымовой тест: JPA-срез получает DataSource реального PostgreSQL 18 из Testcontainers. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresConnectionIT extends PostgresContainerSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void connectsToPostgres18() {
    String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);

    assertThat(serverVersion).startsWith("18.");
  }
}
