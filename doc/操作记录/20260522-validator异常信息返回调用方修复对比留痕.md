# validator 异常信息返回调用方修复对比留痕

## 1. 修复前

批量业务调用链中存在两类问题：

### 1.1 `YhtException` 直接冒出

当 `YhtBatchTradeRequestValidator` 校验失败并抛出 `YhtException` 时：

1. `YhtServiceImpl.submitBatchTrade(...)` 不拦截
2. `YhtYwServiceImpl` 也不转换
3. 调用方拿到的不是业务层统一异常

### 1.2 失败响应被忽略

当 `yhtService.submitBatchTrade(reqDTO)` 返回：

```java
YhtRespDTO.fail("40000", "收款明细开户行号不能为空")
```

原逻辑不会检查 `success=false`，因此：

1. 不抛异常
2. 调用方收不到具体错误消息

## 2. 修复后

在 `YhtYwServiceImpl` 新增统一处理方法：

1. `submitBatchTradeOrThrow(reqDTO)`

新行为：

1. 返回成功 DTO：继续执行
2. 返回失败 DTO：`throw new BizException(errorMsg)`
3. 抛出 `YhtException`：`throw new BizException(errorMessage)`

## 3. 对调用方的效果

修复后，调用方会直接拿到 validator 的具体消息，例如：

1. `批量明细不能为空`
2. `收款明细开户行号不能为空`

而不是：

1. 一户通内部异常类型直接透出
2. 什么异常都不抛
3. 丢失原始校验信息

## 4. 新增测试覆盖

`YhtYwServiceImplTest`

新增覆盖：

1. `YhtException -> BizException(message)`
2. `YhtRespDTO.fail -> BizException(errorMsg)`

## 5. 变更文件

1. `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/.../YhtYwServiceImpl.java`
2. `capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi/.../YhtYwServiceImplTest.java`  
