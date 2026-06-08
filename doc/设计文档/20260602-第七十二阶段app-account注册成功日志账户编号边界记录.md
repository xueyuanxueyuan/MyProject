# 第七十二阶段 app Account 注册成功日志账户编号边界记录

## 1. 阶段目标

第七十二阶段继续执行 Gjj v3 低风险本地日志治理切片，目标限定为 app 模块 `AccountController#registerAccount` 的注册成功日志。

本阶段仅处理单一 Controller、单一成功日志边界：

- 保留接口响应中的账户编号 `zhbh`，不改变业务返回契约；
- 移除成功日志中的账户编号明文值，避免主动日志泄露业务编号；
- 不涉及发布、远程环境、生产配置、数据库变更、删除或大范围重构。

## 2. 涉及文件

生产代码：

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/AccountController.java`

测试代码：

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/AccountControllerMockMvcTest.java`

## 3. TDD 过程

### 3.1 RED：新增日志哨兵测试

新增测试：

- `AccountControllerMockMvcTest#registerAccountSuccessLogShouldNotContainSensitiveAccountNo`

测试行为：

1. Mock `AccountApplicationService#registerAccount` 返回账户编号 `ZH-SECRET-721`；
2. 使用 standalone MockMvc 调用 `/api/account/register`；
3. 断言 HTTP/R 响应仍返回 `data.zhbh=ZH-SECRET-721`；
4. 捕获 `AccountController` INFO 日志；
5. 断言日志包含业务动作“账户注册成功”，但不包含 `ZH-SECRET-721`。

RED 运行命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
java -version
mvn -version
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=AccountControllerMockMvcTest#registerAccountSuccessLogShouldNotContainSensitiveAccountNo \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

RED 证据：

- Java：`openjdk version "17.0.2" 2022-01-18`
- Maven：`Apache Maven 3.9.14`
- 日志实际输出：`账户注册成功: zhbh=ZH-SECRET-721`
- 失败点：`assertFalse(logs.contains("ZH-SECRET-721"))`
- 结果：`BUILD FAILURE`，失败符合预期，证明哨兵能捕获明文账户编号日志。

### 3.2 GREEN：最小生产修复

修改前：

```java
log.info("账户注册成功: zhbh={}", result.getZhbh());
```

修改后：

```java
log.info("账户注册成功");
```

说明：

- 只移除成功日志中的 `zhbh` 明文输出；
- 不改变 `AccountModel`、`AccountAssembler`、`AccountRespDTO` 或 HTTP 响应；
- 不扩大治理到 `queryAccounts` 或 `VoucherController`，保持阶段切片边界。

GREEN/组合回归命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=AccountControllerMockMvcTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

GREEN 证据：

- `AccountControllerMockMvcTest` 共 5 个测试通过；
- 日志输出已变为 `账户注册成功`；
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
- app 模块测试：`Tests run: 106, Failures: 0, Errors: 0, Skipped: 0`
- Reactor 总结果：`BUILD SUCCESS`

注意：曾在仓库根目录误执行同一 Maven 命令，Maven 返回 `Could not find the selected project in the reactor: capinfo-gjj-busi-jshs-app`。该结果已判定为错误工作目录导致的无效回归证据，随后已在正确 v3 reactor 根目录重跑并通过。

### 4.2 路径与内容复核

复核结果：

- `AccountController.java` 存在；
- `AccountControllerMockMvcTest.java` 存在；
- 旧敏感日志 `账户注册成功: zhbh={}` 已不存在；
- 安全日志 `log.info("账户注册成功");` 已存在；
- 哨兵测试 `registerAccountSuccessLogShouldNotContainSensitiveAccountNo` 已存在；
- 测试 fixture `ZH-SECRET-721` 仅用于证明响应保留、日志不输出。

### 4.3 防偷懒占位门禁

执行命令：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/AccountController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/AccountControllerMockMvcTest.java
```

验证结果：

- `✅ [通过] 未发现偷懒式占位痕迹。`

## 5. 风险边界

本阶段为低风险本地代码与测试治理：

- 未发布；
- 未连接或修改远程环境；
- 未修改生产配置；
- 未执行数据库变更；
- 未进行大范围删除；
- 未改变账户注册接口响应字段。

## 6. 下一阶段建议

扫描结果显示仍有低风险主动日志治理候选：

- `VoucherController#createVoucher` 当前输出 `凭证创建成功: pzbh={}`。

建议第七十三阶段继续采用同样 TDD 切片：新增凭证编号日志哨兵测试，确认 RED 后最小移除 `pzbh` 明文日志，再运行 targeted/app reactor/guardrail/doc 门禁。
