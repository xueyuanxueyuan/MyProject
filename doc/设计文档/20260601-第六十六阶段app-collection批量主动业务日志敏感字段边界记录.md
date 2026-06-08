# 第六十六阶段：app collection 批量主动业务日志敏感字段边界记录

## 目标

延续第六十五阶段 `YwglCollectionController#dbsk` 主动业务日志治理，本阶段选取收款批量入口 `YwglCollectionController#plsk` 作为小切片，按 TDD 固定日志边界，避免将批量收款业务受理编号 `ywslbh`、批次流水号 `pclsh` 等业务编号写入服务端主动业务日志。

本阶段只治理 Controller 主动业务日志，不修改 REST 入参、返回契约、应用服务编排、领域模型或数据库结构。

## 检查范围

- 生产代码：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/YwglCollectionController.java`
- MockMvc 测试：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/YwglCollectionControllerMockMvcTest.java`
- 组合回归：`YwglCollectionControllerTest`

## 扫描发现

`YwglCollectionController#plsk` 原始主动日志包含批量子项业务编号和批次流水号：

```java
for (SkywModel item : batch.getItems()) {
    log.info("批量收款子项成功: ywslbh={}", item.getYwslbh());
}

log.info("批量收款完成: pclsh={}, 明细数={}, 成功数={}, 失败数={}, 待处理数={}",
        batch.getPclsh(), batch.getMxsl(), batch.getCgs(), batch.getSbs(), batch.getDcls());
```

其中 `ywslbh`、`pclsh` 属于业务编号。为降低日志敏感信息和业务追踪标识暴露面，本阶段将主动业务日志收敛为只记录批量规模与统计摘要。

## RED 证据

新增测试：

```text
YwglCollectionControllerMockMvcTest#plskRequestLogShouldNotContainSensitiveBatchIdentifiers
```

测试构造以下哨兵业务编号：

- `ywslbh=SK-SECRET-661`
- `ywslbh=SK-SECRET-662`
- `pclsh=PCSK-SECRET-661`

RED 命令：

```text
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#plskRequestLogShouldNotContainSensitiveBatchIdentifiers -Dsurefire.failIfNoSpecifiedTests=false test
```

工具链输出：

```text
openjdk version "17.0.2" 2022-01-18
Apache Maven 3.9.14
```

失败输出摘要：

```text
批量收款请求: 笔数=2
批量收款子项成功: ywslbh=SK-SECRET-661
批量收款子项成功: ywslbh=SK-SECRET-662
批量收款完成: pclsh=PCSK-SECRET-661, 明细数=2, 成功数=0, 失败数=0, 待处理数=2
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

失败点说明：测试断言日志不应包含 `SK-SECRET-661` 等哨兵业务编号，但当前主动日志包含这些值。

## 修复内容

将批量收款主动日志收敛为只记录事件与统计摘要：

```java
for (SkywModel item : batch.getItems()) {
    log.info("批量收款子项成功");
}

log.info("批量收款完成: 明细数={}, 成功数={}, 失败数={}, 待处理数={}",
        batch.getMxsl(), batch.getCgs(), batch.getSbs(), batch.getDcls());
```

接口返回仍通过 `YwglCollectionAssembler.toPlskRespDTO(batch)` 保持原有 `pclsh` 与明细响应契约；本阶段只影响日志内容。

## 验证记录

### GREEN 定向验证

命令：

```text
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#plskRequestLogShouldNotContainSensitiveBatchIdentifiers -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
批量收款请求: 笔数=2
批量收款子项成功
批量收款子项成功
批量收款完成: 明细数=2, 成功数=0, 失败数=0, 待处理数=2
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 收款 Controller 组合回归

命令：

```text
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest,YwglCollectionControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
YwglCollectionControllerMockMvcTest: Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
YwglCollectionControllerTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### app reactor 回归

命令：

```text
mvn -pl capinfo-gjj-busi-jshs-app -am test
```

结果摘要：

```text
capinfo-gjj-busi-jshs-common: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-domain: Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-infrastructure: Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-app: Tests run: 100, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 路径与空白检查

命令：

```text
git diff --check
```

结果：退出码 0。

文件检查摘要：

```text
YwglCollectionController.java: exists=True, trailing_ws_lines=[], ends_newline=True, lines=81
YwglCollectionControllerMockMvcTest.java: exists=True, trailing_ws_lines=[], ends_newline=True, lines=291
```

## 文档路径检查

本阶段文档写入仓库根目录 `doc/设计文档/`：

```text
/home/source/Jetbrains/Probject/Gjj/doc/设计文档/20260601-第六十六阶段app-collection批量主动业务日志敏感字段边界记录.md
```

不是 v3 子工程内部嵌套 `doc/`。

## 结论

本阶段确认并修复了 `YwglCollectionController#plsk` 主动日志中的批量业务编号输出风险。修复后：

- 批量请求日志保留 `笔数`；
- 批量子项日志只保留事件名，不输出 `ywslbh`；
- 批量完成日志保留明细数、成功数、失败数、待处理数，不输出 `pclsh`；
- REST 响应契约与应用服务调用链未变。

## 下一阶段建议

第六十七阶段建议继续沿主动业务日志边界治理路线，优先扫描并处理 `YwglPaymentController#plfk`、`YwglPaymentController#result`、`YwglCollectionController#result` 等仍直接输出业务受理编号或请求路径变量的日志切片；每阶段仍按一个入口一个哨兵测试推进。
