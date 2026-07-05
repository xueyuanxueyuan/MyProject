package cn.capinfo.gjj.yhtmock.model;

public class TradeState {

    public String sysSeqNo;
    public String reqId;
    public String tranCode;
    public String acctNo;
    public String amount;
    public String bankId;
    public String status;
    public String resFlag;
    public String retCode;
    public String retMsg;
    public boolean callbackEnabled = true;
    public String callbackMesgType = "caps.205.001.01";
    public String scenarioName;
    public long updatedAt;
}
