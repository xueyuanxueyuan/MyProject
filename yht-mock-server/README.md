# yht-mock-server

## 交易成功自动动账通知

支持单笔及批量成功明细自动提交模拟动账通知，统一使用虚拟对手信息；默认关闭，需在回调配置面板填写目标地址及收付款动账代码后启用。详见 [自动动账通知使用说明](MOVEMENT.md)。

一户通独立挡板项目，独立于现有业务工程运行，提供：

- 银行 / CAPS 网关模拟：`POST /yht-mock/api/gateway`
- 加密机厂商模拟：`/yht-mock/api/hsm/*`
- 场景规则编排：`/yht-mock/api/scenarios`
- 接口对接记录查看：`GET /yht-mock/api/logs`
- 前端操作页：`http://localhost:9999/yht-mock/index.html`

## 启动

先执行达梦初始化脚本并配置 `YHT_MOCK_DB_URL`、`YHT_MOCK_DB_USERNAME`、`YHT_MOCK_DB_PASSWORD`。历史 JSON 应在首次普通启动前迁移；不再支持文件模式或数据库故障时回退文件。

建库、迁移、K8s Secret 配置及回退边界见仓库根目录 `doc/数据库脚本/一户通挡板达梦/README.md`。

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

「场景规则」已简化为**账号校验规则**，按交易/批量明细中的**对手账号**（非中心账号）校验，只配置四项：

- 规则名称
- 账号规则（正则表达式，命中才适用；留空 = 匹配任意对手账号，非法正则退化为精确匹配）
- 是否有效（不勾选 = 命中即交易失败，返回码 `ACCT_INVALID`）
- 是否一类卡（有效 + 一类卡全部成功；有效 + 二类卡按「日交易限额 1 万元」累计判定，返回码 `LIMIT_EXCEED`）

匹配优先级：后更新的规则优先；未命中任何规则时默认成功。单笔按对手账号逐笔判定，批量按请求文件中每条明细的对手账号逐笔判定（同批次内日限额累计共用）。

另在「回调配置」提供**随机模拟交易失败**开关（`randomFail` + 概率 `randomFailRatio`）：开启后，账号规则判定为成功的交易再按概率随机失败（返回码 `RANDOM_FAIL`），用于压测与异常模拟。

## 资金流水银行流水号

资金流水银行流水号统一**不超过 32 位**：单笔沿用原交易流水（超长自动按「保留前缀 + 确定性哈希」收敛），批量按包生成 `MOCK + 28 位确定性十六进制`。历史超长数据可在资金流水页点「修复超长流水号」或调用 `POST /yht-mock/api/movements/normalize-serials` 一键收敛（幂等，可重复执行，已生成通知报文中的 `yhlsh` 同步改写）。

## 记录持久化

配置、规则、记录、协议、交易、批次和动账幂等全部保存到独立达梦 Schema，不再读写运行快照。

历史迁移使用 `java -jar yht-mock-server-1.0.0-SNAPSHOT.jar --yht-mock.migrate-file=/data/migration/mock-state.json`，只读源文件，原子导入空库；普通服务不自动迁移。

“清除历史数据”仅清空接口记录及协议/交易/批次，保留配置、规则、动账幂等和迁移凭据。

## 回归检查

在项目目录运行 `mvn test` 和 `node --test src/test/js/static-page.test.cjs`。默认数据库测试使用 H2 Oracle 模式；真实达梦测试入口及环境变量见建库说明，未配置时明确跳过。
