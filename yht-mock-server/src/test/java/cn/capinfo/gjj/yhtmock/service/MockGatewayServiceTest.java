package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MockGatewayServiceTest {

    @Test
    void batchResultUsesSameHostSerialNumForAllDetails() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = mock(MockStoreService.class);
        MockCallbackService callbackService = mock(MockCallbackService.class);
        MockGatewayService gatewayService = new MockGatewayService(codecService, storeService, callbackService);
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

        ArgumentCaptor<BatchState> captor = ArgumentCaptor.forClass(BatchState.class);
        verify(storeService).saveBatch(captor.capture());
        String resultFileData = new String(Base64.getDecoder().decode(captor.getValue().fileData), StandardCharsets.UTF_8);
        String[] lines = resultFileData.split("\\R");
        String firstHostSerialNum = lines[1].split("\\|", -1)[7];
        String secondHostSerialNum = lines[2].split("\\|", -1)[7];
        assertThat(firstHostSerialNum).isEqualTo("BATCH-001");
        assertThat(secondHostSerialNum).isEqualTo(firstHostSerialNum);
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
        assertThat(response).contains("报文XML解析失败");
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
        assertThat(response).contains("报文XML解析失败");
        verify(storeService, never()).saveTrade(any());
        verify(storeService).addRecord(any());
    }
}
