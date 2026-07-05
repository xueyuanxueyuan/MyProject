# 20260705-Graphify-skill安装记录

## 背景

用户要求在当前 `d:\Probject\Gjj` 工作区安装 `Graphify skill`。

## 检查过程

### 1. 工作区现状

- 已检查 `.trae/skills`，此前仅存在 `edict-triage`、`edict-planning`、`edict-review`、`edict-dispatch` 四个本地技能入口
- 已在工作区全文检索 `Graphify|graphify`，确认此前不存在 `Graphify` 相关技能文件

### 2. 参考依据

- `doc/通用模板/技能入口统一模板.md`
- `doc/技能库/code-review-graph/SKILL.md`
- Graphify 官方公开资料与安装说明

### 3. 环境检查

执行结果如下：

- `python --version`：未安装
- `uv --version`：未安装
- `pipx --version`：未安装

结论：当前机器不具备直接安装 `graphifyy` 官方 CLI 的前置条件。

## 安装策略

为满足“安装 Graphify skill”且避免做成伪完成状态，本次采用项目内桥接安装：

1. 在 `doc/技能库/graphify/SKILL.md` 写入 Graphify 主源说明
2. 在 `.trae/skills/graphify/SKILL.md` 写入 Trae 本地技能入口
3. 保留官方运行时安装方式，待后续 Python/uv 就绪后再继续执行

## 落地产物

- `doc/技能库/graphify/SKILL.md`
- `.trae/skills/graphify/SKILL.md`
- `doc/操作记录/20260705-Graphify-skill安装记录.md`

## 当前结果

- 当前仓库已具备 `Graphify` 的本地技能入口，Trae 可在工作区内发现该技能
- 当前机器尚未安装 `Graphify` 官方 CLI 运行时，因此暂不能直接执行 `graphify` 命令

## 后续建议

若需要完整启用 Graphify CLI，可在补齐 Python 3.10+ 与 `uv` 后执行：

```powershell
uv tool install graphifyy
graphify install
```

若优先控制影响范围，建议使用项目级安装：

```powershell
graphify install --project
```
