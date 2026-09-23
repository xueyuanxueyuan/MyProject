package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MovementFlowStore {
    private static final String PROCESS_ID = UUID.randomUUID().toString();
    private final JdbcTemplate jdbc;
    private final MockStoreService store;
    private final JdbcModelTable<MovementFlow> flows;
    private final JdbcModelTable<MovementAttempt> attempts;

    MovementFlowStore(JdbcTemplate jdbc, MockStoreService store) {
        this.jdbc = jdbc;
        this.store = store;
        flows = new JdbcModelTable<>(jdbc, "YHT_MOCK_FLOW", MovementFlow.class, "id");
        attempts = new JdbcModelTable<>(jdbc, "YHT_MOCK_FLOW_ATTEMPT", MovementAttempt.class, "id");
    }

    public MovementFlow find(String id) {
        var flow = flows.find(id);
        if (flow == null) throw new IllegalArgumentException("资金流水不存在，请重新查询");
        return flow;
    }

    public MovementFlow register(MovementFlow candidate) {
        return store.write(() -> {
            var current = flows.find(candidate.id);
            if (current != null) {
                if ("MISSING".equals(current.status) && !blank(candidate.centerBankId)
                        && !candidate.centerBankId.equals(current.centerBankId)) {
                    current.centerBankId = candidate.centerBankId;
                    save(current);
                }
                return current;
            }
            var previous = dedupStatus(candidate.movementKey);
            if (previous != null) {
                candidate.status = "SUCC".equals(previous) ? "SUCC" : "LEGACY";
                candidate.lastError = "已有历史发送占位，未保存通知快照，禁止重推；请核对原接口记录";
            }
            candidate.createdAt = System.currentTimeMillis();
            save(candidate);
            return candidate;
        });
    }

    public MovementFlow generated(String id, long version, String payload, String reason) {
        return store.write(() -> {
            var flow = checked(id, version);
            if (!"MISSING".equals(flow.status)) throw new IllegalStateException("仅缺失通知可以补生成");
            if (dedupStatus(flow.movementKey) != null) throw new IllegalStateException("已有发送占位，禁止覆盖历史通知");
            flow.payload = payload;
            flow.status = "PENDING";
            flow.lastError = "";
            save(flow);
            var audit = attempt(flow, "GENERATE", reason);
            audit.status = "SUCC";
            attempts.insert(audit, audit.id);
            return flow;
        });
    }

    public void generationFailed(String id, String reason) {
        store.write(() -> {
            var flow = find(id);
            if ("MISSING".equals(flow.status)) {
                flow.lastError = reason;
                save(flow);
            }
            return null;
        });
    }

    public MovementAttempt claim(String id, long version, boolean confirmed, String reason, String target) {
        return store.write(() -> {
            var flow = checked(id, version);
            if (!Set.of("PENDING", "FAIL", "UNKNOWN").contains(flow.status) || blank(flow.payload)) {
                throw new IllegalStateException("当前通知状态不允许推送：" + flow.status);
            }
            if (!"PENDING".equals(flow.status) && !confirmed) {
                throw new IllegalArgumentException("补推前必须确认结算未收到该通知，避免重复动账");
            }
            String previous = dedupStatus(flow.movementKey);
            if (flow.attemptCount == 0 && previous != null) {
                throw new IllegalStateException("已有历史发送记录，禁止重复推送");
            }
            if (flow.attemptCount > 0 && (previous == null || !flow.status.equals(previous))) {
                throw new IllegalStateException("去重状态不一致，停止补推并核查");
            }
            if (previous == null) {
                jdbc.update("INSERT INTO YHT_MOCK_MOVEMENT (MOVEMENT_KEY, STATUS) VALUES (?, ?)", flow.movementKey, "SENDING");
            } else {
                jdbc.update("UPDATE YHT_MOCK_MOVEMENT SET STATUS = ? WHERE MOVEMENT_KEY = ?", "SENDING", flow.movementKey);
            }
            flow.status = "SENDING";
            flow.attemptCount++;
            save(flow);
            var audit = attempt(flow, "PUSH", reason);
            audit.status = "SENDING";
            audit.target = target;
            attempts.insert(audit, audit.id);
            return audit;
        });
    }

    public MovementFlow finished(MovementAttempt attempt, MockRecord record) {
        return store.write(() -> {
            var flow = find(attempt.flowId);
            var savedAttempt = attempts.find(attempt.id);
            if (!"SENDING".equals(flow.status) || flow.version != attempt.flowVersion
                    || savedAttempt == null || !"SENDING".equals(savedAttempt.status)
                    || !PROCESS_ID.equals(savedAttempt.ownerId)) {
                throw new IllegalStateException("发送尝试已失效，禁止迟到结果覆盖当前状态");
            }
            flow.status = record.status;
            flow.lastError = "SUCC".equals(record.status) ? "" : record.remark;
            save(flow);
            attempt.status = record.status;
            attempt.responseBody = record.responseBody;
            attempt.remark = record.remark;
            attempt.updatedAt = System.currentTimeMillis();
            attempts.save(attempt, attempt.id);
            store.finishMovement(flow.movementKey, record.status);
            store.addRecord(record);
            return flow;
        });
    }

    public MovementFlow recover(String id, long version, boolean confirmedSenderStopped, String reason) {
        String checkedReason = MovementSettings.required(reason, 200, "恢复原因");
        if (!confirmedSenderStopped) throw new IllegalArgumentException("必须确认原发送节点已停止，并核对结算接收结果");
        return store.write(() -> {
            var flow = checked(id, version);
            if (!"SENDING".equals(flow.status)) throw new IllegalStateException("仅中断的发送中记录可恢复");
            var running = attempts.select("C_FLOW_ID = ? AND C_STATUS = ?", "", 0, id, "SENDING");
            if (running.size() != 1 || running.get(0).flowVersion != flow.version) {
                throw new IllegalStateException("发送尝试不一致，请人工核查");
            }
            var old = running.get(0);
            if (blank(old.ownerId) || PROCESS_ID.equals(old.ownerId)) {
                throw new IllegalStateException("当前发送进程尚未退出，禁止释放；须先停止原节点并重启后核对恢复");
            }
            if (!"SENDING".equals(dedupStatus(flow.movementKey))) throw new IllegalStateException("去重状态不一致");
            old.status = "UNKNOWN";
            old.remark = "人工确认原发送节点已停止，接收结果待核对：" + checkedReason;
            old.updatedAt = System.currentTimeMillis();
            attempts.save(old, old.id);
            flow.status = "UNKNOWN";
            flow.lastError = old.remark;
            save(flow);
            store.finishMovement(flow.movementKey, "UNKNOWN");
            var audit = attempt(flow, "RECOVER", checkedReason);
            audit.status = "SUCC";
            audit.remark = "仅恢复为结果不明，没有发送请求；后续补推需另行确认结算未收到";
            attempts.insert(audit, audit.id);
            return flow;
        });
    }

    public List<MovementAttempt> attempts(String id) {
        find(id);
        return attempts.select("C_FLOW_ID = ?", " ORDER BY C_CREATED_AT DESC, C_ID DESC", 0, id);
    }

    public Page query(String from, String to, String bank, String account, String keyword,
                      String direction, String status, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数超出范围");
        var clauses = new ArrayList<String>();
        var args = new ArrayList<Object>();
        clauses.add("1 = 1");
        String start = date(from);
        String end = date(to);
        if (!start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        add(clauses, args, "C_BUSINESS_DATE >= ?", start);
        add(clauses, args, "C_BUSINESS_DATE <= ?", end);
        add(clauses, args, "C_CENTER_BANK_ID = ?", bank);
        add(clauses, args, "C_ACCT_NO = ?", account);
        if (!blank(direction) && !Set.of("IN", "OUT").contains(direction)) throw new IllegalArgumentException("收付方向无效");
        // 通知状态支持多选（逗号分隔），便于一次筛出「可推送（未推送 / 失败）」的流水后批量补推。
        List<String> statuses = new ArrayList<>();
        if (!blank(status)) {
            for (String item : status.split(",")) {
                String value = item.trim();
                if (value.isEmpty() || statuses.contains(value)) continue;
                if (!Set.of("MISSING", "PENDING", "SENDING", "SUCC", "FAIL", "UNKNOWN", "LEGACY").contains(value)) {
                    throw new IllegalArgumentException("通知状态无效");
                }
                statuses.add(value);
            }
        }
        add(clauses, args, "C_DIRECTION = ?", direction);
        if (!statuses.isEmpty()) {
            clauses.add("C_STATUS IN (" + String.join(",", statuses.stream().map(item -> "?").toList()) + ")");
            args.addAll(statuses);
        }
        if (!blank(keyword)) {
            String pattern = "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            clauses.add("(C_SYS_SEQ_NO LIKE ? ESCAPE '!' OR C_BATCH_NO LIKE ? ESCAPE '!' OR C_REQ_ID LIKE ? ESCAPE '!')");
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String where = String.join(" AND ", clauses);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_FLOW WHERE " + where, Long.class, args.toArray());
        var items = flows.page(where, " ORDER BY C_BUSINESS_DATE DESC, C_CREATED_AT DESC, C_ID DESC",
                (page - 1) * size, size, args.toArray());
        return new Page(items, count, page, size);
    }

    private MovementFlow checked(String id, long version) {
        var flow = find(id);
        if (flow.version != version) throw new IllegalStateException("流水状态已更新，请重新查询后操作");
        return flow;
    }

    private void save(MovementFlow flow) {
        flow.version++;
        flow.updatedAt = System.currentTimeMillis();
        flows.save(flow, flow.id);
    }

    private MovementAttempt attempt(MovementFlow flow, String action, String reason) {
        var attempt = new MovementAttempt();
        attempt.id = UUID.randomUUID().toString();
        attempt.flowId = flow.id;
        attempt.ownerId = PROCESS_ID;
        attempt.flowVersion = flow.version;
        attempt.action = action;
        attempt.reason = reason;
        attempt.requestBody = flow.payload;
        attempt.createdAt = System.currentTimeMillis();
        attempt.updatedAt = attempt.createdAt;
        return attempt;
    }

    private String dedupStatus(String key) {
        var rows = jdbc.queryForList("SELECT STATUS FROM YHT_MOCK_MOVEMENT WHERE MOVEMENT_KEY = ?", String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void add(List<String> clauses, List<Object> args, String clause, String value) {
        if (!blank(value)) { clauses.add(clause); args.add(value.trim()); }
    }

    private String date(String value) {
        if (blank(value)) return "";
        try {
            return LocalDate.parse(value.replace("-", ""), DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.BASIC_ISO_DATE);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("业务日期格式错误");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    public record Page(List<MovementFlow> items, long total, int page, int size) { }

    /**
     * 取某个批次的全部资金流水，用于批量明细列表中回显每笔明细的动账状态与失败原因。
     */
    public List<MovementFlow> byBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return List.of();
        }
        return flows.select("C_BATCH_NO = ?", " ORDER BY C_CREATED_AT, C_ID", 0, batchNo);
    }

    /** 银行流水号最大长度：动账通知 yhlsh 及结算端按 32 位约束，生成与历史修复共用该上限。 */
    public static final int SERIAL_MAX = 32;

    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

    /**
     * 银行流水号归一化：不超过 32 位时原样返回；超长时保留前缀并追加由原值确定性派生的短哈希，
     * 同一原值多次归一化结果一致（幂等），生成端与历史修复端共用该规则。
     */
    public static String normalizeSerial(String serial) {
        String value = serial == null ? "" : serial.trim();
        if (value.length() <= SERIAL_MAX) {
            return value;
        }
        String hash = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return value.substring(0, SERIAL_MAX - 13) + hash.substring(0, 13);
    }

    /**
     * 历史数据统一更新：把所有银行流水号超过 32 位的资金流水收敛到 32 位以内。
     * 已生成通知报文（payload）中的 yhlsh 同步改写，避免补推时仍携带超长旧流水号；
     * 原值在返回结果与日志中保留，可追溯。归一化幂等，可重复执行。
     */
    public Map<String, Object> normalizeHistoricalSerials() {
        return store.write(() -> {
            var oversized = flows.select("LENGTH(C_SYS_SEQ_NO) > ?", " ORDER BY C_CREATED_AT, C_ID", 0, SERIAL_MAX);
            var fixed = new ArrayList<Map<String, Object>>();
            for (var flow : oversized) {
                String before = flow.sysSeqNo == null ? "" : flow.sysSeqNo;
                String after = normalizeSerial(before);
                if (after.equals(before)) {
                    continue;
                }
                var row = new LinkedHashMap<String, Object>();
                row.put("id", flow.id);
                row.put("sourceType", flow.sourceType);
                row.put("status", flow.status);
                row.put("before", before);
                row.put("after", after);
                flow.sysSeqNo = after;
                if (!blank(flow.payload)) {
                    try {
                        var node = PAYLOAD_MAPPER.readTree(flow.payload);
                        if (node instanceof ObjectNode object && object.hasNonNull("yhlsh")) {
                            object.put("yhlsh", after);
                            flow.payload = PAYLOAD_MAPPER.writeValueAsString(object);
                        }
                    } catch (Exception ignored) {
                        // 通知报文不可解析时保留原报文，仅更新流水号字段
                    }
                }
                save(flow);
                fixed.add(row);
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("inspected", oversized.size());
            result.put("normalized", fixed.size());
            result.put("fixed", fixed);
            result.put("message", "检查 " + oversized.size() + " 条资金流水，修复超长银行流水号 " + fixed.size()
                    + " 条（上限 " + SERIAL_MAX + " 位，超长按「前缀+确定性哈希」收敛，已生成报文同步改写）");
            return result;
        });
    }
}
