# `zjjs_zj_yht_batch_task` 补库执行记录

## 1. 背景

根据现网 DDL 与当前代码实体 `YhtBatchTaskDO` 对比，确认表 `zjjs_zj_yht_batch_task` 缺少以下 7 列：

1. `platform_batch_no`
2. `fee_no`
3. `file_name`
4. `corp_acct_no`
5. `tran_code`
6. `success_amt`
7. `check_date`

该缺列问题会导致 MyBatis-Plus 在查询 `YhtBatchTaskDO` 时自动展开全字段 `SELECT` 失败，进而触发：

1. `Unknown column 'platform_batch_no' in 'field list'`
2. 批量发送前防重发查询异常
3. 外层事务回滚

## 2. 实际执行

本次已通过应用开发环境配置提供的数据源，直连目标 MySQL 库，并执行最小补库 SQL。

执行内容为：

```sql
ALTER TABLE `zjjs_zj_yht_batch_task`
  ADD COLUMN `platform_batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '平台批次号，caps.102应答返回的BatchNo',
  ADD COLUMN `fee_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '费项代码',
  ADD COLUMN `file_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '批量报文文件名',
  ADD COLUMN `corp_acct_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '企业账号',
  ADD COLUMN `tran_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '交易代码：40501=代付，40502=代收',
  ADD COLUMN `success_amt` decimal(18,2) DEFAULT NULL COMMENT '成功金额',
  ADD COLUMN `check_date` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对账日期';
```

执行结果：

1. `ALTER TABLE` 成功执行
2. 未修改已有列
3. 未改动索引
4. 未改动已有数据

## 3. 执行后验证

### 3.1 目标列验证

执行后再次核对列存在性，结果如下：

1. `platform_batch_no = yes`
2. `fee_no = yes`
3. `file_name = yes`
4. `corp_acct_no = yes`
5. `tran_code = yes`
6. `success_amt = yes`
7. `check_date = yes`

### 3.2 同类查询验证

执行了与报错同类的字段查询：

```sql
SELECT
  id, req_id, batch_no, platform_batch_no, fee_no, file_name,
  corp_acct_no, tran_code, success_amt, check_date
FROM zjjs_zj_yht_batch_task
WHERE del_flag = '0'
LIMIT 1;
```

结果：

1. 查询成功
2. 不再出现缺列异常
3. 当前表中可能暂无符合条件的数据，这不影响列校验结果

## 4. 当前结论

本次导致批量发送报错的数据库缺列问题已完成补库，且关键查询已验证通过。

后续建议：

1. 重新回放批量收款发送场景
2. 同步验证批量任务分页查询
3. 同步验证批量回执、批量状态查询、批量结果提回链路  
