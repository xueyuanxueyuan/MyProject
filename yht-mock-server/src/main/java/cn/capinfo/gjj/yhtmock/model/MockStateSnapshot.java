package cn.capinfo.gjj.yhtmock.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockStateSnapshot {

    public MockSettings settings = new MockSettings();
    public List<MockRecord> records = new ArrayList<>();
    public Map<String, ProtocolState> protocols = new LinkedHashMap<>();
    public Map<String, TradeState> trades = new LinkedHashMap<>();
    public Map<String, BatchState> batches = new LinkedHashMap<>();
    public List<MockScenarioRule> scenarios = new ArrayList<>();
    public long recordSequence = 1L;
    public long scenarioSequence = 1L;
}
