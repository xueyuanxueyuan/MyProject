package cn.capinfo.gjj.yhtmock.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Caps900EnvelopeTest {
    private static final String NAMESPACE = "urn:cbcc:std:caps:2020:tech:xsd:caps.900.001.01";
    private final CapsCodecService codec = new CapsCodecService();

    @TempDir
    Path tempDir;

    @Test
    void signedEncryptedRequestStillReceivesUnsignedPlaintext900() {
        MockStoreService store = new MockStoreService(tempDir.resolve("state.json"));
        MockGatewaySupport support = new MockGatewaySupport(codec, store);
        SvsMockService svs = mock(SvsMockService.class);
        when(svs.decryptBytes(anyString(), any())).thenReturn(
                "<Head><CorpNo>33503C5801</CorpNo></Head><Body><SysChckNo>CHECK-1</SysChckNo></Body>"
                        .getBytes(StandardCharsets.UTF_8));
        MockGatewayService gateway = new MockGatewayService(codec, store,
                mock(MockCallbackService.class), support, svs, List.of(new ProbeHandler(support)));
        gateway.initRegistry();
        for (String mesgType : new String[]{"caps.999.001.01", "caps.888.001.01"}) {
            String request = codec.buildHeader(mesgType, "R", "REF-001",
                    "CAPS", "CAPS", "33503C5801", "904290099992")
                    + "{S:TEST}\r\n" + codec.buildDocumentWithText(mesgType, "AAAA");
            String response = gateway.dispatch(request);
            assertThat(response).doesNotContain("{S:");
            Element root = codec.parseXml(codec.parseFrame(response).xmlBody()).getDocumentElement();
            assertThat(root.getLocalName()).isEqualTo("Document");
            assertThat(root.getNamespaceURI()).isEqualTo(NAMESPACE);
            assertThat(codec.text(root, "ResFlag")).isEqualTo(
                    "caps.999.001.01".equals(mesgType) ? "SUCC" : "FAIL");
        }
        verify(svs, never()).signBytes(anyString(), any());
        verify(svs, never()).encryptBytes(anyString(), any());
    }

    @Test
    void successAndFailureUseDocumentEnvelopeWithPlaintextHead() {
        MockGatewaySupport support = new MockGatewaySupport(codec, null);
        for (String flag : new String[]{"SUCC", "FAIL"}) {
            String xml = support.buildCaps900("33503C5801", flag, "CODE", "result<&>", "CHECK-1");
            Element root = codec.parseXml(xml).getDocumentElement();
            assertThat(root.getLocalName()).isEqualTo("Document");
            assertThat(root.getNamespaceURI()).isEqualTo(NAMESPACE);
            assertThat(root.getAttribute("xmlns:xsi")).isEqualTo("http://www.w3.org/2001/XMLSchema-instance");
            Element message = (Element) root.getFirstChild();
            assertThat(message.getLocalName()).isEqualTo("Message");
            assertThat(message.getNamespaceURI()).isEqualTo(NAMESPACE);
            assertThat(((Element) message.getFirstChild()).getLocalName()).isEqualTo("Head");
            assertThat(codec.text(root, "CorpNo")).isEqualTo("33503C5801");
            assertThat(codec.text(root, "ResFlag")).isEqualTo(flag);
            assertThat(codec.text(root, "ProcCode")).isEqualTo("SUCC".equals(flag) ? "I000" : "E999");
            assertThat(codec.text(root, "ProcMsg")).isEqualTo("SUCC".equals(flag) ? "平台校验成功" : "其他错误请联系中心解决");
            assertThat(codec.text(root, "Remark")).isEqualTo("CHECK-1");
            assertThat(root.getElementsByTagNameNS(NAMESPACE, "Body").getLength()).isZero();
        }
    }

    @Test
    void configuredCodesCannotOverrideProtocolDescriptions() {
        MockGatewaySupport support = new MockGatewaySupport(codec, null);
        for (String code : new String[]{"E012", "S999", "I000", "E017", "CUSTOM", "", null}) {
            var document = codec.parseXml(support.buildCaps900("CORP", "FAIL", code, "custom"));
            String expectedCode = "E012".equals(code) || "S999".equals(code) ? code : "E999";
            String expectedMessage = "E012".equals(code) ? "业务类型非法"
                    : "S999".equals(code) ? "其他系统错" : "其他错误请联系中心解决";
            assertThat(codec.text(document, "ProcCode")).isEqualTo(expectedCode);
            assertThat(codec.text(document, "ProcMsg")).isEqualTo(expectedMessage);
        }
    }

    @Test
    void codecWrapsFallback900ButDoesNotChangeOtherMessages() {
        String ack = codec.buildXml("caps.900.001.01", "<ResFlag>FAIL</ResFlag>", null);
        assertThat(codec.parseXml(ack).getDocumentElement().getLocalName()).isEqualTo("Document");
        String other = codec.buildXml("caps.302.001.01", "<CorpNo>CORP</CorpNo>", "<Value>1</Value>");
        assertThat(codec.parseXml(other).getDocumentElement().getLocalName()).isEqualTo("Message");
        assertThat(codec.parseXml(other).getDocumentElement().getNamespaceURI()).isEqualTo("urn:caps:msg:caps.302.001.01");
    }
}
