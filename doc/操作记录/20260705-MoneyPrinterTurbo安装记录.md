# 20260705-MoneyPrinterTurbo安装记录

## 背景

用户要求安装 MoneyPrinterTurbo。

## 官方安装要求

根据 MoneyPrinterTurbo 官方 README，当前推荐路径包括：

1. Windows 用户优先使用便携一键包
2. 源码部署推荐使用 `uv` 管理 Python 3.11 环境
3. 源码启动 WebUI 前需要执行 `uv sync --frozen`
4. 项目根目录需要存在 `config.toml`
5. 视频处理链路需要 ImageMagick，必要时配置 ffmpeg

## 本机环境检查

已确认可用：

- Git：`2.55.0.windows.2`
- Node.js：`v18.20.8`
- npm：`10.8.2`
- winget：`v1.29.280`

当前不可用或未完成：

- Python：PATH 中仅有 Microsoft Store alias，真实 Python 不可用
- `py`：不可用
- Docker：不可用
- 系统级 `uv`：不可用
- ffmpeg：不可用
- ImageMagick：不可用

## 安装动作

### 1. 隔离目录选择

最初尝试安装到：

- `d:\Probject\MoneyPrinterTurbo`

该路径被当前沙箱限制拒绝，因此改为工作区内隔离目录：

- `d:\Probject\Gjj\external\MoneyPrinterTurbo`

### 2. 源码克隆

第一次克隆因 GitHub 网络连接重置失败，留下不完整 `.git` 目录。

随后清理不完整目录，并使用浅克隆重试：

```powershell
git -c http.version=HTTP/1.1 clone --depth 1 https://github.com/harry0703/MoneyPrinterTurbo.git d:\Probject\Gjj\external\MoneyPrinterTurbo
```

最终克隆成功：

- 当前提交：`63113a3`
- 关键文件已存在：
  - `pyproject.toml`
  - `uv.lock`
  - `requirements.txt`
  - `README.md`
  - `README-en.md`
  - `webui.bat`
  - `main.py`

### 3. 配置文件初始化

已从示例配置复制生成：

- `d:\Probject\Gjj\external\MoneyPrinterTurbo\config.toml`

该文件被 MoneyPrinterTurbo 自身 `.gitignore` 排除，不会进入其项目提交。

### 4. uv 本地可执行文件准备

系统级 `uv` 不可用，winget 静默安装受当前环境限制未成功。

已改为下载官方 Windows x64 便携版 uv，并解压到：

- `d:\Probject\Gjj\external\tools\uv.exe`

验证结果：

- `uv 0.11.26`

### 5. Python 安装尝试

尝试通过 `uv python install 3.11` 下载 Python 3.11，但下载过程长时间停留，已终止。

随后尝试下载 Python 3.11.9 官方安装器到：

- `d:\Probject\Gjj\external\tools\python-3.11.9-amd64.exe`

但下载得到的文件大小异常，仅约 9MB，未形成可用 Python 安装结果。

因此当前尚未完成 Python 运行时与依赖安装。

## 当前安装结果

已完成：

- MoneyPrinterTurbo 源码克隆
- `config.toml` 初始化
- 便携 `uv.exe` 准备
- 官方安装说明核对

未完成：

- Python 3.11 运行时安装
- `uv sync --frozen` 依赖安装
- ffmpeg / ImageMagick 安装
- WebUI/API 实际启动验证

## 当前可用路径

MoneyPrinterTurbo 源码目录：

```text
d:\Probject\Gjj\external\MoneyPrinterTurbo
```

便携 uv：

```text
d:\Probject\Gjj\external\tools\uv.exe
```

后续若 Python 3.11 可用，可在项目目录执行：

```powershell
cd d:\Probject\Gjj\external\MoneyPrinterTurbo
$env:UV_CACHE_DIR='d:\Probject\Gjj\external\.uv-cache'
d:\Probject\Gjj\external\tools\uv.exe sync --frozen
d:\Probject\Gjj\external\tools\uv.exe run streamlit run .\webui\Main.py --browser.gatherUsageStats=False --server.showEmailPrompt=False
```

## 后续建议

推荐下一步二选一：

1. 使用官方 Windows 便携包 `MoneyPrinterTurbo-Portable-Windows-1.3.0.7z`
   - 体积约 1GB
   - 需要可用的 7z 解压工具
   - 更适合 Windows 快速体验
2. 继续源码安装
   - 需要先解决 Python 3.11 下载/安装
   - 再执行 `uv sync --frozen`
   - 再配置 ImageMagick 与 ffmpeg

## 结论

当前已完成 MoneyPrinterTurbo 的源码级安装准备，但运行时依赖尚未完全安装，因此还未达到可启动 WebUI/API 的状态。
