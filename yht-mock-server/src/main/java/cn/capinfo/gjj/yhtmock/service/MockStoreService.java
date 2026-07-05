package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockScenarioContext;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.MockStateSnapshot;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MockStoreService {

    private static final int MAX_RECORDS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path stateFile = Paths.get("data", "mock-state.json");

    private MockStateSnapshot snapshot = new MockStateSnapshot();

    @PostConstruct
    public synchronized void init() {
        load();
    }

    public synchronized MockSettings getSettings() {
        return snapshot.settings;
    }

    public synchronized MockSettings updateSettings(MockSettings settings) {
        if (settings != null) {
            snapshot.settings = settings;
            save();
        }
        return snapshot.settings;
    }

    public synchronized List<MockRecord> listRecords(int limit) {
        return snapshot.records.stream()
                .sorted(Comparator.comparingLong((MockRecord item) -> item.createdAt).reversed())
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized void clearRecords() {
        snapshot.records.clear();
        save();
    }

    public synchronized MockRecord addRecord(MockRecord record) {
        record.id = snapshot.recordSequence++;
        record.createdAt = System.currentTimeMillis();
        snapshot.records.add(record);
        if (snapshot.records.size() > MAX_RECORDS) {
            snapshot.records = snapshot.records.stream()
                    .sorted(Comparator.comparingLong(item -> item.createdAt))
                    .skip(snapshot.records.size() - MAX_RECORDS)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        save();
        return record;
    }

    public synchronized List<ProtocolState> listProtocols() {
        return snapshot.protocols.values().stream()
                .sorted(Comparator.comparingLong((ProtocolState item) -> item.updatedAt).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized List<TradeState> listTrades() {
        return snapshot.trades.values().stream()
                .sorted(Comparator.comparingLong((TradeState item) -> item.updatedAt).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized List<BatchState> listBatches() {
        return snapshot.batches.values().stream()
                .sorted(Comparator.comparingLong((BatchState item) -> item.updatedAt).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized List<MockScenarioRule> listScenarios() {
        return snapshot.scenarios.stream()
                .sorted(Comparator.comparingLong((MockScenarioRule item) -> item.updatedAt).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized MockScenarioRule saveScenario(MockScenarioRule scenarioRule) {
        if (scenarioRule == null) {
            return null;
        }
        if (scenarioRule.id <= 0) {
            scenarioRule.id = snapshot.scenarioSequence++;
            snapshot.scenarios.add(scenarioRule);
        } else {
            snapshot.scenarios.removeIf(item -> item.id == scenarioRule.id);
            snapshot.scenarios.add(scenarioRule);
        }
        scenarioRule.updatedAt = System.currentTimeMillis();
        save();
        return scenarioRule;
    }

    public synchronized boolean deleteScenario(long id) {
        boolean removed = snapshot.scenarios.removeIf(item -> item.id == id);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized MockScenarioRule matchScenario(MockScenarioContext context) {
        return snapshot.scenarios.stream()
                .filter(item -> item.enabled)
                .filter(item -> matches(item.requestMesgType, context.requestMesgType))
                .filter(item -> matches(item.matchAcctNo, context.acctNo))
                .filter(item -> matchesSuffix(item.matchAcctSuffix, context.acctNo))
                .filter(item -> matches(item.matchProtocolNo, context.protocolNo))
                .filter(item -> matches(item.matchReqId, context.reqId))
                .filter(item -> matches(item.matchBatchNo, context.batchNo))
                .filter(item -> matches(item.matchSysSeqNo, context.sysSeqNo))
                .max(Comparator
                        .comparingInt((MockScenarioRule item) -> score(item))
                        .thenComparingLong(item -> item.updatedAt))
                .orElse(null);
    }

    public synchronized void saveProtocol(ProtocolState protocolState) {
        if (protocolState == null || protocolState.protocolNo == null || protocolState.protocolNo.isBlank()) {
            return;
        }
        protocolState.updatedAt = System.currentTimeMillis();
        snapshot.protocols.put(protocolState.protocolNo, protocolState);
        save();
    }

    public synchronized ProtocolState findProtocol(String protocolNo, String acctNo) {
        if (protocolNo != null && !protocolNo.isBlank()) {
            ProtocolState byProtocol = snapshot.protocols.get(protocolNo);
            if (byProtocol != null) {
                return byProtocol;
            }
        }
        if (acctNo == null || acctNo.isBlank()) {
            return null;
        }
        return snapshot.protocols.values().stream()
                .filter(item -> acctNo.equals(item.acctNo))
                .max(Comparator.comparingLong(item -> item.updatedAt))
                .orElse(null);
    }

    public synchronized void saveTrade(TradeState tradeState) {
        if (tradeState == null || tradeState.sysSeqNo == null || tradeState.sysSeqNo.isBlank()) {
            return;
        }
        tradeState.updatedAt = System.currentTimeMillis();
        snapshot.trades.put(tradeState.sysSeqNo, tradeState);
        save();
    }

    public synchronized TradeState findTrade(String sysSeqNo, String reqId) {
        if (sysSeqNo != null && !sysSeqNo.isBlank()) {
            TradeState tradeState = snapshot.trades.get(sysSeqNo);
            if (tradeState != null) {
                return tradeState;
            }
        }
        if (reqId == null || reqId.isBlank()) {
            return null;
        }
        return snapshot.trades.values().stream()
                .filter(item -> reqId.equals(item.reqId))
                .max(Comparator.comparingLong(item -> item.updatedAt))
                .orElse(null);
    }

    public synchronized void saveBatch(BatchState batchState) {
        if (batchState == null || batchState.batchNo == null || batchState.batchNo.isBlank()) {
            return;
        }
        batchState.updatedAt = System.currentTimeMillis();
        snapshot.batches.put(batchState.batchNo, batchState);
        save();
    }

    public synchronized BatchState findBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return null;
        }
        return snapshot.batches.get(batchNo);
    }

    public synchronized Map<String, Object> buildStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("recordCount", snapshot.records.size());
        stats.put("protocolCount", snapshot.protocols.size());
        stats.put("tradeCount", snapshot.trades.size());
        stats.put("batchCount", snapshot.batches.size());
        stats.put("scenarioCount", snapshot.scenarios.size());
        stats.put("callbackAutoPushEnabled", snapshot.settings.autoPushEnabled);
        stats.put("defaultTargetUrl", snapshot.settings.defaultTargetUrl);
        return stats;
    }

    private void load() {
        try {
            Files.createDirectories(stateFile.getParent());
            if (!Files.exists(stateFile)) {
                save();
                return;
            }
            snapshot = objectMapper.readValue(stateFile.toFile(), MockStateSnapshot.class);
            if (snapshot.settings == null) {
                snapshot.settings = new MockSettings();
            }
            if (snapshot.records == null) {
                snapshot.records = new ArrayList<>();
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
            if (snapshot.scenarios == null) {
                snapshot.scenarios = new ArrayList<>();
            }
        } catch (IOException e) {
            snapshot = new MockStateSnapshot();
        }
    }

    private void save() {
        try {
            Files.createDirectories(stateFile.getParent());
            objectMapper.writeValue(stateFile.toFile(), snapshot);
        } catch (IOException ignored) {
        }
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean matchesSuffix(String expectedSuffix, String actual) {
        return expectedSuffix == null || expectedSuffix.isBlank()
                || (actual != null && actual.endsWith(expectedSuffix));
    }

    private int score(MockScenarioRule item) {
        int score = 0;
        if (item.requestMesgType != null && !item.requestMesgType.isBlank()) {
            score += 16;
        }
        if (item.matchAcctNo != null && !item.matchAcctNo.isBlank()) {
            score += 8;
        }
        if (item.matchAcctSuffix != null && !item.matchAcctSuffix.isBlank()) {
            score += 4;
        }
        if (item.matchProtocolNo != null && !item.matchProtocolNo.isBlank()) {
            score += 4;
        }
        if (item.matchReqId != null && !item.matchReqId.isBlank()) {
            score += 2;
        }
        if (item.matchBatchNo != null && !item.matchBatchNo.isBlank()) {
            score += 2;
        }
        if (item.matchSysSeqNo != null && !item.matchSysSeqNo.isBlank()) {
            score += 2;
        }
        return score;
    }
}
