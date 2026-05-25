# yht-mock-server 按账号尾号判定单笔结果规则修复记录

## 1. 背景

原挡板 `yht-mock-server` 的单笔实时交易终态逻辑为：

1. 按成功率随机决定成功或失败；
2. 失败时从失败原因池中随机选择原因。

本次需求变更为：

1. 不再按概率决定成功失败；
2. 按交易方向选择账号字段；
3. 账号尾号大于 `6` 判失败；
4. 账号尾号小于等于 `6` 判成功。

用户确认的口径为：

1. `20601` 单笔付款：看 `DbtrActId`
2. `20602` 单笔收款：看 `CdtrActId`

## 2. 设计与实现

### 2.1 单笔终态策略改造

修改 `MockRealtimeTradeResultPolicy`：

1. 删除按成功率随机判定成功失败的逻辑；
2. 新增按交易方向解析判定账号：
   - `20601` 优先取 `DbtrActId`
   - `20602` 优先取 `CdtrActId`
   - 其他情况按 `DbtrActId -> CdtrActId` 回退
3. 从账号末尾向前提取最后一个数字字符；
4. 若尾号：
   - `<= 6`：返回成功
   - `> 6`：返回失败
5. 失败时继续沿用失败原因池，按索引选择具体失败码和失败描述。

### 2.2 配置与页面口径同步

为了避免页面仍显示“成功率”而误导联调，本次同步调整：

1. 移除 `MockCallbackProperties` 中的 `realtimeTradeSuccessRate`
2. 移除 `MockCallbackPushService.updateProperties(...)` 对成功率的接收
3. 修改 `application.yml` 注释为账号尾号判定规则
4. 修改 `index.html`：
   - 去掉“单笔成功率(%)”滑块
   - 改为显示固定规则说明

## 3. 测试策略

按 TDD 执行：

### 3.1 RED

先修改 `MockRealtimeTradeResultPolicyTest`，新增并调整以下断言：

1. `20601 + DbtrActId` 尾号 `6` 时成功
2. `20602 + CdtrActId` 尾号 `7` 时失败
3. 边界值 `6` 对收款也判成功
4. 失败原因池非法时仍回退默认失败原因池

第一次执行后，测试按预期失败，证明旧逻辑仍是概率策略。

### 3.2 GREEN

完成策略实现后，重新执行测试，全部通过。

## 4. 影响范围

本次只影响 `yht-mock-server` 单笔实时交易终态模拟逻辑：

1. `caps.205` 自动推送前的结果生成
2. 控制台自动回调配置页面的规则展示

不影响：

1. 批量回调
2. 协议回调
3. 人工手动推送回调接口

## 5. 验证命令

```bash
cd /home/source/Jetbrains/Probject/Gjj/yht-mock-server
source ~/.bashrc >/dev/null 2>&1 || true
vfox use java@17.0.2+8
vfox use maven@3.9.14
eval "$(vfox env -s bash)"
mvn -Dtest=MockRealtimeTradeResultPolicyTest \
  -Dmaven.test.skip=false \
  -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DfailIfNoTests=false \
  test
```

## 6. 结果

当前单笔实时交易结果判定已变为：

1. `20601` 看 `DbtrActId` 尾号
2. `20602` 看 `CdtrActId` 尾号
3. 尾号大于 `6` 失败
4. 尾号小于等于 `6` 成功  
