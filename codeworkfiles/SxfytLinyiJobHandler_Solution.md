# 解决思路与步骤文档

## 任务背景
在 `SxfytLinyiJobHandler.java` 类的 `buildPeriodContext` 方法中，原有逻辑会在处理手续费预提时，根据预提参数配置（`jzr=02`，即次日记账）来决定是否要使用当前业务记账日期的前一天（即 `DateUtil.offsetDay(businessDate, -1)`）作为周期截止日参与预提判断。现要求在预提时无需判断记账日期是当日还是次日，直接使用当前记账日期即可。

## 解决思路
1. **移除常量定义**：因为去掉了“次日记账”相关的特殊判断逻辑，类中定义的私有静态常量 `JZR_NEXT_DAY = "02"` 不再被使用，为了保持代码整洁，应一并移除。
2. **修改业务逻辑**：在 `buildPeriodContext` 方法内，将原来含有三元运算符的判断逻辑（判断 `ytsz.getJzr()` 是否等于 `JZR_NEXT_DAY`）替换为直接把传入的 `businessDate` 赋值给 `executeDate`。
3. **清理注释**：去掉关于“次日记账”计算周期的那行不再适用的注释代码，避免对后续阅读代码产生误导。

## 实施步骤
1. **生成对比留痕文件**：修改代码前，提取变动代码对比内容，写入了位于项目 `codeworkfiles` 目录下的 `SxfytLinyiJobHandler_Modification.diff`。
2. **清理常量 `JZR_NEXT_DAY`**：通过文件搜索和替换，将 `private static final String JZR_NEXT_DAY = "02";` 从类成员变量区域中删除。
3. **移除时间偏移判断**：通过文件搜索和替换，将原先的：
   ```java
   // 部分参数配置为“次日记账”，此时需要以前一日作为周期截止日参与预提判断
   Date executeDate = JZR_NEXT_DAY.equals(ytsz.getJzr()) ? DateUtil.offsetDay(businessDate, -1) : businessDate;
   ```
   修改为了：
   ```java
   Date executeDate = businessDate;
   ```
4. **验证代码完整性**：经过确认，`JZR_NEXT_DAY` 没有在当前类的其它方法中引用，代码调整合适，无其他副作用。