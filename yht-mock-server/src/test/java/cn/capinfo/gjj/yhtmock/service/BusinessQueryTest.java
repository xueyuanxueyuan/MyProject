package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.MovementFlow;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BusinessQueryTest {

    @Test
    void businessEndpointsExposeListsAndBatchDetailsOverHttp() throws Exception {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var trade = new TradeState();
        trade.sysSeqNo = "SEQ-HTTP"; trade.reqId = "REQ-HTTP"; trade.status = "SUCC"; trade.amount = "5.00";
        store.saveTrade(trade);
        var batch = new BatchState();
        batch.batchNo = "BATCH-HTTP"; batch.tranCode = "40502"; batch.status = "SUCC"; batch.resFlag = "SUCC";
        batch.totalCount = "1"; batch.totalAmount = "5.00";
        batch.fileData = Base64.getEncoder().encodeToString(("summary\n1|BANK|ACCT-1|5.00|户名|00|交易成功|HOST1")
                .getBytes(StandardCharsets.UTF_8));
        store.saveBatch(batch);
        var controller = new cn.capinfo.gjj.yhtmock.controller.YhtMockApiController(null, store, null);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/business/trade"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].key").value("SEQ-HTTP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].status").value("SUCC"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/business/batch"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].batchNo").value("BATCH-HTTP"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/business/protocol"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/business/unknown"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/batches/BATCH-HTTP/details"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.detailCount").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.details[0].retCode").value("00"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.details[0].retMsg").value("交易成功"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/yht-mock/api/batches/BATCH-MISSING/details"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void businessListReturnsTradeBatchAndProtocolSummariesWithCurrentStatus() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var trade = new TradeState();
        trade.sysSeqNo = "SEQ-1"; trade.reqId = "REQ-1"; trade.tranCode = "20602";
        trade.amount = "12.34"; trade.status = "SUCC"; trade.resFlag = "SUCC"; trade.retCode = "000000";
        trade.acctNo = "CENTER-ACCOUNT"; trade.bankId = "CENTER";
        store.saveTrade(trade);
        var batch = new BatchState();
        batch.batchNo = "BATCH-1"; batch.reqId = "REQ-B1"; batch.tranCode = "40502";
        batch.status = "PROC"; batch.resFlag = "SUCC"; batch.totalCount = "2"; batch.totalAmount = "30.00";
        store.saveBatch(batch);
        var protocol = new ProtocolState();
        protocol.protocolNo = "P-1"; protocol.signReqId = "REQ-P1"; protocol.customerName = "测试客户";
        protocol.status = "SUCC"; protocol.resFlag = "SUCC";
        store.saveProtocol(protocol);

        Map<String, Object> trades = store.businessList("trade", 1, 20);
        assertThat(trades.get("count")).isEqualTo(1);
        assertThat(trades.get("total")).isEqualTo(1L);
        assertThat(trades.get("totalPages")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tradeRows = (List<Map<String, Object>>) trades.get("items");
        assertThat(tradeRows.get(0)).containsEntry("key", "SEQ-1")
                .containsEntry("status", "SUCC").containsEntry("traceKeyword", "REQ-1")
                .containsEntry("amount", "12.34");

        Map<String, Object> batches = store.businessList("batch", 1, 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batchRows = (List<Map<String, Object>>) batches.get("items");
        assertThat(batchRows.get(0)).containsEntry("key", "BATCH-1").containsEntry("status", "PROC")
                .containsEntry("totalCount", "2").containsEntry("traceKeyword", "BATCH-1");

        Map<String, Object> protocols = store.businessList("protocol", 1, 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> protocolRows = (List<Map<String, Object>>) protocols.get("items");
        assertThat(protocolRows.get(0)).containsEntry("key", "P-1").containsEntry("status", "SUCC")
                .containsEntry("customerName", "测试客户");

        assertThatThrownBy(() -> store.businessList("unknown", 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的业务类型");
    }

    @Test
    void businessListPagesTradesAndRejectsOutOfRangePaging() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        for (int index = 1; index <= 3; index++) {
            var trade = new TradeState();
            trade.sysSeqNo = "SEQ-" + index; trade.reqId = "REQ-" + index; trade.status = "SUCC";
            trade.amount = index + ".00"; trade.updatedAt = index;
            store.saveTrade(trade);
        }
        Map<String, Object> first = store.businessList("trade", 1, 2);
        assertThat(first).containsEntry("total", 3L).containsEntry("totalPages", 2)
                .containsEntry("page", 1).containsEntry("size", 2).containsEntry("count", 2);
        Map<String, Object> second = store.businessList("trade", 2, 2);
        assertThat(second).containsEntry("count", 1).containsEntry("page", 2);
        assertThat(second).containsEntry("total", 3L).containsEntry("totalPages", 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstPage = (List<Map<String, Object>>) first.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lastPage = (List<Map<String, Object>>) second.get("items");
        assertThat(firstPage).hasSize(2);
        assertThat(lastPage).hasSize(1);
        // updatedAt 由 saveTrade 重写，分页顺序不保证稳定；这里只断言两页覆盖全部且不重复。
        var keys = new java.util.HashSet<String>();
        firstPage.forEach(row -> keys.add(String.valueOf(row.get("key"))));
        lastPage.forEach(row -> keys.add(String.valueOf(row.get("key"))));
        assertThat(keys).containsExactlyInAnyOrder("SEQ-1", "SEQ-2", "SEQ-3");
        Map<String, Object> empty = store.businessList("trade", 3, 2);
        assertThat(empty).containsEntry("count", 0).containsEntry("total", 3L);
        assertThatThrownBy(() -> store.businessList("trade", 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("分页参数");
        assertThatThrownBy(() -> store.businessList("trade", 1, 101))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("分页参数");
    }

    @Test
    void batchDetailsListEveryDetailWithStatusAndFailureReasonLinkedToFlow() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var batch = new BatchState();
        batch.batchNo = "BATCH-D"; batch.reqId = "REQ-D"; batch.tranCode = "40502";
        batch.status = "SUCC"; batch.resFlag = "SUCC"; batch.centerBankId = "CENTER";
        batch.corpAcctNo = "CENTER-ACCOUNT"; batch.totalCount = "2"; batch.totalAmount = "30.00";
        batch.fileData = Base64.getEncoder().encodeToString(("40502|CORP|FEE|2|30.00|1|1|0|BATCH-D|20260921\n"
                + "1|BANK|ACCT-OK|10.00|户名甲|00|交易成功|HOST1\n"
                + "2|BANK|ACCT-FAIL|20.00|户名乙|01|余额不足|HOST2").getBytes(StandardCharsets.UTF_8));
        store.saveBatch(batch);

        var flow = new MovementFlow();
        flow.id = "FLOW-PKG"; flow.movementKey = "KEY-PKG"; flow.sourceType = "BATCH";
        flow.businessDate = "20260921"; flow.acctNo = "CENTER-ACCOUNT"; flow.centerBankId = "CENTER";
        flow.direction = "IN"; flow.amount = new java.math.BigDecimal("10.00");
        flow.batchNo = "BATCH-D"; flow.status = "FAIL"; flow.lastError = "中心账户所属银行 CENTER 未配置或已停用对手账户";
        store.movementFlows().register(flow);

        Map<String, Object> result = store.batchDetails("BATCH-D");
        assertThat(result).containsEntry("batchNo", "BATCH-D").containsEntry("detailCount", 2)
                .containsEntry("successCount", 1L).containsEntry("successAmount", "10.00")
                .containsEntry("centerBankId", "CENTER").containsEntry("flowGroupMode", "PACKAGE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertThat(details.get(0)).containsEntry("seq", "1").containsEntry("acctNo", "ACCT-OK")
                .containsEntry("amount", "10.00").containsEntry("acctName", "户名甲")
                .containsEntry("retCode", "00").containsEntry("retMsg", "交易成功")
                .containsEntry("hostSerial", "HOST1").containsEntry("success", true);
        assertThat(details.get(1)).containsEntry("seq", "2").containsEntry("retCode", "01")
                .containsEntry("retMsg", "余额不足").containsEntry("success", false);
        // 动账改为按包生成后，明细不再逐笔挂流水，包级流水统一在 packageFlow 中返回。
        assertThat(details.get(0)).doesNotContainKeys("flowStatus", "flowLastError");
        @SuppressWarnings("unchecked")
        Map<String, Object> packageFlow = (Map<String, Object>) result.get("packageFlow");
        assertThat(packageFlow).containsEntry("id", "FLOW-PKG").containsEntry("status", "FAIL")
                .containsEntry("amount", "10.00").containsEntry("direction", "IN")
                .containsEntry("lastError", "中心账户所属银行 CENTER 未配置或已停用对手账户");
        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) result.get("flowStatusCount");
        assertThat(counts).containsEntry("FAIL", 1L);
        assertThat(result.get("fileSummary")).asString().startsWith("40502|CORP|FEE|2|30.00");
    }

    @Test
    void repairProcessingBatchesDerivesFinalStatusFromResultFileAndSkipsUnparsable() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var success = new BatchState();
        success.batchNo = "REPAIR-SUCC"; success.tranCode = "40502"; success.status = "PROC";
        success.totalCount = "2"; success.fileData = Base64.getEncoder().encodeToString(
                ("summary\n1|BANK|A1|10.00|甲|00|交易成功|H1\n2|BANK|A2|20.00|乙|00|交易成功|H2")
                        .getBytes(StandardCharsets.UTF_8));
        store.saveBatch(success);
        var failed = new BatchState();
        failed.batchNo = "REPAIR-FAIL"; failed.tranCode = "40502"; failed.status = "PROC";
        failed.fileData = Base64.getEncoder().encodeToString(
                ("summary\n1|BANK|A1|10.00|甲|01|余额不足|H1").getBytes(StandardCharsets.UTF_8));
        store.saveBatch(failed);
        var noFile = new BatchState();
        noFile.batchNo = "REPAIR-NOFILE"; noFile.tranCode = "40502"; noFile.status = "PROC";
        store.saveBatch(noFile);
        var done = new BatchState();
        done.batchNo = "REPAIR-DONE"; done.tranCode = "40502"; done.status = "SUCC";
        store.saveBatch(done);

        Map<String, Object> result = store.repairProcessingBatches();

        assertThat(result).containsEntry("processingCount", 3).containsEntry("fixedCount", 2)
                .containsEntry("skippedCount", 1);
        assertThat(store.findBatch("REPAIR-SUCC").status).isEqualTo("SUCC");
        assertThat(store.findBatch("REPAIR-FAIL").status).isEqualTo("FAIL");
        assertThat(store.findBatch("REPAIR-FAIL").errorCode).isEqualTo("01");
        assertThat(store.findBatch("REPAIR-FAIL").errorMsg).isEqualTo("余额不足");
        // 缺少结果文件不猜测、不修改；已是终态的批次不参与修复。
        assertThat(store.findBatch("REPAIR-NOFILE").status).isEqualTo("PROC");
        assertThat(store.findBatch("REPAIR-DONE").status).isEqualTo("SUCC");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(skipped.get(0)).containsEntry("batchNo", "REPAIR-NOFILE");
        assertThat(String.valueOf(skipped.get(0).get("reason"))).contains("缺少批次结果文件");
        // 幂等：再次执行时已无处理中批次。
        assertThat(store.repairProcessingBatches()).containsEntry("processingCount", 1)
                .containsEntry("fixedCount", 0).containsEntry("skippedCount", 1);
    }

    @Test
    void batchDetailsRejectUnknownBatchAndBrokenFile() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        assertThatThrownBy(() -> store.batchDetails("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("批次不存在");
        var batch = new BatchState();
        batch.batchNo = "BATCH-BAD"; batch.tranCode = "40502"; batch.status = "SUCC";
        batch.fileData = "这不是Base64!!!";
        store.saveBatch(batch);
        assertThatThrownBy(() -> store.batchDetails("BATCH-BAD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }
}
