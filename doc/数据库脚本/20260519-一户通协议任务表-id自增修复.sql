-- 一户通协议任务表主键自增修复（MySQL）
-- 库：cap_gjj_zjjs_ywgl（与 application-dev-mysql.yml 一致）
-- 现象：签约落库 Field 'id' doesn't have a default value

USE cap_gjj_zjjs_ywgl;

ALTER TABLE zjjs_zj_yht_protocol_task
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键';

-- 若其它一户通任务表也有同类问题，可按需执行：
-- ALTER TABLE zjjs_zj_yht_batch_task MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
-- ALTER TABLE zjjs_zj_yht_batch_detail MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
