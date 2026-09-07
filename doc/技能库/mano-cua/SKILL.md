---
name: mano-cua
description: 基于 Mano-P / mano-skill 的桌面 GUI 自动化适配技能。用于需要真实桌面视觉交互、且目标系统没有稳定 API 或 CLI 可替代时。
---

# Mano CUA

这是基于 `Mininglamp-AI/Mano-P` 与 `Mininglamp-AI/mano-skill` 的本地适配技能。

它的目标不是把上游 ClawHub 安装流程原样搬进当前工作区，而是结合本仓库现有规则，提供一套可追溯、可复用、可在 Windows 环境落地的接入方式：

- 保留上游源码，便于后续比对、升级和二次排障
- 为当前工作区补一个本地技能入口，纳入 `doc/技能库/技能索引.md`
- 提供 Windows 启动脚本，优先走本地源码运行链路
- 明确平台边界、权限要求和安全注意事项

## 何时使用

- 用户明确要求用 `Mano-P` / `mano-cua` / `mano-skill` 做桌面 GUI 自动化
- 需要跨桌面应用或系统窗口执行视觉交互，且没有更稳定的 API / CLI / 浏览器专用工具
- 需要评估 Mano-P 在当前环境中的接入方式、运行边界和风险

## 何时不要优先使用

- 目标系统已有稳定 API、数据库脚本、命令行工具或 MCP 工具
- 只是普通网页自动化，且当前浏览器工具已经足够
- 涉及高敏感信息、不可逆操作，但还没有经过用户明确确认

## 本地路径

- 总入口参考：`codeworkfiles/mano-skill-source/mano-p-readme.md`
- 上游源码：`codeworkfiles/mano-skill-source/`
- 上游技能：`codeworkfiles/mano-skill-source/skill/SKILL.md`
- 依赖安装脚本：`scripts/setup-mano-cua.ps1`
- Windows 启动脚本：`scripts/mano-cua.ps1`

## 当前工作区接入策略

1. 保留 `mano-skill` 上游源码到 `codeworkfiles/mano-skill-source/`
2. 在 `doc/技能库/mano-cua/` 增加当前工作区适配入口
3. 通过 `scripts/mano-cua.ps1` 直接调用上游 `visual/vla.py`
4. Windows 环境默认仅支持云端模式；`--local` 端侧模型仅支持 macOS Apple Silicon

## Windows 使用方式

```powershell
# 安装最小依赖到工作区
powershell -ExecutionPolicy Bypass -File .\scripts\setup-mano-cua.ps1

# 查看帮助
powershell -ExecutionPolicy Bypass -File .\scripts\mano-cua.ps1 --help

# 执行桌面 GUI 自动化任务
powershell -ExecutionPolicy Bypass -File .\scripts\mano-cua.ps1 run "打开浏览器，搜索公积金政策"

# 打开指定网址后开始执行
powershell -ExecutionPolicy Bypass -File .\scripts\mano-cua.ps1 run "搜索 AI 新闻并打开第一条" --url "https://www.bing.com"

# 停止当前任务
powershell -ExecutionPolicy Bypass -File .\scripts\mano-cua.ps1 stop
```

## 执行规则

1. 先判断是否真的需要 GUI 自动化，避免和浏览器工具、Shell、业务 API 重叠
2. 执行前提醒用户：
   - 需要图形桌面
   - 需要屏幕录制/辅助功能权限
   - 运行期间不要手动抢占鼠标键盘
3. Windows 上默认按云端模式使用；若用户要求本地模型，需明确说明当前仅 macOS Apple Silicon 支持
4. 涉及登录、删除、转账、提交等高风险动作，必须先让用户确认
5. 声称“可用”前，至少完成一次真实命令级验证

## 说明

- 上游项目主页：`https://github.com/Mininglamp-AI/Mano-P`
- 上游技能源码：`https://github.com/Mininglamp-AI/mano-skill`
- 当前工作区采用“上游源码保留 + 本地桥接入口 + Windows 启动脚本”的深度集成方式
