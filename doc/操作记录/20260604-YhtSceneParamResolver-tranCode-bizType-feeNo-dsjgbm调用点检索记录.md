# 20260604 YhtSceneParamResolver / tranCode / bizType / feeNo / dsjgbm 调用点检索记录

## 范围
- 主检索范围：`jiaxing/IdeaProjects/capinfo-gjj-busi-jshs/.../capinfo-gjj-busi-zjjs-ywgl-basic-svc-busi`
- 关键词：`YhtSceneParamResolver`、`tranCode`、`bizType`、`feeNo`、`dsjgbm`

## 结论摘要
- `YhtSceneParamResolver`：当前源码未落地，仅在设计/操作文档中出现。
- `tranCode`：实时交易在 `YhtYwServiceImpl` 由方法入参硬编码传入 `20601/20602`；批量交易多由 `bizType` 通过 `BizType.toTranCode` 推导；状态查询存在默认回退 `40502/20602`。
- `bizType`：在 `YhtYwServiceImpl` 中存在 `PAY/COLLECT` 直接硬编码；在控制器透传接口请求后，下游多按请求值使用。
- `feeNo`：存在多处硬编码默认值，主要是 `00001`（业务发起）与 `00000`（查询/兜底）。
- `dsjgbm`：签约接口由控制器透传到 `YhtSignReqDTO`，再由 `YhtServiceImpl` 调用 `YhtFeeMappingServiceImpl` 做静态映射；当前映射表为硬编码。

## 关键代码位置
- `YhtYwServiceImpl.buildRealtimeTradeReq`：`tranCode` 入参、`feeNo="00001"`
- `YhtYwServiceImpl.addFkywPlfkjy`：`bizType="PAY"`
- `YhtYwServiceImpl.addSkywPlskjy`：`bizType="COLLECT"`
- `YhtBatchTradeSubmitOrchestrator.submit`：`tranCode = BizType.toTranCode(reqDTO.getBizType())`
- `YhtBatchStatusOrchestrator`：批量状态查询/结果提回默认 `tranCode="40502"`、`feeNo="00000"`
- `YhtRealtimeStatusQueryOrchestrator`：实时状态查询默认 `tranCode="20602"`、`feeNo="00000"`
- `YhtController.sign`：`signReqDTO.setDsjgbm(reqDTO.getDsjgbm())`
- `YhtServiceImpl.sign`：`feeMappingService.mapToFeeNoList(reqDTO.getDsjgbm())`
- `YhtFeeMappingServiceImpl`：`dsjgbm -> feeNoList` 静态 Map
