# `zjjs_zj_yht_batch_task` 现网 DDL 对比与补库建议

## 1. 对比结论

根据用户提供的现网建表语句，对照当前代码实体 `YhtBatchTaskDO`，可以确认：

当前现网表 **不是只缺 `platform_batch_no` 一列**，而是至少缺以下 7 列：

1. `platform_batch_no`
2. `fee_no`
3. `file_name`
4. `corp_acct_no`
5. `tran_code`
6. `success_amt`
7. `check_date`

## 2. 为什么会直接报错

`YhtBatchTaskDao` 是 `BaseMapper<YhtBatchTaskDO>`，没有手写 SQL。

因此像下面这种查询：

1. `selectList(qw)`
2. `selectPage(page, qw)`
3. `selectOne(wrapper)`

都会由 MyBatis-Plus 根据 `YhtBatchTaskDO` 自动展开成全字段查询。

而 `YhtBatchTaskDO` 已声明：

1. `platformBatchNo -> platform_batch_no`
2. `feeNo -> fee_no`
3. `fileName -> file_name`
4. `corpAcctNo -> corp_acct_no`
5. `tranCode -> tran_code`
6. `successAmt -> success_amt`
7. `checkDate -> check_date`

所以在现网表缺列的情况下，只要一查 `zjjs_zj_yht_batch_task`，就可能直接报：

```sql
Unknown column 'platform_batch_no' in 'field list'
```

报错信息只会先暴露遇到的第一列，不代表只缺这一列。

## 3. 现网已有字段

根据用户提供 DDL，下面这些字段现网已经存在：

1. `id`
2. `zxbh`
3. `revision`
4. `creator`
5. `created_time`
6. `updator`
7. `updated_time`
8. `jbjgbh`
9. `jbjgmc`
10. `qdbm`
11. `del_flag`
12. `req_id`
13. `batch_no`
14. `corp_no`
15. `biz_type`
16. `total_count`
17. `total_amt`
18. `success_count`
19. `fail_count`
20. `processing_count`
21. `batch_status`
22. `error_code`
23. `error_msg`

这些字段与当前实体的公共部分是对齐的。

## 4. 最小补库 SQL

建议优先补齐缺失列，而不是改代码绕过。

可执行的最小补库 SQL 如下：

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

## 5. 执行建议

执行前建议先确认一次现网列状态：

```sql
SHOW CREATE TABLE `zjjs_zj_yht_batch_task`;
```

如果现网不是“全部缺失”，而是只缺其中部分列，则把上面的 SQL 拆成单列执行，避免重复加列报错。

## 6. 为什么不建议先改代码

不建议把这些实体字段直接删掉或标记 `exist = false`，原因是：

1. 这些字段不是无用字段，后续批量回执、平台批次号匹配、结果提回都会用到；
2. 临时绕过只会把问题从“发送前查询报错”推迟到“回执或查询时再报错”；
3. 真正的问题是库表版本没有跟上当前代码版本。

## 7. 一句话结论

以这份现网 DDL 为准，当前 `zjjs_zj_yht_batch_task` 至少缺 7 列；  
本次批量发送报错的根因是 **库表结构落后于代码实体**，应优先补库。  
