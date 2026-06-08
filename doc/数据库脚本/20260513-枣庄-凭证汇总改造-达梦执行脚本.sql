-- =====================================================================
-- 枣庄项目 - 凭证汇总改造 达梦执行脚本
-- 适用模块：capinfo-gjj-busi-cwhs-jzgl
-- 适用数据库：达梦 DM
-- 用途说明：
--   1. 为凭证主表 cwhs_pz 增加中心账号 yhzhhm、业务分类 ywfl
--   2. 为汇总凭证主表 cwhs_hzpz 增加中心账号 yhzhhm、业务分类 ywfl
--   3. 为按 jzrq + yhzhhm + ywfl 的汇总查询补充复合索引
-- 执行前提：
--   1. 先在测试环境验证通过后再执行生产
--   2. 执行前确认当前库中不存在同名字段/索引
--   3. 配套代码与配置已同步发布
-- =====================================================================

-- 1. 凭证主表 cwhs_pz 增加字段
ALTER TABLE cwhs_pz ADD YHZHHM VARCHAR(64);
ALTER TABLE cwhs_pz ADD YWFL VARCHAR(32);

COMMENT ON COLUMN cwhs_pz.YHZHHM IS '中心账号';
COMMENT ON COLUMN cwhs_pz.YWFL IS '业务分类(由ywlx映射codeZl1)';

-- 2. 汇总凭证主表 cwhs_hzpz 增加字段
ALTER TABLE cwhs_hzpz ADD YHZHHM VARCHAR(64);
ALTER TABLE cwhs_hzpz ADD YWFL VARCHAR(32);

COMMENT ON COLUMN cwhs_hzpz.YHZHHM IS '中心账号';
COMMENT ON COLUMN cwhs_hzpz.YWFL IS '业务分类(由ywlx映射codeZl1)';

-- 3. 新增复合索引
CREATE INDEX idx_cwhs_pz_jzrq_yhzhhm_ywfl ON cwhs_pz (jzrq, yhzhhm, ywfl);
CREATE INDEX idx_cwhs_hzpz_jzrq_yhzhhm_ywfl ON cwhs_hzpz (jzrq, yhzhhm, ywfl);

-- =====================================================================
-- 回滚脚本（按需单独执行；执行前务必确认新字段无生产数据依赖）
-- =====================================================================
-- DROP INDEX idx_cwhs_pz_jzrq_yhzhhm_ywfl;
-- DROP INDEX idx_cwhs_hzpz_jzrq_yhzhhm_ywfl;
-- ALTER TABLE cwhs_pz DROP COLUMN yhzhhm;
-- ALTER TABLE cwhs_pz DROP COLUMN ywfl;
-- ALTER TABLE cwhs_hzpz DROP COLUMN yhzhhm;
-- ALTER TABLE cwhs_hzpz DROP COLUMN ywfl;
