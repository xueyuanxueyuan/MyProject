package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockGatewayServiceTest {


    @TempDir
    Path tempDir;

    @Test
    void caps305DeleteCancelsProtocolAndIncludesJiaxingCallbackFields() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        String protocolNo = "PROT-DELETE-001";
        ProtocolState existing = new ProtocolState();
        existing.protocolNo = protocolNo;
        existing.acctNo = "62220000000000008888";
        existing.acctName = "MOCK-PAYER";
        existing.bankId = "105000";
        existing.signReqId = "REQ-SIGN-001";
        existing.status = "SUCC";
        storeService.saveProtocol(existing);
        String xml = codecService.buildXml("caps.305.001.01", "<CorpNo>1111</CorpNo>",
                "<ReqId>REQ-DELE-001</ReqId>"
                        + "<ChngTp>DELE</ChngTp>"
                        + "<SndrFlg>CPSD</SndrFlg>"
                        + "<SndTp>SD00</SndTp>"
                        + "<DbtrProtocol>" + protocolNo + "</DbtrProtocol>"
                        + "<DbtrActId>62220000000000008888</DbtrActId>"
                        + "<DbtrActName>MOCK-PAYER</DbtrActName>"
                        + "<DbtrBankId>105000</DbtrBankId>"
                        + "<FeeNoList>FEE001,FEE002</FeeNoList>");
        String rawMessage = buildRaw(codecService, "caps.305.001.01", xml);

        String response = gatewayService.dispatch(rawMessage);

        assertThat(response).contains("caps.900.001.01").contains("SUCC");
        ProtocolState saved = storeService.findProtocol(protocolNo, "");
        assertThat(saved.status).isEqualTo("CANCELLED");
        assertThat(saved.protocolProcessCode).isEqualTo("CS20");
        assertThat(saved.feeNoList).isEqualTo("FEE001|FEE002");
        ArgumentCaptor<ProtocolState> captor = ArgumentCaptor.forClass(ProtocolState.class);
        verify(callbackService).scheduleCaps306(any(), captor.capture());
        assertThat(captor.getValue().changeType).isEqualTo("DELE");
        assertThat(captor.getValue().protocolProcessCode).isEqualTo("CS20");
    }

    @Test
    void caps305SmsConfirmReusesSignedProtocolByReqId() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        ProtocolState existing = new ProtocolState();
        existing.protocolNo = "PROT-SMS-001";
        existing.acctNo = "62220000000000008888";
        existing.signReqId = "REQ-SMS-001";
        existing.status = "ACCEPTED";
        existing.changeType = "ADDD";
        storeService.saveProtocol(existing);
        String xml = codecService.buildXml("caps.305.001.01", "<CorpNo>1111</CorpNo>",
                "<ReqId>REQ-SMS-001</ReqId>"
                        + "<ChngTp>ADDD</ChngTp>"
                        + "<SndrFlg>CPSD</SndrFlg>"
                        + "<SndTp>SD01</SndTp>"
                        + "<AuthCd>123456</AuthCd>"
                        + "<DbtrProtocol>PROT-SMS-001</DbtrProtocol>"
                        + "<DbtrActId>62220000000000008888</DbtrActId>");

        gatewayService.dispatch(buildRaw(codecService, "caps.305.001.01", xml));

        ProtocolState saved = storeService.findProtocol("PROT-SMS-001", "");
        assertThat(saved.status).isEqualTo("SUCC");
        assertThat(saved.sendType).isEqualTo("SD01");
        assertThat(saved.authCode).isEqualTo("123456");
        assertThat(saved.protocolProcessCode).isEqualTo("CS00");
    }

    @Test
    void caps201KeepsJiaxingSerialNumAndPayAmtInResponseAndState() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        String xml = codecService.buildXml("caps.201.001.01", "<CorpNo>1111</CorpNo>",
                "<ReqId>REQ-TRADE-001</ReqId>"
                        + "<TranCode>201</TranCode>"
                        + "<SysSeqNo>SYS-TRADE-001</SysSeqNo>"
                        + "<SerialNum>SERIAL-JX-001</SerialNum>"
                        + "<DbtrProtocol>PROT-TRADE-001</DbtrProtocol>"
                        + "<DbtrActName>MOCK-PAYER</DbtrActName>"
                        + "<DbtrActId>62220000000000009999</DbtrActId>"
                        + "<DbtrBankId>105000</DbtrBankId>"
                        + "<CdtrActName>JIAXING-GJJ-CENTER</CdtrActName>"
                        + "<CdtrActId>3300000000000001</CdtrActId>"
                        + "<CdtrBankId>105000</CdtrBankId>"
                        + "<PayAmt>CNY123.45</PayAmt>"
                        + "<BllNb>BILL-001</BllNb>"
                        + "<BtchNb>BTCH-001</BtchNb>");

        String response = gatewayService.dispatch(buildRaw(codecService, "caps.201.001.01", xml));

        assertThat(response).contains("caps.202.001.01");
        assertThat(response).contains("<SysSeqNo>SYS-TRADE-001</SysSeqNo>");
        assertThat(response).contains("<SerialNum>SERIAL-JX-001</SerialNum>");
        assertThat(response).contains("<BtchNb>BTCH-001</BtchNb>");
        TradeState saved = storeService.findTrade("SYS-TRADE-001", "");
        assertThat(saved.serialNum).isEqualTo("SERIAL-JX-001");
        assertThat(saved.amount).isEqualTo("123.45");
        assertThat(saved.billNo).isEqualTo("BILL-001");
        ArgumentCaptor<TradeState> captor = ArgumentCaptor.forClass(TradeState.class);
        verify(callbackService).scheduleCaps205(any(), captor.capture());
        assertThat(captor.getValue().creditorAcctName).isEqualTo("JIAXING-GJJ-CENTER");
    }

    @Test
    void caps601ReturnsCaps602WithJiaxingHeadFields() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        String xml = codecService.buildXml("caps.601.001.01",
                "<CorpNo>1111</CorpNo><CheckDate>20260804</CheckDate><TranCode>201</TranCode>", null);

        String response = gatewayService.dispatch(buildRaw(codecService, "caps.601.001.01", xml));

        assertThat(response).contains("caps.602.001.01");
        assertThat(response).contains("<CheckDate>20260804</CheckDate>");
        assertThat(response).contains("<TranCode>201</TranCode>");
        assertThat(response).contains("<FileData>");
    }


    @Test
    void caps306ReturnsSuccessCaps900AndCorrelatesOriginalRequest() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state-306.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);

        ProtocolState existing = new ProtocolState();
        existing.protocolNo = "BANK-PENDING-REQ-BANK-001";
        existing.signReqId = "REQ-BANK-001";
        existing.status = "ACCEPTED";
        existing.changeType = "ADDD";
        storeService.saveProtocol(existing);

        String xml = codecService.buildXml("caps.306.001.01",
                "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<CtrctRtrFlg>1</CtrctRtrFlg><OrgnlReqId>REQ-BANK-001</OrgnlReqId>"
                        + "<OrgnlDbtrProtocol>PROT-BANK-001</OrgnlDbtrProtocol><AuthChl>MOCK</AuthChl>"
                        + "<ProtocolProcessCode>CS00</ProtocolProcessCode><ChngTp>ADDD</ChngTp>"
                        + "<ProtocolStatus>SUCC</ProtocolStatus><Remark>bank sign accepted</Remark>");

        String response = gatewayService.dispatch(buildRaw(codecService, "caps.306.001.01", xml));

        assertThat(response).contains("caps.900.001.01")
                .contains("<ResFlag>SUCC</ResFlag>")
                .contains("<ProcCode>I000</ProcCode>")
                .contains("<Remark>");
        ProtocolState saved = storeService.findProtocol("PROT-BANK-001", "");
        assertThat(saved.status).isEqualTo("SUCC");
        assertThat(saved.signReqId).isEqualTo("REQ-BANK-001");
        assertThat(saved.remark).isEqualTo("bank sign accepted");
        assertThat(storeService.findProtocol("BANK-PENDING-REQ-BANK-001", "")).isNull();
    }

    @Test
    void caps900ContainsRemarkElement() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state-900.json"));
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);

        String xml = codecService.buildXml("caps.999.001.01", "<CorpNo>33503C5801</CorpNo>",
                "<ReqId>REQ-900-001</ReqId>");

        String response = gatewayService.dispatch(buildRaw(codecService, "caps.999.001.01", xml));

        assertThat(response).contains("<Remark>");
    }
    private String buildRaw(CapsCodecService codecService, String mesgType, String xml) {
        return codecService.buildHeader(mesgType, "R", "REF-001",
                "CAPS", "CAPS", "33503C5801", "904290099992") + xml;
    }
    @Test
    void batchResultUsesSameHostSerialNumForAllDetails() {
        CapsCodecService codecService = new CapsCodecService();
        // 真实 store 端到端：批量构建现依赖 getSettings/evaluateCounterparty，mock 未打桩会 NPE。
        var store = DatabaseTestSupport.create(tempDir.resolve("batch-host-serial.json"));
        var callbackService = new MockCallbackService(codecService, store);
        MockGatewayService gatewayService = new MockGatewayService(codecService, store, callbackService);
        String requestFileData = String.join("\n",
                "101|33503C5801|00600|2|155.00|0|0|0|BATCH-001|20260715",
                "1|102100000001||3309050160000934854|壬禊|77.00|||SETTLE-001",
                "2|102100000001||3301050160114086630|塞洒容|78.00|||SETTLE-002");
        String xml = codecService.buildXml("caps.101.001.01", null,
                "<ReqId>REQ-001</ReqId>"
                        + "<BatchNo>BATCH-001</BatchNo>"
                        + "<TranCode>101</TranCode>"
                        + "<CorpNo>33503C5801</CorpNo>"
                        + "<FeeNo>00600</FeeNo>"
                        + "<TotalCount>2</TotalCount>"
                        + "<TotalAmt>155.00</TotalAmt>"
                        + "<CheckDate>20260715</CheckDate>"
                        + "<FileData>" + codecService.base64(requestFileData) + "</FileData>");
        String rawMessage = codecService.buildHeader("caps.101.001.01", "R", "REF-001",
                "CAPS", "CAPS", "33503C5801", "904290099992") + xml;

        gatewayService.dispatch(rawMessage);

        var batch = store.findBatch("BATCH-001");
        String resultFileData = new String(Base64.getDecoder().decode(batch.fileData), StandardCharsets.UTF_8);
        String[] lines = resultFileData.split("\\R");
        String firstHostSerialNum = lines[1].split("\\|", -1)[7];
        String secondHostSerialNum = lines[2].split("\\|", -1)[7];
        assertThat(firstHostSerialNum).isEqualTo("BATCH-001");
        assertThat(secondHostSerialNum).isEqualTo(firstHostSerialNum);
    }
    @Test
    void batchApplyResolvesFinalStatusFromResultDetailsInsteadOfStayingProcessing() {
        CapsCodecService codecService = new CapsCodecService();
        var store = DatabaseTestSupport.create(tempDir.resolve("batch-final-status.json"));
        var callbackService = new MockCallbackService(codecService, store);
        MockGatewayService gatewayService = new MockGatewayService(codecService, store, callbackService);
        String requestFileData = String.join("\n",
                "40502|33503C5801|00600|2|155.00|0|0|0|BATCH-STATUS|20260715",
                "1|102100000001||3309050160000934854|壬禊|77.00|||SETTLE-001",
                "2|102100000001||3301050160114086630|塞洒容|78.00|||SETTLE-002");
        String xml = codecService.buildXml("caps.101.001.01", null,
                "<ReqId>REQ-STATUS</ReqId><BatchNo>BATCH-STATUS</BatchNo><TranCode>40502</TranCode>"
                        + "<CorpNo>33503C5801</CorpNo><FeeNo>00600</FeeNo><TotalCount>2</TotalCount>"
                        + "<TotalAmt>155.00</TotalAmt><CheckDate>20260715</CheckDate>"
                        + "<FileData>" + codecService.base64(requestFileData) + "</FileData>");
        String rawMessage = codecService.buildHeader("caps.101.001.01", "R", "REF-STATUS",
                "CAPS", "CAPS", "33503C5801", "904290099992") + xml;

        gatewayService.dispatch(rawMessage);

        var batch = store.findBatch("BATCH-STATUS");
        // 默认成功场景：明细结果码为 00，批次直接落终态 SUCC，107 与动账生成都以该状态为准。
        assertThat(batch.status).isEqualTo("SUCC");
        assertThat(batch.resFlag).isEqualTo("SUCC");
        assertThat(batch.errorCode).isEmpty();
        assertThat(batch.centerBankId).isEqualTo("0");
    }

    @Test
    void batchApplyFailsWhenCounterpartyAccountInvalid() throws Exception {
        CapsCodecService codecService = new CapsCodecService();
        var store = DatabaseTestSupport.create(tempDir.resolve("batch-acct-invalid.json"));
        var callbackService = new MockCallbackService(codecService, store);
        var gatewayService = new MockGatewayService(codecService, store, callbackService);
        var rule = new cn.capinfo.gjj.yhtmock.model.MockScenarioRule();
        rule.name = "acct-invalid-rule";
        rule.enabled = true;
        rule.accountRule = "3309050160000934854";  // 命中批量明细中的对手账号
        rule.valid = false;                        // 无效 → 该明细失败
        rule.class1Card = true;
        store.saveScenario(rule);
        String requestFileData = String.join("\n",
                "40502|33503C5801|00600|1|10.00|0|0|0|BATCH-FAIL|20260715",
                "1|102100000001||3309050160000934854|壬禊|10.00|||SETTLE-001");
        String rawMessage = codecService.buildHeader("caps.101.001.01", "R", "REF-FAIL",
                "CAPS", "CAPS", "33503C5801", "904290099992")
                + codecService.buildXml("caps.101.001.01", null,
                "<ReqId>REQ-FAIL</ReqId><BatchNo>BATCH-FAIL</BatchNo><TranCode>40502</TranCode>"
                        + "<CorpNo>33503C5801</CorpNo><FeeNo>00600</FeeNo><TotalCount>1</TotalCount>"
                        + "<TotalAmt>10.00</TotalAmt><CheckDate>20260715</CheckDate>"
                        + "<FileData>" + codecService.base64(requestFileData) + "</FileData>");

        gatewayService.dispatch(rawMessage);

        var failed = store.findBatch("BATCH-FAIL");
        // 对手账号命中「无效」规则：明细结果码非 00，批次落 FAIL，错误码为 ACCT_INVALID。
        assertThat(failed.status).isEqualTo("FAIL");
        assertThat(failed.errorCode).isEqualTo("ACCT_INVALID");

        // 无匹配规则时该对手账号默认成功：删除规则后同内容批次落 SUCC。
        store.deleteScenario(rule.id);
        String secondMessage = rawMessage.replace("BATCH-FAIL", "BATCH-SUCC").replace("REF-FAIL", "REF-SUCC");
        gatewayService.dispatch(secondMessage);
        assertThat(store.findBatch("BATCH-SUCC").status).isEqualTo("SUCC");
        assertThat(store.findBatch("BATCH-SUCC").errorCode).isEmpty();
    }

    @Test
    void batchResultQueryDoesNotChangeBatchStatus() throws Exception {
        CapsCodecService codecService = new CapsCodecService();
        var store = DatabaseTestSupport.create(tempDir.resolve("batch-query.json"));
        var callbackService = new MockCallbackService(codecService, store);
        var gatewayService = new MockGatewayService(codecService, store, callbackService);
        String requestFileData = String.join("\n",
                "40502|33503C5801|00600|1|10.00|0|0|0|BATCH-Q|20260715",
                "1|102100000001||3309050160000934854|壬禊|10.00|||SETTLE-001");
        String applyMessage = codecService.buildHeader("caps.101.001.01", "R", "REF-Q",
                "CAPS", "CAPS", "33503C5801", "904290099992")
                + codecService.buildXml("caps.101.001.01", null,
                "<ReqId>REQ-Q</ReqId><BatchNo>BATCH-Q</BatchNo><TranCode>40502</TranCode>"
                        + "<CorpNo>33503C5801</CorpNo><FeeNo>00600</FeeNo><TotalCount>1</TotalCount>"
                        + "<TotalAmt>10.00</TotalAmt><CheckDate>20260715</CheckDate>"
                        + "<FileData>" + codecService.base64(requestFileData) + "</FileData>");
        gatewayService.dispatch(applyMessage);
        // 无规则命中 → 默认成功，批次落 SUCC。
        assertThat(store.findBatch("BATCH-Q").status).isEqualTo("SUCC");

        // 105 是企业查询批次结果的请求：106 只回显状态，不能推动批次状态变成终态。
        String confirmMessage = codecService.buildHeader("caps.105.001.01", "R", "REF-Q2",
                "CAPS", "CAPS", "33503C5801", "904290099992")
                + codecService.buildXml("caps.105.001.01", null, "<BatchNo>BATCH-Q</BatchNo>");
        gatewayService.dispatch(confirmMessage);
        // 105 查询后批次状态保持不变（仍为 SUCC）。
        assertThat(store.findBatch("BATCH-Q").status).isEqualTo("SUCC");
    }

    @Test
    void dispatchRejectsXmlWithDoctypeInsteadOfProcessingTrade() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = mock(MockStoreService.class);
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Message [ <!ENTITY xxe SYSTEM "file:///c:/windows/win.ini"> ]>
                <Message xmlns="urn:caps:msg:caps.201.001.01">
                  <Body>
                    <ReqId>REQ-001</ReqId>
                    <SysSeqNo>&xxe;</SysSeqNo>
                  </Body>
                </Message>
                """;
        String rawMessage = codecService.buildHeader("caps.201.001.01", "R", "REF-001",
                "CAPS", "CAPS", "33503C5801", "904290099992") + xml;

        String response = gatewayService.dispatch(rawMessage);

        assertThat(response).contains("caps.900.001.01");
        assertThat(response).contains("FAIL");
        assertThat(response).contains("<ProcCode>E401</ProcCode>")
                .contains("<ProcMsg>报文无法解析</ProcMsg>");
        verify(storeService, never()).saveTrade(any());
        verify(storeService).addRecord(any());
    }

    @Test
    void dispatchReturnsFailWhenXmlIsMalformed() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = mock(MockStoreService.class);
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Message xmlns="urn:caps:msg:caps.201.001.01">
                  <Body>
                    <ReqId>REQ-001</ReqId>
                """;
        String rawMessage = codecService.buildHeader("caps.201.001.01", "R", "REF-001",
                "CAPS", "CAPS", "33503C5801", "904290099992") + xml;

        String response = gatewayService.dispatch(rawMessage);

        assertThat(response).contains("caps.900.001.01");
        assertThat(response).contains("FAIL");
        assertThat(response).contains("<ProcCode>E401</ProcCode>")
                .contains("<ProcMsg>报文无法解析</ProcMsg>");
        verify(storeService, never()).saveTrade(any());
        verify(storeService).addRecord(any());
    }
}
