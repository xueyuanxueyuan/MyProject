# 柜面购房提取 GJJ 外部接口设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v0.1 |
| 文档状态 | 草稿 |
| 编制日期 | 2026-08-24 |
| 适用范围 | 柜面购房提取首期开发 |
| 关联事项 | 购买自住住房提取住房公积金 |
| 关联文档 | `智能审批平台所需GJJ接口定位说明-v0.1.md`、`柜面购房提取字段来源映射说明-v0.1.md`、`柜面购房提取流程与步骤接口映射说明-v0.1.md` |

## 1. 文档说明

本文件聚焦“柜面购房提取”首期闭环中优先要接的 `P0` 外部接口，先形成一版开发可用的详细设计基线。

当前纳入的接口包括：

1. `findFullInfo`
2. `getGrYhzhxx`
3. `getTqed`
4. `getZdktqje`
5. `listDzyxGndCllxByGndbm`
6. `uploadAndSaveCjmx`
7. `tqsxSq`
8. `tqsxSp`

## 2. `findFullInfo` 个人全量信息查询

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-001 |
| 提供方 | `capinfo-gjj-busi-grxx-grxxcx-basic-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/grxxcx/personInfo/findFullInfo` |
| 来源系统 | GJJ |
| 调用时机 | 身份证识别后初始化受理页 |

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `zjhm` | string | 是 | 证件号码 |
| `zjlx` | string | 否 | 证件类型，默认身份证 |

### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `grzh` | 个人账号 | 后续事项、额度、提交主键 |
| `grdjh` | 个人登记号 | 申请人与事项关联 |
| `xingming` | 姓名 | 申请人信息回显 |
| `dwzh` | 单位账号 | 单位信息回显 |
| `dwdjh` | 单位登记号 | 单位信息回显 |
| `dwmc` | 单位名称 | 单位信息回显 |

### 业务规则

1. 未查询到个人信息时，禁止进入正式受理提交流程。
2. 该接口为幂等读接口，可短超时重试 1 次。
3. `zjhm`、`xingming`、`grzh` 需按敏感信息处理。

## 3. `getGrYhzhxx` 银行账户信息查询

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-002 |
| 提供方 | `capinfo-gjj-busi-jcxx-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/jcxx/grxx/getGrYhzhxx/{id}` |
| 来源系统 | GJJ |
| 调用时机 | 结算信息初始化 |

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 个人银行账户主键或个人标识 |

### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `skkhh` | 收款开户行 | 结算信息默认回填 |
| `skzhmc` | 收款账户名称 | 默认提取人本人 |
| `skzhhm` | 收款账户号码 | 结算账号默认回填 |
| `jyzhlx` | 交易账户类型 | 账户类型回显 |

### 业务规则

1. 未查询到默认银行卡时，允许柜员手工录入。
2. `skzhhm` 页面展示必须脱敏。
3. 提交前仍需调用银行卡校验能力。

## 4. `getTqed` / `getZdktqje` 金额测算接口

### 4.1 `getTqed`

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-003 |
| 提供方 | `capinfo-gjj-busi-gjtq-tqsx-basic-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/tqsx/getTqed` |
| 来源系统 | GJJ |
| 调用时机 | 点击“计算可提金额” |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `grzh` | string | 是 | 个人账号 |
| `ywzlxbh` | string | 是 | 业务子类型编号 |
| `sxid` | string | 否 | 已存在事项时可带入 |

#### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `ktqje` | 可提金额 | 页面展示 |
| `tqed` | 当前提取额度 | 页面展示 |
| `msg` | 提示信息 | 友好提示 |

### 4.2 `getZdktqje`

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-004 |
| 提供方 | `capinfo-gjj-busi-gjtq-tqzf-basic-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `GET /api/v1/tqzf/getZdktqje/{grzh}/{sxid}` |
| 来源系统 | GJJ |
| 调用时机 | 计算上限金额、限制 `tqbj` 输入 |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `grzh` | string | 是 | 个人账号 |
| `sxid` | string | 是 | 事项 ID |

#### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `zdktqje` | 最大可提金额 | 限制输入上限 |

### 4.3 业务规则

1. 测算失败时不得直接提交业务。
2. `tqbj` 必须小于等于 `zdktqje`。
3. 金额测算日志需要保留，用于后续审计和争议定位。

## 5. `listDzyxGndCllxByGndbm` / `uploadAndSaveCjmx` 材料接口

### 5.1 `listDzyxGndCllxByGndbm`

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-005 |
| 提供方 | `capinfo-gjj-busi-dzyx-yxcj-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `GET /api/v1/yxcj/gndcllx/listDzyxGndCllxByGndbm/{gndbm}` |
| 来源系统 | GJJ |
| 调用时机 | `gndbm` 计算完成后初始化材料清单 |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `gndbm` | string | 是 | 功能点编码 |

#### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `cllx` | 材料类型编码 | 材料项标识 |
| `clmc` | 材料名称 | 页面展示 |
| `sfbt` | 是否必传 | 提交前校验 |

### 5.2 `uploadAndSaveCjmx`

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-006 |
| 提供方 | `capinfo-gjj-busi-dzyx-dzda-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/dzda/ywjk/uploadAndSaveCjmx` |
| 来源系统 | GJJ |
| 调用时机 | 柜面上传影像材料 |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ywslbh` | string | 是 | 业务受理编号 |
| `gndbm` | string | 是 | 功能点编码 |
| `cllx` | string | 是 | 材料类型编码 |
| `file` | file/base64 | 是 | 上传文件 |

#### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `fileId` | 文件标识 | 回显与后续预览 |
| `cjmxId` | 采集明细标识 | 材料明细管理 |
| `status` | 上传状态 | 提交前判断 |

### 5.3 业务规则

1. `gndbm` 决定材料清单，不允许跳过分类直接上传。
2. 必传材料未齐或归档未完成时不允许提交。
3. 文件上传失败不建议自动重试，避免重复上传。

## 6. `tqsxSq` 提取事项申请

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-007 |
| 提供方 | `capinfo-gjj-busi-gjtq-tqsx-basic-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/tqsx/tqsxSq` |
| 来源系统 | GJJ |
| 调用时机 | 柜面点击提交业务 |

### 请求参数

以下为当前首期最小提交字段基线：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fzywlx` | string | 是 | 父业务类型 |
| `ywzlxbh` | string | 是 | 业务子类型编号 |
| `tqyy` | string | 是 | 提取原因 |
| `grzh` | string | 是 | 个人账号 |
| `grdjh` | string | 是 | 个人登记号 |
| `zjlx` | string | 是 | 证件类型 |
| `zjhm` | string | 是 | 证件号码 |
| `xingming` | string | 是 | 姓名 |
| `dwzh` | string | 是 | 单位账号 |
| `dwdjh` | string | 是 | 单位登记号 |
| `dwmc` | string | 是 | 单位名称 |
| `tqbj` | number | 是 | 本次提取金额 |
| `tqlx` | string | 是 | 提取类型 |
| `skkhh` | string | 是 | 收款开户行 |
| `skzhmc` | string | 是 | 收款账户名称 |
| `skzhhm` | string | 是 | 收款账户号码 |
| `jyzhlx` | string | 是 | 交易账户类型 |
| `cqrxm` | string | 是 | 产权人姓名 |
| `cqrzjlx` | string | 是 | 产权人证件类型 |
| `cqrzjhm` | string | 是 | 产权人证件号码 |
| `fwxz` | string | 是 | 房屋性质 |
| `gflx` | string | 是 | 购房类型 |
| `fwzl` | string | 是 | 房屋坐落 |
| `fwzj` | number | 否 | 房屋总价 |
| `fwmj` | number | 否 | 房屋面积 |
| `fwssqy` | string | 否 | 房屋所属区域 |
| `cqzh` | string | 否 | 不动产权证号 |
| `wqhth` | string | 否 | 网签合同号 |
| `wqhtbh` | string | 否 | 网签合同编号 |
| `wqbarq` | string | 否 | 网签备案日期 |
| `gjjdke` | number | 否 | 公积金贷款额 |
| `tqgjjzfsf` | string | 否 | 是否首付款提取 |
| `sfkje` | number | 否 | 首付款金额 |

### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `ywslbh` | 业务受理编号 | 贯穿全流程主业务号 |
| `ywlsh` | 业务流水号 | 下游流水跟踪 |
| `sxid` | 事项 ID | 详情查询、审批关联 |
| `status` | 提交状态 | 页面提交结果 |

### 业务规则

1. `tqsxSq` 虽然签名是 `Map<String,Object>`，但当前事项对应 `TqsxGmzfReqDTO`。
2. `cqzh` 和 `wqhth` 至少二选一，不能同时为空。
3. 当 `tqgjjzfsf=1` 时，必须同时传 `sfkje`。
4. 提交前应先完成在途校验、实时资格校验、银行卡校验和材料完整性校验。
5. 接口成功后会生成 `ywslbh`、`ywlsh` 并落待审批业务。

## 7. `tqsxSp` 提取事项审批

| 项目 | 内容 |
| --- | --- |
| 接口编号 | API-GFTQ-P0-008 |
| 提供方 | `capinfo-gjj-busi-gjtq-tqsx-basic-svc` |
| 调用方 | 平台聚合后端 |
| 协议与地址 | `POST /api/v1/tqsx/tqsxSp` |
| 来源系统 | GJJ |
| 调用时机 | 人工审批提交 |

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ywslbh` | string | 是 | 业务受理编号 |
| `splx` | string | 是 | 审批类型 |
| `spyj` | string | 否 | 审批意见 |
| `spzt` | string | 是 | 审批状态 |
| `spwc` | string | 是 | 审批完成标识 |

### 关键响应字段

| 字段 | 说明 | 平台用途 |
| --- | --- | --- |
| `status` | 审批结果状态 | 页面审批结果 |
| `msg` | 结果说明 | 页面提示 |

### 业务规则

1. 审批动作必须基于已存在的 `ywslbh`。
2. 审批回写失败时，平台应允许重试，并记录失败日志。
3. 审批日志应保留操作员、时间、审批意见和审批结果。

## 8. 首期调用顺序建议

首期建议按以下顺序调用：

1. `findFullInfo`
2. `getGrYhzhxx`
3. `getTqed`
4. `getZdktqje`
5. `listDzyxGndCllxByGndbm`
6. `uploadAndSaveCjmx`
7. `tqsxSq`
8. `tqsxSp`

## 9. 当前仍需继续补充的内容

1. `findFullInfo` 和 `getGrYhzhxx` 的真实完整返回字段样例。
2. `getTqed` 与 `getZdktqje` 的真实响应结构和规则口径。
3. `uploadAndSaveCjmx` 的文件上传协议细节。
4. `tqsxSq` 提交报文的完整示例。
5. `tqsxSp` 审批状态枚举和审批类型枚举。

## 10. 结论

当前这 8 个接口已经足以支撑“柜面购房提取”首期主闭环的外部对接设计。下一步建议继续输出：

1. 平台内部聚合接口设计
2. 开发任务拆分
3. 联调记录模板实例化
