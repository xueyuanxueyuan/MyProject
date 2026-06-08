-- 一户通批量明细表补库（MySQL）
-- 库：cap_gjj_zjjs_ywgl
-- 现象：YhtBatchDetailDao.insert 报 Unknown column 'detail_seq' in 'field list'
-- 根因：zjjs_zj_yht_batch_detail 结构落后于 YhtBatchDetailDO 实体
-- 说明：若部分列已存在，请拆成单列执行，避免 Duplicate column 报错

USE cap_gjj_zjjs_ywgl;

-- 执行前可先核对：
-- SHOW CREATE TABLE zjjs_zj_yht_batch_detail;

ALTER TABLE `zjjs_zj_yht_batch_detail`
  ADD COLUMN `detail_seq` INT NULL COMMENT '明细序号，批次内顺序号' AFTER `error_msg`,
  ADD COLUMN `bank_id` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '开户行号（收款方）' AFTER `detail_seq`,
  ADD COLUMN `bank_type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '银行类别' AFTER `bank_id`,
  ADD COLUMN `protocol_no` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '协议号（代收业务必填）' AFTER `bank_type`,
  ADD COLUMN `ret_code` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '回执状态码' AFTER `protocol_no`,
  ADD COLUMN `ret_msg` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '回执状态描述' AFTER `ret_code`,
  ADD COLUMN `phone` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '收款人手机号' AFTER `ret_msg`,
  ADD COLUMN `remark` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '备注' AFTER `phone`,
  ADD COLUMN `dbtr_act_name` VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '付款人名称' AFTER `dbtr_act_id`,
  ADD COLUMN `use` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '用途/摘要' AFTER `remark`;
