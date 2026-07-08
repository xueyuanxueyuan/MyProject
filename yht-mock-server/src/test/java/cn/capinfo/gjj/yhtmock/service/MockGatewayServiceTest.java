package cn.capinfo.gjj.yhtmock.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MockGatewayServiceTest {

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
