---
name: "serena"
description: "Installs and uses Serena MCP for semantic code tooling. Invoke when user asks to install Serena, initialize Serena, or run symbol-aware code analysis/refactoring."
---

# Serena

## 用途

`serena` 是一个通过 MCP 提供语义级代码检索、编辑、重构能力的外部工具链，适合在复杂代码库中提升符号级定位与改动可靠性。

## 触发场景

- 用户明确要求安装 `https://github.com/oraios/serena`
- 用户要求启用 Serena 的 MCP 能力
- 用户要求做符号级分析、重构、引用追踪，并希望接入 Serena

## 本项目安装结论

当前工作区采用“运行时安装 + 本地配置目录”方式接入 Serena：

- 运行时安装：`uv tool install -p 3.13 serena-agent`
- 可执行文件：`C:\Users\Sour\.local\bin\serena.exe`
- 本地配置根目录：`D:\Probject\Gjj\.serena-home`（通过 `SERENA_HOME` 指定）

## 运行前提

- Python 可用
- `uv` 可用（本次通过 `py -m pip install --user uv` 安装）
- Windows 下若 PATH 未自动更新，需显式使用绝对路径调用 `serena.exe`

## 推荐执行方式

### 1. 初始化（项目内目录，避免写用户主目录）

```powershell
$env:SERENA_HOME='D:\Probject\Gjj\.serena-home'
& "$HOME\.local\bin\serena.exe" init
```

### 2. 常用探测命令

```powershell
& "$HOME\.local\bin\serena.exe" --help
& "$HOME\.local\bin\serena.exe" start-mcp-server --help
```

## 风险与边界

- 默认 `serena init` 会写入 `C:\Users\<user>\.serena`；在受限沙箱环境可能失败
- 当前项目应优先使用 `SERENA_HOME` 将配置落在仓库内可控目录
- `.serena-home` 属于本地运行目录，不应纳入仓库版本控制

## 主源位置

- `doc/技能库/serena/SKILL.md`
- `.trae/skills/serena/SKILL.md`
- `doc/操作记录/20260824-serena-skill安装记录.md`
