# 20260604-YhtSceneParamResolver与收付款内部参数配置化清单

## 1. 本轮目标

在已经完成外层 DTO 收口的基础上，继续梳理一户通收付款与签约链路中仍然散落的内部参数，明确：

1. 哪些参数应该正式配置化
2. 哪些参数应继续保留为代码级场景常量
3. 哪些默认值不应继续以硬编码兜底方式存在

## 2. 本轮复核范围

本轮重点复核了以下对象：

1. `YhtSceneParamResolver`
2. `YhtYwServiceImpl`
3. `YhtServiceImpl`
4. `YhtProtocolRequestOrchestrator`
5. `YhtRealtimeStatusQueryOrchestrator`
6. `YhtBatchStatusOrchestrator`
7. `YhtFeeMappingServiceImpl`
8. `YhtCapsProperties`
9. `application.yml`

## 3. 当前真实现状

### 3.1 已配置化的参数

当前已经有正式配置来源的参数只有：

1. `corpNo`

其来源为：

1. `application.yml` 中的 `yht.caps.corp-no`
2. `YhtCapsProperties.corpNo`
3. `YhtServiceImpl`、`YhtProtocolRequestOrchestrator` 等运行时读取

结论：

`corpNo` 当前不属于“未配置化遗留项”，只是在未来多企业号场景下可能需要从“单值配置”升级为“场景/机构映射配置”。

### 3.2 已集中但仍硬编码的参数

`YhtSceneParamResolver` 当前集中维护了以下硬编码值：

1. `DEFAULT_FEE_NO = "00001"`
2. `DEFAULT_SIGN_DSJGBM = "0001"`
3. `REALTIME_PAY_TRAN_CODE = "20601"`
4. `REALTIME_COLLECT_TRAN_CODE = "20602"`
5. `BATCH_PAY_BIZ_TYPE = "PAY"`
6. `BATCH_COLLECT_BIZ_TYPE = "COLLECT"`

这些值已经比之前“散落在业务服务里”更好，但还没有区分哪些应该配置化，哪些只需要集中常量化。

### 3.3 仍然散落在编排器或查询链路中的默认值

当前还有一批默认值没有收敛进统一策略，主要包括：

1. `YhtProtocolRequestOrchestrator` 中协议查询/解约默认 `feeNo = "00000"`
2. `YhtRealtimeStatusQueryOrchestrator` 中实时状态查询默认：
   - `tranCode = "20602"`
   - `feeNo = "00000"`
3. `YhtBatchStatusOrchestrator` 中批量状态查询/结果提回默认：
   - `tranCode = "40502"`
   - `feeNo = "00000"`
4. `YhtBatchDispatchBoundaryServiceImpl`、`YhtBatchTradeMessageBuilder`、`Caps303Builder` 等位置对 `00000` 的重复兜底

### 3.4 仍未配置化的业务映射

当前最核心的业务映射遗留是：

1. `dsjgbm -> feeNoList`

其实现仍在 `YhtFeeMappingServiceImpl` 的静态 `Map` 中，当前内容为：

1. `0001 -> FEE001,FEE002`
2. `0002 -> FEE003,FEE004,FEE005`
3. `0003 -> FEE006`

该映射已在类注释中明确说明“后续可从数据库或配置表加载”，说明这是典型待配置化项。

## 4. 参数分类结论

### 4.1 必须配置化

以下参数建议进入正式配置模型：

1. `dsjgbm -> feeNoList`
2. 收付款/协议上传场景的默认 `feeNo = "00001"`
3. 签约场景默认 `dsjgbm = "0001"`

原因：

1. 这些值不是 CAPS 协议的固定语义，而是当前项目的业务接入口径
2. 它们可能随机构、渠道、业务子类或联调环境变化
3. 继续写死会把业务规则固化在代码里，后续变更成本高

### 4.2 应保留为代码级场景常量

以下参数更适合保留为代码级常量或枚举，不建议配置化：

1. 实时付款 `tranCode = "20601"`
2. 实时收款 `tranCode = "20602"`
3. 批量付款 `bizType = "PAY"`
4. 批量收款 `bizType = "COLLECT"`
5. 批量付款 `tranCode = "40501"`
6. 批量收款 `tranCode = "40502"`

原因：

1. 这些值本质是 CAPS 协议定义下的场景标识
2. 它们不是本项目特有的“业务映射规则”
3. 把协议常量做成业务配置，容易让系统进入“配置能覆盖协议语义”的错误状态

建议做法：

1. 继续保留常量/枚举
2. 但统一归拢到场景解析器、枚举或常量类，不再散落在多个编排器/处理器里

### 4.3 不应继续存在“拍脑袋兜底”

以下默认值不建议继续在查询/补查链路中直接写死兜底：

1. 实时查询默认 `tranCode = "20602"`
2. 批量查询默认 `tranCode = "40502"`

原因：

1. 查询场景本应优先从请求、任务记录或业务上下文中恢复真实交易类型
2. 用“默认按收款/代收兜底”容易把付款任务查成收款任务
3. 这类默认值不属于可配置业务规则，而是错误的查询降级策略

建议做法：

1. 优先使用请求参数
2. 其次使用任务表已落库值
3. 若仍为空，返回明确错误或进入补偿定位，而不是硬编码兜底成某个交易码

### 4.4 可以保留的协议语义默认值

以下值短期内可以继续保留，但建议集中管理：

1. 协议整单查询默认 `feeNo = "00000"`

原因：

1. 该值更像 CAPS 查询协议里的固定语义值
2. 它不是业务机构差异配置
3. 当前多处重复写 `00000`，问题主要在“散落重复”，不是“是否可配置”

建议做法：

1. 抽成统一常量，例如 `YhtProtocolConstants.QUERY_ALL_FEE_NO`
2. 查询编排器、Builder、补偿逻辑统一复用

## 5. 最小配置化实施点

### 5.1 第一优先级

优先落地以下能力：

1. 为 `YhtSceneParamResolver` 增加配置属性承载类，例如：
   - `YhtSceneParamProperties`
2. 将以下值从硬编码迁出到配置：
   - 默认 `feeNo`
   - 默认签约 `dsjgbm`
3. 为 `YhtFeeMappingServiceImpl` 提供配置驱动版本，替换静态 `Map`

这一阶段不改协议常量，只改业务默认值和业务映射。

### 5.2 第二优先级

统一查询与补查链路中的默认值策略：

1. 清理 `YhtRealtimeStatusQueryOrchestrator` 中 `20602` 的兜底
2. 清理 `YhtBatchStatusOrchestrator` 中 `40502` 的兜底
3. 保留 `00000` 这类协议语义值，但改为统一常量

### 5.3 第三优先级

在未来多机构/多企业号场景下，再考虑：

1. 将 `corpNo` 从 `yht.caps.corp-no` 单值配置升级为映射配置
2. 将场景参数模型进一步抽象为：
   - `scene`
   - `jsqd`
   - `jbjgbh`
   - `bizSubType`
   - `corpNo`
   - `feeNo`
   - `feeNoList`
   - `dsjgbm`

## 6. 建议配置模型

若只做最小可用版本，建议先从配置文件承载，而不是立刻上数据库表：

```yaml
yht:
  scene:
    defaults:
      fee-no: "00001"
      sign-dsjgbm: "0001"
    fee-mapping:
      "0001": ["FEE001", "FEE002"]
      "0002": ["FEE003", "FEE004", "FEE005"]
      "0003": ["FEE006"]
```

这样可以先完成：

1. 去硬编码
2. 环境间差异化
3. 后续无痛迁移到 Nacos 或配置表

## 7. 风险边界

本轮结论里最需要避免的误区有两个：

1. 不要把协议固定交易码也做成业务可配置项
2. 不要继续保留“查不到就默认按收款/代收”的查询兜底

因此后续实施应遵循：

1. 业务映射配置化
2. 协议语义常量化
3. 错误兜底显式失败化

## 8. 本轮结论

一句话总结：

当前一户通内部参数里，真正需要配置化的是“业务映射和值域默认值”，不是 CAPS 协议本身；而当前最优先处理的对象，是 `YhtSceneParamResolver` 中的 `DEFAULT_FEE_NO`、`DEFAULT_SIGN_DSJGBM` 和 `YhtFeeMappingServiceImpl` 的静态 `dsjgbm -> feeNoList` 映射，其次才是查询编排器中不合理的交易码默认兜底。
