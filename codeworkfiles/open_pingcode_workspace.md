# 打开 PingCode 工作台操作记录

## 需求背景
用户要求打开其专属的 PingCode 页面，并且考虑到我们之前封装的自动化筛选技能（`pingcode-ui-filter`）需要通过 Chrome CDP 协议来操作，因此我们在打开浏览器时，需要开启远程调试端口。

## 执行步骤

1. **终端命令启动 Chrome**：
   为了让后续可以通过 WebSocket 与浏览器通信（端口设为 `9222`），并在一个独立的用户数据目录中运行（避免与用户平时使用的浏览器实例发生冲突），执行了以下命令：
   
   ```bash
   google-chrome-stable --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-debug https://pingcode.capinfo.com.cn/pjm/projects/GJJ-TSHS/fwwFWqFj/WlytPXXa
   ```

2. **后台运行保障**：
   命令已经以非阻塞（Long Running Process）的形式在独立的终端后台持续运行。

## 结果与下一步
- Chrome 浏览器已经成功被唤起，并直接打开了目标 PingCode 链接。
- CDP 调试端口已就绪。
- 用户可以直接在打开的浏览器中查看数据，或者随时调用我们的自动化技能对刚刚打开的页面进行过滤筛选操作。