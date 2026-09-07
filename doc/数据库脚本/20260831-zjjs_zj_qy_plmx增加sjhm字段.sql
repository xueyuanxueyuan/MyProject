-- 批量签约明细表补库（MySQL / 达梦）
-- 表：zjjs_zj_qy_plmx
-- 现象：plQyJy 批量签约明细链路未保留手机号，结果下载回传时 sjhm 为空
-- 根因：QyPlmx / QyPlmxDO / Mapper / 表结构均缺少 sjhm 字段，申请保存阶段即丢失手机号
-- 说明：若字段已存在，请按单条语句拆分执行，避免重复加列报错

-- ====================
-- MySQL
-- ====================
USE cap_gjj_zjjs_ywgl;

-- 执行前可先核对：
-- SHOW CREATE TABLE zjjs_zj_qy_plmx;

ALTER TABLE `zjjs_zj_qy_plmx`
  ADD COLUMN `sjhm` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '手机号码' AFTER `zjhm`;


-- ====================
-- 达梦
-- ====================
-- 执行前可先核对：
-- DESC zjjs_zj_qy_plmx;

ALTER TABLE zjjs_zj_qy_plmx ADD sjhm VARCHAR(32);

COMMENT ON COLUMN zjjs_zj_qy_plmx.sjhm IS '手机号码';
