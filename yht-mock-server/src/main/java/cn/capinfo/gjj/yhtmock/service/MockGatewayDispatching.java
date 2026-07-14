package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import org.w3c.dom.Document;

import java.util.Set;

interface CapsMessageHandler {

    Set<String> supportedMesgTypes();

    GatewayDispatchResult handle(GatewayRequestContext context);
}

record GatewayRequestContext(
        CapsHeader requestHeader,
        Document document,
        String requestMesgType,
        String reqId,
        String protocolNo,
        String batchNo,
        String sysSeqNo,
        String acctNo,
        MockScenarioRule scenarioRule
) {
}

record GatewayDispatchResult(
        String responseMesgType,
        String responseXml,
        String status,
        String protocolNo,
        String batchNo,
        String sysSeqNo
) {

    static GatewayDispatchResult of(String responseMesgType, String responseXml, String status,
                                    String protocolNo, String batchNo, String sysSeqNo) {
        return new GatewayDispatchResult(responseMesgType, responseXml, status, protocolNo, batchNo, sysSeqNo);
    }
}
