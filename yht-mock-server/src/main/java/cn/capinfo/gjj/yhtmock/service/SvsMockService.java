package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SvsMockService {

    public static final String ENVELOPE_PREFIX = "YHT_MOCK_ENC:v1:";
    private static final String DEFAULT_ALGORITHM = "SM3withSM2";
    private static final Pattern TAG_PATTERN_TEMPLATE = Pattern.compile("<%s(?:\\s[^>]*)?>(.*?)</%s>", Pattern.DOTALL);

    private final MockStoreService storeService;

    public SvsMockService(MockStoreService storeService) {
        this.storeService = storeService;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("service", "zayk-svs-mock");
        result.put("algorithm", DEFAULT_ALGORITHM + "/mock");
        result.put("encryptPrefix", ENVELOPE_PREFIX);
        result.put("verifyLenient", settings().svsVerifyLenient);
        return result;
    }

    public Map<String, Object> exportCert(String certId) {
        String certText = "-----BEGIN MOCK CERT-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(("YHT-MOCK-SVS-CERT:" + safe(certId)).getBytes(StandardCharsets.UTF_8))
                + "\n-----END MOCK CERT-----";
        byte[] certBytes = certText.getBytes(StandardCharsets.UTF_8);
        record("EXPORT_CERT", certId, "", "", certText, "SUCC", null);
        return buildBytesResult(certBytes, true, "mock certificate exported", certId, null, null);
    }

    public Map<String, Object> signDataByCertId(String certId, String signerIdBase64, Integer digestMethod,
                                                String plainBase64, String plainText, String traceId) {
        byte[] plainBytes = resolveInputBytes(plainBase64, plainText);
        byte[] signature = signBytes(resolveSecret(certId, "", digestMethod), plainBytes, DEFAULT_ALGORITHM);
        record("SIGN_DATA_BY_CERT_ID", certId, "", previewPlain(plainBytes), Base64.getEncoder().encodeToString(signature),
                "SUCC", firstNonBlank(traceId, extractTraceId(plainBytes)));
        return buildBytesResult(signature, true, "mock sign success", certId, null, traceId);
    }

    public Map<String, Object> signData(Integer signMethod, Integer keyIndex, String keyValueBase64,
                                        Integer inDataLen, String plainBase64, String plainText, String traceId) {
        byte[] plainBytes = resolveInputBytes(plainBase64, plainText);
        String keyIndexText = keyIndex == null ? "" : String.valueOf(keyIndex);
        byte[] signature = signBytes(resolveSecret("", keyIndexText, signMethod), plainBytes, "0x" + Integer.toHexString(defaultIfNull(signMethod, 0)));
        record("SIGN_DATA", "", keyIndexText, previewPlain(plainBytes), Base64.getEncoder().encodeToString(signature),
                "SUCC", firstNonBlank(traceId, extractTraceId(plainBytes)));
        return buildBytesResult(signature, true, "mock sign success", null, keyIndexText, traceId);
    }

    public Map<String, Object> verifySignedData(String certId, Integer digestMethod, String plainBase64,
                                                String signatureBase64, String traceId) {
        byte[] plainBytes = decodeBase64(plainBase64);
        byte[] signatureBytes = decodeBase64(signatureBase64);
        VerifyResult verifyResult = verifyBytes(resolveSecret(certId, "", digestMethod), plainBytes, signatureBytes, DEFAULT_ALGORITHM);
        String resolvedTraceId = firstNonBlank(traceId, extractTraceId(plainBytes));
        record("VERIFY_SIGNED_DATA", certId, "", previewPlain(plainBytes), verifyResult.message,
                verifyResult.success ? "SUCC" : "FAIL", resolvedTraceId);
        return buildVerifyResult(verifyResult, certId, null, resolvedTraceId);
    }

    public Map<String, Object> verifySignedDataEx(String certDataBase64, Integer signMethod, String plainBase64,
                                                  String signatureBase64, String traceId) {
        byte[] certData = decodeBase64(certDataBase64);
        byte[] plainBytes = decodeBase64(plainBase64);
        byte[] signatureBytes = decodeBase64(signatureBase64);
        String certKey = certData.length == 0 ? "" : Base64.getEncoder().encodeToString(certData);
        VerifyResult verifyResult = verifyBytes(resolveSecret(certKey, "", signMethod), plainBytes, signatureBytes,
                "0x" + Integer.toHexString(defaultIfNull(signMethod, 0)));
        String resolvedTraceId = firstNonBlank(traceId, extractTraceId(plainBytes));
        record("VERIFY_SIGNED_DATA_EX", certKey, "", previewPlain(plainBytes), verifyResult.message,
                verifyResult.success ? "SUCC" : "FAIL", resolvedTraceId);
        return buildVerifyResult(verifyResult, certKey, null, resolvedTraceId);
    }

    public Map<String, Object> encryptData(String certId, String plainBase64, String plainText, Integer mode, String traceId) {
        byte[] plainBytes = resolveInputBytes(plainBase64, plainText);
        byte[] cipherBytes = encryptBytes(certId, plainBytes);
        String resolvedTraceId = firstNonBlank(traceId, extractTraceId(plainBytes));
        record("ENCRYPT_DATA", certId, "", previewPlain(plainBytes), previewPlain(cipherBytes), "SUCC", resolvedTraceId);
        return buildBytesResult(cipherBytes, true, "mock encrypt success", certId, null, resolvedTraceId);
    }

    public Map<String, Object> decryptData(String certId, String cipherBase64, String cipherText, Integer mode, String traceId) {
        byte[] cipherBytes = resolveInputBytes(cipherBase64, cipherText);
        byte[] plainBytes = decryptBytes(certId, cipherBytes);
        String resolvedTraceId = firstNonBlank(traceId, extractTraceId(plainBytes));
        record("DECRYPT_DATA", certId, "", previewPlain(cipherBytes), previewPlain(plainBytes), "SUCC", resolvedTraceId);
        return buildBytesResult(plainBytes, true, "mock decrypt success", certId, null, resolvedTraceId);
    }

    public byte[] signBytes(String certId, byte[] plainBytes) {
        return signBytes(resolveSecret(certId, "", null), plainBytes, DEFAULT_ALGORITHM);
    }

    public boolean verifySignature(String certId, byte[] plainBytes, byte[] signatureBytes) {
        return verifyBytes(resolveSecret(certId, "", null), plainBytes, signatureBytes, DEFAULT_ALGORITHM).success;
    }

    public byte[] encryptBytes(String certId, byte[] plainBytes) {
        String payload = Base64.getEncoder().encodeToString(nullToEmpty(plainBytes));
        return (ENVELOPE_PREFIX + payload).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] decryptBytes(String certId, byte[] cipherBytes) {
        byte[] normalized = nullToEmpty(cipherBytes);
        String cipherText = new String(normalized, StandardCharsets.UTF_8).trim();
        if (cipherText.startsWith(ENVELOPE_PREFIX)) {
            return decodeBase64(cipherText.substring(ENVELOPE_PREFIX.length()));
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(cipherText);
            String decodedText = new String(decoded, StandardCharsets.UTF_8).trim();
            if (decodedText.startsWith(ENVELOPE_PREFIX)) {
                return decodeBase64(decodedText.substring(ENVELOPE_PREFIX.length()));
            }
            return decoded;
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private VerifyResult verifyBytes(String secret, byte[] plainBytes, byte[] signatureBytes, String algorithm) {
        byte[] expected = signBytes(secret, plainBytes, algorithm);
        boolean strictMatched = MessageDigest.isEqual(expected, nullToEmpty(signatureBytes));
        if (strictMatched) {
            return new VerifyResult(true, true, "mock verify success");
        }
        if (settings().svsVerifyLenient) {
            return new VerifyResult(true, false, "mock verify lenient success");
        }
        return new VerifyResult(false, false, "mock verify failed");
    }

    private byte[] signBytes(String secret, byte[] plainBytes, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] prefix = (safe(secret) + "|" + safe(algorithm) + "|").getBytes(StandardCharsets.UTF_8);
            return digest.digest(concat(prefix, nullToEmpty(plainBytes)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private Map<String, Object> buildBytesResult(byte[] value, boolean success, String message,
                                                 String certId, String keyIndex, String traceId) {
        String valueBase64 = Base64.getEncoder().encodeToString(nullToEmpty(value));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("code", success ? "0" : "-1");
        result.put("message", message);
        result.put("valueBase64", valueBase64);
        result.put("value", valueBase64);
        result.put("text", new String(nullToEmpty(value), StandardCharsets.UTF_8));
        result.put("algorithm", DEFAULT_ALGORITHM + "/mock");
        result.put("certId", safe(certId));
        result.put("keyIndex", safe(keyIndex));
        result.put("traceId", safe(traceId));
        return result;
    }

    private Map<String, Object> buildVerifyResult(VerifyResult verifyResult, String certId, String keyIndex, String traceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", verifyResult.success);
        result.put("code", verifyResult.success ? "0" : "-1");
        result.put("message", verifyResult.message);
        result.put("matched", verifyResult.strictMatched);
        result.put("lenient", verifyResult.success && !verifyResult.strictMatched);
        result.put("value", verifyResult.success);
        result.put("algorithm", DEFAULT_ALGORITHM + "/mock");
        result.put("certId", safe(certId));
        result.put("keyIndex", safe(keyIndex));
        result.put("traceId", safe(traceId));
        return result;
    }

    private void record(String operation, String certId, String keyIndex, String request, String response, String status, String traceId) {
        MockRecord record = new MockRecord();
        record.recordType = "SVS";
        record.source = "zayk-svs-mock";
        record.target = "caller";
        record.mesgType = operation;
        record.mesgId = safe(certId);
        record.reqId = safe(firstNonBlank(traceId, keyIndex));
        record.status = status;
        record.requestBody = request;
        record.responseBody = response;
        record.remark = "mock zayk/svs operation";
        storeService.addRecord(record);
    }

    private String resolveSecret(String certId, String keyIndex, Integer method) {
        MockSettings settings = settings();
        String key = settings.svsMockKey == null || settings.svsMockKey.isBlank() ? "YHT-MOCK-SVS" : settings.svsMockKey;
        return key + "|" + safe(certId) + "|" + safe(keyIndex) + "|" + (method == null ? "" : method);
    }

    private MockSettings settings() {
        MockSettings settings = storeService.getSettings();
        if (settings.svsMockKey == null || settings.svsMockKey.isBlank()) {
            settings.svsMockKey = "YHT-MOCK-SVS";
        }
        return settings;
    }

    private byte[] resolveInputBytes(String base64, String text) {
        if (base64 != null && !base64.isBlank()) {
            return decodeBase64(base64);
        }
        return safe(text).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return Base64.getMimeDecoder().decode(value.trim());
    }

    private byte[] nullToEmpty(byte[] bytes) {
        return bytes == null ? new byte[0] : bytes;
    }

    private int defaultIfNull(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String previewPlain(byte[] bytes) {
        String text = new String(nullToEmpty(bytes), StandardCharsets.UTF_8);
        return text.length() > 4000 ? text.substring(0, 4000) + "..." : text;
    }

    private String extractTraceId(byte[] plainBytes) {
        String text = new String(nullToEmpty(plainBytes), StandardCharsets.UTF_8);
        return firstNonBlank(
                findTag(text, "ReqId"),
                findTag(text, "OrgnlReqId"),
                findTag(text, "DbtrProtocol"),
                findTag(text, "OrgnlDbtrProtocol"),
                findTag(text, "BatchNo"),
                findTag(text, "BtchNb"),
                findTag(text, "SysSeqNo"),
                findTag(text, "SerialNum"));
    }

    private String findTag(String text, String tagName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile(String.format(TAG_PATTERN_TEMPLATE.pattern(), tagName, tagName), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record VerifyResult(boolean success, boolean strictMatched, String message) {
    }
}
