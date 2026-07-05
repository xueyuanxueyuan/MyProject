package cn.capinfo.gjj.yhtmock.model;

public class MockSettings {

    public boolean autoPushEnabled = true;
    public String defaultTargetUrl = "http://localhost:8082/api/v1/ywgl/yht/yht/receive";
    public long delayMs = 800L;
    public boolean pushCaps107 = true;
    public boolean pushCaps205 = true;
    public boolean pushCaps306 = true;
    public boolean pushCaps308 = true;
    public String defaultProtocolResult = "SUCC";
    public String defaultTradeResult = "SUCC";
    public String defaultBatchResult = "SUCC";
    public String protocolNotFoundCode = "E0001";
    public String protocolNotFoundMsg = "未查到协议";
    public String hsmMockKey = "YHT-MOCK-HSM";
}
