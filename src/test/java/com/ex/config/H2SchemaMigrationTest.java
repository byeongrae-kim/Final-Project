package com.ex.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class H2SchemaMigrationTest {

    @Test
    void convertsLegacyPaymentProviderEnumWithoutDeletingRows() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:payment-provider-migration;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table purchase_order (
                    purchase_order_id bigint primary key,
                    payment_provider enum('KAKAO', 'TOSS'),
                    provider_transaction_id varchar(200)
                )
                """);
        jdbcTemplate.update(
                "insert into purchase_order(purchase_order_id, payment_provider) values (?, ?)",
                1L,
                "KAKAO"
        );

        new H2SchemaMigration(dataSource).run(null);

        jdbcTemplate.update(
                "insert into purchase_order(purchase_order_id, payment_provider) values (?, ?)",
                2L,
                "PORTONE"
        );

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from purchase_order",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select payment_provider from purchase_order where purchase_order_id = 1",
                String.class
        )).isEqualTo("KAKAO");

        jdbcTemplate.update(
                "update purchase_order set provider_transaction_id = ? where purchase_order_id = ?",
                "imp_unique_001",
                1L
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                "update purchase_order set provider_transaction_id = ? where purchase_order_id = ?",
                "imp_unique_001",
                2L
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
