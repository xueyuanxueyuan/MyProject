-- ==============================================================================
-- 脚本说明：自动发交易任务双维调度重构 - 交易初始化表（zjjs_zj_jycsh）字段扩展
-- 数据库类型：达梦数据库 (DM)
-- 适用模块：capinfo-gjj-busi-zjjs-ywgl
-- 执行日期：2026-05-09
-- ==============================================================================

-- 1. 新增字段：ywlx（业务类型）和 xtlx（系统类型）
-- 注：yhdm（银行代码）字段在原表中已存在，故无需新增。
ALTER TABLE "ZJJS_ZJ_JYCSH" ADD ("YWLX" VARCHAR(32) NULL);
ALTER TABLE "ZJJS_ZJ_JYCSH" ADD ("XTLX" VARCHAR(32) NULL);

-- 2. 添加字段注释
COMMENT ON COLUMN "ZJJS_ZJ_JYCSH"."YWLX" IS '业务类型';
COMMENT ON COLUMN "ZJJS_ZJ_JYCSH"."XTLX" IS '系统类型';

-- 3. 创建复合查询索引
-- 由于新调度器在预检分组时，高频使用 GROUP BY YWLX, XTLX, YHDM
-- 且常伴随 FSZT（发送状态）和 CRSJ（插入时间）的过滤，建议建立如下联合索引以提升查询性能：
CREATE INDEX "IDX_JYCSH_GROUP_PRECHECK" ON "ZJJS_ZJ_JYCSH" ("FSZT", "YWLX", "XTLX", "YHDM", "CRSJ");
