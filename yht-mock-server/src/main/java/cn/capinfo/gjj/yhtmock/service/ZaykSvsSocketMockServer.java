package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class ZaykSvsSocketMockServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ZaykSvsSocketMockServer.class);
    private static final int ERR_UNSUPPORTED = 67149866;

    private final YhtMockProperties properties;
    private final SvsMockService svsMockService;
    private final MockStoreService storeService;
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "yht-mock-svs-socket");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public ZaykSvsSocketMockServer(YhtMockProperties properties, SvsMockService svsMockService, MockStoreService storeService) {
        this.properties = properties;
        this.svsMockService = svsMockService;
        this.storeService = storeService;
    }

    @Override
    public void start() {
        if (running || !properties.getSvsSocket().isEnabled()) {
            return;
        }
        running = true;
        executorService.execute(this::acceptLoop);
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(serverSocket);
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void acceptLoop() {
        int port = properties.getSvsSocket().getPort();
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            log.info("YHT zayk/SVS socket mock started on port {}", port);
            while (running) {
                Socket client = socket.accept();
                executorService.execute(() -> handleClient(client));
            }
        } catch (SocketException e) {
            if (running) {
                log.warn("YHT zayk/SVS socket mock stopped unexpectedly: {}", e.getMessage());
            }
        } catch (IOException e) {
            running = false;
            log.error("YHT zayk/SVS socket mock failed to start on port {}", port, e);
        } finally {
            serverSocket = null;
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setTcpNoDelay(true);
            while (running && !socket.isClosed()) {
                byte[] requestPacket = ZaykSvsAsn1Codec.readDerPacket(socket.getInputStream());
                if (requestPacket == null) {
                    return;
                }
                byte[] responsePacket = handlePacket(requestPacket, socket.getRemoteSocketAddress().toString());
                socket.getOutputStream().write(responsePacket);
                socket.getOutputStream().flush();
            }
        } catch (SocketException ignored) {
            // client closed the connection
        } catch (Exception e) {
            log.warn("YHT zayk/SVS socket mock client handling failed: {}", e.getMessage());
        }
    }

    private byte[] handlePacket(byte[] requestPacket, String remote) {
        ZaykSvsAsn1Codec.SvsRequest request = null;
        String operation = "UNKNOWN";
        String requestSummary = "hex=" + previewHex(requestPacket);
        try {
            request = ZaykSvsAsn1Codec.decodeRequest(requestPacket);
            operation = opName(request.opType());
            requestSummary = summarizeRequest(request);
            byte[] response = dispatch(request);
            addRecord(operation, remote, requestSummary, "SUCC len=" + response.length, "SUCC", traceId(request.body()));
            return response;
        } catch (Exception e) {
            int opType = request == null ? 0 : request.opType();
            byte[] response = ZaykSvsAsn1Codec.encodeErrorResponse(opType, ERR_UNSUPPORTED);
            addRecord(operation, remote, requestSummary, e.getMessage(), "FAIL", "");
            log.warn("YHT zayk/SVS socket mock request failed: {}", e.getMessage());
            return response;
        }
    }

    private byte[] dispatch(ZaykSvsAsn1Codec.SvsRequest request) {
        List<ZaykSvsAsn1Codec.DerValue> body = request.body();
        int opType = request.opType();
        return switch (opType) {
            case 0 -> exportCert(opType, asString(ZaykSvsAsn1Codec.firstOctet(body)));
            case 3 -> signData(opType, body);
            case 4 -> verifySignedData(opType, body);
            case 29, 46, 48 -> encryptData(opType, body);
            case 30, 47, 49 -> decryptData(opType, body);
            case 32 -> signDataByCertId(opType, body);
            case 44 -> verifySignedDataEx(opType, body);
            default -> throw new IllegalArgumentException("Unsupported zayk/SVS opType: " + opType);
        };
    }

    private byte[] exportCert(int opType, String certId) {
        String certText = "-----BEGIN MOCK CERT-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(("YHT-MOCK-SVS-CERT:" + safe(certId)).getBytes(StandardCharsets.UTF_8))
                + "\n-----END MOCK CERT-----";
        return ZaykSvsAsn1Codec.encodeBytesResponse(opType, certText.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] signData(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        int keyIndex = body.size() > 1 ? ZaykSvsAsn1Codec.asInt(body.get(1)) : 0;
        byte[] plain = ZaykSvsAsn1Codec.asBytes(ZaykSvsAsn1Codec.lastOctet(body));
        byte[] signature = svsMockService.signBytes(String.valueOf(keyIndex), plain);
        return ZaykSvsAsn1Codec.encodeBytesResponse(opType, signature);
    }

    private byte[] signDataByCertId(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        String certId = asString(ZaykSvsAsn1Codec.octetAt(body, 0));
        byte[] plain = ZaykSvsAsn1Codec.asBytes(ZaykSvsAsn1Codec.octetAt(body, 3));
        byte[] signature = svsMockService.signBytes(certId, plain);
        return ZaykSvsAsn1Codec.encodeBytesResponse(opType, signature);
    }

    private byte[] verifySignedData(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        String certId = resolveVerifyCertId(body);
        List<ZaykSvsAsn1Codec.DerValue> octets = ZaykSvsAsn1Codec.octets(body);
        byte[] plain = octets.size() >= 1 ? ZaykSvsAsn1Codec.asBytes(octets.get(octets.size() - 2)) : new byte[0];
        byte[] signature = octets.size() >= 2 ? ZaykSvsAsn1Codec.asBytes(octets.get(octets.size() - 1)) : new byte[0];
        boolean ok = svsMockService.verifySignature(certId, plain, signature);
        if (!ok) {
            throw new IllegalArgumentException("mock verify failed");
        }
        return ZaykSvsAsn1Codec.encodeSuccessCodeResponse(opType);
    }

    private byte[] verifySignedDataEx(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        return ZaykSvsAsn1Codec.encodeSuccessCodeResponse(opType);
    }

    private byte[] encryptData(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        String certId = asString(ZaykSvsAsn1Codec.firstOctet(body));
        byte[] plain = ZaykSvsAsn1Codec.asBytes(ZaykSvsAsn1Codec.lastOctet(body));
        byte[] cipher = svsMockService.encryptBytes(certId, plain);
        return ZaykSvsAsn1Codec.encodeBytesResponse(opType, cipher);
    }

    private byte[] decryptData(int opType, List<ZaykSvsAsn1Codec.DerValue> body) {
        String certId = asString(ZaykSvsAsn1Codec.firstOctet(body));
        byte[] cipher = ZaykSvsAsn1Codec.asBytes(ZaykSvsAsn1Codec.lastOctet(body));
        byte[] plain = svsMockService.decryptBytes(certId, cipher);
        return ZaykSvsAsn1Codec.encodeBytesResponse(opType, plain);
    }

    private String resolveVerifyCertId(List<ZaykSvsAsn1Codec.DerValue> body) {
        ZaykSvsAsn1Codec.DerValue taggedCertId = ZaykSvsAsn1Codec.taggedAt(body, 1);
        if (taggedCertId != null) {
            return new String(taggedCertId.value(), StandardCharsets.UTF_8);
        }
        ZaykSvsAsn1Codec.DerValue taggedCert = ZaykSvsAsn1Codec.taggedAt(body, 0);
        if (taggedCert != null) {
            return Base64.getEncoder().encodeToString(taggedCert.value());
        }
        return asString(ZaykSvsAsn1Codec.firstOctet(body));
    }

    private String summarizeRequest(ZaykSvsAsn1Codec.SvsRequest request) {
        return "op=" + request.opType()
                + ", name=" + opName(request.opType())
                + ", fields=" + request.body().size()
                + ", traceId=" + traceId(request.body());
    }

    private String traceId(List<ZaykSvsAsn1Codec.DerValue> body) {
        for (ZaykSvsAsn1Codec.DerValue value : body) {
            if (value.tagClass() == 0 && value.tagNo() == 4) {
                String text = new String(value.value(), StandardCharsets.UTF_8);
                String trace = firstXmlValue(text, "ReqId");
                if (!trace.isBlank()) {
                    return trace;
                }
                trace = firstXmlValue(text, "OrgnlReqId");
                if (!trace.isBlank()) {
                    return trace;
                }
            }
        }
        return "";
    }

    private String firstXmlValue(String text, String tagName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String start = "<" + tagName + ">";
        String end = "</" + tagName + ">";
        int startIndex = text.indexOf(start);
        int endIndex = text.indexOf(end);
        if (startIndex < 0 || endIndex <= startIndex) {
            return "";
        }
        return text.substring(startIndex + start.length(), endIndex).trim();
    }

    private void addRecord(String operation, String remote, String requestBody, String responseBody, String status, String traceId) {
        MockRecord record = new MockRecord();
        record.recordType = "SVS_SOCKET";
        record.source = "zayk-svs-sdk";
        record.target = "yht-mock-svs-socket";
        record.mesgType = operation;
        record.mesgId = operation;
        record.reqId = safe(traceId);
        record.status = status;
        record.requestBody = requestBody;
        record.responseBody = responseBody;
        record.remark = "remote=" + safe(remote) + "; mock zayk/svs socket operation";
        storeService.addRecord(record);
    }

    private String opName(int opType) {
        return switch (opType) {
            case 0 -> "EXPORT_CERT";
            case 3 -> "SIGN_DATA";
            case 4 -> "VERIFY_SIGNED_DATA";
            case 29 -> "ENCRYPT_DATA";
            case 30 -> "DECRYPT_DATA";
            case 32 -> "SIGN_DATA_BY_CERT_ID";
            case 44 -> "VERIFY_SIGNED_DATA_EX";
            case 46 -> "ENCRYPT_DATA_PRIVATE";
            case 47 -> "DECRYPT_DATA_PRIVATE";
            case 48 -> "ENCRYPT_DATA_PUBLIC";
            case 49 -> "DECRYPT_DATA_PUBLIC";
            default -> "OP_" + opType;
        };
    }

    private String asString(ZaykSvsAsn1Codec.DerValue value) {
        return value == null ? "" : ZaykSvsAsn1Codec.asUtf8(value);
    }

    private String previewHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int len = Math.min(bytes.length, 80);
        StringBuilder builder = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            builder.append(String.format("%02X", bytes[i]));
        }
        if (bytes.length > len) {
            builder.append("...");
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}