package com.ex.migration;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 기존 H2 파일의 전체 사용자 테이블을 MySQL로 옮기는 일회성 도구입니다.
 *
 * <p>일반 Spring Boot 실행에는 참여하지 않으며 Gradle의
 * {@code migrateH2ToMySql} 작업으로만 실행됩니다.</p>
 */
public final class H2ToMySqlMigrator {

    private static final String DEFAULT_H2_URL =
            "jdbc:h2:file:./data/finalproject;AUTO_SERVER=TRUE";
    private static final int BATCH_SIZE = 500;

    private H2ToMySqlMigrator() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help()) {
            printUsage();
            return;
        }

        String h2Url = environment("MIGRATION_H2_URL", DEFAULT_H2_URL);
        String h2User = environment("MIGRATION_H2_USER", "sa");
        String h2Password = environment("MIGRATION_H2_PASSWORD", "");
        String mysqlUrl = requiredEnvironment("MIGRATION_MYSQL_URL");
        String mysqlUser = requiredEnvironment("MIGRATION_MYSQL_USER");
        String mysqlPassword = environment("MIGRATION_MYSQL_PASSWORD", "");

        try (Connection source = DriverManager.getConnection(
                    h2Url, h2User, h2Password);
             Connection target = DriverManager.getConnection(
                    mysqlUrl, mysqlUser, mysqlPassword)) {
            MigrationPlan plan = createPlan(source, target);
            printPlan(plan, options);

            if (!options.execute()) {
                System.out.println();
                System.out.println("미리보기만 완료했습니다. 데이터는 변경되지 않았습니다.");
                System.out.println("실제 이전: --args='--execute'");
                System.out.println("대상 초기화 후 이전: --args='--execute --reset-target'");
                return;
            }

            migrate(source, target, plan, options.resetTarget());
        }
    }

    private static MigrationPlan createPlan(
            Connection source,
            Connection target) throws SQLException {
        Map<String, String> sourceTables = tables(source, "PUBLIC");
        Map<String, String> targetTables = tables(target, target.getCatalog());

        if (sourceTables.isEmpty()) {
            throw new IllegalStateException("H2에서 이전할 사용자 테이블을 찾지 못했습니다.");
        }

        List<String> missingTargetTables = new ArrayList<>();
        List<TablePlan> tablePlans = new ArrayList<>();

        for (Map.Entry<String, String> sourceTable : sourceTables.entrySet()) {
            String key = sourceTable.getKey();
            String targetTable = targetTables.get(key);
            if (targetTable == null) {
                missingTargetTables.add(sourceTable.getValue());
                continue;
            }

            List<Column> sourceColumns = columns(source, sourceTable.getValue());
            Map<String, Column> targetColumns = columnMap(
                    columns(target, targetTable));
            List<String> missingColumns = sourceColumns.stream()
                    .filter(column -> !targetColumns.containsKey(
                            normalize(column.name())))
                    .map(Column::name)
                    .toList();
            if (!missingColumns.isEmpty()) {
                throw new IllegalStateException(
                        "MySQL 테이블 " + targetTable
                                + "에 H2 컬럼이 없습니다: " + missingColumns);
            }

            tablePlans.add(new TablePlan(
                    sourceTable.getValue(),
                    targetTable,
                    sourceColumns,
                    count(source, sourceTable.getValue()),
                    count(target, targetTable)));
        }

        if (!missingTargetTables.isEmpty()) {
            throw new IllegalStateException(
                    "MySQL에 다음 테이블이 없습니다: " + missingTargetTables
                            + System.lineSeparator()
                            + "애플리케이션을 MySQL로 한 번 실행해 스키마를 만든 뒤 "
                            + "중지하고 다시 시도하세요.");
        }

        tablePlans.sort(Comparator.comparing(TablePlan::targetTable));
        return new MigrationPlan(tablePlans);
    }

    private static void printPlan(MigrationPlan plan, Options options) {
        System.out.println("H2 -> MySQL 데이터 이전 계획");
        System.out.println("모드: " + (options.execute() ? "실행" : "미리보기"));
        System.out.println("대상 초기화: " + options.resetTarget());
        System.out.println();
        System.out.printf("%-38s %12s %12s%n", "TABLE", "H2", "MYSQL");
        for (TablePlan table : plan.tables()) {
            System.out.printf(
                    "%-38s %,12d %,12d%n",
                    table.targetTable(),
                    table.sourceRows(),
                    table.targetRows());
        }
        System.out.println();
        System.out.printf("H2 전체 행: %,d%n", plan.sourceRows());
        System.out.printf("MySQL 현재 행: %,d%n", plan.targetRows());
    }

    private static void migrate(
            Connection source,
            Connection target,
            MigrationPlan plan,
            boolean resetTarget) throws SQLException {
        if (plan.targetRows() > 0 && !resetTarget) {
            throw new IllegalStateException(
                    "MySQL에 이미 " + plan.targetRows() + "개 행이 있습니다. "
                            + "기존 데이터를 보호하기 위해 중단했습니다. "
                            + "H2 내용으로 완전히 교체하려면 --reset-target을 함께 사용하세요.");
        }

        boolean originalAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        setForeignKeyChecks(target, false);
        try {
            if (resetTarget) {
                clearTarget(target, plan);
            }

            for (TablePlan table : plan.tables()) {
                long copied = copyTable(source, target, table);
                System.out.printf(
                        "이전 완료: %-38s %,d행%n",
                        table.targetTable(), copied);
            }

            verify(target, plan);
            setForeignKeyChecks(target, true);
            target.commit();
            System.out.println();
            System.out.printf(
                    "이전 성공: %d개 테이블, 총 %,d행%n",
                    plan.tables().size(), plan.sourceRows());
        } catch (Exception exception) {
            target.rollback();
            try {
                setForeignKeyChecks(target, true);
            } catch (SQLException ignored) {
                // 원래 예외를 유지합니다.
            }
            if (exception instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw exception;
        } finally {
            target.setAutoCommit(originalAutoCommit);
        }
    }

    private static void clearTarget(
            Connection target,
            MigrationPlan plan) throws SQLException {
        String quote = identifierQuote(target);
        try (Statement statement = target.createStatement()) {
            for (TablePlan table : plan.tables()) {
                statement.executeUpdate(
                        "DELETE FROM " + quoted(quote, table.targetTable()));
            }
        }
    }

    private static long copyTable(
            Connection source,
            Connection target,
            TablePlan table) throws SQLException {
        if (table.sourceRows() == 0) {
            return 0;
        }

        String sourceQuote = identifierQuote(source);
        String targetQuote = identifierQuote(target);
        String sourceSql = "SELECT " + joinColumns(
                sourceQuote, table.columns())
                + " FROM " + quoted(sourceQuote, table.sourceTable());
        String placeholders = String.join(
                ", ", table.columns().stream().map(column -> "?").toList());
        String insertSql = "INSERT INTO "
                + quoted(targetQuote, table.targetTable())
                + " (" + joinColumns(targetQuote, table.columns()) + ") VALUES ("
                + placeholders + ")";

        long copied = 0;
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(sourceSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            int pendingBatch = 0;
            while (rows.next()) {
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    bind(insert, index, rows, metadata.getColumnType(index));
                }
                insert.addBatch();
                pendingBatch++;
                copied++;
                if (pendingBatch == BATCH_SIZE) {
                    insert.executeBatch();
                    pendingBatch = 0;
                }
            }
            if (pendingBatch > 0) {
                insert.executeBatch();
            }
        }
        return copied;
    }

    private static void bind(
            PreparedStatement statement,
            int index,
            ResultSet row,
            int sqlType) throws SQLException {
        switch (sqlType) {
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR,
                    Types.LONGNVARCHAR -> {
                String value = row.getString(index);
                if (value == null) {
                    statement.setNull(index, sqlType);
                } else {
                    statement.setString(index, value);
                }
            }
            case Types.BLOB, Types.BINARY, Types.VARBINARY,
                    Types.LONGVARBINARY -> {
                byte[] value = row.getBytes(index);
                if (value == null) {
                    statement.setNull(index, sqlType);
                } else {
                    statement.setBytes(index, value);
                }
            }
            case Types.BOOLEAN, Types.BIT -> {
                boolean value = row.getBoolean(index);
                if (row.wasNull()) {
                    statement.setNull(index, sqlType);
                } else {
                    statement.setBoolean(index, value);
                }
            }
            default -> statement.setObject(index, row.getObject(index));
        }
    }

    private static void verify(
            Connection target,
            MigrationPlan plan) throws SQLException {
        List<String> failures = new ArrayList<>();
        for (TablePlan table : plan.tables()) {
            long targetRows = count(target, table.targetTable());
            if (targetRows != table.sourceRows()) {
                failures.add(table.targetTable() + " H2=" + table.sourceRows()
                        + ", MySQL=" + targetRows);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "이전 후 행 수 검증에 실패했습니다: " + failures);
        }
    }

    private static Map<String, String> tables(
            Connection connection,
            String schemaOrCatalog) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean h2 = metadata.getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("h2");
        String catalog = h2 ? null : schemaOrCatalog;
        String schema = h2 ? "PUBLIC" : null;
        Map<String, String> tables = new LinkedHashMap<>();
        try (ResultSet result = metadata.getTables(
                catalog, schema, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                String table = result.getString("TABLE_NAME");
                tables.put(normalize(table), table);
            }
        }
        return tables;
    }

    private static List<Column> columns(
            Connection connection,
            String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean h2 = metadata.getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("h2");
        String catalog = h2 ? null : connection.getCatalog();
        String schema = h2 ? "PUBLIC" : null;
        List<Column> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(
                catalog, schema, table, "%")) {
            while (result.next()) {
                columns.add(new Column(
                        result.getString("COLUMN_NAME"),
                        result.getInt("DATA_TYPE"),
                        result.getInt("ORDINAL_POSITION")));
            }
        }
        columns.sort(Comparator.comparingInt(Column::position));
        return columns;
    }

    private static Map<String, Column> columnMap(List<Column> columns) {
        Map<String, Column> result = new LinkedHashMap<>();
        for (Column column : columns) {
            result.put(normalize(column.name()), column);
        }
        return result;
    }

    private static long count(
            Connection connection,
            String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM "
                + quoted(identifierQuote(connection), table);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void setForeignKeyChecks(
            Connection target,
            boolean enabled) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=" + (enabled ? "1" : "0"));
        }
    }

    private static String joinColumns(
            String quote,
            List<Column> columns) {
        return String.join(", ", columns.stream()
                .map(column -> quoted(quote, column.name()))
                .toList());
    }

    private static String identifierQuote(Connection connection)
            throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        return quote == null ? "" : quote.trim();
    }

    private static String quoted(String quote, String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "지원하지 않는 식별자입니다: " + identifier);
        }
        return quote + identifier + quote;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "필수 환경변수가 없습니다: " + name);
        }
        return value;
    }

    private static void printUsage() {
        System.out.println("사용법:");
        System.out.println("  ./gradlew migrateH2ToMySql");
        System.out.println("  ./gradlew migrateH2ToMySql --args='--execute'");
        System.out.println(
                "  ./gradlew migrateH2ToMySql --args='--execute --reset-target'");
        System.out.println();
        System.out.println("필수 환경변수:");
        System.out.println("  MIGRATION_MYSQL_URL");
        System.out.println("  MIGRATION_MYSQL_USER");
        System.out.println("  MIGRATION_MYSQL_PASSWORD (비밀번호가 있을 때)");
        System.out.println();
        System.out.println("선택 환경변수:");
        System.out.println("  MIGRATION_H2_URL");
        System.out.println("  MIGRATION_H2_USER");
        System.out.println("  MIGRATION_H2_PASSWORD");
    }

    private record Options(boolean execute, boolean resetTarget, boolean help) {
        private static Options parse(String[] args) {
            boolean execute = false;
            boolean resetTarget = false;
            boolean help = false;
            for (String argument : args) {
                switch (argument) {
                    case "--execute" -> execute = true;
                    case "--reset-target" -> resetTarget = true;
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException(
                            "알 수 없는 옵션입니다: " + argument);
                }
            }
            if (resetTarget && !execute) {
                throw new IllegalArgumentException(
                        "--reset-target은 --execute와 함께 사용해야 합니다.");
            }
            return new Options(execute, resetTarget, help);
        }
    }

    private record Column(String name, int sqlType, int position) {
    }

    private record TablePlan(
            String sourceTable,
            String targetTable,
            List<Column> columns,
            long sourceRows,
            long targetRows) {
    }

    private record MigrationPlan(List<TablePlan> tables) {
        private long sourceRows() {
            return tables.stream().mapToLong(TablePlan::sourceRows).sum();
        }

        private long targetRows() {
            return tables.stream().mapToLong(TablePlan::targetRows).sum();
        }
    }
}
