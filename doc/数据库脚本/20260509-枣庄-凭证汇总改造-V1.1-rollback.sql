-- =====================================================================
-- 枣庄项目 - 凭证汇总改造 V1.1 回滚脚本
-- 与 20260509-枣庄-凭证汇总改造-V1.1.sql 配套
-- 注意：回滚前请确认无生产数据依赖新字段
-- =====================================================================

DROP INDEX idx_cwhs_pz_jzrq_yhzhhm_ywfl;
DROP INDEX idx_cwhs_hzpz_jzrq_yhzhhm_ywfl;

ALTER TABLE cwhs_pz   DROP COLUMN yhzhhm;
ALTER TABLE cwhs_pz   DROP COLUMN ywfl;
ALTER TABLE cwhs_hzpz DROP COLUMN yhzhhm;
ALTER TABLE cwhs_hzpz DROP COLUMN ywfl;
