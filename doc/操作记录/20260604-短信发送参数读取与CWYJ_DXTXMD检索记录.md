# 20260604-短信发送参数读取与CWYJ_DXTXMD检索记录

## 检索范围
- 短信发送实现
- 参数读取实现
- `CWYJ_DXTXMD` 关键字
- 自动月结/月结未完成短信相关实现

## 结论摘要
- `CWYJ_DXTXMD` 仅在需求文档中出现，当前代码库未发现落地实现。
- 资金结算侧已存在两类可复用通知能力：
  - `SmsServiceImpl`：按手机号列表直接组装批量短信并通过信息交换平台发送。
  - `MessageSendServiceImpl` / `MessageUtils`：面向消息中心/门户的站内信或场景化消息发送。
- 参数读取的主流实现是 `ParamUtil.parm(...)` / `ParamUtils.parm(...)`，返回 `CsXtcsDTO` 后读取 `getCsValue()`。
- 若后续实现“月结未完成短信提醒”，最接近的复用入口是：
  - 参数读取复用 `ParamUtil.parm(Constants.HSDWDM, XtlxEnum.XTLX_ZJJS.getValue(), "JYCS", "<参数名>", new Date())`
  - 短信发送复用 `SmsService.sendBatchSms(...)`，但其当前接收人来源是配置文件，不是系统参数表。

## 关键文件
- 需求来源：`doc/需求文档/财务自动月结流程.md`
- 短信服务：`.../SmsService.java`、`.../SmsServiceImpl.java`
- 短信调用方：`.../YhzhxxYeCheckJob.java`
- 站内消息服务：`.../MessageSendServiceImpl.java`
- 场景化消息工具：`.../MessageUtils.java`
- 参数读取示例：`.../YwglServiceImpl.java`、`.../ZbkhServiceImpl.java`

## 复用建议
- 需要手机号参数表驱动时，新增一个参数解析层，把 `CWYJ_DXTXMD` 解析为手机号列表，再适配到 `SmsServiceImpl` 的 `ReadySendMessage` 结构。
- 若只需门户站内通知而非短信，可直接复用 `MessageSendServiceImpl`。
