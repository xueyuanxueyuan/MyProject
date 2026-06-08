# 第六十七阶段：app collection 单笔成功日志业务编号边界记录

## 目标

延续第六十六阶段 `YwglCollectionController#plsk` 批量主动业务日志治理，本阶段选取收款单笔入口 `YwglCollectionController#dbsk` 的成功日志作为小切片，按 TDD 固定日志边界，避免将单笔收款业务受理编号 `ywslbh` 写入服务端主动业务日志。

本阶段只治理 Controller 主动业务日志，不修改 REST 入参、返回契约、应用服务编排、领域模型或数据库结构。

## 检查范围

- 生产代码：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/YwglCollectionController.java`
- MockMvc 测试：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/YwglCollectionControllerMockMvcTest.java`
- 组合回归：`YwglCollectionControllerTest`

## 扫描发现

主动日志扫描仍发现单笔收款成功日志输出业务受理编号：

```java
log.info("单笔收款成功: ywslbh={}", result.getYwslbh());
```

该编号可用于业务链路追踪，属于应从主动业务日志中收敛的业务标识。本阶段保持响应 DTO 继续返回业务编号，仅移除日志中的编号值。

## RED 证据

新增测试：

```text
YwglCollectionControllerMockMvcTest#dbskSuccessLogShouldNotContainSensitiveBusinessSerialNo
```

测试构造哨兵业务编号：

- `ywslbh=SK-SECRET-671`

RED 命令：

```text
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#dbskSuccessLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
```

工具链输出：

```text
openjdk version "17.0.2" 2022-01-18
Apache Maven 3.9.14
```

失败输出摘要：

```text
单笔收款请求: skje=100.00
单笔收款成功: ywslbh=SK-SECRET-671
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

失败点说明：测试断言日志不应包含 `SK-SECRET-671`，但当前主动成功日志包含该业务受理编号。

## 修复内容

将单笔收款成功日志收敛为只记录事件名：

```java
log.info("单笔收款成功");
```

接口返回仍通过 `YwglCollectionAssembler.toSkywRespDTO(result)` 保持原有 `jslsh/ywslbh` 响应契约；本阶段只影响日志内容。

## 验证记录

### GREEN 定向验证

命令：

```text
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#dbskSuccessLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
单笔收款请求: skje=100.00
单笔收款成功
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
YwglCollectionControllerMockMvcTest: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
YwglCollectionControllerTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
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
capinfo-gjj-busi-jshs-app: Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
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
YwglCollectionControllerMockMvcTest.java: exists=True, trailing_ws_lines=[], ends_newline=True, lines=326
```

## 文档路径检查

本阶段文档写入仓库根目录 `doc/设计文档/`：

```text
/home/source/Jetbrains/Probject/Gjj/doc/设计文档/20260601-第六十七阶段app-collection单笔成功日志业务编号边界记录.md
```

不是 v3 子工程内部嵌套 `doc/`。

## 结论

本阶段确认并修复了 `YwglCollectionController#dbsk` 成功日志中的业务受理编号输出风险。修复后：

- 单笔收款请求日志仍保留金额摘要 `skje`；
- 单笔收款成功日志只保留事件名，不输出 `ywslbh`；
- REST 响应契约与应用服务调用链未变。

## 下一阶段建议

第六十八阶段建议继续处理 `YwglCollectionController#result` 的路径变量查询日志，或转向 `YwglPaymentController#result` / `TransferController#create` 等仍输出业务受理编号的入口。仍建议保持一个入口一个哨兵测试的小步 TDD 节奏。
