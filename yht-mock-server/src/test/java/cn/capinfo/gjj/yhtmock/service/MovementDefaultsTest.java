package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.MovementSettings;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MovementDefaultsTest {

    @Test
    void newSettingsHaveStableReconciliationDefaults() {
        var settings = new MovementSettings();
        assertDefaults(settings);
        assertThat(settings.enabled).isFalse();
        assertThat(settings.balance).isEqualByComparingTo("0.00");
    }

    @Test
    void blankStoredDefaultsAreFilledWithoutOverwritingEndpoint() {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://settlement.invalid/api/v1/ywgl/saveZhbdzt";
        store.updateSettings(settings);
        new JdbcTemplate(database).update("UPDATE YHT_MOCK_MOVEMENT_CFG SET C_RECEIVE_CODE=NULL, C_PAY_CODE=' ', C_COUNTERPARTY_ACCOUNT=NULL, C_COUNTERPARTY_NAME=NULL, C_COUNTERPARTY_BANK=NULL");
        assertDefaults(store.getSettings().movement);
        assertThat(store.getSettings().movement.targetUrl).isEqualTo(settings.movement.targetUrl);
    }

    @Test
    void explicitSavedValuesRemainUnchanged() {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.receiveCode = "CUSTOM-IN";
        settings.movement.payCode = "CUSTOM-OUT";
        settings.movement.counterpartyAccount = "123456789";
        settings.movement.counterpartyName = "既有对账户";
        settings.movement.counterpartyBank = "123456789012";
        store.updateSettings(settings);
        var restarted = DatabaseTestSupport.create(database, new YhtMockProperties());
        restarted.applyStartupSettings();
        assertThat(restarted.getSettings().movement).usingRecursiveComparison().isEqualTo(settings.movement);
    }

    @Test
    void enabledWithoutTargetUrlIsRejectedAndNothingIsSaved() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.enabled = true;
        settings.movement.targetUrl = "";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.updateSettings(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通知地址");
        assertThat(store.getSettings().movement.enabled).isFalse();
    }

    @Test
    void enabledWithTargetUrlRoundTripsAcrossReload() {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.enabled = true;
        settings.movement.targetUrl = "http://settlement.invalid/api/v1/ywgl/saveZhbdzt";
        store.updateSettings(settings);
        var restarted = DatabaseTestSupport.create(database, new YhtMockProperties());
        assertThat(restarted.getSettings().movement.enabled).isTrue();
        assertThat(restarted.getSettings().movement.targetUrl).isEqualTo("http://settlement.invalid/api/v1/ywgl/saveZhbdzt");
    }

    private void assertDefaults(MovementSettings settings) {
        assertThat(settings.receiveCode).isEqualTo("SBDC100");
        assertThat(settings.payCode).isEqualTo("SBDC100");
        assertThat(settings.counterpartyAccount).isEqualTo("99993304000000000001");
        assertThat(settings.counterpartyName).isEqualTo("一户通模拟对账专户");
        assertThat(settings.counterpartyBank).isEqualTo("999999999999");
    }
}
