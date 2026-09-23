package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockStoreServiceTest {

    @Test
    void initUsesConfiguredSettlementReceiveUrlWhenDatabaseIsNew() {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, properties("http://configured.invalid/receive"));
        assertThat(store.getSettings().defaultTargetUrl).isEqualTo("http://configured.invalid/receive");
    }

    @Test
    void restartKeepsStoredSettingsByDefault() {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, properties("http://configured.invalid/receive"));
        MockSettings settings = store.getSettings();
        settings.defaultTargetUrl = "http://saved.invalid/receive";
        settings.autoPushEnabled = false;
        store.updateSettings(settings);
        var restarted = DatabaseTestSupport.create(source, properties("http://new.invalid/receive"));
        restarted.applyStartupSettings();
        assertThat(restarted.getSettings().defaultTargetUrl).isEqualTo("http://saved.invalid/receive");
        assertThat(restarted.getSettings().autoPushEnabled).isFalse();
    }

    @Test
    void restartCanOverrideStoredCallbackSettings() {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, properties("http://configured.invalid/receive"));
        store.getSettings();
        var overridden = properties("http://override.invalid/receive");
        overridden.getCallback().setOverrideStoredSettings(true);
        var restarted = DatabaseTestSupport.create(source, overridden);
        restarted.applyStartupSettings();
        assertThat(restarted.getSettings().defaultTargetUrl).isEqualTo("http://override.invalid/receive");
    }

    private YhtMockProperties properties(String url) {
        var properties = new YhtMockProperties();
        properties.getSettlement().setReceiveUrl(url);
        return properties;
    }
}
