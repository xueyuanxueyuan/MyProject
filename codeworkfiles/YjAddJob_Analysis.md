# CwhsController.addYj 接口定时任务调用深度分析与实现

## 1. 接口深度分析

通过分析 `cn.capinfo.gjj.busi.cwhs.jzgl.busi.controller.CwhsController` 中的 `addYj` 方法，以及底层的 `CwhsServiceImpl.addYj` 实现，我们可以得出以下结论：

### 1.1 核心入参与依赖
- **`YjAddReqDTO`**: 这是月结增加请求的核心参数对象，其中关键的校验字段（使用了 `@NotBlank`）包括：
  - `qdbm`: 渠道编码
  - `yjny`: 月结年月
  - `ztbh`: 账套编号
  - `zxbh`: 中心编号（由 Controller 补充注入）
- **`CurrentUser<InnerUser> currentUser`**: 通过 `@Current` 注解在 Controller 层自动注入的当前上下文用户信息对象。

### 1.2 Service 层对用户信息的依赖链路
在 `CwhsServiceImpl.addYj` 的实现中，`currentUser` 并未被简单透传，而是深度参与了业务核心逻辑：
1. **获取内部用户对象**：`InnerUser innerUser = currentUser.getUserDetail();`
2. **操作人名称写入**：`yj.setCreaterName(innerUser.getName());`
3. **渠道编码与权限读取**：在内部方法 `addYmjzpz(yjAddReqDTO, currentUser, phjcHzReqDTO)` 中，不仅使用了 `innerUser.getQdbm()` 构造凭证信息，还在生成月末结转凭证时将 `currentUser` 传递到了 `ymjzpz` 凭证生成接口中。

### 1.3 定时任务调用的核心难点
在 XXL-JOB 等定时任务执行环境下，**由于不存在 HTTP 请求，`@Current` 注解无法自动拦截并组装 `CurrentUser` 上下文**。如果直接调用 Service，传入 `null` 会导致后续流程中的 `getUserDetail().getName()` 抛出空指针异常 (NPE)。

---

## 2. 解决思路与用户信息模拟方案

要在定时任务中完美复用 `CwhsServiceImpl.addYj` 的核心逻辑，必须在定时任务（`JobHandler`）内部进行**用户信息的有效模拟**以及**业务参数的动态计算**。

### 2.1 模拟 CurrentUser 注入
为了让 Service 层能正常获取到操作人信息（如记录日志、写入 `creator` 或 `createrName` 等），我们需要手工构建一个后台操作专用的 `CurrentUser` 对象：
- **`InnerUser` 的构建**：可以实例化 `InnerUser` 对象。通过分析 `CwhsServiceImpl` 中的代码，月结等定时生成的凭证会大量使用到用户的 `username`（或 `id`）以及 `name`（例如通过 `getNameByUsername` 查询真实姓名，或直接使用 `name` 字段赋值给 `createrName`）。
- **借助系统枚举**：可以使用项目中预定义的 `AutoUser` 枚举（如 `AutoUser.AUTO1` 代表“系统自动生成”），将其 `code` 赋给用户的 `username`/`id`，将 `value` 赋给 `name`。
- **渠道信息补充**：为其赋予默认或可配置的渠道编码（`qdbm`），如 `01`。
- **包裹进 CurrentUser**：通过 `currentUser.setUserDetail(innerUser)` 建立包装关系。

### 2.2 业务参数 (YjAddReqDTO) 的动态生成
定时任务不同于前端人工点击，其执行参数需要自包含或自计算：
- **账套与中心编号 (ztbh / zxbh)**：系统中有多个账套，且状态可能会发生变化。我们需要通过 `ZtszDmService` 查出当前中心（如 `Constants.HSDWBH`）下所有处于**“审批通过”**状态（`SpztEnum.SPZT_SPTG`）的生效账套（`ZtszDO`），然后在定时任务中通过循环来对每个账套进行月结操作。
- **月结年月 (yjny)**：通过查询每个账套对应的 `ZtsxDO` (账套属性表)，获取其当前的 `yjny`（当前月结年月），利用日期工具类计算其下个月的年月（例如从 `2026-03` 计算出 `2026-04`）。
- **防超前月结拦截**：要求计算出来的下个月结年月**不能大于等于当前的真实年月**（即不能提前进行未来的月结，或者当月还没过完就进行了月结），如果不满足此条件，则跳过该账套的本次执行。
- **月结前预提凭证检查**：在正式发起月结操作前，根据当前账套对应的结算资金类型(`jszjlx`)去 `YtszDmService`（预提设置表）查询所有生效的预提参数。根据预提周期(`ytzq`: 01年/03季/04月)判断当前月结年月对应的月末是否需要进行预提。
  如果需要预提，则去 `PzDmService`（凭证表 `cwhs_pz`）根据**业务类型 (`ywlx`)** 和**记账日期范围（当前月结年月对应的月初至月末）**查询是否存在**已记账/已汇总**（`jzbj` in `('jz', 'hz')`）的凭证记录，如果没有，则不允许进行该账套的月结。
  其中，预提类型与业务类型（`ywlx`）对应关系为：
  - `01`住房公积金：对应凭证 `ywlx = '11110'` (预提利息)
  - `02`手续费：对应凭证 `ywlx in ('sxfyt_gd', 'sxfyt_gj')`
  - `03`定期存款：对应凭证 `ywlx in ('67', '67_02')`
  - `04`国家债券：跳过检查

---

## 3. 定时任务落地步骤

1. **确定落盘位置**：基于项目开发规范，在对应业务模块的定时任务专用目录（`cn.capinfo.gjj.busi.cwhs.jzgl.busi.service.bean`）下创建新的 `JobHandler`。
2. **编写 YjAddJobHandler**：
   - 增加 `@XxlJob("yjAddJobHandler")` 注解以便调度中心发现。
   - 使用 `ZtszDmService` 查出所有 `spzt` 为审批通过的 `ZtszDO`。
   - 遍历每个账套，使用 `ZtsxDmService` 查出账套属性，取出 `yjny`，计算下个月年月。
   - 判断：`nextYjny.compareTo(currentRealYjny) >= 0` 时，使用 `continue` 跳过。
   - 预提检查：如果是国家债券(`04`)则跳过；根据 `ytzq` 类型(`01/03/04`)判断是否命中当前 `month`，获取当月月初和月末日期，将预提类型`ytlx`映射为实际凭证业务类型集合，去 `cwhs_pz` 校验是否存在 `ywlx` 包含在映射集合内且 `jzbj` 在 `('jz', 'hz')` 范围内的已记账凭证。
   - 实例化 `InnerUser` 和 `CurrentUser` 并赋值 `AutoUser` 系统常量。
   - 组装 `YjAddReqDTO` 并调用 `cwhsService.addYj`。
   - 根据循环的结果，调用 `XxlJobHelper.handleSuccess` 或 `handleFail` 将汇总的执行状态同步回调度中心。

> **详细的代码实现见：** `/home/source/Jetbrains/Probject/Gjj/prod/IdeaProjects/capinfo-gjj-busi-jshs/capinfo-gjj-busi-cwhs-jzgl/capinfo-gjj-busi-cwhs-jzgl-basic-svc-busi/src/main/java/cn/capinfo/gjj/busi/cwhs/jzgl/busi/service/bean/YjAddJobHandler.java`