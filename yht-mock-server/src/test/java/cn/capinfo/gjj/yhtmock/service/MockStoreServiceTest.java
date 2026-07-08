package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.MockSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MockStoreServiceTest {

    @Test
    void initRestoresStateFromBackupWhenPrimaryFileIsCorrupted(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("mock-state.json");
        MockStoreService originalStore = new MockStoreService(stateFile);
        originalStore.init();
        MockSettings settings = new MockSettings();
        settings.defaultTargetUrl = "http://example.com/callback";
        settings.protocolNotFoundMsg = "backup-kept";
        originalStore.updateSettings(settings);

        Files.writeString(stateFile, "{corrupted", StandardCharsets.UTF_8);

        MockStoreService recoveredStore = new MockStoreService(stateFile);
        recoveredStore.init();

        assertThat(recoveredStore.getSettings().defaultTargetUrl).isEqualTo("http://example.com/callback");
        assertThat(recoveredStore.getSettings().protocolNotFoundMsg).isEqualTo("backup-kept");
    }

    @Test
    void initRestoresStateFromBackupWhenPrimaryFileIsMissing(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("mock-state.json");
        MockStoreService originalStore = new MockStoreService(stateFile);
        originalStore.init();
        MockSettings settings = new MockSettings();
        settings.defaultTargetUrl = "http://example.com/missing";
        settings.protocolNotFoundMsg = "missing-primary";
        originalStore.updateSettings(settings);

        Path backupFile = stateFile.resolveSibling(stateFile.getFileName() + ".bak");
        assertThat(Files.exists(backupFile)).isTrue();

        Files.deleteIfExists(stateFile);

        MockStoreService recoveredStore = new MockStoreService(stateFile);
        recoveredStore.init();

        assertThat(recoveredStore.getSettings().defaultTargetUrl).isEqualTo("http://example.com/missing");
        assertThat(recoveredStore.getSettings().protocolNotFoundMsg).isEqualTo("missing-primary");
        assertThat(Files.exists(stateFile)).isTrue();
        assertThat(Files.exists(backupFile)).isTrue();
    }

    @Test
    void updateSettingsWritesBackupAndLeavesNoTempFile(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("mock-state.json");
        MockStoreService storeService = new MockStoreService(stateFile);
        storeService.init();
        MockSettings settings = new MockSettings();
        settings.defaultTargetUrl = "http://example.com/tmp";
        storeService.updateSettings(settings);

        Path backupFile = stateFile.resolveSibling(stateFile.getFileName() + ".bak");
        Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");

        assertThat(Files.exists(stateFile)).isTrue();
        assertThat(Files.exists(backupFile)).isTrue();
        assertThat(Files.exists(tempFile)).isFalse();
    }
}
