# Commit提交规范说明

## 1. 编写目的

为支撑AI辅助开发KPI统计，自2026年5月起，研发人员提交代码时应在commit message中增加AI辅助开发标识。

该标识用于统计研发人员AI工具应用覆盖率、AI辅助commit数量、AI辅助代码变更量和AI生成代码采纳率（代码库纳入估算口径）。

本规范尽量保持轻量，不要求研发人员填写AI辅助场景、AI建议代码量、AI采纳代码量、采纳率和任务编号。相关统计由GitLab统计工具基于commit记录、代码变更量和AI辅助标识自动生成。

## 2. 标准格式

Commit message采用如下格式：

```text
<提交类型>: <简短说明>

<可选：补充说明>

AI辅助: true/false
AI工具: Trae/智谱/DeepSeek/ChatGPT/Claude/CodeX/Cursor/Qcoder/Other/None
```

其中，前两部分保持原有代码提交习惯，最后两行是AI辅助开发统计字段。

## 3. 字段说明

### 3.1 AI辅助

`AI辅助`表示本次commit是否使用AI工具产生实质帮助。

取值固定为：

```text
true
false
```

不要使用以下写法：

```text
是/否
Y/N
yes/no
True/False
TRUE/FALSE
```

统计工具第一阶段按小写`true`和`false`进行解析。

### 3.2 AI工具

`AI工具`表示本次commit主要使用的AI辅助开发工具。

取值固定为：

```text
Trae
智谱
DeepSeek
ChatGPT
Claude
CodeX
Cursor
Qcoder
Other
None
```

当`AI辅助: true`时，`AI工具`必须填写当前实际使用的主要工具。当前 Codex（含 Codex Desktop/CLI）统一填写`CodeX`。工具身份明确时，不得填写`Other`代替实际工具名称。

`Other`不是未知工具或枚举判断不清时的默认值，仅在用户或项目规范明确确认该工具应归类为`Other`时使用。若提交执行者无法确认当前工具名称，或实际工具不在现有枚举中，必须在提交前向用户求证，并按确认结果填写及维护枚举；禁止猜测或自行使用`Other`兜底。

当`AI辅助: false`时，`AI工具`应填写：

```text
None
```

第一阶段建议只填写一个主要工具。如果一次提交同时使用多个AI工具，填写对本次提交帮助最大的工具。后续如确有需要，再扩展多工具统计。

## 4. 提交类型建议

提交类型用于说明本次commit的主要变更类型。建议使用以下类型：

```text
feat：新增功能
fix：缺陷修复
test：测试相关
refactor：代码重构
docs：文档修改
style：格式调整
chore：杂项调整
build：构建相关
ci：流水线相关
perf：性能优化
other：其他
```

提交类型不是AI统计的核心字段，但建议逐步规范，便于后续结合模型进行研发场景分析。

## 5. AI辅助判定规则

只要本次commit中进入代码库的内容，在代码生成、代码补全、问题定位、修改建议、测试补齐、脚本编写、代码解释或代码审查中使用了AI工具，并对最终提交结果产生实质帮助，即标记为：

```text
AI辅助: true
```

该标识表示本次commit存在AI实质参与，不表示本次commit中的全部代码均由AI生成。

以下情况应标记为`AI辅助: true`：

```text
1. 使用AI生成了部分代码、测试、脚本、配置或文档，并采纳到commit中。
2. 使用AI分析报错、定位缺陷，并根据AI建议完成代码修改。
3. 使用AI解释历史代码、梳理逻辑，并据此完成本次代码调整。
4. 使用AI生成或补充单元测试、接口测试、测试数据。
5. 使用AI辅助重构代码结构、补充参数校验、异常处理、日志等。
6. 使用AI做代码审查，并根据AI建议修改后提交。
7. 使用AI辅助生成技术文档、接口说明、部署脚本，并提交到代码库。
```

以下情况可标记为`AI辅助: false`：

```text
1. 只是让AI生成commit message，未影响代码、测试、配置或文档内容。
2. 只是询问通用概念，但未影响本次提交内容。
3. AI给过建议，但最终没有采纳，也没有影响本次提交结果。
4. 本次提交完全由人工完成。
```

边界情况按“AI是否对最终提交结果产生实质帮助”判断。

## 6. 标准示例

### 6.1 AI辅助功能开发

```text
feat: 增加文件上传大小校验

补充上传文件大小限制和错误提示，避免超大文件进入后续处理流程。

AI辅助: true
AI工具: Trae
```

### 6.2 AI辅助缺陷修复

```text
fix: 修复登录验证码为空时报错问题

AI辅助: true
AI工具: 智谱
```

### 6.3 AI辅助测试补齐

```text
test: 补充订单金额边界测试用例

AI辅助: true
AI工具: ChatGPT
```

### 6.4 AI辅助代码重构

```text
refactor: 优化用户查询服务结构

AI辅助: true
AI工具: Qcoder
```

### 6.5 非AI辅助提交

```text
chore: 调整日志输出格式

AI辅助: false
AI工具: None
```

### 6.6 AI辅助文档修改

```text
docs: 更新支付接口调用说明

AI辅助: true
AI工具: DeepSeek
```

## 7. 执行要求

二季度第一阶段建议按以下规则执行：

```text
1. 所有commit都应包含“AI辅助”和“AI工具”两个字段。
2. 当“AI辅助: true”时，“AI工具”必须填写Trae/智谱/DeepSeek/ChatGPT/Claude/CodeX/Cursor/Qcoder/Other中的一个。
3. 工具身份明确时必须填写实际工具名称；Codex统一填写CodeX，禁止使用Other代替。
4. 无法确认工具名称或工具不在枚举中时，提交前必须向用户求证，禁止猜测或默认填写Other。
5. Other仅在用户或项目规范明确确认应归入Other时使用。
6. 当“AI辅助: false”时，“AI工具”必须填写None。
7. 未填写“AI辅助”的commit，统计工具标记为格式异常。
8. “AI辅助: true”但“AI工具: None”的commit，统计工具标记为格式异常。
9. “AI辅助: false”但“AI工具”不是None的commit，统计工具标记为待确认。
```

如果初期执行阻力较大，可分阶段推进：

```text
5月：要求所有AI辅助commit必须填写“AI辅助: true”和“AI工具”。
6月：要求所有commit都填写“AI辅助”和“AI工具”。
```

从统计质量角度，建议尽早要求所有commit都填写两个字段，便于区分“未使用AI”和“忘记填写”。

## 8. 统计工具解析规则

GitLab统计工具应按固定字段解析commit message。

核心解析字段：

```text
AI辅助:
AI工具:
```

建议解析规则：

```text
1. 从commit message末尾解析“AI辅助”和“AI工具”字段。
2. “AI辅助”只接受true和false。
3. “AI工具”只接受Trae、智谱、DeepSeek、ChatGPT、Claude、CodeX、Cursor、Qcoder、Other、None。
4. 字段缺失、字段值不合法或组合不合法的commit，进入异常清单。
5. 异常commit不计入AI辅助开发活跃度和采纳率统计，待人工确认后再处理。
```

建议异常类型：

```text
AI辅助字段缺失
AI工具字段缺失
AI辅助字段取值错误
AI工具字段取值错误
AI辅助为true但AI工具为None
AI辅助为false但AI工具不是None
```

## 9. 与KPI统计的关系

本规范用于支撑两个KPI指标。

AI工具月活率：

```text
AI工具月活率 = 当月至少存在1个“AI辅助: true”commit的研发人员数 / 当月纳入统计研发人员总数
```

AI生成代码采纳率采用代码库纳入估算口径：

```text
AI生成代码采纳率（代码库纳入估算口径） = “AI辅助: true”commit的代码变更量 / 当月全部commit代码变更量
```

代码变更量第一阶段建议采用：

```text
代码变更量 = additions + deletions
```

该采纳率口径反映AI辅助成果进入GitLab代码库的程度，不等同于AI工具内部真实建议采纳事件。由于现阶段AI辅助开发工具未统一提供建议代码量和采纳代码量日志，本年度先采用GitLab代码库纳入估算口径进行统计。

## 10. 注意事项

一是不要在commit中填写AI建议代码行数、采纳代码行数和采纳率。开发人员难以准确统计，容易形成主观填报。

二是不要把“AI辅助: true”理解为整次提交全部由AI生成。它只表示AI对本次提交结果产生了实质帮助。

三是必须如实填写实际使用的AI工具名称。当前 Codex（含 Codex Desktop/CLI）统一填写`CodeX`；工具身份明确时禁止填写`Other`。若无法确认工具名称或实际工具不在枚举中，必须在提交前向用户求证，并按确认结果更新枚举或填写；`Other`仅在用户或项目规范明确确认应归类为`Other`时使用。

四是如果一次commit包含多个研发场景，不需要人工填写场景。后续可由统计工具结合模型对commit内容进行辅助分类。

五是Q2先以提醒和统计为主，不建议一开始通过强拦截影响正常提交。规范稳定后，可考虑通过Git hook或CI规则进行自动校验。

## 11. 推荐Git提交模板

为降低填写成本，建议给研发人员配置Git commit template。

模板内容如下：

```text
<提交类型>: <简短说明>

<可选：补充说明>

AI辅助: false
AI工具: None
```

研发人员使用AI辅助开发时，只需修改最后两行为：

```text
AI辅助: true
AI工具: Trae
```

或填写其他主要AI工具。

## 12. 代码提交人名称约束

Git 提交记录中的作者姓名必须使用真实中文姓名（如“张三”）或中文姓名全拼（如“zhangsan”或“ZhangSan”），禁止使用英文名（如“Tom”）或网络昵称（如“风清扬”）。
