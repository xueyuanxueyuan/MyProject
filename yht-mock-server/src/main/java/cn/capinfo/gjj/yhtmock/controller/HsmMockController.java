package cn.capinfo.gjj.yhtmock.controller;

import cn.capinfo.gjj.yhtmock.service.HsmMockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/yht-mock/api/hsm")
public class HsmMockController {

    private final HsmMockService hsmMockService;

    public HsmMockController(HsmMockService hsmMockService) {
        this.hsmMockService = hsmMockService;
    }

    @PostMapping("/sign")
    public Map<String, Object> sign(@RequestBody HsmSignRequest request) {
        return hsmMockService.sign(request.certId, request.keyIndex, request.algorithm, request.data);
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody HsmVerifyRequest request) {
        return hsmMockService.verify(request.certId, request.keyIndex, request.algorithm, request.data, request.signature);
    }

    @PostMapping("/encrypt")
    public Map<String, Object> encrypt(@RequestBody HsmEncryptRequest request) {
        return hsmMockService.encrypt(request.certId, request.data);
    }

    @PostMapping("/decrypt")
    public Map<String, Object> decrypt(@RequestBody HsmDecryptRequest request) {
        return hsmMockService.decrypt(request.certId, request.cipher);
    }

    public static class HsmSignRequest {
        public String certId;
        public String keyIndex;
        public String algorithm;
        public String data;
    }

    public static class HsmVerifyRequest extends HsmSignRequest {
        public String signature;
    }

    public static class HsmEncryptRequest {
        public String certId;
        public String data;
    }

    public static class HsmDecryptRequest {
        public String certId;
        public String cipher;
    }
}
