package cn.capinfo.gjj.yhtmock.model;

public class MockRecord {

    public long id;
    public String recordType;
    public String source;
    public String target;
    public String mesgType;
    public String mesgId;
    public String reqId;
    public String protocolNo;
    public String batchNo;
    public String sysSeqNo;
    public String status;
    public String requestBody;
    public String responseBody;
    /**
     * 解密后的请求/响应业务报文：请求或响应经过 CAPS 信封加密时保存明文，便于链路视图对比“加密报文 vs 解密后报文”。
     * 未加密时保持为空，表示原始报文本身即明文。
     */
    public String decryptedRequestBody;
    public String decryptedResponseBody;
    public String remark;
    public long createdAt;
}
