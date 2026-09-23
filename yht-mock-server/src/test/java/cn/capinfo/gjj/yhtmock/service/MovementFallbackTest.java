package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.BankCounterparty;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MovementFallbackTest {

    @Test
    void fallbackUsesDefaultAccountWithoutCenterBankConfiguration() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        settings.movement.fallbackEnabled = true;
        settings.movement.counterpartyAccount = "8888000011112222";
        settings.movement.counterpartyName = "兜底对手户";
        settings.movement.counterpartyBank = "888888888888";
        store.updateSettings(settings);
        try (var service = new MovementNotificationService(store)) {
            var header = new CapsHeader();
            header.origSender = "CORP";
            service.notifyTrade(header, MovementFlowTest.trade());
            var flow = store.movementFlows().query("", "", "", "", "", "", "", 1, 20).items().get(0);
            assertThat(flow.status).isEqualTo("MISSING");
            flow = service.generateMovement(flow.id, flow.version, "兜底生成");
            assertThat(flow.status).isEqualTo("PENDING");
            assertThat(flow.payload)
                    .contains("\"jydszh\":\"8888000011112222\"")
                    .contains("\"jydshm\":\"兜底对手户\"")
                    .contains("\"jydshh\":\"888888888888\"");
        }
    }

    @Test
    void fallbackWinsOverConfiguredCenterBankAccount() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var bank = new BankCounterparty();
        bank.bankId = "CENTER";
        bank.bankName = "中心银行";
        bank.accountNo = "99990001";
        bank.accountName = "按银行配置的对手";
        bank.accountBankId = "999999999999";
        store.saveBankCounterparty(bank);
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        settings.movement.fallbackEnabled = true;
        store.updateSettings(settings);
        try (var service = new MovementNotificationService(store)) {
            var header = new CapsHeader();
            header.origSender = "CORP";
            service.notifyTrade(header, MovementFlowTest.trade());
            var flow = store.movementFlows().query("", "", "", "", "", "", "", 1, 20).items().get(0);
            flow = service.generateMovement(flow.id, flow.version, "兜底生成");
            assertThat(flow.payload)
                    .contains("\"jydszh\":\"99993304000000000001\"")
                    .contains("\"jydshm\":\"一户通模拟对账专户\"")
                    .contains("\"jydshh\":\"999999999999\"")
                    .doesNotContain("99990001");
        }
    }

    @Test
    void fallbackDisabledStillRequiresCenterBankAccount() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        settings.movement.fallbackEnabled = false;
        store.updateSettings(settings);
        try (var service = new MovementNotificationService(store)) {
            var header = new CapsHeader();
            header.origSender = "CORP";
            service.notifyTrade(header, MovementFlowTest.trade());
            var flow = store.movementFlows().query("", "", "", "", "", "", "", 1, 20).items().get(0);
            var id = flow.id;
            long version = flow.version;
            assertThatThrownBy(() -> service.generateMovement(id, version, "未兜底生成"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未配置或已停用对手账户");
            assertThat(store.movementFlows().find(id).lastError).contains("未配置或已停用对手账户");
        }
    }

    @Test
    void fallbackSwitchRoundTripsAcrossInstances() {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        settings.movement.fallbackEnabled = true;
        store.updateSettings(settings);
        var restarted = DatabaseTestSupport.create(database, new YhtMockProperties());
        assertThat(restarted.getSettings().movement.fallbackEnabled).isTrue();
        assertThat(restarted.getSettings().movement.enabled).isFalse();
    }

    @Test
    void fallbackAccountFieldsAreValidatedOnSaveWhenMovementEnabled() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.enabled = true;
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        settings.movement.fallbackEnabled = true;
        settings.movement.counterpartyName = "兜底户名".repeat(30);
        assertThatThrownBy(() -> store.updateSettings(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("兜底对手户名");
        var stored = store.getSettings().movement;
        assertThat(stored.fallbackEnabled).isFalse();
        assertThat(stored.enabled).isFalse();
    }
}
