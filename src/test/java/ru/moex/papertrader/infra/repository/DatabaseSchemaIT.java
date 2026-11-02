package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;

/** Проверяет, что миграция V1 создаёт ровно минимальный набор таблиц этапа 3. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseSchemaIT extends PostgresContainerSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void createsExactlySevenDomainTables() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                + " AND table_name <> 'flyway_schema_history' ORDER BY table_name",
            String.class);

    assertThat(tables)
        .containsExactly(
            "account_balances",
            "accounts",
            "fills",
            "instruments",
            "nav_daily",
            "orders",
            "positions");
  }

  @Test
  void storesMoneyWithTwoAndPricesWithSixDecimalPlaces() {
    Map<String, Map<String, Object>> columns =
        jdbcTemplate
            .queryForList(
                "SELECT table_name, column_name, numeric_precision, numeric_scale"
                    + " FROM information_schema.columns WHERE table_schema = 'public'"
                    + " AND data_type = 'numeric'")
            .stream()
            .collect(
                Collectors.toMap(
                    row -> row.get("table_name") + "." + row.get("column_name"), row -> row));

    assertThat(columns.keySet())
        .contains(
            "account_balances.cash",
            "fills.commission",
            "nav_daily.cash",
            "instruments.lot_size",
            "orders.price",
            "fills.price",
            "positions.average_price");

    assertThat(columns.get("nav_daily.cash").get("numeric_scale")).isEqualTo(2);
    assertThat(columns.get("fills.commission").get("numeric_scale")).isEqualTo(2);
    assertThat(columns.get("instruments.lot_size").get("numeric_scale")).isEqualTo(6);
    assertThat(columns.get("fills.price").get("numeric_scale")).isEqualTo(6);
    assertThat(columns.get("positions.average_price").get("numeric_scale")).isEqualTo(6);
    assertThat(columns.get("nav_daily.cash").get("numeric_precision")).isEqualTo(19);
  }
}
