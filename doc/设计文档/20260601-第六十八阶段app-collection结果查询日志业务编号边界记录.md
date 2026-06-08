# 第六十八阶段：app collection 结果查询日志业务编号边界记录

## 目标

延续第六十七阶段 `YwglCollectionController#dbsk` 成功日志治理，本阶段选取收款结果查询入口 `YwglCollectionController#result` 作为小切片，按 TDD 固定日志边界，避免将路径变量业务受理编号 `ywslbh` 写入服务端主动业务日志。

本阶段只治理 Controller 主动业务日志，不修改 REST 路径、返回契约、应用服务编排、领域模型或数据库结构。

## 检查范围

- 生产代码：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/YwglCollectionController.java`
- MockMvc 测试：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/YwglCollectionControllerMockMvcTest.java`
- 组合回归：`YwglCollectionControllerTest`

## 扫描发现

主动日志扫描仍发现收款结果查询日志输出业务受理编号：

```java
log.info("收款结果查询: ywslbh={}", ywslbh);
```

该编号来自路径变量，属于业务标识。本阶段保持查询入参传递与响应 DTO 返回不变，仅移除日志中的编号值。

## RED 证据

新增测试：

```text
YwglCollectionControllerMockMvcTest#resultQueryLogShouldNotContainSensitiveBusinessSerialNo
```

测试构造哨兵业务编号：

- `ywslbh=SK-SECRET-681`

RED 命令：

```text
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#resultQueryLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
```

工具链输出：

```text
openjdk version "17.0.2" 2022-01-18
Apache Maven 3.9.14
```

失败输出摘要：

```text
收款结果查询: ywslbh=SK-SECRET-681
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

失败点说明：测试断言日志不应包含 `SK-SECRET-681`，但当前主动查询日志包含该业务受理编号。

## 修复内容

将收款结果查询日志收敛为只记录事件名：

```java
log.info("收款结果查询");
```

查询仍通过 `ywglCollectionApplicationService.queryCollectionResult(ywslbh)` 使用原路径变量；响应仍通过 `YwglCollectionAssembler.toSkywResultRespDTO(result)` 保持 `jslsh` 等返回契约。

## 验证记录

### GREEN 定向验证

命令：

```text
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=YwglCollectionControllerMockMvcTest#resultQueryLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
收款结果查询
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
YwglCollectionControllerMockMvcTest: Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
YwglCollectionControllerTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
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
capinfo-gjj-busi-jshs-app: Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 结论

本阶段确认并修复了 `YwglCollectionController#result` 查询日志中的业务受理编号输出风险。修复后：

- 查询日志只保留事件名；
- 查询参数继续传入应用服务；
- REST 响应契约未变；
- 未涉及发布、远程环境、生产配置、数据库破坏性变更或大范围删除。

## 下一阶段建议

第六十九阶段建议转向 `YwglPaymentController#result` 或 `TransferController#create` 等仍输出业务受理编号的入口，继续以一个入口一个哨兵测试的小步 TDD 节奏推进。
