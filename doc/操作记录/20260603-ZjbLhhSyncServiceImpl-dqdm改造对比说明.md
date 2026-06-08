# ZjbLhhSyncServiceImpl dqdm 改造对比说明

## 更正说明

- 已撤销通过修改 `pom.xml` 打包 `json` 资源的中间方案。
- 当前最终方案为：运行期资源放在 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-app/src/main/resources/zjbSsx.json`。
- 为兼容 `busi` 模块测试，额外放置测试资源 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/test/resources/zjbSsx.json`。

## 需求背景

将 `ZjbLhhSyncServiceImpl` 中硬编码的 `dqdm` 数组改为从 `zjbSsx.json` 中读取，避免地区编码维护分散。

## 改动前

### 目标文件

- `jiaxing/IdeaProjects/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/main/java/cn/capinfo/gjj/busi/zjjs/ywgl/busi/wbjk/service/impl/ZjbLhhSyncServiceImpl.java`

### 现状

```java
private final String[] DQDM = {"3330","3332","3333","3334","3335","3336","3337","3338","3339"};
```

- 问题 1：地区编码硬编码在服务类中，后续维护需要改代码。
- 问题 2：`zjbSsx.json` 已存在同源配置，但当前逻辑未复用。
- 问题 3：`zjbSsx.json` 位于 `src/main/java`，默认未显式声明为模块资源，存在运行期读取不到的风险。

## 改动后方案

### 代码层

- 新增 `zjbSsx.json` 加载逻辑。
- 从 JSON 中提取“温州市”节点的城市值与区县值，生成同步任务需要的 `dqdm` 列表。
- 增加加载失败时的异常说明，避免静默使用错误数据。

### 资源层

- 运行期 `zjbSsx.json` 放入 `app` 模块根资源目录，保证应用打包后可直接通过类路径读取。
- 测试期 `zjbSsx.json` 放入 `busi` 模块 `src/test/resources`，保证单测类路径可读取同名资源。

### 测试层

- 先增加 JSON 解析失败用例，验证当前实现尚不支持从配置读取。
- 再补充解析成功和同步调用用例，确保地区编码来源切换后行为正确。

## 预计影响范围

- `ZjbLhhSyncServiceImpl`
- `ZjbLhhSyncServiceImplTest`
- `capinfo-gjj-busi-zjjs-ywgl-basic-svc-app/src/main/resources/zjbSsx.json`
- `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/test/resources/zjbSsx.json`

## 风险与控制

- 风险：JSON 资源放错模块或路径过深，会导致运行期与测试期类路径不一致。
- 控制：运行期资源放在 `app` 模块根资源目录，测试期资源放在 `busi` 模块测试资源目录，不再依赖自定义 `pom.xml` 资源规则。

## 实际实施结果

- 已将 `ZjbLhhSyncServiceImpl` 中的硬编码 `dqdm` 数组替换为 `loadSyncRegionCodes()` 懒加载逻辑。
- 已在解析逻辑中从 `zjbSsx.json` 提取“温州市”节点主值 `3330` 及其子节点值 `3332-3339`。
- 已将运行期 `zjbSsx.json` 迁移到 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-app/src/main/resources/zjbSsx.json`。
- 已补充测试资源 `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/src/test/resources/zjbSsx.json`。
- 已重写 `ZjbLhhSyncServiceImplTest`，覆盖 JSON 解析结果和同步任务请求组合校验。

## 验证记录

- `GetDiagnostics`：`ZjbLhhSyncServiceImpl.java` 无新增诊断错误。
- `scripts/guardrails/agent-delivery-guardrail.sh --scan-only`：执行通过，未发现需要扫描的目标文件。
- `mvn -f jiaxing/IdeaProjects/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/pom.xml -Dtest=ZjbLhhSyncServiceImplTest test`：
  因仓库 Maven 镜像配置被 `maven-default-http-blocker` 拦截，且缺少 `com.tienon:refbdcutil:1.0`、`cn.capinfo.framework:cipher:1.0` 等依赖，未能完成单测执行。

## 结论

- 功能改造已完成。
- 当前剩余阻塞为仓库外部依赖解析问题，不是本次代码改动引入的编译错误。
