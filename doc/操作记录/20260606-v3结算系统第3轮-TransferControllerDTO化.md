# v3结算系统第3轮操作记录 — TransferController DTO 化

> 日期：2026-06-06
> 操作人：Hermes Agent
> 对应会话：20260606_104955_（当前会话）
> 前置文档：doc/操作记录/20260605-v3结算系统第2轮-批量业务持久化.md

---

## 一、本轮范围

**阶段：TransferController DTO 化**（优先级 G3 缺口）

### 变更清单

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `app/.../controller/TransferController.java` | 修改 | `@RequestBody Map<String, Object>` → `@RequestBody @Valid TransferCreateReqDTO` |
| 2 | `app/.../service/TransferApplicationService.java` | 修改 | 删除 `createTransfer(Map)` 方法（仅保留 DTO 重载） |
| 3 | `app/.../controller/TransferControllerTest.java` | 修改 | 测试适配 DTO 参数 |
| 4 | `app/.../controller/TransferControllerMockMvcTest.java` | 修改 | Mock 切为 DTO 参数 + 新增 @Valid 校验失败测试 |
| 5 | `app/.../service/TransferApplicationServiceTest.java` | 修改 | 删除 Map 方法测试（仅保留 DTO 方法测试） |

### 代码变更说明

**Controller 层：**
- 移除 `Map<String, Object>` 手工参数入口
- 改用 `@RequestBody @Valid TransferCreateReqDTO`，利用 `@NotBlank`/`@NotNull`/`@DecimalMin` 注解自动校验

**AppService 层：**
- 删除 `createTransfer(Map)` 方法（原方法手动从 Map 提取字段构造 DTO）
- 只保留 `createTransfer(TransferCreateReqDTO)` 方法

**测试覆盖：**
- 原有测试全部适配 DTO 参数
- 新增 `createTransferShouldFailWithValidationErrorWhenRequiredFieldsMissing` — 发送缺字段请求，验证 `MethodArgumentNotValidException` 被 `GlobalExceptionHandler` 捕获返回 1001/PARAM_ERROR

## 二、验证结果

| 验证项 | 结果 |
|--------|------|
| 全量模块编译 | ✅ 0 错误 |
| 全量测试 | ✅ **194 个测试**，0 失败 |
| @Valid 参数校验 | ✅ GlobalExceptionHandler 已处理 MethodArgumentNotValidException |
| 对外接口兼容 | ✅ POST 请求 JSON 格式不变（字段名一致） |
| 守卫脚本 | ✅ `agent-delivery-guardrail.sh --scan-only` 通过 |

## 三、影响范围

| 维度 | 影响 |
|------|------|
| 对外 REST API | ❌ JSON 请求/响应格式不变 |
| 对内 AppService API | ✅ 不再暴露 Map 方法，仅接收 TransferCreateReqDTO |
| 代码可维护性 | ✅ DTO 承载显式校验注解，错误信息由框架自动生成 |
| 现有测试 | ✅ 全部通过，无回归 |
| 测试数变化 | 删除 1 个 Map 测试 + 新增 1 个 @Valid 测试 = 净 0 变化 |

## 四、本轮未做

以下缺口仍待后续轮次：
- **G2 — 策略未真正调用**：FkywDomainService 仅 log 策略类名，未调用 `processBusinessAccounting()`
- **G4 — 收款/付款缺少不变量校验**：FkywDomainService 没有金额 >0、账户必填校验
- **G5 — Entity 命名不规范**：低优先级

## 五、后续建议

| 建议 | 优先级 | 说明 |
|------|--------|------|
| **策略粒度细化与调用** | 🟡 中 | G2 — 让 FkywDomainService 实际调用策略的 `processBusinessAccounting()` |
| **收款/付款不变量校验** | 🟡 中 | G4 — 为 FkywDomainService/SkywDomainService 补充金额 >0 校验 |
| **更新看板标记** | 🟡 中 | T1/T2/T3 可标记 DONE；R69/R70/R71 评估归档 |
