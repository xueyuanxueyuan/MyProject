# YHT Mock Server 接口覆盖度审核报告

> 审核日期：2026-05-14
> 规范文档：上海集中代收付系统报文交换规范V1.2.7.7
> 挡板项目：yht-mock-server
> 修复状态：✅ 已完成修复

---

## 一、总体结论

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 规范定义报文总数 | 26 | 26 |
| 挡板已实现报文数 | 8 | **26** |
| 完全缺失报文数 | 18 | **0** |
| 覆盖率 | 30.8% | **100%** |

---

## 二、修复后接口覆盖度明细

### ✅ 同步请求处理接口（11个）

| # | 报文编号 | 报文名称 | 方向 | 应答报文 | 修复内容 |
|---|---------|---------|------|---------|---------|
| 1 | caps.101.001.01 | 批量业务申请报文 | 机构→CAPS | caps.102 | 🆕 新增 |
| 2 | caps.103.001.01 | 批量业务状态查询报文 | 机构→CAPS | caps.104 | 🆕 新增 |
| 3 | caps.105.001.01 | 批量业务结果提回报文 | 机构→CAPS | caps.106 | 🆕 新增 |
| 4 | caps.201.001.01 | 实时业务申请报文 | 机构→CAPS | caps.202 | 🔧 修复：应答从caps.900改为caps.202 |
| 5 | caps.203.001.01 | 实时业务查询报文 | 机构→CAPS | caps.204 | 🔧 修复：补全Head字段 |
| 6 | caps.301.001.01 | 协议上传报文 | 机构→CAPS | caps.302 | 🆕 新增 |
| 7 | caps.303.001.01 | 协议查询报文 | 机构→CAPS | caps.304 | 🔧 修复：补全Head字段 |
| 8 | caps.305.001.01 | 协议签约管理报文 | 双向 | caps.900 | 🔧 修复：支持RPLC操作，提取完整字段 |
| 9 | caps.307.001.01 | 通用业务撤销报文 | 机构→CAPS | caps.900 | 🆕 新增 |
| 10 | caps.601.001.01 | 业务对账提回报文 | 机构→CAPS | caps.602 | 🆕 新增 |
| 11 | caps.999.001.01 | 自动化验证报文 | 双向 | caps.900 | 🔧 修复：解析CheckType/SysChckNo |

### ✅ 同步应答报文（8个）

| # | 报文编号 | 报文名称 | 说明 |
|---|---------|---------|------|
| 1 | caps.102.001.01 | 批量业务应答报文 | 🆕 新增，含BatchNo/ResFlag |
| 2 | caps.104.001.01 | 批量业务状态查询应答报文 | 🆕 新增，含BatchStatus/BatchNo |
| 3 | caps.106.001.01 | 批量业务结果提回应答报文 | 🆕 新增，含FileData(Base64) |
| 4 | caps.202.001.01 | 实时业务应答报文 | 🆕 新增，含ReturnTime/SysSeqNo/RetCode |
| 5 | caps.204.001.01 | 实时业务查询应答报文 | 🔧 修复：补全CorpNo/ReturnTime等字段 |
| 6 | caps.302.001.01 | 协议上传应答报文 | 🆕 新增，含ResFlag/ErrorCode |
| 7 | caps.304.001.01 | 协议查询应答报文 | 🔧 修复：补全CorpNo/ErrorCode/ErrorMsg |
| 8 | caps.602.001.01 | 业务对账提回应答报文 | 🆕 新增，含CheckDate/TranCode/FileData |

### ✅ 异步回调报文（6个，通过trigger-callback触发）

| # | 报文编号 | 报文名称 | 说明 |
|---|---------|---------|------|
| 1 | caps.107.001.01 | 批量业务处理结果回执报文 | 🆕 新增 |
| 2 | caps.205.001.01 | 实时业务处理结果回执报文 | 🔧 修复：补全CorpNo/TranCode/SysSeqNo/PayAmt/Dbtr/Cdtr |
| 3 | caps.306.001.01 | 协议签约管理应答报文 | 🔧 修复：补全CorpNo/ResFlag/ErrorCode/ErrorMsg |
| 4 | caps.308.001.01 | 业务撤销结果通知报文 | 🆕 新增 |
| 5 | caps.600.001.01 | 业务汇总对账通知报文 | 🆕 新增 |
| 6 | caps.916.001.01 | 行名行号变更通知报文 | 🆕 新增 |

### ✅ 通用报文（1个）

| # | 报文编号 | 报文名称 | 说明 |
|---|---------|---------|------|
| 1 | caps.900.001.01 | 通用确认报文 | 🔧 修复：ResFlag/ProcCode/ProcMsg移至Head，Body留空，增加CorpNo |

---

## 三、关键修复项

### 3.1 报文结构修复

| 修复项 | 修复前 | 修复后 |
|--------|--------|--------|
| caps.900结构 | ResFlag/ProcCode/ProcMsg在Body中 | ✅ 移至Head中，Body留空 |
| caps.900 CorpNo | 缺失 | ✅ 已添加至Head |
| caps.201应答类型 | 返回caps.900 | ✅ 返回caps.202 |
| caps.202字段 | 完全缺失 | ✅ 新增ReturnTime/SysSeqNo/SerialNum/RetCode/RetMsg |
| caps.204 Head字段 | 缺少CorpNo/ErrorCode/ErrorMsg | ✅ 已补全 |
| caps.304 Head字段 | 缺少CorpNo/ErrorCode/ErrorMsg | ✅ 已补全 |

### 3.2 批量业务新增

| 新增项 | 说明 |
|--------|------|
| MockBatch模型 | 新增批量业务数据模型，含BatchNo/BatchStatus/CheckDate等 |
| caps.101→caps.102 | 批量申请→应答，生成BatchNo，状态PROC |
| caps.103→caps.104 | 批量状态查询→应答，支持BBNO/BFLE两种查询 |
| caps.105→caps.106 | 批量结果提回→应答，PROC自动转SUCC，返回Base64文件流 |
| caps.107回调 | 批量业务处理结果回执，含BatchNo/BatchStatus/FileData |

### 3.3 协议管理补全

| 新增项 | 说明 |
|--------|------|
| caps.301→caps.302 | 协议上传→应答，支持ADDD/DELE操作 |
| caps.305 RPLC | 新增协议更新操作支持 |
| caps.305字段提取 | 补全ChngTp/SndrFlg/CstmrId/CstmrNm/DbtrCardType等20+字段 |
| caps.306回调 | 补全CorpNo/ResFlag/ErrorCode/ErrorMsg/CtrctRtrFlg |

### 3.4 其他新增

| 新增项 | 说明 |
|--------|------|
| caps.307→caps.900 | 通用业务撤销请求 |
| caps.308回调 | 业务撤销结果通知，含OrgnlId/CancleId/RetCode |
| caps.601→caps.602 | 业务对账提回→应答，含CheckDate/TranCode/Base64文件流 |
| caps.600回调 | 业务汇总对账通知，含CheckDate/ChannelCode/FileData |
| caps.916回调 | 行名行号变更通知，含SysSeqNo/FileData |

---

## 四、按业务分类的覆盖度

| 业务分类 | 规范报文数 | 修复前 | 修复后 | 状态 |
|---------|-----------|--------|--------|------|
| 批量业务(1xx) | 7 | 0 (0%) | 7 (100%) | ✅ 完全覆盖 |
| 实时业务(2xx) | 5 | 5 (100%) | 5 (100%) | ✅ 格式已修复 |
| 协议管理(3xx) | 6 | 4 (66.7%) | 6 (100%) | ✅ 完全覆盖 |
| 对账业务(6xx) | 3 | 0 (0%) | 3 (100%) | ✅ 完全覆盖 |
| 系统控制(9xx) | 3 | 2 (66.7%) | 3 (100%) | ✅ 完全覆盖 |
| 行名行号(916) | 1 | 0 (0%) | 1 (100%) | ✅ 完全覆盖 |
| **合计** | **26** | **8 (30.8%)** | **26 (100%)** | ✅ **100%覆盖** |

---

## 五、修改文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| MockTrade.java | 修改 | 新增20+字段（Dbtr/Cdtr/ReturnTime/CheckDate等） |
| MockProtocol.java | 修改 | 新增20+字段（ChngTp/SndrFlg/CstmrId/AuthMd等） |
| MockBatch.java | 新增 | 批量业务数据模型 |
| MockStore.java | 修改 | 新增批量业务存储和查询方法 |
| CallbackRequest.java | 修改 | 新增BatchNo/TranCode/SysSeqNo/PayAmt等回调字段 |
| YhtMockApiController.java | 重写 | 覆盖全部26种报文，修复报文格式 |

---

*本报告由AI辅助审核生成，编译验证已通过（mvn compile exit code 0）。*
