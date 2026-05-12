-- =====================================================================
-- 枣庄项目 - 凭证新增中心账号与业务分类及汇总口径调整 V1.1
-- 详细设计：doc/设计文档/20260509-枣庄项目-凭证新增中心账号与业务分类及汇总口径调整-详细设计-V1.1.md
-- 评审通过：doc/操作记录/20260509-枣庄项目-凭证汇总改造设计最终评审报告.md  (98/100)
-- 数据库  ：达梦 / MySQL 兼容
-- 适用范围：枣庄(zaozhuang)
-- =====================================================================

-- 1. 凭证主表 cwhs_pz 新增字段
ALTER TABLE cwhs_pz ADD yhzhhm VARCHAR(64);
ALTER TABLE cwhs_pz ADD ywfl   VARCHAR(32);
COMMENT ON COLUMN cwhs_pz.yhzhhm IS '中心账号';
COMMENT ON COLUMN cwhs_pz.ywfl   IS '业务分类(由ywlx映射codeZl1)';

-- 2. 汇总凭证主表 cwhs_hzpz 新增字段
ALTER TABLE cwhs_hzpz ADD yhzhhm VARCHAR(64);
ALTER TABLE cwhs_hzpz ADD ywfl   VARCHAR(32);
COMMENT ON COLUMN cwhs_hzpz.yhzhhm IS '中心账号';
COMMENT ON COLUMN cwhs_hzpz.ywfl   IS '业务分类(由ywlx映射codeZl1)';

-- 3. 复合索引（按设计 §5.2，覆盖按日期+维度的汇总查询）
CREATE INDEX idx_cwhs_pz_jzrq_yhzhhm_ywfl   ON cwhs_pz   (jzrq, yhzhhm, ywfl);
CREATE INDEX idx_cwhs_hzpz_jzrq_yhzhhm_ywfl ON cwhs_hzpz (jzrq, yhzhhm, ywfl);
