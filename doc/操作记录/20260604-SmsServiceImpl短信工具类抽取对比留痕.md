# SmsServiceImpl 短信工具类抽取对比留痕

## 1. 任务背景

- 目标文件：`SmsServiceImpl`
- 目标动作：将其中可复用的短信报文拼装与信息交换平台调用能力抽取到 `core-common/utils` 下，供结算核算服务复用。
- 约束要求：原有 `SmsServiceImpl` 保留，不删除现有入口方法。

## 2. 现状分析

### 2.1 当前实现包含的职责

`SmsServiceImpl` 当前同时承担了以下职责：

1. 读取配置并遍历接收人列表；
2. 组装短信发送请求报文；
3. 调用 `LshTools` 生成批次号；
4. 调用 `XxjhTools` 访问信息交换平台；
5. 解析平台返回结果并抛出业务异常。

### 2.2 不能直接整体搬迁的原因

当前短信报文依赖以下 `zhgl` 模块内实体：

- `SmsBody`
- `ReadySendMessage`
- `CustomMessageTemplate`

上述类型位于 `capinfo-gjj-busi-zjjs-zhgl-basic-svc-busi` 模块，若直接搬迁到 `core-common`，会导致公共模块反向依赖业务模块，不符合模块边界。

## 3. 抽取方案

### 3.1 抽取内容

抽取为公共短信工具类，沉淀以下能力：

1. 生成短信批次号；
2. 按通用 `JSONObject` 结构组装短信发送报文；
3. 调用信息交换平台短信接口；
4. 统一解析返回值并保留异常语义。

### 3.2 保留内容

`SmsServiceImpl` 继续保留：

1. `sendBatchSms(YhzhxxYeCheckProperties properties, String content)` 业务入口；
2. 原类中的私有方法签名；
3. 原有日志与调用流程骨架。

其中私有方法将改为委托公共工具类执行，以保证原调用方不受影响。

## 4. 计划改动文件

### 4.1 新增

- `core-common/utils/SmsSendUtils.java`
- `core-common/src/test/.../SmsSendUtilsTest.java`

### 4.2 修改

- `core-common/pom.xml`
- `SmsServiceImpl.java`

## 5. 供结算核算服务复用方式

结算核算服务后续可直接注入公共短信工具类，调用公共方法完成：

1. 短信批次号生成；
2. 短信报文拼装；
3. 信息交换平台调用。

无需再复制 `SmsServiceImpl` 的私有实现。
