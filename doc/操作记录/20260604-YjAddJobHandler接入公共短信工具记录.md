# YjAddJobHandler 接入公共短信工具记录

## 1. 目标

- 在 `YjAddJobHandler#yjStatusCheckJobHandler` 中补齐月结未完成短信提醒能力。
- 复用已抽取到 `core-common` 的 `SmsSendUtils`。
- 保留原有 XXL 任务失败语义，不因短信发送异常改变月结检查结论。

## 2. 实现方案

### 2.1 接收人来源

- 按需求文档约定，从系统参数读取：
  - 系统：`ZJJS`
  - 参数类型：`JYCS`
  - 参数名：`CWYJ_DXTXMD`
- 参数值按逗号分隔解析手机号列表，同时兼容中文逗号并做去重、去空白处理。

### 2.2 短信发送能力

- 直接注入 `SmsSendUtils`。
- 通过新增配置类 `MonthEndSmsAlertProperties` 提供：
  - `gjj.month-end.sms-alert.enabled`
  - `gjj.month-end.sms-alert.url`
  - `gjj.month-end.sms-alert.template-id`
  - `gjj.month-end.sms-alert.remark`

### 2.3 任务语义

- 若发现未完成月结账套，仍以 `XxlJobHelper.handleFail(...)` 标识任务失败。
- 若短信未发送成功，只在失败信息中追加“短信提醒未发送”，不伪装为已发送。

## 3. 新增与修改文件

### 3.1 新增

- `MonthEndSmsAlertProperties.java`
- `MonthEndSmsAlertHelper.java`
- `MonthEndSmsAlertHelperTest.java`

### 3.2 修改

- `YjAddJobHandler.java`

## 4. 验证说明

- `GetDiagnostics` 对 `YjAddJobHandler.java` 未报语法问题。
- 其余新增文件在当前 IDE 环境下仅返回 non-project 提示。
- Maven 完整测试仍受仓库外部依赖下载限制影响，需要在可访问依赖仓库环境中复核。
