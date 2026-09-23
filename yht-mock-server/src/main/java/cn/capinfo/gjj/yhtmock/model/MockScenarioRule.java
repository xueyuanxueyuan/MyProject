package cn.capinfo.gjj.yhtmock.model;

/**
 * 账号校验规则（原「场景规则」简化而来）。
 *
 * 该规则不再按中心账户匹配并强制成功/失败，而是按【对手账号】校验交易：
 * - accountRule：对手账号正则（命中才适用本规则）。
 * - valid=false：该对手账户无效，命中即交易失败。
 * - valid=true 且 class1Card=true：一类卡，全部成功。
 * - valid=true 且 class1Card=false：二类卡，按「日交易限额 1 万元」累计判定。
 * 另由 MockSettings.randomFail 开关对全部交易随机失败（覆盖上述判定）。
 */
public class MockScenarioRule {

    public long id;
    public String name;
    public boolean enabled = true;
    /** 对手账号正则，命中本规则才适用。空正则视为匹配任意对手账号。 */
    public String accountRule;
    /** 该对手账户是否有效：false 时命中即失败。 */
    public boolean valid = true;
    /** 是否一类卡：false = 二类卡（受日交易限额 1 万元约束）。 */
    public boolean class1Card = true;
    public String remark;
    public long updatedAt;
}
