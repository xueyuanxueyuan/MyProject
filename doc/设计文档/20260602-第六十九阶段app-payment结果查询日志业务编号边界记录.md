# 第六十九阶段：app-payment 付款结果查询日志业务编号边界记录

## 1. 阶段目标

本阶段治理 `YwglPaymentController#result` 的主动业务日志边界，避免付款结果查询路径变量 `ywslbh` 被写入应用日志。

本阶段仅调整本地 Java Controller 日志文案与 MockMvc 回归测试，不涉及发布、远程环境、生产配置、数据库 DDL 或大范围删除。

## 2. 背景与现状

上一阶段已完成 collection 模块结果查询日志治理，收款结果查询日志由输出 `ywslbh` 收敛为固定业务动作摘要。

本阶段复核 payment 模块时发现：

```java
@PostMapping("/result/{ywslbh}")
public R<FkywResultRespDTO> result(@PathVariable String ywslbh) {
    log.info("付款结果查询: ywslbh={}", ywslbh);

    FkywModel model = ywglPaymentApplicationService.queryPaymentResult(ywslbh);

    return R.ok(YwglPaymentAssembler.toFkywResultRespDTO(model));
}
```

其中 `ywslbh` 来自外部路径变量，属于业务受理编号。按当前日志边界治理口径，主动业务日志不应输出该外部输入值；接口响应与服务调用仍需保留该编号。

## 3. 看板拆解

已在 `jshs-v3` 看板创建第六十九阶段任务：

| 看板任务 | 目标 |
| --- | --- |
| `R69-0-复核付款结果查询日志治理现状` | 读取 Controller 与 MockMvc 测试，确认当前日志仍输出 `ywslbh` |
| `R69-1-付款结果查询日志哨兵RED` | 新增日志哨兵测试并确认 RED |
| `R69-2-付款结果查询日志最小修复GREEN` | 最小修改 Controller 日志并运行 GREEN/组合回归 |
| `R69-3-第六十九阶段回归门禁与文档留痕` | 运行 app reactor 回归、diff/path/guardrail 检查并写入阶段记录 |

## 4. TDD 过程

### 4.1 RED

新增测试：

```java
@Test
void resultQueryLogShouldNotContainSensitiveBusinessSerialNo() throws Exception {
    FkywModel result = fkywModel("FK-SECRET-691", JslxEnum.DB, new BigDecimal("100.00"), JsztEnum.CLZ);
    result.setGxsj(LocalDateTime.of(2026, 6, 1, 18, 10, 1));
    when(ywglPaymentApplicationService.queryPaymentResult("FK-SECRET-691")).thenReturn(result);
    Logger logger = (Logger) LoggerFactory.getLogger(YwglPaymentController.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    Level originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);

    try {
        mockMvc.perform(post("/api/payment/result/{ywslbh}", "FK-SECRET-691"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.jslsh").value("FK-SECRET-691"))
                .andExpect(jsonPath("$.data.jg").value("1"));
    } finally {
        logger.setLevel(originalLevel);
        logger.detachAppender(appender);
    }

    String logs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
    assertTrue(logs.contains("付款结果查询"));
    assertFalse(logs.contains("FK-SECRET-691"));

    verify(ywglPaymentApplicationService).queryPaymentResult("FK-SECRET-691");
    verifyNoMoreInteractions(ywglPaymentApplicationService);
}
```

RED 命令：

```bash
export JAVA_HOME=/home/source/.vfox/sdks/java@17.0.2+8
export MAVEN_HOME=/home/source/.vfox/sdks/maven@3.9.14
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=YwglPaymentControllerMockMvcTest#resultQueryLogShouldNotContainSensitiveBusinessSerialNo \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

RED 结果摘要：

```text
12:43:02.854 [main] INFO cn.capinfo.gjj.app.controller.YwglPaymentController -- 付款结果查询: ywslbh=FK-SECRET-691
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
YwglPaymentControllerMockMvcTest.resultQueryLogShouldNotContainSensitiveBusinessSerialNo:287 expected: <false> but was: <true>
BUILD FAILURE
```

失败原因符合预期：测试不是编译错误或夹具错误，而是现有主动日志确实包含哨兵编号 `FK-SECRET-691`。

### 4.2 GREEN

最小修复：

```java
@PostMapping("/result/{ywslbh}")
public R<FkywResultRespDTO> result(@PathVariable String ywslbh) {
    log.info("付款结果查询");

    FkywModel model = ywglPaymentApplicationService.queryPaymentResult(ywslbh);

    return R.ok(YwglPaymentAssembler.toFkywResultRespDTO(model));
}
```

保留内容：

- 路由仍为 `/api/payment/result/{ywslbh}`。
- 路径变量仍传入 `ywglPaymentApplicationService.queryPaymentResult(ywslbh)`。
- 响应仍通过 `YwglPaymentAssembler.toFkywResultRespDTO(model)` 输出业务结果。
- 接口响应中的 `jslsh` 不变；只治理主动日志输出。

## 5. 验证记录

### 5.1 工具链

```text
java: openjdk version "17.0.2" 2022-01-18
maven: Apache Maven 3.9.14
Maven home: /home/source/.vfox/sdks/maven@3.9.14
```

### 5.2 定向 GREEN

命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=YwglPaymentControllerMockMvcTest#resultQueryLogShouldNotContainSensitiveBusinessSerialNo \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
12:43:45.355 [main] INFO cn.capinfo.gjj.app.controller.YwglPaymentController -- 付款结果查询
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 5.3 付款 Controller 组合回归

命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am \
  -Dtest=YwglPaymentControllerMockMvcTest,YwglPaymentControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果摘要：

```text
YwglPaymentControllerMockMvcTest: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
YwglPaymentControllerTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
总计: Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 5.4 app reactor 回归

命令：

```bash
mvn -pl capinfo-gjj-busi-jshs-app -am test
```

结果摘要：

```text
capinfo-gjj-busi-jshs-common: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-domain: Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-infrastructure: Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
capinfo-gjj-busi-jshs-app: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 6. 影响面

| 项目 | 结论 |
| --- | --- |
| API 路由 | 不变 |
| 请求路径变量 | 不变 |
| ApplicationService 调用 | 不变 |
| 响应 DTO 契约 | 不变 |
| 数据库/Mapper/Entity | 未修改 |
| 远程发布/生产配置 | 未触及 |
| 日志输出 | 由携带 `ywslbh` 改为固定业务动作摘要 |

## 7. 后续建议

1. 继续按“单个 Controller / 单个日志点 / 单个阶段”的粒度扫描主动业务日志。
2. 对来自路径变量、请求体、请求头、外部回调参数的业务编号和账号姓名类字段，优先使用 Logback `ListAppender` 哨兵测试治理。
3. 金额、笔数等摘要是否保留，应按业务日志最小必要原则逐项判定；避免一次性大范围删除日志上下文。
