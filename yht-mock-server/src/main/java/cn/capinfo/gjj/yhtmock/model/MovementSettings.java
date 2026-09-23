package cn.capinfo.gjj.yhtmock.model;

import java.math.BigDecimal;
import java.net.URI;

public class MovementSettings {
    public boolean enabled;
    /**
     * 对手账户兜底开关：开启后所有动账通知统一使用下方默认兜底账户，
     * 不再按中心账户所属银行标识查询 YHT_MOCK_BANK_ACCOUNT。
     */
    public boolean fallbackEnabled;
    public String targetUrl = "";
    public String receiveCode = "SBDC100";
    public String payCode = "SBDC100";
    public String counterpartyAccount = "99993304000000000001";
    public String counterpartyName = "一户通模拟对账专户";
    public String counterpartyBank = "999999999999";
    public BigDecimal balance = new BigDecimal("0.00");
    public String authorizationEnv = "";

    public void validate() {
        applyDefaults();
        if (!enabled) {
            return;
        }
        URI target = URI.create(required(targetUrl, 2048, "通知地址"));
        if (!("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))
                || target.getHost() == null || target.getUserInfo() != null
                || target.getRawQuery() != null || target.getRawFragment() != null) {
            throw new IllegalArgumentException("通知地址必须为不含凭证、查询串及片段的 HTTP/HTTPS 地址");
        }
        validateNotificationFields();
    }

    public void validateNotificationFields() {
        required(receiveCode, 10, "收款动账代码");
        required(payCode, 10, "付款动账代码");
        if (balance == null || balance.scale() > 2) {
            throw new IllegalArgumentException("模拟余额不能为空且最多两位小数");
        }
        if (fallbackEnabled) {
            // 兜底账户直接作为通知对手方，保存阶段就校验，避免生成通知时才失败。
            required(counterpartyAccount, 40, "兜底对手账号");
            required(counterpartyName, 80, "兜底对手户名");
            required(counterpartyBank, 40, "兜底对手行号");
        }
        if (authorizationEnv != null && !authorizationEnv.isBlank()
                && !authorizationEnv.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("鉴权环境变量名称格式错误");
        }
    }

    public void applyDefaults() {
        receiveCode = defaultIfBlank(receiveCode, "SBDC100");
        payCode = defaultIfBlank(payCode, "SBDC100");
        counterpartyAccount = defaultIfBlank(counterpartyAccount, "99993304000000000001");
        counterpartyName = defaultIfBlank(counterpartyName, "一户通模拟对账专户");
        counterpartyBank = defaultIfBlank(counterpartyBank, "999999999999");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(label + "不能为空且长度不能超过" + max);
        }
        return value.trim();
    }
}
