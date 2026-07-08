## 背景
- 审计范围：`yht-mock-server`（一户通独立挡板项目）
- 审计日期：2026-07-07
- 审计目标：识别可被端到端利用的中危及以上漏洞（不纳入仅理论风险）

## 结论摘要
- 发现高危：未鉴权管理接口导致敏感报文泄露；可控出站请求导致 SSRF，且可通过日志接口回读内网响应形成闭环。

## 发现 1（高危）：未鉴权日志接口导致敏感报文/凭据泄露
- 入口：
  - `GET /yht-mock/api/logs`：[YhtMockApiController.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/controller/YhtMockApiController.java#L48-L57)
- 数据落地：
  - 网关请求/响应原文被写入 `record.requestBody/responseBody`：[MockGatewayService.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/service/MockGatewayService.java#L165-L184)
  - 回调请求/响应原文被写入 `record.requestBody/responseBody`：[MockCallbackService.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/service/MockCallbackService.java#L172-L203)
  - 日志对象包含 `requestBody/responseBody` 字段：[MockRecord.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/model/MockRecord.java#L3-L20)
- 影响：
  - 外部访问者可直接读取请求/响应报文原文，可能包含账号、协议号、交易流水号以及报文头中的用户名/口令字段等敏感信息。

## 发现 2（高危）：可控目标 URL 的 SSRF，且可通过日志接口回读内网响应
- 入口（用户可控 targetUrl）：
  - `POST /yht-mock/api/trigger-callback`：[YhtMockApiController.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/controller/YhtMockApiController.java#L99-L110)
- 出站请求构造（未做 allowlist/内网拦截）：
  - `HttpRequest.newBuilder(URI.create(targetUrl)).POST(...)`：[MockCallbackService.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/service/MockCallbackService.java#L172-L203)
- 回读闭环（将响应体记录并通过 logs 对外暴露）：
  - `record.responseBody = response.body()`：[MockCallbackService.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/service/MockCallbackService.java#L184-L203)
  - `GET /yht-mock/api/logs` 直接返回 records：[YhtMockApiController.java](file:///d:/Probject/Gjj/yht-mock-server/src/main/java/cn/capinfo/gjj/yhtmock/controller/YhtMockApiController.java#L48-L57)
- 影响：
  - 外部访问者可对内网地址/本机地址发起 HTTP POST（内网探测、访问云元数据地址、命中内部管理面、触发有副作用的接口）。
  - 响应体可被记录并通过日志接口回读，实现“可交互”的 SSRF。

## 建议整改
- 访问控制：
  - 该服务默认应仅允许运维/开发内网访问；在网关/Ingress 层做 IP allowlist。
  - 对 `/yht-mock/api/**` 增加鉴权（至少 Basic Auth/Token），并对敏感接口（logs、callback-config、scenarios、trigger-callback）做更严格授权。
- SSRF 防护：
  - `targetUrl` 启用严格 allowlist（协议仅允许 http/https；host 必须在白名单；端口限定；禁止 localhost、内网网段、link-local、metadata 等）。
  - 禁止跟随重定向（如后续引入客户端配置时需显式关闭）。
- 数据最小化：
  - 禁止记录/对外返回完整报文体与响应体；对必要字段做脱敏（账号、证件号、手机号、口令等）。
  - `GET /logs` 默认不返回 `requestBody/responseBody`，改为按需、受控、分页查询，并提供“脱敏视图”。

