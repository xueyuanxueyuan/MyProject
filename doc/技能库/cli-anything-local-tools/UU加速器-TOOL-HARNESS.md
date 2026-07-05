# UU加速器 TOOL HARNESS

## 1. 工具基本信息

- 工具名称：UU加速器
- 可执行文件名：`uu_launcher.exe`、`uu.exe`
- 典型安装路径：
  - `C:\Program Files (x86)\Netease\UU\uu_launcher.exe`
  - `C:\Program Files (x86)\Netease\UU\5226\uu.exe`
- 调用方式：
  - 隐藏 CLI（优先）
  - GUI 自动化（回退）
  - 原生可执行文件启动（仅拉起应用）

## 2. 探测命令

```powershell
Get-ItemProperty 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*' |
  Where-Object { $_.DisplayName -match 'UU|网易UU|加速器' } |
  Select-Object DisplayName, DisplayVersion, InstallLocation, DisplayIcon

Get-ChildItem 'C:\Users\<用户名>\AppData\Roaming\Netease\UU' -File |
  Select-Object Name, Length, LastWriteTime

& 'C:\Program Files (x86)\Netease\UU\5226\7za.exe' l 'C:\Users\<用户名>\AppData\Roaming\Netease\UU\netease-uu-booster.7z'

& 'C:\Users\<用户名>\AppData\Local\Temp\uu-booster-docs\netease-uu-booster\bin\uu-cli.exe' --json doctor
```

说明：
- 注册表可确认版本与启动器路径。
- `AppData\Roaming\Netease\UU` 下存在 `netease-uu-booster.7z`，其中包含可用的 `uu-cli.exe` 与完整命令文档。
- `uu_launcher.exe --help` 依然不提供可读帮助，但隐藏 CLI 已足够支持游戏搜索、启动加速与状态查询。

## 3. 只读能力

- 查看版本：读取 `uu_launcher.exe` / `uu.exe` 的 `VersionInfo`
- 查看状态：`Get-Process uu, uu_launcher`
- 查看帮助：优先阅读 `netease-uu-booster.7z` 内文档，或执行 `uu-cli.exe --json doctor`
- 列出现有资源：读取安装目录与 `AppData\Roaming\Netease\UU` 缓存、日志、配置文件

## 4. 最小可用调用

```powershell
& 'C:\Users\<用户名>\AppData\Local\Temp\uu-booster-docs\netease-uu-booster\bin\uu-cli.exe' --json doctor
```

说明：
- 这是当前验证过的最小可用调用。
- 当 `doctor` 返回 `status=ready` 后，可继续调用 `games/start/status` 完成完整加速闭环。

## 5. 常见任务映射

| 用户意图 | 推荐命令 | 输出校验 |
|---|---|---|
| 打开 UU加速器 | `uu-cli.exe --json start` 或启动器绝对路径 | `doctor` 或进程查询成功 |
| 确认版本 | 读取可执行文件 `VersionInfo` | 返回 `6.11.1.632` 等版本号 |
| 查安装路径 | 读取卸载注册表项 | 返回 `DisplayIcon` 或安装目录 |
| 搜索游戏 | `uu-cli.exe --json games --search "<关键词>"` | 返回 `count` 与 `games[]` |
| 自动加速指定游戏 | `uu-cli.exe --json start --id <游戏ID>` | `status --id <游戏ID>` 返回 `starting/boosting` |
| 查询加速状态 | `uu-cli.exe --json status [--id <游戏ID>]` | 返回 `boosters[]` 与 `status` |

## 6. 输出与验证

- 退出码预期：启动命令返回 `0`
- 成功输出关键字：JSON 中 `success=true`
- 失败输出关键字：路径不存在、权限不足、参数错误
- 产物文件路径检查：无
- 二次验证命令：

```powershell
& 'C:\Users\<用户名>\AppData\Local\Temp\uu-booster-docs\netease-uu-booster\bin\uu-cli.exe' --json status
```

## 7. 风险等级

- 只读：探测路径、版本、帮助、进程状态
- 项目内写操作：无
- 系统级写操作：无
- 需要管理员权限：通常不需要；若涉及驱动修复或网络层调整则可能需要
- 需要联网：实际加速功能需要联网

## 8. 对代理的专用约束

- 是否必须先读取帮助再调用：是
- 是否必须先创建临时目录：否
- 是否要求绝对路径：建议是
- 是否支持 JSON 输出：是
- 是否存在长驻进程：是
- 是否需要用户额外审批：纯启动通常不需要；盲点 GUI 点击建议先说明风险

## 9. 已知问题与回退方案

- 不在 PATH 时怎么办：直接使用绝对路径启动
- 帮助命令失败时怎么办：退回到 `netease-uu-booster.7z` 内的 Markdown 文档与 `uu-cli.exe --json doctor`
- GUI 必须打开时怎么办：优先改走隐藏 CLI；GUI 仅作回退方案
- 无头模式不可用时怎么办：停止继续盲点操作，改用 `uu-cli.exe`

## 10. 真实探测结论

- 本机已安装 `UU加速器 6.11.1.632`
- 启动器路径为 `C:\Program Files (x86)\Netease\UU\uu_launcher.exe`
- 已确认 `C:\Users\Sour\AppData\Roaming\Netease\UU\netease-uu-booster.7z` 内存在隐藏 CLI：`uu-cli.exe`
- 已验证 `uu-cli.exe --json doctor` 返回 `status=ready`
- 已验证 `uu-cli.exe --json games --search 'Dota 2'` 可检索到 `dota2` 与 `dota2国际服`
- 已验证 `uu-cli.exe --json start --id 518cbd94d5a35c9a74000008` 可启动 `DOTA2` 加速
- 已验证 `uu-cli.exe --json status --id 518cbd94d5a35c9a74000008` 返回 `status=boosting`、节点 `无锡联通13208`、延迟 `29ms`、丢包 `0.0`
