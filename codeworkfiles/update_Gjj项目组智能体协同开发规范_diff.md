# Gjj项目组智能体协同开发规范修改对比（AI提交）

## 1. 目标
在 `/home/source/Jetbrains/Probject/Gjj/doc/rules/Gjj项目组智能体协同开发规范.md` 中增加任务拆解与工时估算、工作日志产出的规则，以对齐工时填报系统的字段要求。

## 2. 修改点及对比

### 修改点一：4. 标准工件（强制）
**原代码：**
```markdown
## 4. 标准工件（强制）
- 需求规格：`app_spec.txt`
- 特性清单：`feature_list.json`
- 会话进度：`claude-progress.txt`（记录结构参照 `doc/rules/claude-progress-template.txt`）
- 启动脚本：`init.sh`
```
**新代码：**
```markdown
## 4. 标准工件（强制）
- 需求规格：`app_spec.txt`
- 特性清单：`feature_list.json`
- 会话进度：`claude-progress.txt`（记录结构参照 `doc/rules/claude-progress-template.txt`）
- 工作日志：`daily_work_log.md`（记录每日工时、工作类型与内容，用于填报工时系统）
- 启动脚本：`init.sh`
```

### 修改点二：5.2 分析阶段
**原代码：**
```markdown
### 5.2 分析阶段
- 将需求拆解为可验证特性，写入 `feature_list.json`，初始状态统一为 `passes: false`。
- 门禁：特性不可验证、描述模糊、无优先级不得进入设计阶段。
```
**新代码：**
```markdown
### 5.2 分析与计划阶段
- 将需求拆解为可验证特性，写入 `feature_list.json`，初始状态统一为 `passes: false`。
- 对拆解出的每项任务/特性进行**工时估算**，并排定工作计划（预估开始和完成时间），一并记录在 `feature_list.json` 或 `claude-progress.txt` 中。
- 门禁：特性不可验证、描述模糊、无优先级、无工时估算与计划不得进入设计阶段。
```

### 修改点三：6. 会话执行协议（强制）
**原代码：**
```markdown
- 会话收尾动作：
  - 更新进度与交接信息。
  - 更新特性状态。
  - 提交变更并记录验证证据。
```
**新代码：**
```markdown
- 会话收尾动作：
  - 更新进度与交接信息。
  - 更新特性状态。
  - **产出每日工作日志**：每日下班/收尾时，根据当日实际执行情况产出简洁明了的工作日志（如 `daily_work_log.md`）。日志内容必须包含工时系统所需字段：**所属项目名称、工作类型、耗费工时（小时）、工作内容描述（500字以内）**，以便直接复制填报工时系统。
  - 提交变更并记录验证证据。
```
