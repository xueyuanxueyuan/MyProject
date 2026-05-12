# Gjj 项目区域差异方法清单提示词

## 1. 使用方式
- 基线目录：`prod/IdeaProjects/capinfo-gjj-busi-jshs`
- 区域目录：`zaozhuang`、`linyi`、`wenzhou`、`jiaxing`（与 `prod` 同级）
- 执行顺序：先定位 `prod` 方法，再定位区域差异方法。

## 2. 差异总览（后端 Java）
- `prod` 方法索引文件数：`703`
- `zaozhuang`：差异文件 `45`，新增文件 `20`，缺失文件 `160`
- `linyi`：差异文件 `59`，新增文件 `9`，缺失文件 `234`
- `wenzhou`：差异文件 `66`，新增文件 `26`，缺失文件 `241`
- `jiaxing`：差异文件 `87`，新增文件 `30`，缺失文件 `91`

## 3. 区域差异方法清单（重点）
### 3.1 zaozhuang
- 高频新增能力：
  - `CwhsServiceImpl` 增补手续费支付申请与凭证附加字段链路（如 `generateSxfyttzPz`、`queryPzfjSummary`、`pageSxfzfsq`）
  - `YwglServiceImpl` 增补贷款资金调拨审批与转账链路（如 `addDkZjdbSq`、`submitDkZjdbSp`、`dkZjdbTransfer`）
  - `ZhglServiceImpl` 增补账户拨付金链路（如 `addZhbfj`、`submitZhbfjSp`）
- 典型减少能力：
  - 一户通 `caps/*` 相关类在区域分支未保留（以文件缺失为主）

### 3.2 linyi
- 高频变更特点：
  - `CwhsServiceImpl` 保留核心记账能力，但移除批量预提调相关方法（如 `batchSaveSxfyttz`、`querySxfyttzList`）
  - `YwglServiceImpl` 保留 `cwzjzfTjsp`，移除钱包签约与部分审批封装（如 `corpWalletSign`、`submitCwzjzfApproval`）
  - `LcglServiceImpl` 精简流程方法（如移除 `saveDqck`、`submitDqck`）
- 区域新增文件以 `subscribe` 订阅类为主（如 `CwzjdbSpwcSub`、`ZhglSub`）。

### 3.3 wenzhou
- 高频变更特点：
  - `CwhsServiceImpl` 新增日终状态与作业调度方法（如 `getSfZdRq`、`rzCwRwZxYcFeedback`）
  - `YwglServiceImpl` 增补导入导出与对账补偿方法（如 `exportPlywSkMx`、`importPlywJgMxFromExcel`、`dqckLxytDsrw`）
  - `DailyProcessServiceImpl` 与 `RzclServiceImpl` 增补失败反馈与自动作业方法
- 典型减少能力：
  - 与临沂类似，移除部分 `batchSaveSxfyttz`、`saveDqck` 旧路径方法。

### 3.4 jiaxing
- 变更规模最大（差异文件最多）：
  - `YwglServiceImpl` 在支付、批量、账户维护侧新增方法最多（如 `pageSkywPlmxCx`、`zjjsZfkWh`、`zjjsZskWh`）
  - `CwhsServiceImpl` 增补记账日期与月末关账相关方法（如 `pageYhckrjz`、`pzJzrqXg`）
  - `ZhglServiceImpl` 增补分页方法（如 `pageZjjsZfk`、`pageZjjsZsk`）
- 典型减少能力：
  - 同样移除部分旧式审批封装与预提调整旧方法。

## 4. 差异定位提示词模板
```markdown
请按“prod基线 + 区域差异”执行：
1. 在 `prod/IdeaProjects/capinfo-gjj-busi-jshs` 定位目标方法；
2. 在 `<region>/IdeaProjects/capinfo-gjj-busi-jshs` 定位同路径方法差异；
3. 输出以下内容：
   - prod 方法签名
   - 区域新增方法
   - 区域移除方法
   - 影响接口/任务
   - 最小回归路径
```

## 5. 数据来源
- 差异原始数据文件：`codeworkfiles/region_method_diff_report.json`
- 说明：本清单基于 Java 方法签名对比自动生成，适用于“快速定位差异”；合并发布前需按实际业务链路再确认一次。
