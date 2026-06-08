# 第七十三阶段 app Voucher 创建成功日志凭证编号边界记录

## 1. 阶段目标

第七十三阶段继续执行 Gjj v3 低风险本地日志治理切片，目标限定为 app 模块 `VoucherController#createVoucher` 的创建成功日志。

本阶段仅处理单一 Controller、单一成功日志边界：

- 保留接口响应中的凭证编号 `pzbh`，不改变业务返回契约；
- 移除成功日志中的凭证编号明文值，避免主动日志泄露业务编号；
- 不涉及发布、远程环境、生产配置、数据库变更、删除或大范围重构。

## 2. 涉及文件

生产代码：

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/VoucherController.java`

测试代码：

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/VoucherControllerMockMvcTest.java`

## 3. TDD 过程

### 3.1 RED：新增日志哨兵测试

新增测试：

- `VoucherControllerMockMvcTest#createVoucherSuccessLogShouldNotContainSensitiveVoucherNo`

测试行为：

1. Mock `VoucherApplicationService#createVoucher` 返回凭证编号 `PZ-SECRET-731`；
2. 使用 standalone MockMvc 调用 `/api/voucher/create`；
3. 断言 HTTP/R 响应仍返回 `data.pzbh=PZ-SECRET-731`；
4. 捕获 `VoucherController` INFO 日志；
5. 断言日志包含业务动作“凭证创建成功”，但不包含 `PZ-SECRET-731`。

RED 运行命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=VoucherControllerMockMvcTest#createVoucherSuccessLogShouldNotContainSensitiveVoucherNo \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

RED 证据：

- 日志实际输出：`凭证创建成功: pzbh=PZ-SECRET-731`
- 失败点：`assertFalse(logs.contains("PZ-SECRET-731"))`
- 结果：`BUILD FAILURE`，失败符合预期，证明哨兵能捕获明文凭证编号日志。

### 3.2 GREEN：最小生产修复

修改前：

```java
log.info("凭证创建成功: pzbh={}", result.getPzbh());
```

修改后：

```java
log.info("凭证创建成功");
```

说明：

- 只移除成功日志中的 `pzbh` 明文输出；
- 不改变 `VoucherModel`、`VoucherAssembler`、`VoucherRespDTO` 或 HTTP 响应；
- 不扩大治理到其他 Controller，保持阶段切片边界。

GREEN/组合回归命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=VoucherControllerMockMvcTest,VoucherControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

GREEN 证据：

- `VoucherControllerMockMvcTest` 与 `VoucherControllerTest` 共 4 个测试通过；
- 日志输出已变为 `凭证创建成功`；
- 结果：`BUILD SUCCESS`。

## 4. 回归与门禁

### 4.1 app reactor 回归

在 v3 reactor 根目录执行：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
java -version
mvn -version
mvn -pl capinfo-gjj-busi-jshs-app -am test
```

验证结果：

- Java：`openjdk version "17.0.2" 2022-01-18`
- Maven：`Apache Maven 3.9.14`
- Reactor：`capinfo-gjj-busi-jshs-v3`、`common`、`api`、`domain`、`infrastructure`、`app` 全部 `SUCCESS`
- app 模块测试：`Tests run: 107, Failures: 0, Errors: 0, Skipped: 0`
- Reactor 总结果：`BUILD SUCCESS`

### 4.2 路径与内容复核

复核结果：

- `VoucherController.java` 存在；
- `VoucherControllerMockMvcTest.java` 存在；
- 旧敏感日志 `凭证创建成功: pzbh={}` 已不存在；
- 安全日志 `log.info("凭证创建成功");` 已存在；
- 哨兵测试 `createVoucherSuccessLogShouldNotContainSensitiveVoucherNo` 已存在；
- 测试 fixture `PZ-SECRET-731` 仅用于证明响应保留、日志不输出。

### 4.3 防偷懒占位门禁

在 Gjj 仓库根目录执行：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/VoucherController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/VoucherControllerMockMvcTest.java
```

验证结果：

- `✅ [通过] 未发现偷懒式占位痕迹。`

说明：曾在 v3 reactor 子目录误调用仓库根目录脚本，返回 `scripts/guardrails/agent-delivery-guardrail.sh: 没有那个文件或目录`。该结果已判定为错误工作目录导致的无效 guardrail 证据，随后已在 Gjj 仓库根目录重跑并通过。

## 5. 风险边界

本阶段为低风险本地代码与测试治理：

- 未发布；
- 未连接或修改远程环境；
- 未修改生产配置；
- 未执行数据库变更；
- 未进行大范围删除；
- 未改变凭证创建接口响应字段。

## 6. 下一阶段建议

继续扫描 app 层 Controller/ApplicationService 主动业务日志，优先选择单一日志、单一测试类、可用 RED/GREEN 证明的低风险切片。当前回归日志中仍可见若干主动日志包含请求数值或地区编码等字段，下一阶段建议先重新扫描并按“业务编号/流水号/外部输入字段优先”排序后再选取目标。
