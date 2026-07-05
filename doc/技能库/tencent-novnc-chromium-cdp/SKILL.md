---
name: "tencent-novnc-chromium-cdp"
description: "Bridges noVNC, Chromium-family browsers, and CDP for browser automation. Invoke when the user wants a reusable local browser-control skill with remote preview or CDP attachment."
---

# Tencent noVNC Chromium CDP

## 用途

`tencent-novnc-chromium-cdp` 是当前工作区面向浏览器自动化的专用桥接技能。

本技能按用户要求采用该命名，但在当前仓库中的实际落地能力聚焦于三层：

- `noVNC`：提供远程可视化浏览器预览入口
- `Chromium / Edge / Chrome`：提供真实浏览器进程
- `CDP`：提供可编程自动化控制接口

其目标是让 Trae 在需要时可以稳定地“接管浏览器进程并自动操作页面”，而不仅仅是做一次性脚本执行。

## 触发场景

- 用户要求安装 `tencent novnc chromium cdp` 技能
- 用户希望实现自动操作浏览器
- 用户要求用本机浏览器而不是纯云端浏览器完成自动化
- 用户要求既能程序化控制浏览器，又最好保留可视化观察入口

## 能力分层

### 1. CDP 控制层

优先通过 Chrome DevTools Protocol 控制浏览器，这是自动化主通道。

适用方式：

- 连接已开启远程调试端口的浏览器
- 使用 `browser-use --connect`
- 使用 `browser-use --cdp-url`
- 未来接入任意 CDP 客户端时，统一按 `http://localhost:9222` 或对应 websocket 端点处理

### 2. Chromium 浏览器层

优先使用 Chromium 系浏览器作为被控浏览器。

当前机器检查结果：

- 未发现 `chrome` / `chromium` 在 PATH 中
- 已发现本机 Edge 可执行文件：
  - `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`

因此当前环境下可将 **Edge 视为 Chromium 家族浏览器替代实现**。

### 3. noVNC 预览层

`noVNC` 不是自动化主控制通道，而是“远程可视化观察层”。

适合场景：

- 浏览器运行在容器或远程环境中
- 需要边看边调试
- 需要在不直接占用宿主桌面的情况下观察浏览器动作

如果本机没有 Docker / noVNC 运行时，则该层暂不启用，不影响本地 CDP 自动化主链路。

## 默认模式

### 模式 A：本机浏览器 + CDP

这是当前环境下的首选模式。

启动思路：

```powershell
"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --remote-debugging-port=9222 --user-data-dir="d:\Probject\Gjj\.cache\edge-cdp"
```

随后可由浏览器自动化工具接管：

```powershell
browser-use --connect state
```

或：

```powershell
browser-use --cdp-url http://127.0.0.1:9222 open https://example.com
```

### 模式 B：容器中的 Chromium + noVNC + CDP

这是“带可视化远程预览”的增强模式。

典型结构：

- 容器内运行 Chromium
- 暴露 `9222` 供 CDP 接管
- 暴露 `8080` 供 noVNC 访问

但当前机器检查结果显示：

- 未探测到 `docker` 命令

因此本次只安装技能入口和运行规范，不直接启用容器模式。

## 标准流程

### 1. 探测

- 检查本机浏览器路径
- 检查是否已开启远程调试端口
- 检查 `browser-use` 或其他 CDP 客户端是否可用
- 若用户要求 noVNC，再检查 Docker / 容器基础设施

### 2. 启动

- 优先启动独立用户数据目录的 Chromium 系浏览器
- 必须开启 `--remote-debugging-port`
- 不直接污染用户日常浏览器配置

### 3. 接管

- 优先通过 `--connect` 或显式 `--cdp-url` 接管浏览器
- 先做只读状态探测，再做真实页面操作

### 4. 验证

- 验证 CDP 端口是否可连
- 验证页面状态、元素、截图是否可获取
- 若启用 noVNC，再额外验证预览入口是否可访问

## 安全边界

- 不默认接管用户的日常浏览器配置目录
- 必须优先使用单独的 `--user-data-dir`
- 不在未说明影响的情况下关闭用户当前浏览器
- 需要登录态的场景，优先说明接管方式，而不是强制复用用户真实工作会话
- noVNC 若对外暴露，必须提醒端口、密码和访问范围风险

## 与现有技能的关系

- `browser-use`：适合作为 CDP 实际执行层
- `settlement-window-verifier`：适合作为“接管已打开浏览器窗口”的专项经验参考
- `pingcode-ui-filter`：适合作为“本地 Chrome/CDP 启动方式”的专项经验参考
- `integrated_browser` MCP：适合普通网页测试；若用户明确要求本机浏览器 + CDP / noVNC，则优先使用本技能

## 本项目安装结论

当前工作区已采用本地桥接方式安装该技能：

- 主源文档：`doc/技能库/tencent-novnc-chromium-cdp/SKILL.md`
- 本地入口：`.trae/skills/tencent-novnc-chromium-cdp/SKILL.md`
- 运行时说明：`doc/技能库/tencent-novnc-chromium-cdp/RUNTIME-SETUP.md`

## 主源位置

- `doc/技能库/tencent-novnc-chromium-cdp/SKILL.md`
- `doc/技能库/tencent-novnc-chromium-cdp/RUNTIME-SETUP.md`
- `.trae/skills/tencent-novnc-chromium-cdp/SKILL.md`
- `doc/操作记录/20260705-tencent-novnc-chromium-cdp技能安装记录.md`
