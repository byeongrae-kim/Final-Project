package com.ex.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hibernate ddl-auto=update가 기존 H2 enum CHECK 제약조건을 갱신하지 않는 문제를 보정합니다.
 * 기존 KAKAO/TOSS 주문 데이터는 유지하고 PORTONE 값만 추가로 허용합니다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class H2SchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(
                    connection.getMetaData().getDatabaseProductName()
            )) {
                return;
            }

            migratePaymentProviderConstraint(connection);
            createPaymentTransactionUniqueIndex(connection);
        }
    }

    private void createPaymentTransactionUniqueIndex(Connection connection) throws Exception {
        if (!columnExists(connection, "PURCHASE_ORDER", "PROVIDER_TRANSACTION_ID")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create unique index if not exists
                    ux_purchase_order_provider_transaction
                    on purchase_order(provider_transaction_id)
                    """);
        }
        log.info("H2 포트원 결제번호 중복 방지 인덱스를 확인했습니다.");
    }

    private boolean columnExists(
            Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        String query = """
                select count(*)
                from information_schema.columns
                where upper(table_schema) = 'PUBLIC'
                  and upper(table_name) = ?
                  and upper(column_name) = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tableName.toUpperCase(Locale.ROOT));
            statement.setString(2, columnName.toUpperCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void migratePaymentProviderConstraint(Connection connection) throws Exception {
        boolean migrated = migrateLegacyEnumColumn(connection);
        String query = """
                select tc.constraint_name, cc.check_clause
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on tc.constraint_catalog = cc.constraint_catalog
                 and tc.constraint_schema = cc.constraint_schema
                 and tc.constraint_name = cc.constraint_name
                where upper(tc.table_schema) = 'PUBLIC'
                  and upper(tc.table_name) = 'PURCHASE_ORDER'
                  and upper(tc.constraint_type) = 'CHECK'
                """;

        List<String> outdatedConstraints = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String constraintName = resultSet.getString("constraint_name");
                String checkClause = resultSet.getString("check_clause");
                String normalized = checkClause == null
                        ? ""
                        : checkClause.toUpperCase(Locale.ROOT);

                if (normalized.contains("PAYMENT_PROVIDER")
                        && !normalized.contains("PORTONE")) {
                    outdatedConstraints.add(constraintName);
                }
            }
        }

        if (outdatedConstraints.isEmpty()) {
            if (migrated) {
                log.info("H2 payment_provider 컬럼을 PORTONE 호환 형식으로 갱신했습니다.");
            }
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String constraintName : outdatedConstraints) {
                statement.execute("alter table purchase_order drop constraint "
                        + quoteIdentifier(constraintName));
            }
        }

        log.info("H2 payment_provider 제약조건을 PORTONE 호환 형식으로 갱신했습니다.");
    }

    private boolean migrateLegacyEnumColumn(Connection connection) throws Exception {
        String query = """
                select data_type
                from information_schema.columns
                where upper(table_schema) = 'PUBLIC'
                  and upper(table_name) = 'PURCHASE_ORDER'
                  and upper(column_name) = 'PAYMENT_PROVIDER'
                """;

        String dataType = null;
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                dataType = resultSet.getString("data_type");
            }
        }

        if (dataType == null
                || !dataType.toUpperCase(Locale.ROOT).contains("ENUM")) {
            return false;
        }

        /*
         * H2 ENUM('KAKAO','TOSS') 타입은 Hibernate ddl-auto=update가
         * 새 enum 상수 PORTONE을 추가하지 못합니다. VARCHAR로 바꾸면
         * 기존 값은 그대로 유지되고 Java PaymentProvider enum이 값 검증을 담당합니다.
         */
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    alter table purchase_order
                    alter column payment_provider varchar(20)
                    """);
        }
        return true;
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
