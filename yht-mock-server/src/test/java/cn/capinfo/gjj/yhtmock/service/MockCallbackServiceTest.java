package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockCallbackServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void manualBankSignBuildsValidBksdCaps305AndSavesPendingState() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = new MockCallbackService(codecService, storeService);

        String message = callbackService.buildManualBankInitiatedMessage(
                "caps.305.bank-sign",
                "REQ-BANK-SIGN-001",
                "",
                "62220000000000001111",
                "张三",
                "330400199001010011",
                "00600|00601",
                "105000",
                "银行主动签约测试");

        CapsCodecService.ParsedFrame frame = codecService.parseFrame(message);
        var document = codecService.parseXml(frame.xmlBody());
        assertThat(frame.header().mesgType).isEqualTo("caps.305.001.01");
        assertThat(codecService.text(document, "ReqId")).isEqualTo("REQ-BANK-SIGN-001");
        assertThat(codecService.text(document, "SndrFlg")).isEqualTo("BKSD");
        assertThat(codecService.text(document, "ChngTp")).isEqualTo("ADDD");
        assertThat(codecService.text(document, "CstmrNm")).isEqualTo("张三");
        assertThat(codecService.text(document, "FeeNoList")).isEqualTo("00600|00601");
        assertThat(codecService.text(document, "DbtrActId")).isEqualTo("62220000000000001111");
        assertThat(codecService.text(document, "DbtrActName")).isEqualTo("张三");
        assertThat(codecService.text(document, "DbtrCardType")).isEqualTo("03");
        assertThat(codecService.text(document, "DbtrBankId")).isEqualTo("105000");
        assertThat(codecService.text(document, "SndTp")).isEqualTo("SD00");

        ProtocolState state = storeService.findProtocolByReqId("REQ-BANK-SIGN-001");
        assertThat(state).isNotNull();
        assertThat(state.protocolNo).isEqualTo("BANK-PENDING-REQ-BANK-SIGN-001");
        assertThat(state.status).isEqualTo("PENDING");
        assertThat(state.changeType).isEqualTo("ADDD");
        assertThat(state.callbackEnabled).isFalse();
    }

    @Test
    void manualBankCancelBuildsValidBksdCaps305AndSavesPendingState() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = new MockCallbackService(codecService, storeService);

        String message = callbackService.buildManualBankInitiatedMessage(
                "caps.305.bank-cancel",
                "REQ-BANK-CANCEL-001",
                "PROTOCOL-001",
                "",
                "张三",
                "330400199001010011",
                "00600|00601",
                "105000",
                "银行主动解约测试");

        CapsCodecService.ParsedFrame frame = codecService.parseFrame(message);
        var document = codecService.parseXml(frame.xmlBody());
        assertThat(frame.header().mesgType).isEqualTo("caps.305.001.01");
        assertThat(codecService.text(document, "ReqId")).isEqualTo("REQ-BANK-CANCEL-001");
        assertThat(codecService.text(document, "SndrFlg")).isEqualTo("BKSD");
        assertThat(codecService.text(document, "ChngTp")).isEqualTo("DELE");
        assertThat(codecService.text(document, "CstmrNm")).isEqualTo("张三");
        assertThat(codecService.text(document, "FeeNoList")).isEqualTo("00600|00601");
        assertThat(codecService.text(document, "DbtrProtocol")).isEqualTo("PROTOCOL-001");
        assertThat(codecService.text(document, "DbtrBankId")).isEqualTo("105000");
        assertThat(codecService.text(document, "SndTp")).isEqualTo("SD00");

        ProtocolState state = storeService.findProtocol("PROTOCOL-001", "");
        assertThat(state).isNotNull();
        assertThat(state.signReqId).isEqualTo("REQ-BANK-CANCEL-001");
        assertThat(state.status).isEqualTo("PENDING");
        assertThat(state.changeType).isEqualTo("DELE");
        assertThat(state.callbackEnabled).isFalse();
    }
    @Test
    void manualBankCancelRequiresProtocolNumber() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = new MockCallbackService(codecService, storeService);

        assertThatThrownBy(() -> callbackService.buildManualBankInitiatedMessage(
                "caps.305.bank-cancel", "REQ-BANK-CANCEL-002", "", "", "张三",
                "330400199001010011", "00600", "105000", "银行主动解约测试"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议号");
    }

    @Test
    void manualBankMessageRejectsUnknownLogicType() {
        CapsCodecService codecService = new CapsCodecService();
        MockStoreService storeService = DatabaseTestSupport.create(tempDir.resolve("state.json"));
        MockCallbackService callbackService = new MockCallbackService(codecService, storeService);

        assertThatThrownBy(() -> callbackService.buildManualBankInitiatedMessage(
                "caps.305.unknown", "REQ-BANK-UNKNOWN-001", "PROTOCOL-001", "", "张三",
                "330400199001010011", "00600", "105000", "非法业务类型"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务类型");
    }
}
