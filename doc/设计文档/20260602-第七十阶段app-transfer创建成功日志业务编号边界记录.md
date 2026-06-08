# 第七十阶段 app Transfer 创建成功日志业务编号边界记录

## 1. 阶段目标

本阶段延续 app 层主动业务日志降敏治理，聚焦 `TransferController#createTransfer` 成功日志。

当前风险点：接口响应仍需按业务契约返回 `ywslbh`，但服务端主动成功日志不应重复输出业务受理编号。`ywslbh` 即使来自系统生成或返回模型，也可能承载可关联业务轨迹，应按敏感业务编号处理。

## 2. 变更范围

### 2.1 生产代码

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/TransferController.java`

变更内容：

```java
log.info("转账创建成功");
```

替代原先输出：

```java
log.info("转账创建成功: ywslbh={}", result.getYwslbh());
```

### 2.2 测试代码

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/TransferControllerMockMvcTest.java`

新增测试：

```java
createTransferSuccessLogShouldNotContainSensitiveBusinessSerialNo
```

测试策略：

- Mock `TransferApplicationService#createTransfer` 返回 `ywslbh=ZZ-SECRET-701` 的转账模型。
- 使用 Logback `ListAppender<ILoggingEvent>` 捕获 `TransferController` 日志。
- 断言响应 JSON 仍返回 `$.data.ywslbh=ZZ-SECRET-701`，保持接口契约不变。
- 断言日志包含固定事件名 `转账创建成功`。
- 断言日志不包含 `ZZ-SECRET-701`。

## 3. TDD 验证记录

### 3.1 RED

命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
java -version
mvn -v
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=TransferControllerMockMvcTest#createTransferSuccessLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
```

证据摘要：

- Java：`openjdk version "17.0.2"`
- Maven：`Apache Maven 3.9.14`
- RED 日志：`TransferController -- 转账创建成功: ywslbh=ZZ-SECRET-701`
- RED 失败：`assertFalse(logs.contains("ZZ-SECRET-701"))` 失败，`expected: <false> but was: <true>`
- Maven 结果：`BUILD FAILURE`

### 3.2 GREEN 与组合回归

命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=TransferControllerMockMvcTest#createTransferSuccessLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=TransferControllerMockMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

证据摘要：

- GREEN 日志：`TransferController -- 转账创建成功`
- 定向测试：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
- Controller 组合测试：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- Maven 结果：`BUILD SUCCESS`

### 3.3 app reactor 回归

命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am test
```

证据摘要：

- common：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- domain：`Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`
- infrastructure：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`
- app：`Tests run: 104, Failures: 0, Errors: 0, Skipped: 0`
- Maven 结果：`BUILD SUCCESS`

### 3.4 R70-3 回归复核

命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
java -version
mvn -v
mvn -pl capinfo-gjj-busi-jshs-app -am test
mvn -pl capinfo-gjj-busi-jshs-app -am clean test
mvn -pl capinfo-gjj-busi-jshs-app -am -DskipTests package
```

证据摘要：

- vfox hook 尝试结果：`vfox requires hook support. Please ensure vfox is properly initialized with 'vfox activate'`；随后按项目技能口径使用显式 `JAVA_HOME`/`MAVEN_HOME` 回退。
- Java：`openjdk version "17.0.2" 2022-01-18`。
- Maven：`Apache Maven 3.9.14`，Maven home 为 `/home/source/.vfox/sdks/maven@3.9.14`，Maven 运行 Java 版本为 `17.0.2`。
- 复核期间曾观察到未 clean 状态下 infrastructure 测试运行期 `NoClassDefFoundError: cn/capinfo/gjj/common/exception/BizException`，随后通过 reactor package 重新生成模块产物后复跑。
- `mvn -pl capinfo-gjj-busi-jshs-app -am -DskipTests package`：`BUILD SUCCESS`，common/api/domain/infrastructure/app 六个 reactor 项均 SUCCESS。
- 最新 `mvn -pl capinfo-gjj-busi-jshs-app -am test`：`BUILD SUCCESS`。
- 最新 app reactor 测试汇总：common `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`；domain `Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`；infrastructure `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`；app `Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`。

## 4. 交付门禁

### 4.1 空白检查

命令：

```bash
git diff --check -- \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/TransferController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/TransferControllerMockMvcTest.java
```

结果：退出码 0，未发现空白错误。

R70-3 全仓复核命令：

```bash
git diff --check
```

结果：退出码 0，未发现空白错误。

### 4.2 路径检查

检查结果：

- `TransferController.java` 位于 v3 app Maven 源码树，且非 `target` 下唯一匹配源文件。
- `TransferControllerMockMvcTest.java` 位于 v3 app Maven 测试源码树，且非 `target` 下唯一匹配测试文件。
- `doc/设计文档` 存在且为仓库根目录下设计文档目录。
- `scripts/guardrails/agent-delivery-guardrail.sh` 存在且为仓库根目录下 guardrail 脚本。

### 4.3 Guardrail

命令：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/TransferController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/TransferControllerMockMvcTest.java
```

结果：`✅ [通过] 未发现偷懒式占位痕迹。`

R70-3 指定文件复核命令：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/main/java/cn/capinfo/gjj/app/controller/TransferController.java \
  prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/capinfo-gjj-busi-jshs-app/src/test/java/cn/capinfo/gjj/app/controller/TransferControllerMockMvcTest.java \
  doc/设计文档/20260602-第七十阶段app-transfer创建成功日志业务编号边界记录.md
```

结果：`✅ [通过] 未发现偷懒式占位痕迹。`

## 5. 风险边界

- 本阶段只修改本地 Java 日志模板与本地测试。
- 未修改接口路径、请求结构、响应结构、领域模型、持久化模型、Mapper SQL 或数据库设计。
- 未执行发布、远程上传、服务重启、生产配置变更或数据库变更。
- `$.data.ywslbh` 响应字段保留，满足外部调用契约；仅收敛服务端主动日志输出。

## 6. R70-2 GREEN 复核补充

本轮看板任务 `t_1b22634a` 复核时，源码与哨兵测试已落在本阶段目标状态：

- `TransferController#createTransfer` 成功日志为固定事件名：`log.info("转账创建成功");`
- `TransferControllerMockMvcTest#createTransferSuccessLogShouldNotContainSensitiveBusinessSerialNo` 覆盖 `ywslbh=ZZ-SECRET-701` 哨兵值，验证响应仍返回业务受理编号但日志不包含该编号。
- 重新执行 `mvn -pl capinfo-gjj-busi-jshs-app -am clean -Dtest=TransferControllerMockMvcTest#createTransferSuccessLogShouldNotContainSensitiveBusinessSerialNo -Dsurefire.failIfNoSpecifiedTests=false test`，结果 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 重新执行 `mvn -pl capinfo-gjj-busi-jshs-app -am -Dtest=TransferControllerMockMvcTest,TransferControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，结果 `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 重新执行 `mvn -pl capinfo-gjj-busi-jshs-app -am test`，结果 common 5、domain 54、infrastructure 17、app 104 全部通过，`BUILD SUCCESS`。
- 重新执行 guardrail/空白/路径检查：`scripts/guardrails/agent-delivery-guardrail.sh --scan-only ...` 输出 `✅ [通过] 未发现偷懒式占位痕迹`；`git diff --check` 退出码 0；源码/测试/doc 均位于预期仓库根目录。

## 7. 下一步建议

继续按低风险本地阶段推进 app 主动业务日志治理。当前 app reactor 日志中仍可见部分 Controller 主动成功日志输出业务编号，例如：

- `ReconController -- 对账创建成功: dzbh=...`
- `VoucherController -- 凭证创建成功: pzbh=...`
- `AccountController -- 账户注册成功: zhbh=...`
- `AccountController -- 查询账户列表: hsdwdm=..., count=...`

建议第七十一阶段优先选择单一 Controller 成功日志切片，按同样 TDD 模式新增哨兵测试、确认 RED、最小收敛日志、运行 app reactor 与 guardrail。
