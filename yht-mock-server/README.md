# yht-mock-server

一户通独立挡板项目，独立于现有业务工程运行，提供：

- 银行 / CAPS 网关模拟：`POST /yht-mock/api/gateway`
- 加密机厂商模拟：`/yht-mock/api/hsm/*`
- 场景规则编排：`/yht-mock/api/scenarios`
- 接口对接记录查看：`GET /yht-mock/api/logs`
- 前端操作页：`http://localhost:9999/yht-mock/index.html`

## 启动

```bash
mvn spring-boot:run
```

## 默认端口

- `9999`

## 主要接口

- `GET /yht-mock/api/stats`
- `GET /yht-mock/api/logs`
- `DELETE /yht-mock/api/logs`
- `GET /yht-mock/api/protocols`
- `GET /yht-mock/api/trades`
- `GET /yht-mock/api/batches`
- `GET /yht-mock/api/callback-config`
- `POST /yht-mock/api/callback-config`
- `GET /yht-mock/api/scenarios`
- `POST /yht-mock/api/scenarios`
- `DELETE /yht-mock/api/scenarios/{id}`
- `POST /yht-mock/api/trigger-callback`
- `POST /yht-mock/api/hsm/sign`
- `POST /yht-mock/api/hsm/verify`
- `POST /yht-mock/api/hsm/encrypt`
- `POST /yht-mock/api/hsm/decrypt`

## 场景规则说明

可通过前端页面或接口配置规则，按以下条件组合匹配：

- 请求报文类型
- 账号精确值
- 账号尾号
- 协议号
- `ReqId`
- 批次号
- 流水号

规则命中后可控制：

- `ResFlag`
- 业务状态
- 返回码 / 返回信息
- 是否禁止自动回调
- 指定回调报文类型

## 记录持久化

项目会将模拟记录与当前状态保存到：

```text
data/mock-state.json
```
