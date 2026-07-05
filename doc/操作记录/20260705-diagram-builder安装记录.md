# 20260705-diagram-builder安装记录

## 背景

用户要求在当前 `d:\Probject\Gjj` 工作区安装 `diagram-builder`。

## 检查过程

### 1. 工作区检索

- 已检索 `.trae/skills`、`doc/技能库` 与全仓库内容
- 未发现同名 `diagram-builder` 技能或历史安装痕迹

### 2. 外部检索结论

外部可找到多个“图表生成类技能”参考，例如：

- `drawio-skill`
- `excalidraw-diagram-skill`
- `draw-io-diagram-generator`

但未检索到与用户请求完全同名、且可直接按当前仓库习惯落位的 `diagram-builder` 公开技能。

## 安装策略

为满足“安装 diagram-builder”这一意图，并避免误装成不匹配的第三方技能，本次采用项目内桥接安装：

1. 在 `doc/技能库/diagram-builder/SKILL.md` 建立主源文档
2. 在 `.trae/skills/diagram-builder/SKILL.md` 建立 Trae 本地入口
3. 将其定义为“通用画图技能入口”，负责在 Mermaid、draw.io、Excalidraw 等方案之间做选型

## 落地产物

- `doc/技能库/diagram-builder/SKILL.md`
- `.trae/skills/diagram-builder/SKILL.md`
- `doc/操作记录/20260705-diagram-builder安装记录.md`

## 当前结果

- 当前仓库已具备 `diagram-builder` 的本地技能入口，Trae 可在工作区内发现该技能
- 该技能为本地桥接技能，目标是统一“画图任务”的入口，而不是绑定单一第三方实现

## 后续建议

- 若后续主要输出 Markdown 文档中的图，优先使用 `Mermaid`
- 若后续主要输出专业可编辑图文件，优先扩展到 `draw.io`
- 若后续需要偏说明性的草图表达，可扩展到 `Excalidraw`
