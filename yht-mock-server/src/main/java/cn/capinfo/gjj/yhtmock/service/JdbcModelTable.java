package cn.capinfo.gjj.yhtmock.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class JdbcModelTable<T> {

    private static final Set<String> INDEXED = Set.of("businessDate", "direction", "sourceType", "flowId", "centerBankId", "protocolNo", "acctNo", "signReqId", "sysSeqNo", "reqId", "batchNo", "requestMesgType", "matchAcctNo", "matchAcctSuffix", "matchProtocolNo", "matchReqId", "matchBatchNo", "matchSysSeqNo", "recordType", "mesgType", "status");
    private final JdbcTemplate jdbc;
    private final String table;
    private final Class<T> model;
    private final String key;
    private final List<Field> fields;

    JdbcModelTable(JdbcTemplate jdbc, String table, Class<T> model, String key) {
        this.jdbc = jdbc;
        this.table = table;
        this.model = model;
        this.key = key;
        this.fields = Arrays.stream(model.getFields())
                .filter(field -> field.getType() == String.class || field.getType() == long.class
                        || field.getType() == boolean.class || field.getType() == BigDecimal.class)
                .toList();
    }

    List<T> select(String predicate, String order, int limit, Object... arguments) {
        String sql = "SELECT * FROM " + table + " WHERE " + predicate + order;
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            if (limit > 0) {
                statement.setMaxRows(limit);
            }
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, (result, row) -> read(result));
    }

    List<T> page(String predicate, String order, int offset, int size, Object... arguments) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + predicate + order);
            statement.setMaxRows(Math.addExact(offset, size));
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, (org.springframework.jdbc.core.ResultSetExtractor<List<T>>) result -> {
            var rows = new java.util.ArrayList<T>();
            int skipped = 0;
            while (skipped < offset && result.next()) skipped++;
            while (rows.size() < size && result.next()) rows.add(read(result));
            return rows;
        });
    }

    T find(Object value) {
        var rows = select(column(key) + " = ?", "", 1, value);
        return rows.isEmpty() ? null : rows.get(0);
    }

    void save(T value, Object keyValue) {
        if (find(keyValue) == null) {
            insert(value, keyValue);
            return;
        }
        List<Field> updated = fields.stream().filter(field -> !field.getName().equals(key)).toList();
        String sql = "UPDATE " + table + " SET " + updated.stream()
                .map(field -> column(field.getName()) + " = ?").collect(Collectors.joining(", "))
                + " WHERE " + column(key) + " = ?";
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql);
            for (int index = 0; index < updated.size(); index++) {
                bind(statement, index + 1, updated.get(index), value);
            }
            statement.setObject(updated.size() + 1, keyValue);
            return statement;
        });
    }

    void insert(T value, Object keyValue) {
        boolean singleton = key.equals("singletonId");
        String columns = fields.stream().map(field -> column(field.getName())).collect(Collectors.joining(", "));
        int size = fields.size() + (singleton ? 1 : 0);
        String placeholders = String.join(", ", java.util.Collections.nCopies(size, "?"));
        String sql = "INSERT INTO " + table + " (" + (singleton ? "C_SINGLETON_ID, " : "")
                + columns + ") VALUES (" + placeholders + ")";
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql);
            int offset = singleton ? 1 : 0;
            if (singleton) {
                statement.setObject(1, keyValue);
            }
            for (int index = 0; index < fields.size(); index++) {
                bind(statement, index + 1 + offset, fields.get(index), value);
            }
            return statement;
        });
    }

    private void bind(java.sql.PreparedStatement statement, int index, Field field, T value) throws SQLException {
        try {
            Object content = field.get(value);
            if (field.getType() == String.class && !isIndexed(field)) {
                if (content == null) {
                    statement.setNull(index, Types.CLOB);
                } else {
                    String text = (String) content;
                    statement.setCharacterStream(index, new java.io.StringReader(text), text.length());
                }
            } else if (field.getType() == boolean.class) {
                statement.setInt(index, (boolean) content ? 1 : 0);
            } else {
                statement.setObject(index, content);
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot bind model field", exception);
        }
    }

    private T read(ResultSet result) throws SQLException {
        try {
            T value = model.getDeclaredConstructor().newInstance();
            for (Field field : fields) {
                String name = column(field.getName());
                if (field.getType() == String.class) {
                    if (isIndexed(field)) {
                        field.set(value, result.getString(name));
                    } else {
                        Clob clob = result.getClob(name);
                        try {
                            field.set(value, clob == null ? null : clob.getSubString(1, Math.toIntExact(clob.length())));
                        } finally {
                            if (clob != null) {
                                clob.free();
                            }
                        }
                    }
                } else if (field.getType() == long.class) {
                    field.setLong(value, result.getLong(name));
                } else if (field.getType() == boolean.class) {
                    field.setBoolean(value, result.getInt(name) != 0);
                } else {
                    field.set(value, result.getBigDecimal(name));
                }
            }
            return value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read model", exception);
        }
    }

    private boolean isIndexed(Field field) {
        return INDEXED.contains(field.getName()) || field.getName().equals(key);
    }

    private String column(String name) {
        return "C_" + name.replaceAll("([A-Z])", "_$1").toUpperCase(Locale.ROOT);
    }
}
