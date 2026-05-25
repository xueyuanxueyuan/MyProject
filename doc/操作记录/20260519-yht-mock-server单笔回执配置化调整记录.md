# yht-mock-server 单笔回执配置化调整记录

## 1. 目标

在已经支持“九一比例成功失败、失败原因随机生成”的基础上，继续将以下能力做成可配置：

1. 单笔实时交易成功率；
2. 单笔实时交易失败原因池；
3. 页面可视化调整；
4. 配置文件默认值与 `/api/callback-config` 接口联动。

## 2. 本次改动

### 2.1 配置模型扩展

在 `MockCallbackProperties` 中新增：

1. `realtimeTradeSuccessRate`
2. `realtimeTradeFailureReasons`

说明：

- `realtimeTradeSuccessRate` 取值范围按 `0~100` 处理；
- `realtimeTradeFailureReasons` 使用 `错误码|错误描述` 的字符串列表格式，便于 YAML、接口和页面统一处理。

### 2.2 策略类支持读取配置

`MockRealtimeTradeResultPolicy` 不再写死成功率和失败原因池，而是改为：

1. 从 `MockCallbackProperties` 读取成功率；
2. 从 `MockCallbackProperties` 读取失败原因池；
3. 当配置为空或格式非法时，回退到默认失败原因池。

### 2.3 配置接口联动

`MockCallbackPushService.updateProperties(...)` 已支持动态更新：

1. `realtimeTradeSuccessRate`
2. `realtimeTradeFailureReasons`

因此页面保存后，无需重启挡板即可生效。

### 2.4 页面配置入口

`static/index.html` 的“回调模拟”页新增“自动回调配置”卡片，支持：

1. 自动推送开关；
2. 默认目标 URL；
3. 通用延迟；
4. 单笔回调延迟；
5. 单笔成功率滑块；
6. 失败原因池文本框；
7. 各类回调开关。

失败原因池输入格式为：

```text
100001|付款账户余额不足
100002|付款账户状态异常
```

每行一条。

### 2.5 配置文件默认值

在 `application.yml` 中新增：

1. `yht.mock.callback.realtime-trade-success-rate`
2. `yht.mock.callback.realtime-trade-failure-reasons`

便于直接通过配置文件初始化默认策略。

## 3. 验证

执行命令：

```bash
eval "$(vfox env -s bash)" && mvn -Dtest=MockRealtimeTradeResultPolicyTest test
```

验证结果：

- `Tests run: 3`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`
- `BUILD SUCCESS`

守卫扫描：

```bash
scripts/guardrails/agent-delivery-guardrail.sh --scan-only
```

结果：

- 通过，未发现偷懒式占位痕迹。

## 4. 当前效果

现在 `yht-mock-server` 对 `caps.201 -> caps.205` 的模拟能力已经具备两层控制：

1. 默认行为仍可直接按 90% 成功、10% 失败运行；
2. 也可以在页面或配置文件中把成功率改成任意 `0~100`；
3. 失败原因池可以按业务需要扩展或替换；
4. 修改后通过 `/api/callback-config` 即时生效。

## 5. 后续建议

1. 若需要更精细策略，可继续扩展为“按交易代码分别配置成功率”；
2. 若需要更稳定复现失败场景，可增加“强制下一笔失败”开关；
3. 联调时建议保留一组固定失败原因，便于结算侧做错误码映射验证。
