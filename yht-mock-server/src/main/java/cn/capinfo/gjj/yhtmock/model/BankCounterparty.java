package cn.capinfo.gjj.yhtmock.model;

public class BankCounterparty {
    public String bankId;
    public String bankName;
    public String accountNo;
    public String accountName;
    public String accountBankId;
    public boolean enabled = true;
    public long updatedAt;

    public void validate() {
        bankId = MovementSettings.required(bankId, 40, "中心银行标识");
        if (!bankId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("中心银行标识仅允许字母、数字、下划线和短横线");
        }
        bankName = MovementSettings.required(bankName, 80, "银行名称");
        accountNo = MovementSettings.required(accountNo, 40, "对手账号");
        accountName = MovementSettings.required(accountName, 80, "对手户名");
        accountBankId = MovementSettings.required(accountBankId, 40, "对手行号");
    }
}
