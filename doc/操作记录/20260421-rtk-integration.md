# rtk（rtk-ai/rtk）分析与 Trae 集成记录

## 背景

用户提供仓库：`https://github.com/rtk-ai/rtk`，要求分析并集成到 Trae 中。

## 仓库定位（结论）

`rtk` 是一个 CLI 代理工具，目标是对高噪声/高体量的命令输出进行过滤压缩，使输出更短更可读，从而减少大模型上下文占用。

## Trae 集成边界

Trae 自身的工具链是否支持“无感 hook”外部 CLI，取决于 Trae 平台能力。

为保证稳定与可控，本次采用“深度集成但可回滚”的方式：

- 仓库内提供：技能入口 + 使用口令 + 启用/回滚脚本
- 不自动改写全局 shell 配置
- 若本机已安装 `rtk`，会话内可启用别名实现更接近“无感”

## 已落地内容

### 技能

- `doc/技能库/rtk/SKILL.md`
- `.trae/skills/rtk/SKILL.md`

### 脚本与说明

- `scripts/rtk/README.zh-CN.md`
- `scripts/rtk/check.sh`
- `scripts/rtk/enable-bash.sh`
- `scripts/rtk/disable-bash.sh`

## 使用方式

### 中文口令（对 AI 说）

- 以后命令输出太长就用 rtk
- 用 rtk 跑这个命令：{命令}

### 命令行（本机）

```bash
./scripts/rtk/check.sh
./scripts/rtk/enable-bash.sh
```

## 备注

由于网络波动，当前环境对 GitHub 克隆存在不稳定情况；集成以“仓库内适配层”先落地，`rtk` 二进制由用户按 README 指引自行安装到 PATH。
