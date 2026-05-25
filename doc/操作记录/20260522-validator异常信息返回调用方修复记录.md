# validator 异常信息返回调用方修复记录

## 1. 背景

用户要求：当一户通批量业务在 `YhtBatchTradeRequestValidator` 校验失败时，需要把具体异常信息返回给调用方，而不是被吞掉、原样冒出不一致异常，或只返回空泛失败。

本次重点核对链路：

1. `YhtBatchTradeRequestValidator`
2. `YhtServiceImpl.submitBatchTrade(...)`
3. `YhtYwServiceImpl.addFkywPlfkjy(...)`
4. `YhtYwServiceImpl.addSkywPlskjy(...)`

## 2. 根因

根因集中在 `YhtYwServiceImpl`：

### 2.1 `YhtException` 没有翻译成主业务异常

当 `validator` 直接抛出 `YhtException` 时，异常会一路冒出，调用方收到的是一户通内部异常类型，而不是当前结算业务层统一使用的 `BizException`。

### 2.2 `YhtRespDTO.fail(...)` 返回结果被忽略

即使 `submitBatchTrade(...)` 返回了失败响应，原有 `YhtYwServiceImpl` 也没有检查 `success=false`，导致失败消息没有继续抛给调用方。

## 3. 修复方案

在 `YhtYwServiceImpl` 中新增统一提交入口：

`submitBatchTradeOrThrow(YhtBatchTradeReqDTO reqDTO)`

行为如下：

1. 正常调用 `yhtService.submitBatchTrade(reqDTO)`
2. 若返回 `YhtRespDTO.success=true`，直接通过
3. 若返回 `YhtRespDTO.success=false`，抛出 `BizException(errorMsg)`
4. 若底层抛出 `YhtException`，转成 `BizException(errorMessage)`

这样调用方无论遇到：

1. `validator` 直接抛异常
2. 一户通服务层返回失败 DTO

都能拿到明确的校验失败信息。

## 4. TDD 过程

### 4.1 RED

在 `YhtYwServiceImplTest` 中新增 2 个失败用例：

1. `addFkywPlfkjyShouldThrowBizExceptionWithValidatorMessageWhenYhtThrows`
2. `addSkywPlskjyShouldThrowBizExceptionWithFailResponseMessage`

初次执行结果：

1. 第一个测试失败，实际抛出的是 `YhtException`
2. 第二个测试失败，实际没有抛出任何异常

这与用户反馈一致。

### 4.2 GREEN

补充统一异常翻译后重新执行，4 个测试全部通过。

## 5. 涉及文件

### 5.1 生产代码

1. `YhtYwServiceImpl.java`

### 5.2 测试代码

1. `YhtYwServiceImplTest.java`

## 6. 验证结果

定向测试命令：

```bash
eval "$(python3 - <<'PY'
import json
print(json.load(open('/home/source/.version-fox/tmp/20260522-1021723/env-state.json'))['cached_output'])
PY
)" && mvn -pl capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi -am -Dtest=YhtYwServiceImplTest -Dmaven.test.skip=false -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

验证结果：

1. `YhtYwServiceImplTest` 共 4 个测试全部通过
2. 守卫扫描通过
3. 守卫复核命令通过

## 7. 补充说明

IDE 当前对 `YhtYwServiceImpl.java` 仍有 `Name clash` 诊断，但同时提示该文件被识别为 `non-project file`。  
本次 Maven 编译与测试已通过，可确认该诊断属于当前 IDE 项目归属误报，不是实际编译错误。  
