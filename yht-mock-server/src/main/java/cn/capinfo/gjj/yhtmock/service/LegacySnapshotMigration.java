package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.MockStateSnapshot;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.MovementSettings;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class LegacySnapshotMigration {

    private final MockStoreService store;

    public LegacySnapshotMigration(MockStoreService store) {
        this.store = store;
    }

    public Result migrate(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ObjectMapper mapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        MockStateSnapshot snapshot = mapper.readValue(bytes, MockStateSnapshot.class);
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        normalize(snapshot);
        validateIds(snapshot.records, item -> item.id);
        validateIds(snapshot.scenarios, item -> item.id);
        validateKeys(snapshot.protocols, item -> item.protocolNo);
        validateKeys(snapshot.trades, item -> item.sysSeqNo);
        validateKeys(snapshot.batches, item -> item.batchNo);
        if (snapshot.recordSequence < 1 || snapshot.scenarioSequence < 1) {
            throw new IllegalArgumentException("Snapshot sequence must be positive");
        }
        snapshot.movementAttempts.forEach((key, status) -> {
            if (key == null || key.isBlank() || status == null || status.isBlank()) {
                throw new IllegalArgumentException("Invalid movement deduplication entry");
            }
        });
        snapshot.settings.movement.validate();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return store.importSnapshot(snapshot, hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void normalize(MockStateSnapshot snapshot) {
        if (snapshot.settings == null) {
            snapshot.settings = new MockSettings();
        }
        if (snapshot.settings.movement == null) {
            snapshot.settings.movement = new MovementSettings();
        }
        if (snapshot.records == null) {
            snapshot.records = new ArrayList<>();
        }
        if (snapshot.scenarios == null) {
            snapshot.scenarios = new ArrayList<>();
        }
        if (snapshot.protocols == null) {
            snapshot.protocols = new LinkedHashMap<>();
        }
        if (snapshot.trades == null) {
            snapshot.trades = new LinkedHashMap<>();
        }
        if (snapshot.batches == null) {
            snapshot.batches = new LinkedHashMap<>();
        }
        if (snapshot.movementAttempts == null) {
            snapshot.movementAttempts = new LinkedHashMap<>();
        }
    }

    private <T> void validateIds(List<T> items, ToLongFunction<T> id) {
        var seen = new HashSet<Long>();
        for (T item : items) {
            if (item == null || id.applyAsLong(item) <= 0 || !seen.add(id.applyAsLong(item))) {
                throw new IllegalArgumentException("Invalid or duplicate snapshot identifier");
            }
        }
    }

    private <T> void validateKeys(Map<String, T> items, Function<T, String> key) {
        items.forEach((storedKey, item) -> {
            if (storedKey == null || storedKey.isBlank() || item == null || !storedKey.equals(key.apply(item))) {
                throw new IllegalArgumentException("Snapshot map key does not match its business identifier");
            }
        });
    }

    public record Result(boolean alreadyImported, String sha256, Map<String, Long> counts) {
    }
}
