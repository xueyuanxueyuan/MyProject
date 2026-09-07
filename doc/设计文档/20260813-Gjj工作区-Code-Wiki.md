# Gjj 工作区 Code Wiki

> 生成日期：2026-08-13
> 适用范围：`d:\Probject\Gjj`
> 文档定位：工作区级代码导览、架构索引与运行说明

## 1. 文档目标

本文档用于对 `Gjj` 工作区做一份结构化、可检索的 Code Wiki，帮助后续开发、排障、评审与交接时快速回答以下问题：

- 这个工作区里到底有哪些工程
- 各工程之间按什么方式组织
- 主要模块分别负责什么
- 关键类和关键入口在哪里
- 模块之间如何依赖和调用
- 本地如何启动、构建与验证

## 2. 结论先看

`Gjj` 不是单一项目，而是一个“文档中台 + 多地区前后端工程 + 辅助工具工程”的综合工作区。

整体可分为四层：

1. `doc/`：规则、需求、设计、评审、数据库脚本、技能与项目记忆的治理中台
2. `prod/`：产品基线与主索引工程，包含后端公共平台、认证前端、业务前端等
3. `jiaxing/zaozhuang/wenzhou/linyi/`：按地区落地的后端与前端工程
4. `yht-mock-server/`、`zjb-openapi-auto-test/`：联调挡板与自动化测试类辅助工程

从代码分析角度看，当前最适合作为“代表性样本”的主业务工程是：

- 后端：`jiaxing/IdeaProject/capinfo-gjj-busi-jshs`
- 前端：`jiaxing/WebStromProject/capinfo-gjj-frontend-jshs` 与 `jiaxing/WebStromProject/capinfo-gjj-frontend-jshs-gm`

原因如下：

- 嘉兴目录同时保留新老前端，便于理解迁移关系
- 嘉兴结算后端模块齐全，结构与其他地区目录高度同构
- 当前工作区长期记忆已明确提到嘉兴存在“新老前端并存”和“定期存款支取”类业务改造

## 3. 工作区整体结构

### 3.1 顶层目录地图

| 目录 | 类型 | 作用 |
|---|---|---|
| `doc/` | 文档主仓 | 项目规范、技能库、记忆库、需求/设计/评审、数据库脚本 |
| `config/` | 配置 | 部署服务器等辅助配置 |
| `prod/` | 基线代码 | 主线后端、主线前端、公共平台 |
| `jiaxing/` | 区域代码 | 嘉兴后端与前端 |
| `zaozhuang/` | 区域代码 | 枣庄后端与前端 |
| `wenzhou/` | 区域代码 | 温州后端与前端 |
| `linyi/` | 区域代码 | 临沂前端等区域工程 |
| `yht-mock-server/` | 独立服务 | 一户通挡板/模拟网关/加密模拟 |
| `zjb-openapi-auto-test/` | Python 工具 | 住建部 OpenAPI 自动化测试工具 |
| `external/` | 外部工具 | 非核心业务源码或外部集成工具 |
| `.codex-run/`、`.cache/`、`.tmp/` | 运行缓存 | 本地临时目录、自动化输出、缓存文件 |

### 3.2 区域化组织方式

工作区遵循“`prod` 为主索引，各地区与 `prod` 同级平铺”的布局：

- `prod/IdeaProject/*`：后端主线或公共平台
- `prod/WebStromProject/*`：前端主线
- `jiaxing/IdeaProject/*`：嘉兴后端
- `jiaxing/WebStromProject/*`：嘉兴前端
- 其余地区目录与嘉兴保持类似结构

这意味着：

- 业务能力通常存在“基线版本 + 地区版本”
- 同名模块在不同地区目录中一般是同构演进，而不是完全独立设计
- 理解单个地区实现时，应把它放在“主线同构变种”的上下文中看待

### 3.3 文档仓与代码仓的关系

根目录 `README.md` 明确指出：根级 Git 仓库主要跟踪 `doc/` 下的文档与规范，业务代码并不默认纳入该根仓提交范围。

这带来两个重要认知：

- `doc/` 是工作区的规则与知识入口，不等于全部源码
- 代码真实形态需要同时查看 `doc/` 和各地区工程目录，不能只扫文档下结论

## 4. 技术栈总览

### 4.1 后端

后端主干以 Java Maven 多模块工程为主，典型特征如下：

- JDK：Java 17
- 构建：Maven 3
- 运行框架：Spring Boot
- 服务治理：注册中心 + OpenFeign 微服务调用
- 持久层：MyBatis / Mapper XML
- 配置方式：`application.yml` + 多 profile 切换
- 数据库支持：MySQL / PostgreSQL / 达梦 / Kingbase

典型启动类普遍包含以下能力：

- `@SpringBootApplication`
- `@EnableFeignClients`
- `@MapperScan`
- `@EnableDiscoveryClient`

这说明后端不是单体应用，而是“多业务微服务 + 聚合入口服务”的结构。

### 4.2 前端

工作区前端存在明显的“双代际并存”：

#### 老前端

- 工程示例：`capinfo-gjj-frontend-jshs-gm`
- 技术栈：Vue 2 + Vue CLI + Vuex + Vue Router 3 + Element UI
- Node 要求：README 中给出 `node v14.4.0`、`yarn 1.16.0`
- 特点：页面直接堆叠在 `src/components/<业务域>` 下，历史包袱较重，业务组件多

#### 新前端

- 工程示例：`capinfo-gjj-frontend-jshs`
- 技术栈：Vue 3 + TypeScript + Vite + Pinia + Vue Router 4 + Element Plus
- Node 要求：`package.json` 中声明 `node >=16.18.0`
- 特点：`src/views` + `src/api` + `src/stores` + 动态路由装配，结构更现代

### 4.3 辅助工具

- `yht-mock-server`：Spring Boot 3 的独立挡板服务
- `zjb-openapi-auto-test`：纯标准库 Python 工具，无第三方依赖

## 5. 核心工程地图

### 5.1 后端主样本：`capinfo-gjj-busi-jshs`

该工程是结算核算业务主工程，当前在嘉兴、枣庄、温州、prod 等目录下均有同构版本。其根 POM 是聚合工程，统一聚合多个业务域模块。

#### 一级模块一览

| 模块 | 职责摘要 |
|---|---|
| `capinfo-gjj-busi-zjjs-core` | 公共 DTO、枚举、事件、工具类等核心公共能力 |
| `capinfo-gjj-busi-zjjs-ywgl` | 业务管理核心域，收付款、调拨、一户通、定期存款等主业务编排 |
| `capinfo-gjj-busi-zjjs-zhgl` | 账户管理，银行账户、虚拟户、账户余额与状态维护 |
| `capinfo-gjj-busi-cwhs-jzgl` | 财务核算与记账，凭证、分录、账务、报表与核算日志 |
| `capinfo-gjj-busi-cwhs-cxtj` | 查询统计与外部报文/接口适配 |
| `capinfo-gjj-busi-jshs-gm-agg` | 聚合层，对外统一提供柜面/管理端接口，内部转发到各微服务 |
| `capinfo-gjj-busi-zjjs-lcgl` | 流程管理，尤其是理财、定存、申请/确认/审批链路编排 |
| `capinfo-gjj-busi-zjjs-sp` | 审批服务，对接统一工作流能力 |
| `capinfo-gjj-busi-jhgl-zjjh` | 资金计划管理，年度计划、计划调整及审批流 |

### 5.2 模块内部的标准四层

典型模块普遍遵循如下结构：

```text
xxx-module/
├── xxx-basic-svc-api/      # FeignClient、DTO、常量
├── xxx-basic-svc-api-v2/   # V2 版 API 契约（部分模块存在）
├── xxx-basic-svc-busi/     # 业务层：Controller / Service / DAO / Mapper / Entity
└── xxx-basic-svc-app/      # 启动层：Spring Boot 启动类 + application.yml
```

这四层大体对应：

- `api`：服务契约层
- `api-v2`：兼容新版本或地区适配的第二套契约
- `busi`：真实业务实现
- `app`：部署与启动壳层

### 5.3 聚合层与微服务关系

后端最重要的主线不是单模块内部，而是模块之间的调用关系。可以概括为：

```text
前端
  -> gm-agg 聚合服务
      -> ywgl-api
      -> zhgl-api
      -> cwhs-api
      -> lcgl-api
      -> sp-api
      -> jhgl-api

ywgl / zhgl / cwhs / lcgl / sp / jhgl
  -> 通过 FeignClient 互相编排
  -> 共享 core-common 中的 DTO / 枚举 / 公共事件
```

`gm-agg` 的定位是“统一外部入口”，而不是承载核心业务算法。核心业务逻辑主要沉积在 `ywgl`、`cwhs-jzgl`、`zhgl`、`lcgl` 四个模块里。

## 6. 嘉兴后端深描

本节以 `jiaxing/IdeaProject/capinfo-gjj-busi-jshs` 作为详细样本。

### 6.1 关键启动入口

| 模块 | 启动类 | 说明 |
|---|---|---|
| `zjjs-ywgl` | `ZjjsYwglApplication` | 业务管理服务启动入口 |
| `zjjs-zhgl` | `ZjjsZhglApplication` | 账户管理服务启动入口 |
| `zjjs-lcgl` | `ZjjsLcglApplication` | 流程管理服务启动入口 |
| `cwhs-jzgl` | `CwhsJzglApplication` | 财务核算服务启动入口 |
| `zjjs-sp` | `SpApplication` | 审批服务启动入口 |
| `jhgl-zjjh` | `JhglZjjhApplication` | 资金计划服务启动入口 |
| `jshs-gm-agg` | `JshsApplication` | 聚合层服务启动入口 |
| `cwhs-cxtj` | `CxtjApplication` | 查询统计服务启动入口 |

### 6.2 模块职责与角色

#### 1. `zjjs-ywgl`

这是最典型的“业务编排中心”。

主要承担：

- 收付款业务
- 资金调拨
- 网联/外围接口对接
- 一户通相关业务
- 定期存款业务
- 各种地区化策略扩展

关键特征：

- 模块体量最大
- 依赖最广
- 对 `zhgl`、`cwhs`、`lcgl`、`sp` 等模块都有显式调用

#### 2. `cwhs-jzgl`

这是“财务核算核心”。

主要承担：

- 凭证生成
- 账务处理
- 报表与分录
- 对账与核算日志
- 某些审批和消息发布后的财务落账动作

其实现中可以看到切面、工厂、发布器、任务作业等多种结构，说明它不仅是 CRUD 模块，而是一个核算平台。

#### 3. `zjjs-zhgl`

这是“账户主数据与余额控制中心”。

主要承担：

- 银行账户信息管理
- 账户使用状态与账户属性维护
- 虚拟账户/中心账户等账户域数据管理
- 账户余额、转存、冻结等账户状态相关处理

#### 4. `zjjs-lcgl`

这是“流程串联模块”，尤其重要于理财、定期存款、申请与确认链路。

主要承担：

- 定期存款申请与确认链路编排
- 存单信息、用途类型、理财业务流程衔接
- 对 `zhgl`、`ywgl`、`cwhs`、`sp` 的跨模块编排

#### 5. `zjjs-sp`

这是“统一审批适配层”。

主要承担：

- 发起审批
- 查询审批状态
- 对接统一工作流服务
- 作为业务模块和工作流平台之间的桥接服务

#### 6. `jhgl-zjjh`

这是“资金计划管理”模块。

主要承担：

- 计划年度、计划明细、计划调整
- 部分计划审批发起
- 与还款、审批等外围能力联动

#### 7. `jshs-gm-agg`

这是“统一对外接口网关”。

典型调用链：

```text
Controller -> Service -> FeignClient -> 下游业务服务
```

它更像 BFF/聚合网关，而不是规则引擎或核心业务承载层。

### 6.3 后端关键类与函数说明

以下表格列出理解系统时最值得优先阅读的类。

| 类 | 所在模块 | 角色 | 阅读价值 |
|---|---|---|---|
| `YwglServiceImpl` | `zjjs-ywgl` | 主业务编排中心 | 绝大部分收付款、调拨、定存、一户通链路都从这里展开 |
| `ZhglServiceImpl` | `zjjs-zhgl` | 账户服务核心实现 | 理解账户域建模与余额/属性处理的关键入口 |
| `CwhsServiceImpl` | `cwhs-jzgl` | 财务核算中心实现 | 凭证、分录、账务、核算处理的主战场 |
| `LcglServiceImpl` | `zjjs-lcgl` | 流程编排实现 | 理解定期存款申请/确认、审批衔接的关键 |
| `SpServiceImpl` | `zjjs-sp` | 审批桥接实现 | 理解业务如何对接工作流引擎 |
| `JhglServiceImpl` | `jhgl-zjjh` | 计划管理实现 | 理解资金计划与审批联动 |
| `YwglController` | `zjjs-ywgl` | V1 业务接口入口 | 适合从接口维度快速扫面业务范围 |
| `CwhsController` | `cwhs-jzgl` | 财务接口入口 | 可快速看到核算对外提供哪些能力 |
| `YwglFeignClient` | `zjjs-ywgl-api` | 服务契约 | 用于识别跨模块调用边界 |
| `CwhsFeignClient` | `cwhs-api` | 服务契约 | 用于理解核算服务被哪些地方依赖 |
| `RegionBusinessStrategy` | `zjjs-ywgl` | 地区策略扩展点 | 理解多地区差异实现的重要入口 |

### 6.4 关键函数/职责模式

虽然当前文档不逐项罗列每个方法签名，但从实现结构上可总结出三类重点函数：

#### 1. Controller 暴露型函数

常见特征：

- 名称体现业务动作，如新增、确认、查询结果、审批发起等
- 参数多为 `ReqDTO`
- 返回统一 `RespDTO` 或业务响应包装对象

这类函数适合做“接口清单索引”，不适合直接承载复杂判断。

#### 2. Service 编排型函数

典型代表：`YwglServiceImpl`、`LcglServiceImpl`、`CwhsServiceImpl` 中的大型业务方法。

常见特征：

- 一个方法内串联多张表、多模块 Feign 调用、状态流转与校验
- 涉及 DTO 转换、数据库更新、审批触发、消息记录
- 往往是问题定位与改造的高频落点

#### 3. Feign 契约型函数

典型代表：`*FeignClient`

常见特征：

- 通过 `@FeignClient` 指向具体服务
- `@RequestMapping` 或 `@PostMapping` 显式声明跨服务路径
- 是识别模块边界和依赖方向的最佳入口

### 6.5 后端依赖关系总结

从当前代码结构看，依赖方向可做如下归纳：

#### 强中心模块

- `zjjs-ywgl`
- `cwhs-jzgl`

这两个模块占据大部分代码量，是核心链路最集中的区域。

#### 账户与流程支撑模块

- `zjjs-zhgl`
- `zjjs-lcgl`

前者负责账户域，后者负责申请/确认/审批串联。

#### 平台接口模块

- `zjjs-sp`
- `jhgl-zjjh`
- `cwhs-cxtj`

分别偏审批、计划和查询统计/接口适配。

#### 公共基础模块

- `zjjs-core-common`

主要沉积：

- 通用 DTO
- 公共事件
- 枚举
- 工具类
- 外围接口相关基础对象

### 6.6 架构层面的风险点

从现有实现可以看出几个典型风险：

1. `YwglServiceImpl`、`CwhsServiceImpl` 等超大类明显存在“上帝类”倾向
2. 业务模块之间通过 Feign 深度互调，容易出现跨域耦合
3. V1/V2 API 并存，存在重复契约与维护成本
4. 地区差异、银行差异、前端差异叠加后，复杂度主要向 `ywgl` 与 `cwhs` 汇聚

## 7. 嘉兴前端深描

嘉兴目录下同时存在两套结算前端：

- 新版：`capinfo-gjj-frontend-jshs`
- 旧版：`capinfo-gjj-frontend-jshs-gm`

### 7.1 前端双轨结构

#### 新版前端：`capinfo-gjj-frontend-jshs`

定位：

- Vue 3 新架构前端
- 已承接大部分结算核算菜单与页面
- 适合作为后续开发主战场

核心目录：

```text
src/
├── api/           # 接口按业务域拆分
├── assets/        # 静态资源
├── common/        # 通用常量与工具
├── components/    # 通用组件
├── directives/    # 自定义指令
├── hooks/         # 组合式函数
├── layouts/       # 布局壳
├── routers/       # 静态路由 + 动态路由
├── stores/        # Pinia 状态管理
├── views/         # 页面视图
└── main.ts        # 启动入口
```

业务域分区可以从 `src/api` 与 `src/views` 看出：

- `cwhs`
- `zjjs`
- `lcgl`
- `jhgl`
- `spzx`
- `system`
- `workflow`
- `yht`

#### 旧版前端：`capinfo-gjj-frontend-jshs-gm`

定位：

- Vue 2 柜面前端
- 保留完整历史业务形态
- 常作为迁移对照和问题追溯参照物

核心目录：

```text
src/
├── api/
├── assets/
├── common/
├── components/    # 页面与业务组件大量混放于此
├── router/
├── store/
└── main.js
```

### 7.2 新前端关键入口

| 文件 | 作用 |
|---|---|
| `src/main.ts` | 挂载应用、Pinia、路由、Element Plus、全局指令 |
| `src/App.vue` | 全局容器与主题、配置同步入口 |
| `src/routers/index.ts` | Router 创建、模式配置、守卫接入 |
| `src/routers/modules/staticRouter.ts` | 静态路由定义 |
| `src/routers/modules/dynamicRouter.ts` | 动态菜单转路由逻辑 |
| `src/routers/modules/routeComponentResolver.ts` | 菜单 `component` 字段到页面组件的解析器 |
| `src/stores/index.ts` | Pinia 初始化 |
| `src/stores/modules/auth.ts` | 菜单、权限、动态资源的核心状态 |
| `vite.config.ts` | Vite 构建、代理、别名、HTTPS 模式配置 |
| `index.html` | 全局 HTML 入口，读取 `public/js/config.js` |

### 7.3 新前端路由与状态管理

#### 路由

新版前端采用：

- 静态路由：登录页、基础布局页、错误页、任务展示页等
- 动态路由：从权限资源接口或本地资源清单生成

关键特点：

- 路由模式支持 `hash` / `history`
- 组件路径通过 `component` 字段解析到 `src/views/<path>.vue` 或 `src/views/<path>/index.vue`
- 兼容旧菜单路径命名差异

#### 状态管理

新版前端采用 Pinia，常见模块包括：

- `auth`
- `user`
- `business`
- `dict`
- `global`
- `keepAlive`
- `tabs`
- `portal`
- `systemConfig`

说明：

- 认证、菜单、权限与系统配置已从 Vuex 式大仓库改为模块化状态
- 支持持久化存储

### 7.4 旧前端的阅读价值

旧前端仍然很重要，原因有三点：

1. 某些页面虽然已迁移，但旧版实现仍是业务口径最完整的参照
2. 动态菜单、业务组件、历史工具组件在旧项目中保留最全
3. 当前新老前端并存，实际问题定位常需要“新旧对照”

因此建议：

- 先在新前端查现状
- 找不到或逻辑不清时，再回旧前端找历史实现

## 8. 辅助项目说明

### 8.1 `yht-mock-server`

#### 定位

一户通独立挡板服务，用于模拟外围依赖。

覆盖能力：

- CAPS 网关报文接收
- HSM 模拟
- SVS 模拟
- 场景规则配置
- 回调触发
- 日志与状态查看页面

#### 关键入口

| 文件/类 | 作用 |
|---|---|
| `YhtMockServerApplication` | Spring Boot 启动类 |
| `YhtMockApiController` | 网关、场景规则、日志、状态接口聚合入口 |
| `HsmMockController` | HSM HTTP 接口 |
| `SvsMockController` | SVS HTTP 接口 |
| `MockGatewayService` | 报文分发与核心网关处理 |
| `MockStoreService` | 状态存储与日志落盘 |
| `MockCallbackService` | 异步回调推送 |
| `SvsMockService` | SVS 模拟实现 |
| `HsmMockService` | HSM 模拟实现 |
| `ZaykSvsSocketMockServer` | 兼容旧 Socket 方式的 SVS 挡板 |

#### 适用场景

- 一户通联调
- 回调模拟
- 加密/签名链路联调
- 无真实银行或真实安全设备时的本地替身环境

### 8.2 `zjb-openapi-auto-test`

#### 定位

住建部 OpenAPI 自动化测试工具。

#### 核心能力

- 读取 OpenAPI/Swagger JSON
- 读取银行 Excel 测试数据
- 注入城市固定参数
- 批量生成请求用例
- 选择 dry-run 或真实 HTTP 调用
- 生成 `report.json` 与 `report.html`

#### 关键脚本/函数

| 文件/函数 | 作用 |
|---|---|
| `zjb_openapi_runner.py` | 主入口脚本 |
| `parse_args()` | CLI 参数解析 |
| `load_openapi()` | 读取 OpenAPI 描述 |
| `read_xlsx_rows()` | 解析 Excel |
| `load_city_config()` | 读取城市配置 |
| `apply_fixed_params()` | 注入固定参数 |
| `build_case()` | 生成单个测试用例 |
| `call_http()` | 发起真实调用 |
| `write_report()` | 输出 JSON/HTML 报告 |
| `write_template_xlsx()` | 生成 Excel 模板 |

#### 适用场景

- 外部接口联调前的数据准备
- 批量回归测试
- 按城市差异切换固定参数后做接口验证

## 9. 项目运行与构建方式

本工作区包含多类工程，不能用单一命令启动。建议按工程类型分别处理。

### 9.1 嘉兴后端 `capinfo-gjj-busi-jshs`

#### 常见准备

- JDK 17
- Maven 3
- 本地可访问私服依赖
- 根据模块选择数据库 profile

#### 常见构建方式

在后端聚合根目录执行：

```bash
mvn clean install -DskipTests
```

若只编译某个模块：

```bash
mvn -pl capinfo-gjj-busi-zjjs-ywgl -am clean package -DskipTests
```

#### 启动方式

通常有两种：

1. 直接在 IDE 中运行对应 `*Application`
2. 在 `*-app` 模块中通过 Maven/Spring Boot 启动

示例思路：

```bash
cd jiaxing/IdeaProject/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev-dm
```

注意：

- 实际可用 profile 以各模块 `src/main/resources/application*.yml` 为准
- 不同模块支持的 profile 命名不完全一致

### 9.2 新前端 `capinfo-gjj-frontend-jshs`

#### 环境要求

- Node >= 16.18.0
- npm 或 yarn

#### 安装与运行

```bash
cd jiaxing/WebStromProject/capinfo-gjj-frontend-jshs
npm install
npm run dev
```

其他常用命令：

```bash
npm run dev:https
npm run build:dev
npm run build:test
npm run build:pro
npm run preview
npm run type:check
```

#### 配置特点

- 默认使用 Vite
- 支持代理配置
- 支持 HTTPS 开发模式
- `index.html` 会预先加载运行时配置脚本

### 9.3 旧前端 `capinfo-gjj-frontend-jshs-gm`

#### 环境要求

- Node v14.4.0
- Yarn 1.16.0

#### 安装与构建

```bash
cd jiaxing/WebStromProject/capinfo-gjj-frontend-jshs-gm
yarn install
yarn run build
```

典型本地开发通常还会配合：

```bash
yarn run serve
```

#### 特点

- Vue CLI 工程
- 开发代理直接指向多个微服务路径
- 和新版前端并不完全兼容，注意 Node 版本隔离

### 9.4 `yht-mock-server`

```bash
cd yht-mock-server
mvn spring-boot:run
```

默认可访问页面：

```text
http://localhost:9999/yht-mock/index.html
```

### 9.5 `zjb-openapi-auto-test`

#### dry-run

```bash
py zjb-openapi-auto-test/zjb_openapi_runner.py --mode dry-run --city jiaxing --openapi <openapi-json-or-url> --xlsx <bank-xlsx>
```

#### 真实调用

```bash
py zjb-openapi-auto-test/zjb_openapi_runner.py --mode run --city jiaxing --openapi <openapi-json-or-url> --xlsx <bank-xlsx>
```

#### 生成模板

```bash
py zjb-openapi-auto-test/zjb_openapi_runner.py --mode template --output template.xlsx
```

## 10. 推荐阅读顺序

如果是第一次接触本仓库，建议按下面顺序进入：

1. 先看 `doc/README.md`
2. 再看 `doc/项目规范/统一主规则-通用工具链接入说明.md`
3. 看本文件建立整体地图
4. 进入 `jiaxing/IdeaProject/capinfo-gjj-busi-jshs/pom.xml` 理解模块树
5. 先读 `jshs-gm-agg` 的 Controller 和 Service，理解外部接口入口
6. 再读 `YwglServiceImpl`、`CwhsServiceImpl`、`ZhglServiceImpl`、`LcglServiceImpl`
7. 前端先读 `capinfo-gjj-frontend-jshs/src/main.ts`、`src/routers`、`src/stores`
8. 页面问题若在新前端看不清，再回旧前端对照
9. 联调问题需要外部依赖时，再看 `yht-mock-server` 与 `zjb-openapi-auto-test`

## 11. 关键认知与维护建议

### 11.1 关键认知

- 这是工作区，不是单仓单工程
- `doc/` 是知识入口，但不是全部源码
- `prod` 与各地区目录是同构变体，不宜孤立理解
- 后端核心复杂度集中在 `ywgl` 与 `cwhs-jzgl`
- 前端当前处于“新旧并行、逐步迁移”的状态
- 辅助项目在联调时非常重要，不能忽略

### 11.2 阅读和改造建议

- 优先以嘉兴工程作为定位样本，再横向对比其他地区
- 修改后端业务逻辑时，先检查是否牵涉 `gm-agg -> ywgl/zhgl/cwhs/lcgl/sp` 的跨模块链路
- 修改前端页面时，先确认目标在新前端还是旧前端
- 处理定期存款、支取申请/确认等流程时，必须同时关注 `lcgl`、`ywgl`、`zhgl`
- 处理财务口径、凭证、分录、报表时，必须优先核对 `cwhs-jzgl`

## 12. 后续可扩展方向

本版 Code Wiki 已覆盖工作区总览、主模块职责、关键类、依赖关系与运行方式。若后续需要更细粒度文档，建议继续拆分为以下专题：

- `capinfo-gjj-busi-jshs` 模块调用关系图
- `YwglServiceImpl` 业务分域拆解表
- `CwhsServiceImpl` 核算链路专题
- 前端新旧菜单与页面迁移映射表
- 各地区与 `prod` 的差异对照文档

## 13. 本文档的边界

本文档是工作区级 Wiki，总结的是稳定结构与高频入口，不追求列出每个类、每个接口、每个 Mapper 的完整清单。

对于具体改造任务，仍应进一步结合：

- 需求文档
- 设计文档
- 评审记录
- 当前目标模块源码
- 当前地区分支实现

进行二次精读与验证。
