# ZjbLhhSyncServiceImpl 全量地区并发分布式改造记录

## 需求确认

- 地区范围：遍历 `zjbSsx.json` 中全部非空 `value`
- 并发方式：分布式锁 + 节点内线程池
- 失败策略：单个地区失败记录后继续，不中断整体任务

## 改造目标

将原“固定城市地区编码同步”升级为“全量地区联行号同步任务”，并保证：

- 同一时刻只允许一个节点执行该任务
- 单节点内通过线程池并发提升同步效率
- 继续复用原有 `wbjkService.zjLhhcxcl` 查询与落库逻辑

## 实现摘要

### 资源位置更正

- 运行期资源文件 `zjbSsx.json` 最终放置于 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-app/src/main/resources/zjbSsx.json`。
- 为兼容 `busi` 模块单测，测试资源文件放置于 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/test/resources/zjbSsx.json`。
- 服务类读取路径已调整为浅路径 `zjbSsx.json`。

### 服务实现

- 文件：`ZjbLhhSyncServiceImpl.java`
- 改动点：
  - 删除按单一城市节点筛选地区编码的逻辑
  - 改为递归解析 `zjbSsx.json` 中全部 `value`
  - 引入 `YhtBusinessKeySupport`，复用现有 Redis 业务键能力做任务级分布式锁
  - 引入 `taskExecutor`，基于 `CompletableFuture.runAsync` 并发执行每个“银行编码 x 地区编码”组合
  - 保留单任务失败只记日志并累计失败数的策略

### 汇总对象

- 文件：`ZjbLhhSyncSummary.java`
- 改动点：
  - 增加自定义消息支持
  - 新增 `skipped(String message)` 工厂方法
  - 支持分布式锁未抢到时返回“已跳过”消息

### 测试调整

- 文件：`ZjbLhhSyncServiceImplTest.java`
- 新增校验：
  - 省市县配置应能正确解析为地市级队列项
  - 分布式锁未获取时任务应直接跳过
  - 获取锁后应按“一个地市队列项 x 全部银行编码”执行

## 关键设计取舍

### 为什么复用 `YhtBusinessKeySupport`

- 当前模块已存在稳定的 Redis 业务键能力
- `acquire/release` 语义已经满足任务级互斥需求
- 避免重复写一套新的 Redis 锁工具，减少维护点

### 为什么复用 `taskExecutor`

- 当前模块已存在统一异步线程池配置
- 线程数、队列、拒绝策略已在模块级集中管理
- 比临时 `Executors.newFixedThreadPool` 更利于后续运维和统一关闭

## 当前已知影响

- 任务总量将从原先单城市范围扩大为全部地区范围
- 若银行编码数量较多，并发请求量会明显提升
- 任务执行耗时与外部接口吞吐能力强相关，后续可按需要再加批次限流

## 2026-06-03 降载优化

### 问题现象

- 全量地区开启后，请求组合数变为“银行编码数 x 2139”
- 原实现会一次性将全部组合提交到 `taskExecutor`
- 在 `CallerRunsPolicy` 和共享线程池配置下，会形成持续请求洪峰，导致下游服务被打崩

### 根因

- 问题不在单次线程数，而在于任务提交模式
- 之前是“先把全部 future 一次性创建并入池，再统一等待完成”
- 这种模式会持续压满线程池和队列，对外部服务没有节奏控制

### 第一轮优化

- 增加 `SYNC_BATCH_SIZE = 30`
- 先将同步请求按小批次拆分
- 每批提交后立即 `join` 等待该批完成，再进入下一批
- 保持分布式锁与单任务失败继续策略不变

### 优化效果

- 显著降低瞬时请求峰值
- 避免一次性向线程池和下游服务注入数千个待执行请求
- 将任务模型从“全量洪峰”调整为“分批稳态推进”

## 2026-06-03 断点续跑优化

### 新问题

- 即使降低瞬时洪峰，单次任务仍需处理全量“银行编码 x 地区编码”组合
- 实际运行反馈显示：半小时一个银行都无法处理完成
- 说明问题已经从“并发过高”升级为“单次全量模型不可用”

### 最终方案

- 保持“始终强制覆盖”策略不变
- 以 `zjbSsx.json` 的“省 -> 地市 -> 区县/县级市”层级构建地市级队列
- 每个队列项包含“地市本级 value + 全部下级区县/县级市 value”
- 引入 Redis 城市游标 `zjb:lhh:sync:daily:cityCursor`
- 每次任务只处理 `1` 个地市队列项
- 当前地市内再按 `SYNC_BATCH_SIZE = 20` 分批执行“全部银行 x 当前地市全部地区编码”
- 当前地市处理完成后，将下一个地市索引写回 Redis；走到末尾后自动回到 `0`

### 方案收益

- 单次任务从“全量全国组合”缩小为“单个地市组合”
- 单次任务耗时可控，不再出现“一轮跑半天一个银行都没结束”
- 保留全量覆盖能力，但改为按地市多轮完成
- 对下游服务压力从“长时间连续轰炸”变为“按地市轮转、固定节奏”

## 验证记录

- `GetDiagnostics`
  - `ZjbLhhSyncServiceImpl.java`：无新增诊断问题
  - `ZjbLhhSyncSummary.java`：无新增诊断问题
  - `ZjbLhhSyncServiceImplTest.java`：无新增诊断问题
- Maven 单测命令：
  - `mvn -f jiaxing/IdeaProjects/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/pom.xml -Dtest=ZjbLhhSyncServiceImplTest test`
  - 当前仍受仓库外部依赖解析阻塞，未能在本地环境完成执行
- 门禁脚本：
  - `scripts/guardrails/agent-delivery-guardrail.sh --scan-only`
  - 执行通过，未发现需要扫描的目标文件

## 后续建议

- 若担心一次性提交过多并发任务，可继续将“全部地区”按批次切片提交到线程池
- 若需要更细粒度的分布式并行，可进一步改造为“按地区分片 + 多节点抢分片”模式
- 若外部接口有频控限制，建议补充并发度参数化配置
