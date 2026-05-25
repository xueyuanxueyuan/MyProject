# yht-mock-server 单笔回执九一比例模拟调整记录

## 1. 目标

针对 `yht-mock-server` 的单笔实时交易回调 `caps.205.001.01`，将原来的“固定成功”调整为：

1. 90% 概率返回成功；
2. 10% 概率返回失败；
3. 失败时从受控失败原因池中随机挑选失败码与失败原因。

## 2. 原实现问题

原实现位于 `MockCallbackPushService.applyRealtimeTradeFinalResult(...)`，逻辑为固定写死：

- `ResFlag=SUCC`
- `RetCode=000000`
- `RetMsg=交易成功`

这会导致：

1. 挡板无法覆盖失败场景联调；
2. 结算侧只能验证成功回执路径；
3. 统计页成功率会长期是 100%，不能反映真实模拟场景。

## 3. 本次实现

### 3.1 新增策略类

新增 `MockRealtimeTradeResultPolicy`，专门负责单笔实时交易终态决策：

- 抽取“九一比例成功失败”逻辑；
- 抽取失败原因池；
- 统一写回 `MockTrade` 的 `resFlag`、`retCode`、`retMsg`、`returnTime`、`checkDate`。

### 3.2 失败原因池

当前失败原因池如下：

1. `100001 / 付款账户余额不足`
2. `100002 / 付款账户状态异常`
3. `100003 / 银行返回处理超时`
4. `100004 / 单笔交易金额超限`
5. `100005 / 付款账户户名校验失败`

### 3.3 推送链路改造

`MockCallbackPushService` 不再自己决定成功结果，而是改为调用 `MockRealtimeTradeResultPolicy.applyFinalResult(trade)`。

同时增加日志：

- `已生成单笔实时交易终态`

日志中会带出：

- `serialNum`
- `sysSeqNo`
- `resFlag`
- `retCode`
- `retMsg`

这样联调时可以直接看到本次挡板到底是生成成功还是失败。

## 4. 自动化测试

新增测试文件：

- `MockRealtimeTradeResultPolicyTest`

覆盖点：

1. 当桶位命中 `0~8` 时，结果为成功；
2. 当桶位命中 `9` 时，结果为失败；
3. 失败原因索引越界时，仍然会回到受控失败原因池，不会生成池外脏数据。

## 5. 验证命令

```bash
eval "$(vfox env -s bash)" && mvn -Dtest=MockRealtimeTradeResultPolicyTest test
```

验证结果：

- `Tests run: 3`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`
- `BUILD SUCCESS`

## 6. 当前效果

调整完成后，挡板在处理 `caps.201` 后异步推送 `caps.205` 时：

1. 大多数情况下会返回成功；
2. 小概率返回失败；
3. 失败码与失败原因每次会从失败原因池中随机选择；
4. 结算侧可以同时联调成功与失败路径。

## 7. 后续建议

1. 如果后续需要更灵活控制，可把成功比例和失败原因池提为配置项；
2. 可继续补一个集成测试，验证 `MockCallbackPushService` 实际构造出的 `caps.205` 回调报文中会带出随机失败信息；
3. 联调时可多发几笔单笔收款，观察结算侧是否能正确处理成功与失败两条路径。
