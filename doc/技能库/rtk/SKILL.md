---
name: rtk
description: Integrates rtk (Rust Token Killer) as a CLI output compressor. Invoke when commands produce huge output or when user asks to reduce token/context usage in terminal workflows.
---

# rtk

`rtk` 是一个 CLI 代理工具，用来在输出进入大模型上下文之前做过滤与压缩，目标是让“命令输出更短、更可读、占用更少上下文”。

## 你对我说的中文口令

- 以后命令输出太长就用 rtk
- 用 rtk 跑这个命令：{命令}
- 让 git 输出更短一点（用 rtk 思路）

## 适用场景（我会自动联想）

- `git diff / git log / git status` 输出太长
- 搜索类输出太多（例如大范围 grep、日志检索）
- 我需要跑命令但担心输出撑爆上下文

## Trae 深度集成（在 Trae 里能做到什么）

Trae 的工具输出是否能“默认被 rtk 过滤”，取决于 Trae 对外部 CLI hook 的支持。当前工作区采用可控且稳定的“深度集成”方案：

1. **仓库内集成**：提供安装、自检、启用、使用口令与约定
2. **可选本机集成**：你按脚本把 `rtk` 放进 PATH，并启用别名/包装器
3. **会话级策略**：只要检测到 `rtk` 可用，我在执行高输出命令时优先用 `rtk` 包裹

## 安装与启用（推荐路径）

1. 先安装 `rtk` 二进制到本机 PATH（见 `scripts/rtk/README.zh-CN.md`）
2. 在本仓库根目录执行：

```bash
./scripts/rtk/check.sh
```

3. 需要“更深”的命令透明化时，再执行：

```bash
./scripts/rtk/enable-bash.sh
```

## 说明

- 本技能只负责“在当前仓库内把 rtk 接入 Trae 工作流”，不强制改动你的全局 shell 配置
- 你选择了“深度集成”，我采用“可回滚、可控范围、默认不破坏现有命令习惯”的方式落地
