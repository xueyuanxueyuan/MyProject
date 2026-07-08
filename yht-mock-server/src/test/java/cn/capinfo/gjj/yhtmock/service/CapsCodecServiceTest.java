package cn.capinfo.gjj.yhtmock.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapsCodecServiceTest {

    @Test
    void parseXmlParsesValidMessageAndReadsNamespacedText() {
        CapsCodecService codecService = new CapsCodecService();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Message xmlns="urn:caps:msg:caps.999.001.01">
                  <Body>
                    <ReqId>REQ-1</ReqId>
                  </Body>
                </Message>
                """;

        var document = codecService.parseXml(xml);

        assertThat(document).isNotNull();
        assertThat(codecService.text(document, "ReqId")).isEqualTo("REQ-1");
    }

    @Test
    void parseXmlRejectsDoctype() {
        CapsCodecService codecService = new CapsCodecService();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Message [ <!ENTITY xxe SYSTEM "file:///c:/windows/win.ini"> ]>
                <Message xmlns="urn:caps:msg:caps.999.001.01">
                  <Body>
                    <ReqId>REQ-1</ReqId>
                    <SysSeqNo>&xxe;</SysSeqNo>
                  </Body>
                </Message>
                """;

        var document = codecService.parseXml(xml);

        assertThat(document).isNull();
    }
}

