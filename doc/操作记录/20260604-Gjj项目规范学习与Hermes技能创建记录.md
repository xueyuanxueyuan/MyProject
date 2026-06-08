# 20260604-Gjj项目规范学习与Hermes技能创建记录

## 背景
作为 Hermes Agent，需要学习 Gjj 项目组的全套规范体系（开发规范、提示词、技能），并创建适用于自己的技能和规则。

## 学习成果

### 阅读的文档清单

1. **AGENTS.md** — 项目最高优先级指令，"三省六部"工作流入口
2. **CLAUDE.md** — 统一主规则，工作流路由、交付门禁
3. **.cursorrules** — Gjj 编码与专属约定速查
4. **doc/项目规范/Gjj项目组开发规范.md** — 完整版开发规范（DDD、命名、接口、前端等351行）
5. **doc/项目规范/Gjj项目组智能体协同开发规范.md** — 智能体协作流程与门禁
6. **doc/项目规范/打包编译发布规范.md** — 前后端打包/发布/制品管理
7. **doc/项目规范/项目开发规范.md** — 前端专项（已并入总规范）
8. **doc/提示词/Gjj项目组开发提示词.md** — 后端快捷提示词模板
9. **doc/提示词/Gjj项目组前后端项目开发提示词.md** — 主提示词入口
10. **doc/通用模板/技能入口统一模板.md** — 技能入口文件骨架
11. **doc/技能库/vfox-toolchain/SKILL.md** — vfox 工具链切换技能
12. **doc/技能库/edict-triage/SKILL.md** — 太子批复技能
13. **doc/技能库/edict-planning/SKILL.md** — 中书省规划技能
14. **doc/技能库/edict-review/SKILL.md** — 门下省审议技能
15. **doc/技能库/edict-dispatch/SKILL.md** — 尚书省调度技能
16. **doc/技能库/edict-hanlin/SKILL.md** — 翰林院汇总技能
17. **doc/技能库/anti-laziness-anti-hallucination/SKILL.md** — 防偷懒防幻觉技能
18. **doc/技能库/verification-before-completion/SKILL.md** — 交付前验证技能
19. **doc/技能库/gjj-build-deploy/SKILL.md** — 编译打包技能
20. **doc/技能库/gjj-remote-deploy/SKILL.md** — 远程发布技能
21. **doc/需求分析/20260512-技能规则提示词压缩分析.md** — 入口层重复压降分析
22. **scripts/guardrails/agent-delivery-guardrail.sh** — 守卫脚本（扫描占位符）

### 核心知识总结

1. **三省六部流程**: 所有实质性任务必须走 Triage → Planning → Review → Dispatch → Execute → Report
2. **SDK管理**: vfox 管理 JDK 17、Maven 3.9.14、Node.js 16.20.2，编译前必须切换并验证
3. **DDD架构**: Interface → Application → Domain ← Infrastructure，单向依赖
4. **命名规范**: 中文拼音缩写 + 驼峰命名
5. **技术栈**: Spring Boot + MyBatis-Plus + OpenFeign + Kafka + XXL-Job（后端），Vue 2 + Element UI + Vuex（前端）
6. **数据库**: 达梦 DM8，15个系统字段标准
7. **交付门禁**: 防偷懒防幻觉 + 交付前验证守卫脚本
8. **文档落盘**: 需求分析/设计/评审/操作记录必须归档到 doc/

## 创建的 Hermes 技能

| 技能名 | 能力 |
|--------|------|
| `gjj-sansheng-edict` | 三省六部工作流（Hermes 适配版） |
| `gjj-vfox-toolchain` | vfox 工具链切换（JDK/Maven/Node.js） |
| `gjj-dev-standards` | 开发规范速查（DDD/命名/接口/Feign/数据库/Git） |
| `gjj-anti-laziness-verification` | 防偷懒防幻觉 + 交付前验证 |
| `gjj-doc-landing` | 文档落盘规范（分析/设计/评审/操作记录） |

## 创建的 Hermes 规则文件

- `HERMES_GJJ_RULES.md` — 项目级规则集，涵盖工作流/工具链/编码/文档/Git/用户偏好