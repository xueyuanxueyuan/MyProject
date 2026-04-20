---
name: "settlement-window-verifier"
description: "Connects to portal-launched settlement system windows and verifies UI in the real browser. Invoke when the user asks to validate pages that open a new settlement window after portal login."
---

# Settlement Window Verifier

用于处理“先登录门户，再跳转并新开结算系统窗口”的自动化验证场景。

## 何时使用

- 用户要求自动验证结算系统页面。
- 用户说明页面是从门户跳转进入，并且会新开浏览器窗口。
- 用户要求验证弹窗、按钮、导出、筛选、截图等真实 UI 效果。
- 已经出现“自动化找不到新打开的结算系统窗口”这类问题。

## 核心原则

1. 不要默认使用单独启动的新浏览器实例去验证门户跳转后的页面。
2. 优先接管“用户当前正在使用的浏览器进程”，否则新开的结算系统窗口通常不在自动化控制范围内。
3. 若无法接管现有浏览器，必须先判断是否因为 Chrome 未开启远程调试端口。
4. 在无法自动接管当前窗口时，要明确告知用户原因，并给出下一次可稳定复用的启动方式。

## 标准验证流程

### 1. 先确认浏览器接管方式

优先尝试连接用户当前浏览器：

```bash
browser-use --connect state
```

如果返回类似以下错误：

```text
Could not discover a running Chrome instance with remote debugging enabled.
```

说明当前浏览器没有开启远程调试端口，无法直接接管已打开的新窗口。

### 2. 可接管时的操作步骤

当 `--connect` 可用时，按以下顺序执行：

1. 读取当前状态：

```bash
browser-use --connect state
```

2. 如有多个标签页或窗口，切换到结算系统页：

```bash
browser-use --connect switch 1
browser-use --connect state
```

3. 进入目标页面，打开目标弹窗。
4. 获取状态、截图、按钮文本，验证右上角导出按钮是否可见、是否可点击。
5. 必要时点击导出按钮，并继续验证“导出本页 / 导出全部”等浮层是否可正常展开。

### 3. 无法接管时的标准结论

如果 `--connect` 不可用，要输出以下类型结论：

- 当前无法自动接管用户已经打开的结算系统新窗口。
- 根因是用户当前 Chrome 进程未开启远程调试端口。
- 这不是业务代码问题，而是自动化接管前置条件缺失。
- 当前问题若从用户截图已能确认，可先基于截图和代码修复结果给出结论。
- 同时要为下次验证准备标准启动流程。

## 下次稳定复用的启动方案

指导用户使用带远程调试端口的 Chrome 启动门户和结算系统。

Linux 示例：

```bash
google-chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/trae-chrome-debug
```

说明：

- `--remote-debugging-port=9222`：允许自动化工具接管当前浏览器。
- `--user-data-dir=/tmp/trae-chrome-debug`：建议使用独立临时目录，避免污染用户主浏览器配置。

用户后续应在这个浏览器里完成：

1. 登录门户
2. 从门户跳入结算系统
3. 打开需要验证的业务页面和弹窗

随后自动化侧执行：

```bash
browser-use --connect state
```

这样即使门户新开了结算系统窗口，也仍然属于同一个可接管的 Chrome 进程。

## 验证要点

针对结算系统弹窗类问题，至少检查以下内容：

1. 目标弹窗是否真正打开。
2. 右上角导出按钮是否完整可见。
3. 按钮是否未被遮挡、裁剪或超出容器边界。
4. 点击后导出菜单是否出现。
5. 必要时截图留证。

## 结论模板

### 可自动验证成功

- 已成功接管用户当前结算系统窗口。
- 已定位到目标弹窗。
- 已确认导出按钮可见/不可见。
- 已确认点击行为正常/异常。

### 当前无法自动接管

- 已确认门户跳转后新开了结算系统窗口。
- 当前 Chrome 未开启远程调试端口，无法接管该新窗口。
- 已基于截图/代码给出当前判断。
- 已提供下次可稳定自动验证的浏览器启动方式。

## 注意事项

- 不要在未说明影响的情况下强制关闭用户当前浏览器。
- 不要假设 `--profile "Default"` 等同于“接管用户当前已打开的浏览器窗口”。
- `--profile` 更适合复用配置和登录态，不保证接管“已经打开的新窗口”。
- 真正需要跨门户跳转和新窗口验证时，优先使用 `--connect` 配合远程调试端口。
