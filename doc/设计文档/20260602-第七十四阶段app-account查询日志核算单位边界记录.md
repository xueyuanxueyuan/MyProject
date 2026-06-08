# 第七十四阶段 app-account 查询日志核算单位边界记录

## 1. 阶段范围

- 阶段编号：第七十四阶段
- 所属模块：`capinfo-gjj-busi-jshs-app`
- 控制器：`cn.capinfo.gjj.app.controller.AccountController`
- 本阶段目标：治理账户查询成功日志中的 `X-HSDWDM` 请求头明文输出。
- 风险级别：低风险本地改动。

本阶段只处理一个 Controller 的一个主动日志切片，不涉及发布、远程环境、生产配置、数据库结构或数据变更，也不做大范围删除。

## 2. 问题背景

`AccountController#queryAccounts` 原日志为：

```java
log.info("查询账户列表: hsdwdm={}, count={}", hsdwdm, accounts.size());
```

其中 `hsdwdm` 来源于 HTTP 请求头 `X-HSDWDM`。虽然该值仍需传递给应用服务并通过响应数据保持原业务语义，但主动成功日志不应记录该外部输入值，避免把核算单位编码边界扩大到日志链路。

## 3. TDD 过程

### 3.1 RED

新增 MockMvc 哨兵测试：

- 文件：`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/AccountControllerMockMvcTest.java`
- 方法：`queryAccountsSuccessLogShouldNotContainSensitiveOrganizationCodeHeader`

测试构造：

- 请求头：`X-HSDWDM: HSDWDM-SECRET-741`
- 应用服务仍接收该请求头值并返回含同值的账户响应模型。
- HTTP 响应仍断言 `$.data[0].hsdwdm = HSDWDM-SECRET-741`，证明业务响应语义未被移除。
- 日志断言：
  - 包含 `查询账户列表`
  - 包含 `count=1`
  - 不包含 `HSDWDM-SECRET-741`

RED 命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=AccountControllerMockMvcTest#queryAccountsSuccessLogShouldNotContainSensitiveOrganizationCodeHeader \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

RED 结果：失败符合预期。

关键失败证据：

```text
INFO cn.capinfo.gjj.app.controller.AccountController -- 查询账户列表: hsdwdm=HSDWDM-SECRET-741, count=1
AssertionFailedError: expected: <false> but was: <true>
```

## 4. 生产代码修改

文件：

`prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/AccountController.java`

修改后日志：

```java
log.info("查询账户列表: count={}", accounts.size());
```

保留内容：

- `hsdwdm` 仍作为请求头入参传递给 `accountApplicationService.queryAccounts(hsdwdm)`。
- 响应 DTO 中仍可返回账户的 `hsdwdm` 字段。
- 日志仍保留账户查询动作与结果数量 `count`。

移除内容：

- 主动成功日志中的 `hsdwdm={}` 占位与请求头明文值。

## 5. 验证记录

### 5.1 GREEN 与组合回归

命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=AccountControllerMockMvcTest,AccountControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 5.2 app reactor 回归

命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am test
```

结果：

```text
capinfo-gjj-busi-jshs-common: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-domain: Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-infrastructure: Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-app: Tests run: 108, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

工具链验证：

```text
openjdk version "17.0.2" 2022-01-18
Apache Maven 3.9.14
```

### 5.3 guardrail 与内容门禁

命令：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/AccountController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/AccountControllerMockMvcTest.java
```

结果：

```text
✅ [通过] 未发现偷懒式占位痕迹。
```

内容哨兵：

```text
PASS - prod query log removes hsdwdm placeholder
PASS - prod query log keeps count
PASS - test sentinel exists
PASS - test sentinel checks secret absent
```

## 6. 变更文件

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/AccountController.java`
- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/AccountControllerMockMvcTest.java`
- `doc/设计文档/20260602-第七十四阶段app-account查询日志核算单位边界记录.md`

## 7. 风险与边界

- 未修改接口路径、请求参数、请求头、响应结构或应用服务调用。
- 未修改数据库、远程环境、生产配置或发布脚本。
- 本阶段仅约束主动成功日志不输出请求头核算单位编码。
- root git 下 `prod/...` 路径被 `.gitignore:2:/*` 忽略，因此源码变更不在 root git porcelain 中体现；本阶段以文件内容、测试结果和 guardrail 作为验证依据。

## 8. 后续建议

继续沿用单 Controller、单日志、TDD RED/GREEN 的低风险治理方式，扫描剩余主动日志中是否仍存在来自请求体、请求头、路径变量或外部系统返回的业务编号明文输出；每个切片独立留痕、独立回归。
