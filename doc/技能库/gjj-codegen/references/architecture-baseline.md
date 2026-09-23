# Gjj 架构基线（代码生成事实源）

> 核实日期：2026-09-10
> 核实方式：实读 `prod/IdeaProject/**` 源码 + `package.json` + 平台父 pom，非凭记忆。
> 用途：代码生成前对齐现状。**与本文冲突时，以当前源码为准并回写本文。**

## 1. 工程空间

| 空间 | 类型 | 说明 |
|---|---|---|
| `d:\Probject\Gjj` | **文档仓** | 主要跟踪 `doc/` 下的文档与规范。**不是源码仓**，禁止在此新建 `src/`、`pom.xml` |
| `prod/IdeaProject/*` | 基线源码 | 主后端，含 `capinfo-gjj-auth`、`-busi-aiyw`、`-busi-gjtq`、`-busi-gzjc`、`-busi-jshs`、`-busi-zhfw`、`-platform-public` |
| `prod/WebStromProject/*` | 基线前端 | `capinfo-gjj-frontend-auth`（B 栈）、`capinfo-gjj-frontend-jshs-gm`（A 栈） |
| `jiaxing/` `linyi/` `zaozhuang/` `wenzhou/` | 区域副本 | 同构多城市副本，实现可能漂移 |

**模块命名模板**：`capinfo-gjj-busi-<域>-<子域>-basic-svc-{api|app|busi}`
（变体：`-api-v2` / `-app` / `-busi`；zhfw 系简写 `capinfo-gjj-busi-zhfw-mg-{api|app|busi}`）

**角色统计（prod 全量）**：`-api` 46、`-app` 44、`-busi` 43、`-core` 7、`-common` 5、`-agg` 4、`-web` 3。
独立 `-domain` / `-infra` / `-dal` / `-repository` 模块：**不存在**（未找到证据）。

## 2. Java 包层级

```
cn.capinfo.gjj.busi.<域>.<子域>.api        ← -api 模块
    ├── client/     FeignClient / Fallback / FallbackFactory
    ├── constant/   XxxConstant
    └── dto/        req/ resp/ evt/ （+ 业务分组目录）
cn.capinfo.gjj.busi.<域>.<子域>.app        ← -app 模块，仅启动类 + application*.yml
cn.capinfo.gjj.busi.<域>.<子域>.busi       ← -busi 模块
    ├── controller/      XxxController extends BaseController
    ├── service/         XxxService + impl/XxxServiceImpl
    ├── domain/
    │   ├── entity/      领域实体（无后缀，extends BaseEntity）
    │   ├── dao/         XxxDO extends BaseDO + mapper/XxxDOMapper + XxxDOMapper.xml
    │   └── service/     XxxDmService + impl/XxxDmServiceImpl extends BaseServiceImpl
    ├── strategy/        地区策略 RegionBusinessStrategy + impl/
    ├── validator/       自定义校验器
    ├── config/ prop/ dispatch/ publish/ subscribe/ bean/
    └── <外部集成>/      wbjk/ yht/ yqzl/ wzzljk/
```

**启动类真实写法**（`ZjjsYwglApplication.java`）：
```java
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableFeignClients(basePackages = "cn.capinfo.gjj")
@SpringBootApplication(scanBasePackages = {"cn.capinfo.gjj"})
@MapperScan("cn.capinfo.gjj.**.mapper")
@EnableDiscoveryClient
public class ZjjsYwglApplication {
    public static void main(String[] args) { SpringApplication.run(ZjjsYwglApplication.class, args); }
}
```
> 注意：`scanBasePackages` 收窄会导致外部 jar 的 `@Component`（含 Feign `fallback`）被静默漏扫——排查见 `doc/技能库/spring-bean-injection-triage/SKILL.md`。

## 3. 技术栈版本

| 组件 | 版本 | 证据 |
|---|---|---|
| Java | **17** | `maven.compiler.source/target` |
| Spring Boot | **3.4.9** | 平台父 pom（`jakarta.*` 命名空间实证） |
| Spring Cloud | 2024.0.2 | 同上 |
| Spring Cloud Alibaba | 2023.0.3.3 | 同上 |
| ORM | **MyBatis-Plus 3.5.14** | `mybatis-plus-spring-boot3-starter` |
| Lombok | 有 | `@Data` / `@Slf4j` 遍地 |
| Hutool | 5.8.40 | `cn.hutool.core.*` |
| Knife4j | 4.5.0 | OpenAPI 3 注解 |
| Swagger | 2.2.27 | — |
| 注册中心 | Nacos | `spring.cloud.nacos.discovery.server-addr` |
| RPC | **Feign**（无 Dubbo） | `@EnableFeignClients`；`@DubboService` 0 处 |
| 分页 | MyBatis-Plus `Page`（无 PageHelper） | — |
| 分库分表 | ShardingSphere 5.2.1 | — |
| Bean 映射 | `JoinBeanUtil` / Hutool `BeanUtil`（**无 MapStruct**） | — |
| 数据库多环境 | mysql / postgre / **dm（达梦）** / kingbase | pom profile |

> `gjj_arch_scan.py scan` 常常只探测到 Java 17——版本 key 锁在远端父 pom，本地扫不到。**以本表为准。**

## 4. 命名统计（prod 全量，文件名后缀）

| 后缀 | 数量 | 用途 / 落点 |
|---|---:|---|
| `*ReqDTO` | 2012 | 入参，`api/dto/req/` |
| `*RespDTO` | 868 | 出参，`api/dto/resp/` |
| `*EvtDTO` | 190 | MQ 事件体，`api/dto/evt/` |
| `*DO` | 572 | 持久化对象，`busi/domain/dao/` |
| `*Entity` | 42 | 少量（多为框架类） |
| `*VO` | 5 | 几乎不用 |
| `*BO` / `*QO` | 0 / 0 | **不使用** |

> 历史不一致：存在 `dto/res` 目录 13 处（zhfw/agg 系变体）。**新代码统一用 `resp`。**

## 5. 统一契约

**响应** `cn.capinfo.gjj.core.common.base.R<T>`：
```
 SUCCESS_CODE=0  FAIL_CODE=-1  TIMEOUT=-2  VALID_EX=-9  OPERATION_EX=-10  401/403
 字段：code / data / msg("ok") / path / extra / timestamp
 静态：R.success(data) R.success() R.success(data,msg) R.fail(code,msg) R.fail(msg)
       R.fail(BizException) R.fail(Throwable) R.validFail(msg) R.timeout()
       getIsSuccess() → code==0 || code==200
```
`BaseController` 代理同名实例方法（`success(data)` / `fail(msg)` / `validFail(...)`）。

**异常** `cn.capinfo.gjj.core.common.exception.BizException`（extends `BaseUncheckedException`）：
`new BizException(msg)`（code 默认 -1）、`new BizException(code, msg)`、`BizException.wrap(...)`、`BizException.validFail(...)`。
> 全局处理由框架 `ExceptionResolver` 策略（`BizExceptionResolver`）完成；业务模块内通常**无** `@RestControllerAdvice`。

**关键类全限定名速查**：
```
cn.capinfo.gjj.core.common.base.BaseController
cn.capinfo.gjj.core.common.base.R
cn.capinfo.gjj.core.common.constant.Constants        // Constants.HSDWDM 地区标识
cn.capinfo.gjj.core.common.exception.BizException
cn.capinfo.gjj.core.common.join.JoinBeanUtil         // copyBean(src, Target.class)
cn.capinfo.gjj.core.common.user.CurrentUser
cn.capinfo.gjj.basic.auth.anno.Current
cn.capinfo.gjj.basic.auth.entity.InnerUser
cn.capinfo.gjj.basic.validate.anno.Check
cn.capinfo.gjj.basic.db.mapper.IBaseMapper<T>        // extends BaseMapper<T>
cn.capinfo.gjj.basic.db.base.BaseDO<T>
cn.capinfo.gjj.expand.operate.log.annotation.OptLog
cn.capinfo.gjj.expand.lsh.tools.LshTools             // 流水号
cn.capinfo.gjj.basic.kafka.template.CapinfoKafkaTemplate
```

## 6. 数据库约定

**15 个系统字段**（建表必须含，不得改名删减）：
```
ID BIGINT NOT NULL
ZXBH NVARCHAR(15) NOT NULL
REVISION INT NOT NULL
CREATOR NVARCHAR(100)
CREATED_TIME TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP()
UPDATOR NVARCHAR(100)
UPDATED_TIME TIMESTAMP(0)
JBJGBH NVARCHAR(30) NOT NULL
JBJGMC NVARCHAR(90) NOT NULL
WDBH NVARCHAR(30)
WDMC NVARCHAR(90)
QDLX NVARCHAR(10)
QDBM NVARCHAR(16)
DEL_FLAG NVARCHAR(1) NOT NULL DEFAULT '0'
HSJGBH NVARCHAR(10) NOT NULL DEFAULT '01'
```
输出 DDL：表名与字段名**大写、不带引号**。脚本落 `doc/数据库脚本/`。

**BaseDO 已含**：`id`（`@TableId(AUTO)`）、`qdbm`、`createdTime`、`delFlag`（`@TableLogic`，`'0'`/`'1'`）、`creator`、`revision`（`@Version`）。DO 中只声明业务字段。

MP 全局配置：
```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: del_flag
      logic-delete-value: '1'
      logic-not-delete-value: '0'
```

## 7. 地区差异

- 根目录平行存在 `prod` / `jiaxing` / `linyi` / `zaozhuang` / `wenzhou`。
- 代码内地区标识：`Constants.HSDWDM`（配置 `gjj.hsjg.hsdwdm`，默认 `110000000000000`）。
- 策略分支：`busi/strategy/RegionBusinessStrategy.java` + `impl/DefaultRegionStrategy.java`、`impl/WzStrategy.java`；由 `HsdwdmRegionConfigService` 读取。
- **跨城市复制实现前**，先读 `doc/提示词/Gjj项目-区域差异方法清单提示词.md`，确认是否要走策略分支而非硬改。

## 8. 未找到证据的项（不要假设存在）

MapStruct、Dubbo、PageHelper、独立 `-domain`/`-infra`/`-dal` 模块、`@DataScope` / `@Dict` / 多租户注解、业务模块内的 `BizException` 全局 `@RestControllerAdvice`、第二套统一响应类。
