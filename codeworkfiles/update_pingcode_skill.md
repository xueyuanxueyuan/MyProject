# 更新 PingCode 技能：专属地址与启动规范

## 需求背景
为了保证后续的自动化操作能准确地命中用户的工作台，用户要求对我们之前提取的 `pingcode-ui-filter` 技能进行优化升级：
1. 在调用浏览器打开 PingCode 时，默认**必须使用本地 Chrome 浏览器**。
2. 当用户说“打开我的 pingcode”时，必须精准指向用户专属的地址（`https://pingcode.capinfo.com.cn/pjm/projects/GJJ-TSHS/fwwFWqFj/WlytPXXa`），绝对不能使用 PingCode 的公开官网地址。

## 更新思路与执行步骤

1. **更新核心记忆 (Core Memory)**
   - 添加了一条用户级别的规则记忆 (Rule)。
   - **内容**：记录了用户对浏览器的偏好（需带上 `--remote-debugging-port=9222` 和 `--user-data-dir=/tmp/chrome-debug`）以及其专属的内网/私有化部署的 PingCode URL。
   - **目的**：确保 AI 在任何对话的上下文中，只要提及“打开 PingCode”，就能从记忆里准确提取出这些强制约束。

2. **更新双端技能文件 (SKILL.md)**
   - 修改了 `doc/skills/pingcode-ui-filter/SKILL.md`（源文件）和 `.trae/skills/pingcode-ui-filter/SKILL.md`（智能体应用文件）。
   - 在技能说明文档中增加了 **“前置条件：打开浏览器与特定 URL 规范”** 章节。
   - 补充了对应的适用场景（“用户要求打开我的pingcode”），并提供了标准的启动命令示例。

## 总结
通过技能文档的升级以及 AI 核心记忆的固化，现在“打开 PingCode”和“筛选 PingCode”已经完美结合在一起。未来无论在哪一次新的对话中，只需简单一句“打开我的pingcode”，智能体就能使用正确的环境，帮你直达正确的工作流节点。