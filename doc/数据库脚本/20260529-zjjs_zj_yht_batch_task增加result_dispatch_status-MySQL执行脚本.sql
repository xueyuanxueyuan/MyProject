-- =====================================================================
-- 一户通批量任务表增加业务结果分发状态字段 — MySQL 执行脚本
-- 适用库：cap_gjj_zjjs_ywgl
-- 适用表：zjjs_zj_yht_batch_task
-- 目的：为 105/106 主动提回 与 107 异步回执共用的业务回推链路提供一户通域专属幂等闸门
-- 状态约定：N-未分发，P-分发中，Y-已分发
-- 配对脚本：20260529-zjjs_zj_yht_batch_task增加result_dispatch_status-达梦执行脚本.sql
-- 说明：若列已存在，请勿重复执行
-- =====================================================================

USE cap_gjj_zjjs_ywgl;

-- 执行前可先核对：
-- SHOW COLUMNS FROM zjjs_zj_yht_batch_task LIKE 'result_dispatch_status';

ALTER TABLE `zjjs_zj_yht_batch_task`
  ADD COLUMN `result_dispatch_status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N'
  COMMENT '业务结果分发状态：N-未分发/P-分发中/Y-已分发' AFTER `check_date`;

-- 建议历史数据初始化：
-- 1. 已终态且已完成业务回推的批次，可按实际情况更新为 Y
-- 2. 存量未处理数据默认保留 N
-- UPDATE zjjs_zj_yht_batch_task SET result_dispatch_status = 'Y'
--   WHERE batch_status IN ('SUCCESS','PARTIAL_SUCCESS','FAIL') AND result_dispatch_status = 'N';
