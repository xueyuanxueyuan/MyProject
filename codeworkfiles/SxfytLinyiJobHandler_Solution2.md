# 解决思路与步骤文档

## 任务背景
在 `SxfytLinyiJobHandler.java` 类的 `buildPeriodContext` 方法中，需要修改计算周期的截止日期：
1. **统一截至日**：原本各种周期（月、季、年）的截止日期都是“执行日”本身，现要求“计算周期截至日为月末最后一天”（对月为当月末，季为季末即该季最后一月的月末，年为年末即十二月月末）。
2. **执行日期校验**：判断当前执行日期必须大于计算周期的截至日。只有当执行日期（`businessDate`）超过该计算周期的截止日时，才允许执行当前预提逻辑。

## 解决思路
1. **分别重构周期计算**：
   - **月预提**：直接使用 `DateUtil.endOfMonth(executeDate)` 取当月最后一天。
   - **季预提**：使用 `Calendar` 计算本季度的最后一天，即季度开始月往后推3个月，再减去1天，并使用 `DateUtil.endOfDay` 将时分秒置为 `23:59:59`。
   - **年预提**：使用 `Calendar` 设置到当年的 12 月 31 日，同样使用 `DateUtil.endOfDay` 置为一天末尾。
2. **新增校验逻辑**：
   - 在构建好 `PeriodContext` 后，统一增加 `businessDate.compareTo(context.getEndDate()) <= 0` 的校验。
   - 如果 `businessDate` 未超过 `endDate`，直接返回 `null`，不再执行当期预提。
   - 因为 `endDate` 的时间部分会被设置为 `23:59:59`，而 `businessDate` 为 `00:00:00`，这种严格时间对比能够非常精确地阻挡同一天（即 `<= 0`）内的提前或当期触发，只允许次日及以后（`> 0`）的执行请求。

## 实施步骤
1. **生成对比留痕文件**：提取本次更改的代码对比差异，写入项目根目录 `codeworkfiles` 文件夹下的 `SxfytLinyiJobHandler_Modification2.diff`。
2. **调整代码逻辑**：
   - 提取原本分散的 `return new PeriodContext(...)` 逻辑，改由变量 `context` 统一接收。
   - 逐一补全了月、季、年的 `endDate` 计算逻辑，将原先传入 `executeDate` 的地方替换为真实的周期末尾日。
   - 在函数最后补充 `if (businessDate.compareTo(context.getEndDate()) <= 0) { return null; }` 防御性检查。
3. **验证代码与保存**：检查无多余无用逻辑，方法边界清晰，符合任务诉求。