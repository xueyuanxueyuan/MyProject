# 20260707-枣庄 `zhbdtzClJobHandler` 定时任务改造记录

## 1. 背景
- 需求：修改枣庄项目 `zhbdtzClJobHandler` 定时任务。
- 目标：改为查询 `zjjs_zj_zhbdtzcl` 表中插入日期在当前时间 10 分钟之前、审批状态为 `10` 的待处理动账数据。
- 处理要求：每次最多处理 50 笔，处理逻辑与 `SubZjZhbdtzclHandler` 对应链路保持一致。

## 2. 改动内容
- 调整 `ZhbdtzClJobHandler`，不再查询 `ZjZhbdtz` 到账通知表。
- 定时任务改为调用 `ZjZhbdtzclDmService.listPendingBeforeTime("10", 当前时间-10分钟, 50)`。
- 查询结果逐笔调用 `ywglService.jsywDzcl(...)`，与 `SubZjZhbdtzclHandler` 最终落到的处理链路保持一致。
- 补充 `ZjZhbdtzclDmService` 查询接口，并在 `ZjZhbdtzclDmServiceImpl` 中按以下条件分页查询：
  - `spzt = 10`
  - `created_time <= 当前时间 - 10 分钟`
  - 按 `created_time`、`id` 正序
  - 仅取前 50 条

## 3. 涉及文件
- `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/main/java/cn/capinfo/gjj/busi/zjjs/ywgl/busi/service/bean/ZhbdtzClJobHandler.java`
- `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/main/java/cn/capinfo/gjj/busi/zjjs/ywgl/busi/domain/service/ZjZhbdtzclDmService.java`
- `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/main/java/cn/capinfo/gjj/busi/zjjs/ywgl/busi/domain/service/impl/ZjZhbdtzclDmServiceImpl.java`

## 4. 验证情况
- 在仓库 `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs` 下执行：

```bash
mvn -pl capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi -am -DskipTests compile -s settings-tsp.xml
```

- 结果：`BUILD SUCCESS`
- 说明：编译输出中存在仓库内原有的 Lombok `equals/hashCode` 告警，本次改动未引入新增编译错误。
