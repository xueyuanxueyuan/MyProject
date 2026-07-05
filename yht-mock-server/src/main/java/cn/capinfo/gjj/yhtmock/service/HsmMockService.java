package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HsmMockService {

    private final MockStoreService storeService;

    public HsmMockService(MockStoreService storeService) {
        this.storeService = storeService;
    }

    public Map<String, Object> sign(String certId, String keyIndex, String algorithm, String data) {
        String signature = digestSignature(resolveSecret(certId, keyIndex), safe(data), safe(algorithm, "SM3withSM2"));
        record("SIGN", certId, keyIndex, data, signature, "SUCC");
        return buildResult(signature, true);
    }

    public Map<String, Object> verify(String certId, String keyIndex, String algorithm, String data, String signature) {
        String expected = digestSignature(resolveSecret(certId, keyIndex), safe(data), safe(algorithm, "SM3withSM2"));
        boolean matched = expected.equals(signature);
        record("VERIFY", certId, keyIndex, data, signature, matched ? "SUCC" : "FAIL");
        return buildResult(signature, matched);
    }

    public Map<String, Object> encrypt(String certId, String data) {
        String secret = resolveSecret(certId, "");
        String cipher = Base64.getEncoder().encodeToString((secret + "::" + safe(data)).getBytes(StandardCharsets.UTF_8));
        record("ENCRYPT", certId, "", data, cipher, "SUCC");
        return buildResult(cipher, true);
    }

    public Map<String, Object> decrypt(String certId, String cipher) {
        String plain;
        boolean success = false;
        try {
            String decoded = new String(Base64.getDecoder().decode(safe(cipher)), StandardCharsets.UTF_8);
            int index = decoded.indexOf("::");
            plain = index >= 0 ? decoded.substring(index + 2) : decoded;
            success = true;
        } catch (Exception e) {
            plain = e.getMessage();
        }
        record("DECRYPT", certId, "", cipher, plain, success ? "SUCC" : "FAIL");
        return buildResult(plain, success);
    }

    private Map<String, Object> buildResult(String value, boolean success) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("value", value);
        return result;
    }

    private String resolveSecret(String certId, String keyIndex) {
        MockSettings settings = storeService.getSettings();
        return settings.hsmMockKey + "|" + safe(certId) + "|" + safe(keyIndex);
    }

    private String digestSignature(String secret, String data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((secret + "|" + algorithm + "|" + data).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void record(String operation, String certId, String keyIndex, String request, String response, String status) {
        MockRecord record = new MockRecord();
        record.recordType = "HSM";
        record.source = "vendor-simulator";
        record.target = "caller";
        record.mesgType = operation;
        record.mesgId = safe(certId);
        record.reqId = safe(keyIndex);
        record.status = status;
        record.requestBody = request;
        record.responseBody = response;
        record.remark = "mock hsm operation";
        storeService.addRecord(record);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
