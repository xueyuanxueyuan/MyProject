-- =====================================================================
-- 一户通批量任务表增加业务结果分发状态字段 — 达梦 DM 执行脚本
-- 适用 Schema：cap_gjj_zjjs_ywgl（连接时指定，或执行 SET SCHEMA）
-- 适用表：zjjs_zj_yht_batch_task
-- 目的：为 105/106 主动提回 与 107 异步回执共用的业务回推链路提供一户通域专属幂等闸门
-- 状态约定：N-未分发，P-分发中，Y-已分发
-- 配对脚本：20260529-zjjs_zj_yht_batch_task增加result_dispatch_status-MySQL执行脚本.sql
-- 说明：若列已存在，请勿重复执行
-- =====================================================================

-- 执行前可先核对（达梦会将未加引号的标识符转为大写存储）：
-- SELECT column_name FROM user_tab_columns
--  WHERE table_name = 'ZJJS_ZJ_YHT_BATCH_TASK'
--    AND column_name = 'RESULT_DISPATCH_STATUS';

-- 如需显式切换 Schema：
-- SET SCHEMA cap_gjj_zjjs_ywgl;

ALTER TABLE zjjs_zj_yht_batch_task ADD result_dispatch_status VARCHAR(2) DEFAULT 'N';

COMMENT ON COLUMN zjjs_zj_yht_batch_task.result_dispatch_status IS '业务结果分发状态：N-未分发/P-分发中/Y-已分发';

-- 建议历史数据初始化：
-- 1. 已终态且已完成业务回推的批次，可按实际情况更新为 Y
-- 2. 存量未处理数据默认保留 N
-- UPDATE zjjs_zj_yht_batch_task SET result_dispatch_status = 'Y'
--   WHERE batch_status IN ('SUCCESS','PARTIAL_SUCCESS','FAIL') AND result_dispatch_status = 'N';
