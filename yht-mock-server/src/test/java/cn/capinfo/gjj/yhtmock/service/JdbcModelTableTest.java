package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcModelTableTest {

    @Test
    void everyPersistentFieldRoundTripsThroughItsOwnColumn() throws Exception {
        var jdbc = new JdbcTemplate(DatabaseTestSupport.newDatabase());
        verify(jdbc, "YHT_MOCK_FLOW", MovementFlow.class, "id", "id-中文");
        verify(jdbc, "YHT_MOCK_FLOW_ATTEMPT", MovementAttempt.class, "id", "id-中文");
        verify(jdbc, "YHT_MOCK_BANK_ACCOUNT", BankCounterparty.class, "bankId", "bankId-中文");
        verify(jdbc, "YHT_MOCK_SETTINGS", MockSettings.class, "singletonId", 1);
        verify(jdbc, "YHT_MOCK_MOVEMENT_CFG", MovementSettings.class, "singletonId", 1);
        verify(jdbc, "YHT_MOCK_RECORD", MockRecord.class, "id", 123L);
        verify(jdbc, "YHT_MOCK_PROTOCOL", ProtocolState.class, "protocolNo", "protocolNo-中文");
        verify(jdbc, "YHT_MOCK_TRADE", TradeState.class, "sysSeqNo", "sysSeqNo-中文");
        verify(jdbc, "YHT_MOCK_BATCH", BatchState.class, "batchNo", "batchNo-中文");
        verify(jdbc, "YHT_MOCK_SCENARIO", MockScenarioRule.class, "id", 123L);
    }

    private <T> void verify(JdbcTemplate jdbc, String name, Class<T> type, String key, Object keyValue) throws Exception {
        T original = type.getDeclaredConstructor().newInstance();
        for (var field : type.getFields()) {
            if (field.getType() == String.class) {
                field.set(original, field.getName() + "-中文");
            } else if (field.getType() == long.class) {
                field.setLong(original, 123L);
            } else if (field.getType() == boolean.class) {
                field.setBoolean(original, false);
            } else if (field.getType() == BigDecimal.class) {
                field.set(original, new BigDecimal("123456789012.34"));
            }
        }
        var table = new JdbcModelTable<>(jdbc, name, type, key);
        table.save(original, keyValue);
        var mapper = new ObjectMapper();
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(table.find(keyValue))).isEqualTo(mapper.valueToTree(original));
        for (var field : type.getFields()) {
            if (field.getType() == String.class && !field.getName().equals(key) && !field.getName().equals("flowId")) {
                field.set(original, null);
            } else if (field.getType() == boolean.class) {
                field.setBoolean(original, true);
            }
        }
        table.save(original, keyValue);
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(table.find(keyValue))).isEqualTo(mapper.valueToTree(original));
    }
}
