package cn.capinfo.gjj.yhtmock.model;

import java.math.BigDecimal;

public class MovementFlow {
    public String id;
    public String movementKey;
    public String sourceType;
    public String businessDate;
    public String centerBankId;
    public String acctNo;
    public String sysSeqNo;
    public String reqId;
    public String batchNo;
    public String tranCode;
    public String direction;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public BigDecimal amount;
    public String status = "MISSING";
    public String payload;
    public String lastError;
    public long version;
    public long attemptCount;
    public long createdAt;
    public long updatedAt;
}
