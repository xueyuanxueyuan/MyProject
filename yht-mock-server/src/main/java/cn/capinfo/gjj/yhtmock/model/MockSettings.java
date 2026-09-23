package cn.capinfo.gjj.yhtmock.model;

public class MockSettings {

    public MovementSettings movement = new MovementSettings();

    public boolean autoPushEnabled = true;
    public String defaultTargetUrl;
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
    public String svsMockKey = "YHT-MOCK-SVS";
    public boolean svsVerifyLenient = true;

    /** 随机模拟交易状态开关：开启后交易状态随机失败（覆盖账号校验规则判定）。 */
    public boolean randomFail = false;
    /** 随机失败概率（0~1），仅当 randomFail=true 时生效。 */
    public double randomFailRatio = 0.5;
}
