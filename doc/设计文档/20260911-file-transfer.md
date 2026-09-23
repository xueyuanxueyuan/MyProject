# 嘉兴住建部文件传输封装

## 方法调用分析与封装契约

原 FileUploadDemo 依次调用 CompressUtil.zipFile、FileTransferUtil.MD5、DESedeUtil.encryptFile、FileTransferUtil.fileUpload；原 FileDownloadDemo 调用 fileDownload、decryptFile、MD5 比较、unzipFile。原工具使用固定流水和落地路径，返回签名仅打印比较结果，失败可能返回 null。

现上传入口为 upload(String txCode, String yhdm, File sourceFile)，返回 UploadResult（getFileId、getDigest）。下载入口为 download(String txCode, String yhdm, String fileId, String digest, File destinationDirectory)，返回解压目录。银行代码和摘要是协议必要参数，因此不能省略。上传支持普通文件，与原 1.pdf 示例一致；下载目录必须尚不存在，避免覆盖已有文件。

上传执行 ZIP → Base64(MD5(ZIP)) → 3DES → multipart → 解密并校验响应。下载执行加密文件标识 → multipart → 解密文件 → ZIP 摘要比较 → 安全解压。每次请求按 BDC501 规则生成 sendSeqNo：Constants.HSDWBH + 当前日期 yyMMdd + lsh.substring(1, 10)，其中 lsh 来自 LshTools.getLsh("zjjs_ywgl_jszllsh")。URL 与请求头使用同一流水。临时目录按调用隔离并清理。

## 配置与密钥

- gjj.zjb.file.url：必填，HTTP 文件服务根地址，不包含 /Refbdc-fileserver。现有配置中未找到此地址；不得复用 TCP 端口。本次未修改环境配置文件，需在启用功能的环境配置中提供实际地址。
- gjj.zjb.file.success-status：上传必填，服务端约定的上传成功状态。原 Demo 未提供枚举，不能猜测；测试中使用0只表示模拟协议，不代表真实服务已确认。
- gjj.zjb.file.tx-code：默认05，保留原 Demo；若复用结算密钥应按协议配置01，独立组合贷密钥使用05。需与实际签到密钥匹配。
- gjj.hsjg.hsdwbh：沿用已有发送节点配置。
- gjj.zjb.key.redisKey：沿用已有 Redis 键名，读取 JSON 的 zjbKey。
- gjj.zjb.client.CHARSET：原始密钥字符串编码，缺省 UTF-8；转换成 Base64 后交给 DESedeUtil。每次传输只读取一次密钥，不记录密钥内容。

FileTransferConfiguration 位于 cn.capinfo.gjj 扫描范围内，将工具注册为 Spring Bean。连接超时10秒、读取超时120秒；未自动重试上传，避免重复提交。HTTP异常和业务失败向调用方抛出，不吞掉返回 null。

## 业务调用

业务类注入 FileTransferUtil 后调用：

```java
FileTransferUtil.UploadResult uploaded = fileTransferUtil.upload(txCode, yhdm, sourceFile);
String fileId = uploaded.getFileId();
String digest = uploaded.getDigest();
File directory = fileTransferUtil.download(txCode, yhdm, fileId, digest, destinationDirectory);
```

上述上传返回的标识和摘要应通过业务交易传递给下载方。下载解压目录包含原文件名；原1.pdf示例对应 new File(directory, "1.pdf")。

两个 Demo 均保留 main 入口，反射启动现有 CxtjApplication 后获取 Bean；必须使用包含 app 模块的运行类路径，并加载正常项目配置和 Redis，不能只用 busi 模块单独运行。上传参数为银行代码、源文件路径，下载参数为银行代码、文件标识、文件摘要、解压目录。后续参数原样传入 Spring 启动，可指定已有 profile。

## 验证记录（2026-09-11）

- 工具链：vfox use java@17.0.2+8 成功；maven@3.9.14 未安装，实际使用已安装 Maven3.9.16，mvn -v 确认 Java17.0.2。
- 模块 Maven 编译通过。首次 test 默认跳过测试，不作为测试通过证据。
- 显式运行 mvn -f <busi模块>/pom.xml -Dtest=FileTransferUtilTest -Dmaven.test.skip=false -DskipTests=false test：5项测试，0失败、0错误、0跳过。
- 覆盖上传/下载原文件内容一致、请求流水符合 BDC501 格式且头与 URL 一致、Redis缺失、响应长度非法、响应签名错误、业务失败状态、下载摘要错误及ZIP路径穿越。
- git diff --check 通过。根目录不存在 scripts/guardrails/agent-delivery-guardrail.sh，使用差异、占位符、硬编码密钥及UTF-8/BOM检查替代。
- 未连接真实住建部，未验证真实成功状态枚举、真实 TxCode/密钥组合、应用启动以及大文件压力。Demo不会脱离应用配置独立传输。
- 未提交、未推送，不改变用户已有暂存状态。新增配置类和测试需随目标类一起纳入后续提交。

