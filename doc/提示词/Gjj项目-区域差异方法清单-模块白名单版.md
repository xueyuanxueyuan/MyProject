# Gjj 项目区域差异方法清单（模块白名单版）

## 1. 白名单范围
- 仅保留以下三块高频模块：
  - `capinfo-gjj-busi-zjjs-ywgl`
  - `capinfo-gjj-busi-cwhs-jzgl`
  - `capinfo-gjj-busi-zjjs-zhgl`
- 其余模块不在本清单中展开，需时再回查全量清单。

## 2. 区域差异规模（白名单内）
- `zaozhuang`：白名单差异文件 `21`（全量差异 `45`）
- `linyi`：白名单差异文件 `32`（全量差异 `59`）
- `wenzhou`：白名单差异文件 `31`（全量差异 `66`）
- `jiaxing`：白名单差异文件 `29`（全量差异 `87`）

## 3. 区域重点差异
### 3.1 zaozhuang
- `cwhs-jzgl`：`CwhsServiceImpl` 大量新增手续费支付申请与附加字段方法（例如 `generateSxfyttzPz` 链路）。
- `zjjs-ywgl`：`YwglServiceImpl` 增加贷款资金调拨链路（如 `addDkZjdbSq`、`submitDkZjdbSp`、`dkZjdbTransfer`）。
- `zjjs-zhgl`：`ZhglServiceImpl` 增加账户拨付金能力（如 `addZhbfj`、`submitZhbfjSp`）。

### 3.2 linyi
- `cwhs-jzgl`：显著移除预提与汇总旧方法（如 `batchSaveSxfyttz`、`querySxfyttzList`、`getNextPzCrossDay`）。
- `zjjs-ywgl`：移除钱包签约与部分审批封装（如 `corpWalletSign`、`submitCwzjzfApproval`），保留 `cwzjzfTjsp`。
- `zjjs-zhgl`：`ZhglServiceImpl` 仅少量新增（`queryDwjgByDshm`、`updateZskFpjg`）。

### 3.3 wenzhou
- `cwhs-jzgl`：新增日终自动化相关能力（如 `executeRzclStepAuto`、`getSfZdRq`、`rzCwRwZxYcFeedback`）。
- `zjjs-ywgl`：新增导入导出与对账补偿（如 `exportPlywSkMx`、`importPlywJgMxFromExcel`、`dqckLxytDsrw`）。
- `zjjs-zhgl`：以小幅增强为主，含单户同步相关方法。

### 3.4 jiaxing
- `zjjs-ywgl`：变更最重，新增批量对账与账户维护链路（如 `pageSkywPlmxCx`、`zjjsZfkWh`、`zjjsZskWh`）。
- `cwhs-jzgl`：新增记账日期调整与日终链路（如 `pageYhckrjz`、`pzJzrqXg`、`executeRzclStepAuto`）。
- `zjjs-zhgl`：新增分页能力（如 `pageZjjsZfk`、`pageZjjsZsk`）。

## 4. 快速执行模板
```markdown
按模块白名单差异执行：
1. 先在 `prod` 命中以下三模块目标方法：
   - `zjjs-ywgl` / `cwhs-jzgl` / `zjjs-zhgl`
2. 再到 `<region>` 命中同路径方法差异；
3. 输出：
   - prod 方法
   - 区域新增方法
   - 区域移除方法
   - 最小回归路径（接口 + 数据 + 任务）
```

## 5. 数据来源
- 白名单差异原始数据：`codeworkfiles/region_module_whitelist_diff.json`
- 全量差异原始数据：`codeworkfiles/region_method_diff_report.json`
