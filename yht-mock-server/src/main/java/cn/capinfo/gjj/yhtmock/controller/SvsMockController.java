package cn.capinfo.gjj.yhtmock.controller;

import cn.capinfo.gjj.yhtmock.service.SvsMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/yht-mock/api/svs")
public class SvsMockController {

    private final SvsMockService svsMockService;

    public SvsMockController(SvsMockService svsMockService) {
        this.svsMockService = svsMockService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return svsMockService.health();
    }

    @GetMapping("/export-cert")
    public Map<String, Object> exportCert(@RequestParam(required = false) String certId) {
        return svsMockService.exportCert(certId);
    }

    @PostMapping("/sign-data-by-cert-id")
    public Map<String, Object> signDataByCertId(@RequestBody SignDataByCertIdRequest request) {
        return svsMockService.signDataByCertId(
                request.certId,
                request.signerIdBase64,
                request.digestMethod,
                request.plainBase64,
                request.plainText,
                request.traceId);
    }

    @PostMapping("/sign-data")
    public Map<String, Object> signData(@RequestBody SignDataRequest request) {
        return svsMockService.signData(
                request.signMethod,
                request.keyIndex,
                request.keyValueBase64,
                request.inDataLen,
                request.plainBase64,
                request.plainText,
                request.traceId);
    }

    @PostMapping("/verify-signed-data")
    public Map<String, Object> verifySignedData(@RequestBody VerifySignedDataRequest request) {
        return svsMockService.verifySignedData(
                request.certId,
                request.digestMethod,
                request.plainBase64,
                request.signatureBase64,
                request.traceId);
    }

    @PostMapping("/verify-signed-data-ex")
    public Map<String, Object> verifySignedDataEx(@RequestBody VerifySignedDataExRequest request) {
        return svsMockService.verifySignedDataEx(
                request.certDataBase64,
                request.signMethod,
                request.plainBase64,
                request.signatureBase64,
                request.traceId);
    }

    @PostMapping("/encrypt-data")
    public Map<String, Object> encryptData(@RequestBody EncryptDataRequest request) {
        return svsMockService.encryptData(
                request.certId,
                request.plainBase64,
                request.plainText,
                request.mode,
                request.traceId);
    }

    @PostMapping("/decrypt-data")
    public Map<String, Object> decryptData(@RequestBody DecryptDataRequest request) {
        return svsMockService.decryptData(
                request.certId,
                request.cipherBase64,
                request.cipherText,
                request.mode,
                request.traceId);
    }

    public static class SignDataByCertIdRequest {
        public String certId;
        public String signerIdBase64;
        public Integer digestMethod;
        public String plainBase64;
        public String plainText;
        public String traceId;
    }

    public static class SignDataRequest {
        public Integer signMethod;
        public Integer keyIndex;
        public String keyValueBase64;
        public Integer inDataLen;
        public String plainBase64;
        public String plainText;
        public String traceId;
    }

    public static class VerifySignedDataRequest {
        public String certId;
        public Integer digestMethod;
        public String plainBase64;
        public String signatureBase64;
        public String traceId;
    }

    public static class VerifySignedDataExRequest {
        public String certDataBase64;
        public Integer signMethod;
        public String plainBase64;
        public String signatureBase64;
        public String traceId;
    }

    public static class EncryptDataRequest {
        public String certId;
        public String plainBase64;
        public String plainText;
        public Integer mode;
        public String traceId;
    }

    public static class DecryptDataRequest {
        public String certId;
        public String cipherBase64;
        public String cipherText;
        public Integer mode;
        public String traceId;
    }
}
