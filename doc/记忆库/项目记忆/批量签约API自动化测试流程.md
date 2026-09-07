# 签约类接口（单笔 / 批量）API 自动化测试流程

> 归类：流程规则 / 跨会话常用结论
> 来源：2026-08-14 会话，依据 Apipost/Swagger 文档核实
> 验证状态：接口字段已与 Swagger 文档核对；脚本 `--dry-run` 已验证各模式（单笔 both/sign/query、批量 sign/query）请求体与文档示例一致。

## 0. 整体概览与模式选择（--flow）

脚本 `scripts/plqly_caller.py` 支持**两套流程**，由顶层 `--flow` 选择：

| 流程 | `--flow` | 子步骤 `--mode`（默认） | 说明 |
|------|----------|------------------------|------|
| 单笔签约 | `single` | `sign` / `query` / `both`（默认 both） | 明文账号，无密文；`both` = 签约后自动查状态 |
| 批量签约 | `batch` | `sign`（默认）/ `query` / `download` | 需加密字段 `gjjZipData` |

**模式选择逻辑（重要）：**
- 调用时必须**显式指定 `--flow single` 或 `--flow batch`**。
- 若**未指定 `--flow`**：脚本在**交互终端**会主动询问用户「single=单笔签约 / batch=批量签约」（默认 batch）；在**非交互环境**（如 Agent 自动执行）默认按 `batch` 处理并打印提示，建议始终显式指定。
- **Agent 侧约定**：当用户请求未明确"单笔/批量"时，先用 AskUserQuestion 向用户确认执行模式，再发起调用。
- 非法组合会被拦截：`--flow single --mode download`、`--flow batch --mode both` 均报错退出。

### 0.1 单笔签约测试取样规则（默认每家银行对公/对私各 1 条）

> 来源：2026-08-14 用户明确指示「没有特殊要求每家银行对公对私各取一条数据测即可，不用一次性全部数据都测」。

- **默认取样**：无特殊要求时，单笔签约测试每家银行仅取 **对私 1 条 + 对公 1 条**（数据齐全前提下）发起即可，**不要一次性跑全部账户**。
- **全量开关**：确需全量回归时，在驱动脚本加 `--all`（`tmp/run_single_sign.py --all`）跑该银行全部账户；`--per-type N` 可调整每条数（默认 1）。
- **数据齐全判定**（缺则无样本可取）：
  - 对私样本需「账号 + 户名 + 证件号码(zjh)」三者齐全；缺证件号码则对私无样本（单笔签约证件号必填）。
  - 对公样本需「账号 + 户名 + 统一社会信用代码(tyshxydm)」齐全；缺统一社会信用代码则对公无样本。
- **驱动脚本**：`tmp/run_single_sign.py` 已内置该默认（`--per-type 1`，`--all` 切全量）。每次运行落原始结果 `tmp/single_sign_report.json`，`tmp/gen_single_sign_report.py` 生成 `tmp/单笔签约测试报告.md`。
- **单笔签约必填项提醒**：`备注` 必须非空（统一传「账号签约」），否则报「[备注]不能为空」；兴业银行等还需补「手机号」否则报「该客户手机号码未输入」。

## 1. 协作分工（人 / AI）

1. **提供数据**：用户提供账号信息（批量=明细 JSON；单笔=zh/hm/zjlx/zjh/sjhm/zhlx/jyzhlx）。
2. **加密（仅批量）**：批量需用户线下用项目加密组件对明细加密 → 回传密文；**单笔无需加密**，直接给明文账号。
3. **AI 发起测试**：用 `scripts/plqly_caller.py` 按 `--flow` 发起并反馈响应。

## 2. 接口契约（Swagger 为准，图片 OCR 不可信）

### 2.1 批量签约（batch）

- 批量签约 `POST /api/v1/ywgl/plQyJysq` — 文档 `https://docs.apipost.net/docs/detail/6b3ee47eac72000?target_id=370609c5b39031&locale=zh-cn`
- 结果查询 `POST /api/v1/ywgl/plQyJyZtcx` — 文档 `https://docs.apipost.net/docs/detail/6b3f024dc069000?target_id=370609c5b39032&locale=zh-cn`
- 明细下载 `POST /api/v1/ywgl/plQyJyMxXz` — 文档 `https://docs.apipost.net/docs/detail/6b3f119a6472000?locale=zh-cn&target_id=370609c5b39033`

**批量签约请求体真实字段**

| 字段 | 含义 | 常用取值 |
|------|------|----------|
| qdlx | 渠道类型 | 01 |
| qdbm | 渠道编码（与银行无关，非银行/行码） | gt |
| jbjgbh | 经办机构编号 | 0101 |
| jbjgmc | 经办机构名称 | 市本级 |
| wdbh | 网点编号 | ""（批量传空串） |
| wdmc | 网点名称 | ""（批量传空串） |
| xtlx | 系统类型 | GJGL |
| ywslbh | 业务受理编号（同批次相同） | 与 ywlsh 同值 |
| ywlsh | 业务流水号（同批次相同） | 随机 20 位 |
| wjlx | 文件类型 | 1 |
| yhdm | 银行代码（6 位） | 工行 102000 / 农行 103000 |
| plxmbh | 批量项目编号 | "" |
| beizhu | 备注 | "" |
| gjjZipData | **加密后的明细密文** | 用户回传的密文 |

### 2.2 单笔签约（single）

- 单笔签约 `POST /api/v1/ywgl/getDbqyMessage` — 文档 `https://docs.apipost.net/docs/detail/6b40ac4cd879000?target_id=370609c5b39030&locale=zh-cn`
- 单笔签约状态查询 `POST /api/v2/ywgl/getQyztcxMessage` — 文档 `https://docs.apipost.net/docs/detail/6b40ae094070000?locale=zh-cn&target_id=370609c5b3902f`

**单笔签约请求体真实字段**（明文，无密文、无 ywlsh）

| 字段 | 含义 | 常用取值 |
|------|------|----------|
| qdlx | 渠道类型 | 01 |
| qdbm | 渠道编码（与银行无关） | gt |
| jbjgbh | 经办机构编号 | 0101 |
| jbjgmc | 经办机构名称 | 市本级 |
| wdbh | 网点编号 | **null**（单笔传 null） |
| wdmc | 网点名称 | **null**（单笔传 null） |
| xtlx | 系统类型 | GJGL |
| ywslbh | 业务受理编号 | 20 位，缺省脚本自动生成 |
| ywjbjgbh | 业务经办机构编号 | 0101 |
| ywqd | 业务渠道 | gt |
| zhlx | 账户类型 | 1=个人 2=单位 |
| yhdm | 银行代码（6 位） | 102000 |
| zh | 账号 | 明文 |
| hm | 户名 | 明文 |
| zjlx | 证件类型 | 01 |
| zjh | 证件号 | 明文 |
| sjhm | 手机号 | 明文 |
| jyzhlx | 交易账户类型 | 1 |
| beizhu | 备注 | 账号签约 |

**单笔状态查询请求体字段**（仅账号身份，**无 ywslbh / 无 ywlsh / 无密文 / 无 sjhm / 无 beizhu**）：
`qdlx / qdbm / jbjgbh / jbjgmc / wdbh(null) / wdmc(null) / xtlx / yhdm / zh / hm / zjlx / zjh / jyzhlx / zhlx`

> 注意：单笔状态查询路径在 **`/api/v2/`**，批量三个接口均在 **`/api/v1/`**。

### 2.3 重要修正（图片 OCR 是误读）

- 加密字段名是 **`gjjZipData`**，不是图片误读的 `gjjzjlpotdc`（仅批量签约用到；单笔无此字段）。
- 图片里出现的 `qdly/qdhm/jbjgph/wddz/xzbs/gjjz/ywlztz/tssmxx` 均为 OCR 幻觉，非真实字段。
- 文档示例里的 `"$schema": "替换银行编号"` 是 **swagger 描述泄漏，非真实字段**，调用时忽略。

### 2.4 固定字段（渠道 / 机构 / 网点 / 系统）—— 用示例默认值，无需更改

以下字段属于渠道、机构、网点、系统等环境级信息，**各接口统一使用示例默认值，不随测试数据变化、无需逐条修改**：

| 字段 | 示例默认值 | 含义 |
|------|-----------|------|
| qdlx | 01 | 渠道类型 |
| qdbm | gt | 渠道编码（**与银行无关**，不是银行/行码） |
| jbjgbh | 0101 | 经办机构编号 |
| jbjgmc | 市本级 | 经办机构名称 |
| wdbh | ""（批量）/ null（单笔） | 网点编号 |
| wdmc | ""（批量）/ null（单笔） | 网点名称 |
| xtlx | GJGL | 系统类型 |
| ywjbjgbh | 0101 | 业务经办机构编号（单笔专用） |
| ywqd | gt | 业务渠道（单笔专用） |

> 注：脚本中这些字段已设为带默认值的参数，正常调用无需传。仅当切换城市/机构环境且用户明确要求时再改。
> 注意：**`qdbm` 是渠道编码，不是银行标识**；银行由 `yhdm`（6 位银行代码）标识，例如工行 `yhdm=102000`。二者不要混淆。

## 3. 调用顺序与依赖关系

### 3.1 单笔签约链路

1. 调用**单笔签约** `getDbqyMessage`（传明文账号身份字段）。
2. **待其返回成功（HTTP 200）后**，再调用**单笔状态查询** `getQyztcxMessage`。
3. 状态查询**不依赖签约返回的流水号**，仅复用同一组账号身份信息（`yhdm/zh/hm/zjlx/zjh/jyzhlx/zhlx`）。
- 脚本已内置此顺序：`--flow single --mode both` 会先签约、签约非 200 则跳过查询；`--dry-run` 会顺序打印两份请求体。

### 3.2 批量签约链路

1. 批量签约 `plQyJysq`（含 `gjjZipData` 密文）。
2. 返回成功后，用同一 `ywslbh`/`ywlsh` 调**结果查询** `plQyJyZtcx`。
3. 当查询返回 `pczt = 1 或 2` 时，用同一 `ywslbh`/`ywlsh` 调**明细下载** `plQyJyMxXz`。

## 4. 关键注意点（易踩坑）

- **yhdm 必须是 6 位**（已确认，两流程通用校验）：工行 `102000`、农行 `103000`。非 6 位时脚本拦截并提醒是否给错，需 `--force` 才继续。早前口头说的 `10200`（5 位）是错的。
- **单笔无需加密，批量必须加密**：单笔直接传明文账号；批量必须把明细加密成 `gjjZipData` 密文再传。
- 单笔状态查询路径在 `/api/v2/`，批量均在 `/api/v1/`。
- 单笔流程**不需要 ywlsh**；`ywslbh` 为 20 位、缺省由脚本自动生成。
- 同一批次 `ywslbh`/`ywlsh` 相同（批量）；对私（zhlx=1）明细不填 `tyshxydm/zzjgdm/zzjgmc`，对公（zhlx=2）保留且 `zzjgmc=hm`（针对批量明细 JSON）。
- **结果查询复用签约的 ywslbh/ywlsh（批量）或账号身份（单笔）**：必须先签约成功，否则查不到。
- 三接口调用链（批量）：签约(sign)成功 → 结果查询(query) → `pczt=1/2` 时 → 明细下载(download)。

## 5. 自动化脚本

- 路径：`D:\Probject\Gjj\scripts\plqly_caller.py`（纯标准库，无需额外依赖）
- 顶层 `--flow {single,batch}` 选流程；`--mode` 选子步骤（取值随 flow 不同）。
- 公共能力：`--dry-run` 打印请求体、`--encrypted`/`--encrypted-file` 传密文、`--insecure` 跳过 SSL、`--yhdm`/`--qdbm`/`--url`/`--host`/`--city` 可调；环境配置共用 `zjb-openapi-auto-test/city-config.json`。
- 调用示例：
  ```bash
  # —— 单笔签约（默认 both = 签约后自动查状态）——
  python scripts/plqly_caller.py --flow single --mode both \
    --yhdm 102000 --zh 129300126211035084 --hm 李一 --zjlx 01 --zjh 522725199608258457 \
    --sjhm 18799665411 --zhlx 1 --jyzhlx 1
  # 仅单笔签约 / 仅状态查询
  python scripts/plqly_caller.py --flow single --mode sign  --yhdm 102000 --zh ... --hm ... --zjlx 01 --zjh ... --sjhm ... --zhlx 1 --jyzhlx 1
  python scripts/plqly_caller.py --flow single --mode query --yhdm 102000 --zh ... --hm ... --zjlx 01 --zjh ... --zhlx 1 --jyzhlx 1

  # —— 批量签约 ——
  python scripts/plqly_caller.py --flow batch --mode sign   --ywlsh 20260814369258741036 --qdbm gt --yhdm 102000 --encrypted-file tmp/icbc.txt
  python scripts/plqly_caller.py --flow batch --mode query  --ywlsh 20260814369258741036 --qdbm gt --yhdm 102000
  python scripts/plqly_caller.py --flow batch --mode download --ywlsh 20260814369258741036 --qdbm gt --yhdm 102000
  ```
- 响应自动尝试 JSON 格式化，失败时回退原文。

## 6. 明细整理约定（AI 整理阶段）

- **批量明细**字段顺序参考用户原始示例：`ywslbh/jbjgbh/jbjgmc/yhdm/zh/hm/khlx/zhlx/yhmc/yhlhh/zjlx/zjhm/.../dbxe/ywlsh/ywmxlsh/dqrq/kkzq/beizhu/sfsqyhkk`。
  - 对私（zhlx=1）：省略 `tyshxydm/zzjgdm/zzjgmc`。
  - 对公（zhlx=2）：保留三字段，`zzjgmc = hm`。
  - 同批 `ywslbh=ywlsh`，`ywmxlsh` 逐条唯一。
- **单笔账号**直接给明文：`zh/hm/zjlx/zjh/sjhm/zhlx/jyzhlx/yhdm`；无需加密、无需 ywslbh（脚本自动生成 20 位）。
