---
name: "spring-bean-injection-triage"
description: "多模块 Spring Boot 应用启动期 Bean 注入失败（UnsatisfiedDependencyException / BeanCreationException / Injection of resource dependencies failed）的根因定位流程。以 javap 反编译字节码 + 本地 Maven 仓库 jar 为事实源，逐步排除 bean 缺失、bean 名冲突、组件扫描越界（Feign fallback / @Component）三类真因。适用于 Gjj 各微服务（jiaxing / zaozhuang / prod/IdeaProject/*）。"
---

# Spring Bean 注入失败三层定位法

用于 Spring Boot 启动时出现下列异常但**最内层 Caused by 被日志截断**的场景：

```
UnsatisfiedDependencyException: Error creating bean with name 'xxxController':
  Unsatisfied dependency expressed through constructor parameter N:
    Error creating bean with name 'xxxServiceImpl': Injection of resource dependencies failed
```

## 何时使用

- 应用 `Application run failed`，异常栈是一层层 `UnsatisfiedDependencyException` 套娃。
- 用户粘贴的日志末尾被 `... N common frames omitted` 吃掉，看不到真正的 `NoSuchBeanDefinitionException`。
- 注入字段是 `@Resource` / `@Autowired` 的 **FeignClient**（最常见），也可能是普通 Service / Mapper。

## 核心原则

1. **源码会撒谎、字节码不会**：IDEA 里看得到源码不代表运行时 jar 里有对应的 bean 定义。一切结论必须以 `javap` 反编译产物或本地 Maven 仓库 jar 为准。
2. **先穷举再定罪**：把所有注入字段一次性列全（用 `javap -p` 取运行时字段签名），逐个别名点名核对，不要凭第一眼印象下结论。
3. **Windows 路径铁律**：`javap` / `java` 只认 `C:/...` 盘符路径，`/c/Users/...` 这类 Git Bash 路径一律报 `找不到类`，别把"类不存在"误判成"依赖缺失"。
4. **别让管道吞退出码**：`javap ... | grep ... | head` 会吞掉真实错误；查不到东西时，先去掉管道看原始输出。
5. 静态分析到顶就要**如实停手**，宣告"需运行时验证"，不许臆造结论。

## 标准流程

### 第 0 步：锁定最内层受害者

从异常栈顶往下读：`xxxController → xxxServiceImpl(构造器参数 N) → yyyServiceImpl: Injection of resource dependencies failed`。
真正的炸点在 **`yyyServiceImpl`**（最后那个 `Injection of resource dependencies failed` 的 bean），找它，而不是最外层 Controller。

### 第 1 步：清点受害者注入了什么

```bash
grep -nE "@Resource|@Autowired|private .*;|@Service|@RequiredArgsConstructor" <受害者>.java
```
若是构造器注入，改看构造器签名。

### 第 2 步：区分"本模块"与"外部 jar"

- 本模块：组件扫描范围天然覆盖，只需看有没有对应 `@Component/@Service`。
- **外部 jar**：按 import 的类全限定名去本地 Maven 仓库找 artifact。重点怀疑对象就此诞生——因为它们的依赖包通常不在本服务扫描范围内。

### 第 3 步：javap 核验外部类的注解（关键动作）

```bash
# 注意：必须是 C:/ 盘符路径
jar="C:/Users/<user>/.m2/repository/<group路径>/<artifact>/<version>/<artifact>-<version>.jar"
unzip -l "$jar" | grep -i client          # 先确认 jar 里到底有没有这个类
javap -v -cp "$jar" <类全限定名> | sed -n '1,45p'
```

看两类东西：
- **是否有 `@FeignClient` / `@Component` 等注册型注解**（没注解 = 永远不会有 bean）。
- **注解里的 `value/name/contextId/url/fallback`**：
  - `contextId` 重复 → bean 名冲突。
  - `url = "${xxx:}"` 且配置为空 → 找负载均衡器失败。
  - **有 `fallback` / `fallbackFactory`** → 高危，见第 5 步。

### 第 4 步：核对启动类的登记与扫描范围

```bash
javap -v -cp <app模块>/target/classes <启动类全限定名> | grep -A80 "EnableFeignClients\|ComponentScan"
```
同时对比 `target/classes` 时间戳与源码修改时间——**源码改了但没重编译**是经典假象陷阱。

重点看 `@SpringBootApplication(scanBasePackages=...)` 或显式 `@ComponentScan`：外部 jar 里的 Fallback、配置类如果包名不在这个范围里，**扫不进来，等于不存在**。

### 第 5 步：判断 fallback 会不会真的被解析

光有 `fallback` 不一定炸——取决于 targeter：
- classpath 有 `spring-cloud-starter-circuitbreaker-resilience4j` 或 sentinel starter → `FeignCircuitBreakerTargeter`，**fallback 会被当 bean 去容器里取**，取不到就炸。
- 没有熔断器 → `DefaultTargeter`，不解析 fallback，不炸。

查法：
```bash
grep -oE "<artifactId>[^<]+</artifactId>" <feign starter 的 pom> | sort -u
```
顺着 `capinfo-gjj-basic-dependencies` → `capinfo-gjj-feign-basic-stater` 这类自研 starter 一路递归挖。

### 第 6 步：验证 Fallback 类可否被手工注册

```bash
javap -cp "$jar" <Fallback全限定名> | grep -E "^public|^class|^final|abstract"
```
`public class` + public 无参构造器 → 可以 `new` 出来注册成 `@Bean`。

## 修法三选一（须由用户拍板，不得擅自决定）

| 方案 | 做法 | 代价 |
|---|---|---|
| A 注册 Fallback Bean（推荐） | 本模块 config 包加 `@Bean return new XxxFeignClientFallback();` | 保留熔断降级，不动扫描范围 |
| B 关闭 Feign 熔断 | `spring.cloud.openfeign.circuitbreaker.enabled: false` | 最快，但本服务全部 Feign 降级能力失效 |
| C 放开扫描范围 | 扩大 `scanBasePackages` | 会把下游服务实现类装配进本服务，污染严重，一般不用 |

## 排障踩坑清单

- `application.yml` 含数据库/Redis 凭据时，直接 `Read` 会被安全策略拦截 → 改用 `Grep -n "^spring:"` 等行级检索定位插入点，改完用 `git diff` 复核。
- 改配置前先 `cp application.yml application.yml.bak-<日期>`；项目铁律是"备份优先 + 不新建覆盖"。
- 改完 YAML 必须做语法校验：`python -c "import yaml;d=yaml.safe_load(open(p,encoding='utf-8'));print(d['spring']['cloud']['openfeign'])"`（缩进错一级，Spring 静默忽略配置，你以为改了其实没改）。
- Maven 走 `.vfox` 符号链接时 `mvn` 脚本常解析 MAVEN_HOME 失败；可用 Java 直接拉起：
  `java -classpath "<MH>/boot/plexus-classworlds-*.jar" -Dclassworlds.conf="<MH>/bin/m2.conf" -Dmaven.home="<MH" -Dmaven.multiModuleProjectDirectory=<工程目录> org.codehaus.plexus.classworlds.launcher.Launcher <goals>`
- 离线 `-o` 构建常因缺少父 pom 失败 → 此时不要死磕构建，转静态分析并如实说明"未做实机重启验证"。

## 反模式（血泪教训，务必先看）

1. **不要基于截断的日志推断根因。** 用户粘贴的日志常在 `... N common frames omitted` 处断掉，最内层 `Caused by` 才是真凶。推断出来的"最像的解释"经常是错的——本技能的第一版结论就是错的（见版本记录）。第一步应该是**索要完整日志**，或用 `2>&1` 把完整输出落文件后再读。
2. **FeignClient 创建失败常常是被连坐。** 异常形如 `Error creating bean with name 'xxxFeignClient': FactoryBean threw exception`，栈顶是 `NamedContextFactory.createContext → AbstractApplicationContext.refresh → finishRefresh → publishEvent → getApplicationListeners → getBean(...)` 时，**真正的炸点是那个被 getBean 的类**，不是 Feign 本身。
3. **Bean 注入失败的常见非注入原因**：三方自动配置（springdoc / knife4j / 监控 / 报表）在主容器或子容器 refresh 时缺类（`NoClassDefFoundError`）。看到 `ClassNotFoundException` 嵌套在最底层，优先补 jar / 排除自动配置，而不是去改注入关系。
4. **`NoSuchMethodError` = 版本打架，不是代码问题。** 例：`swagger-core-jakarta:2.2.30` 调 `io.swagger.v3.oas.annotations.Parameter.validationGroups()`（2.2.30 才新增），而 classpath 上 `swagger-annotations-jakarta` 是 2.2.22 → 炸。修法：在聚合 pom 的 `dependencyManagement` 把同一套件的多个 artifact 统一锁版本，别只锁一个。核验手段：`javap -cp <jar> <类> | grep <方法名>` 逐个版本对比方法是否存在。
4. **javap 提取注解属性时，类级注解在输出末尾**，方法级注解（`value=["/xxx"]` 的 @RequestMapping）数量极多会淹没结果；用 `grep -a`（javap 输出含二进制字节，不加 `-a` 会被当成二进制文件而停止输出）并定位 `SourceFile` 之后的内容。
5. **先确认"默认行为"再下药**：例如 `spring.cloud.openfeign.circuitbreaker.enabled` 在 spring-cloud-openfeign 4.x 默认即为 false（`CircuitBreakerPresentFeignTargeterConfiguration` 上带 `@ConditionalOnProperty(havingValue="true")`，无 matchIfMissing），显式配 false 是 no-op。改配置前先用 `javap -v` 看 `@ConditionalOnProperty` 是否带 `matchIfMissing=true`。

## 输出规范

诊断完成后必须给出：
1. **根因一句话**（哪个 bean、为什么没有）。
2. **证据链**（逐条列出 javap/grep 输出作为支撑，注明命令）。
3. **修法选项 + 各自代价**，交由用户选择。
4. **验证状态**：明确区分「已静态核验」与「尚未实机重启验证」，不许把静态分析说成"已修复"。

## 版本记录

- 2026-09-09（第一次修订，推翻初版结论）：
  - **初版结论（已证伪，勿复用）**：判定真因为 `CxtjFeignClient` 的 `fallback=CxtjFeignClientFallback` 不在 `scanBasePackages` 范围内。错因：日志被截断，没有最内层 `Caused by`；且忽略了 spring-cloud-openfeign 4.2.2 中熔断默认关闭（`@ConditionalOnProperty(havingValue="true")` 无 matchIfMissing），fallback 根本不会被解析。
  - **真实根因（已确认）**：本地仓库 `org.webjars:webjars-locator-lite:1.0.1` **只有 pom 和 sources，缺少二进制 jar**，而 springdoc-openapi-starter-webmvc-ui:2.8.9 依赖它；Spring 6.2 的 `LiteWebJarsResourceResolver` 强依赖 `org.webjars.WebJarVersionLocator`，springdoc 的 `SwaggerResourceResolver` 继承该类 → Feign 子容器 refresh 发布事件时创建 `EnableWebMvcConfiguration` → `NoClassDefFoundError: org/webjars/WebJarVersionLocator`。
  - **修法**：在 `capinfo-gjj-busi-znsp/pom.xml` 的 dependencyManagement 锁定 `org.webjars:webjars-locator-lite:1.1.0`（本地 jar 完整），并在 `capinfo-gjj-busi-znsp-busi/pom.xml` 显式声明该依赖（不写版本）。
  - **配套动作**：撤销了基于错误结论加的 `spring.cloud.openfeign.circuitbreaker.enabled=false`（no-op，且注释里写了错误归因）。
