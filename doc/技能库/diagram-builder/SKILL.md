---
name: "diagram-builder"
description: "Builds diagrams from natural-language requirements and chooses suitable formats such as Mermaid, draw.io, or Excalidraw. Invoke when the user asks to create, update, or install a diagram-building skill."
---

# Diagram Builder

## 用途

`diagram-builder` 是当前工作区的通用画图技能入口，用于根据用户描述选择合适的图表表达方式，并产出适合落库、评审或交付的图形文件。

## 触发场景

- 用户要求安装、启用或使用 `diagram-builder`
- 用户要求“画图”“生成流程图”“补一张架构图”“做时序图/ER 图/类图”
- 用户希望把需求说明、架构说明或流程文本转成可读图形
- 用户希望补充可落库的文档图示，而不是只写纯文字

## 能力边界

本技能优先做“图表选型 + 生成策略约束”，不绑定单一图形工具：

- `Mermaid`：适合流程图、时序图、ER 图、状态图、Gantt，便于直接落在 Markdown 文档中
- `draw.io`：适合需要精细排版、可视化交付、后续人工继续编辑的架构图和复杂流程图
- `Excalidraw`：适合偏说明性、草图风格、概念表达类图示
- 若当前任务只是“快速解释”，也可先给出 Mermaid 或结构化文本草图，再视需要升级为更重的图形格式

## 默认策略

1. 先识别图表类型：流程、架构、时序、类图、ER、脑图、职责边界。
2. 优先选择最轻且可维护的产物：
   - 文档内长期维护优先 `Mermaid`
   - 需交付可编辑图文件优先 `draw.io`
   - 需强调表达风格或草图感优先 `Excalidraw`
3. 图中元素必须来自用户需求或当前仓库事实，禁止凭空补业务组件。
4. 复杂图表应同时产出简短说明，解释图中关键节点、连线与阅读顺序。

## 本项目安装结论

当前工作区已采用本地桥接方式安装 `diagram-builder`：

- 主源文档：`doc/技能库/diagram-builder/SKILL.md`
- Trae 本地入口：`.trae/skills/diagram-builder/SKILL.md`

## 本地补充

- 当前仓库原生已存在较多与图谱、图示、流程相关的文档与技能参考，但不存在同名 `diagram-builder`
- 因未找到明确同名公开技能来源，本次安装为项目内通用桥接技能，而非第三方原样镜像
- 后续若用户明确指定 `Mermaid`、`draw.io` 或 `Excalidraw`，应在此技能基础上进一步生成对应产物

## 主源位置

- `doc/技能库/diagram-builder/SKILL.md`
- `.trae/skills/diagram-builder/SKILL.md`
- `doc/操作记录/20260705-diagram-builder安装记录.md`
