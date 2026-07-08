# 20260705-枣庄测试库-CWHS_YHCKRJZ批量置空wjlj记录

## 1. 背景

用户要求在枣庄测试库中查询并处理 `CAP_GJJ_JSHS.CWHS_YHCKRJZ` 表，将满足以下条件的数据的 `wjlj` 字段置空：

- `rzrq < date '2026-07-01'`
- `wjlj is not null`
- `trim(wjlj) <> ''`

本次操作通过本机 `jookdb` 已保存的枣庄测试库连接参数确认目标库，并使用本机达梦 JDBC 驱动执行。

## 2. 风险确认

本次为数据库批量写操作，更新前已向用户确认：

- 执行方式：直接更新
- 置空方式：更新为 `NULL`
- 备份方式：用户选择不先创建备份表

## 3. 执行 SQL

更新前计数 SQL：

```sql
select count(*)
from CAP_GJJ_JSHS.CWHS_YHCKRJZ
where rzrq < date '2026-07-01'
  and wjlj is not null
  and trim(wjlj) <> '';
```

更新 SQL：

```sql
update CAP_GJJ_JSHS.CWHS_YHCKRJZ
set wjlj = null
where rzrq < date '2026-07-01'
  and wjlj is not null
  and trim(wjlj) <> '';
```

更新后校验 SQL：

```sql
select count(*)
from CAP_GJJ_JSHS.CWHS_YHCKRJZ
where rzrq < date '2026-07-01'
  and wjlj is not null
  and trim(wjlj) <> '';
```

## 4. 执行结果

- 更新前命中数量：`1,350,840`
- 实际更新数量：`1,350,840`
- 更新后剩余数量：`0`

## 5. 事务补充处理

用户反馈执行更新后疑似存在未提交事务导致锁表风险，随后补充执行显式 `commit` 并复核：

- 显式提交结果：`COMMIT_SENT=true`
- 提交后同条件剩余数量：`0`

补充提交校验 SQL：

```sql
commit;

select count(*)
from CAP_GJJ_JSHS.CWHS_YHCKRJZ
where rzrq < date '2026-07-01'
  and wjlj is not null
  and trim(wjlj) <> '';
```

## 6. 清理

本次执行使用的临时 Java 程序、提交检查程序和 class 文件已删除。

## 7. 结论

`CAP_GJJ_JSHS.CWHS_YHCKRJZ` 表中满足 `rzrq < date '2026-07-01'` 且 `wjlj` 非空的数据，已按用户确认全部将 `wjlj` 更新为 `NULL`，并已补充显式提交。
