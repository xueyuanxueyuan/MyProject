# 批量签约（plQyJysq / plQyJyZtcx / plQyJyMxXz）API 自动化测试流程

> 归类：流程规则 / 跨会话常用结论
> 来源：2026-08-14 会话，依据 Apipost/Swagger 文档核实
> 验证状态：接口字段已与 Swagger 文档核对；脚本 `--dry-run` 已验证与文档示例一致。yhdm 位数待确认。

## 1. 协作分工（人 / AI）

1. **用户提供明文明细 JSON**：账号、户名、证件类型/号、zhlx 等，由 AI 按既定规则整理为接口明细格式。
2. **用户线下加密**：用项目加密组件对明细列表加密，得到密文。
3. **用户回传密文**（直接粘贴或存文件）。
4. **AI 自动发起测试**：用 `scripts/plqly_caller.py` POST 到接口，并反馈响应结果。

## 2. 接口契约（Swagger 为准，图片 OCR 不可信）

- 方法路径：`POST /api/v1/ywgl/plQyJysq`
- 默认 host（嘉兴 K8s）：`https://k8s.gg.jgj31129`
- Swagger 文档：`https://docs.apipost.net/docs/detail/6b3ee47eac72000?target_id=370609c5b39031&locale=zh-cn`

### 请求体真实字段

| 字段 | 含义 | 常用取值 |
|------|------|----------|
| qdlx | 渠道类型 | 01 |
| qdbm | 渠道编码（与银行无关，非银行/行码） | gt |
| jbjgbh | 经办机构编号 | 0101 |
| jbjgmc | 经办机构名称 | 市本级 |
| wdbh | 网点编号 | "" |
| wdmc | 网点名称 | "" |
| xtlx | 系统类型 | GJGL |
| ywslbh | 业务受理编号（同批次相同） | 与 ywlsh 同值 |
| ywlsh | 业务流水号（同批次相同） | 随机 20 位 |
| wjlx | 文件类型 | 1 |
| yhdm | 银行代码（6 位） | 工行 102000 / 农行 103000 |
| plxmbh | 批量项目编号 | "" |
| beizhu | 备注 | "" |
| gjjZipData | **加密后的明细密文** | 用户回传的密文 |

### 重要修正（图片 OCR 是误读）

- 加密字段名是 **`gjjZipData`**，不是图片误读的 `gjjzjlpotdc`。
- 图片里出现的 `qdly/qdhm/jbjgph/wddz/xzbs/gjjz/ywlztz/tssmxx` 均为 OCR 幻觉，非真实字段。
- 文档示例里的 `"$schema": "替换银行编号"` 是 **swagger 描述泄漏，非真实字段**，调用时忽略。

### 固定字段（渠道 / 机构 / 网点 / 系统）—— 用示例默认值，无需更改

以下 7 个字段属于渠道、机构、网点、系统等环境级信息，**各接口统一使用示例默认值，不随测试数据变化、无需逐条修改**：

| 字段 | 示例默认值 | 含义 |
|------|-----------|------|
| qdlx | 01 | 渠道类型 |
| qdbm | gt | 渠道编码（**与银行无关**，不是银行/行码） |
| jbjgbh | 0101 | 经办机构编号 |
| jbjgmc | 市本级 | 经办机构名称 |
| wdbh | "" | 网点编号 |
| wdmc | "" | 网点名称 |
| xtlx | GJGL | 系统类型 |

> 注：脚本中这些字段已设为带默认值的参数，正常调用无需传。仅当切换城市/机构环境且用户明确要求时再改。
> 注意：**`qdbm` 是渠道编码，不是银行标识**；银行由 `yhdm`（6 位银行代码）标识，例如工行 `yhdm=102000`。二者不要混淆。

## 3. 结果查询接口（plQyJyZtcx）

> 调用时机：**批量签约接口返回成功后**，用签约申请时的 `ywslbh` 和 `ywlsh` 调用本接口查询结果。

- 方法路径：`POST /api/v1/ywgl/plQyJyZtcx`
- 默认 host（嘉兴 K8s）：`https://k8s.gg.jgj31129`
- Swagger 文档：`https://docs.apipost.net/docs/detail/6b3f024dc069000?target_id=370609c5b39032&locale=zh-cn`
- 请求体字段：与签约接口**完全相同，但不含 `gjjZipData` 加密字段**（只需业务标识 ywslbh/ywlsh）。
- 字段表同上第 2 节（去掉 `gjjZipData` 一行即可）。
- 关键返回字段 `pczt`：查询结果状态。当 `pczt = 1 或 2` 时，继续调用【明细下载接口】。

## 3.1 明细下载接口（plQyJyMxXz）

> 调用时机：**结果查询返回 `pczt = 1 或 2` 时**，用签约申请时的同一组 `ywslbh`/`ywlsh` 调用本接口下载明细。

- 方法路径：`POST /api/v1/ywgl/plQyJyMxXz`
- 默认 host（嘉兴 K8s）：`https://k8s.gg.jgj31129`
- Swagger 文档：`https://docs.apipost.net/docs/detail/6b3f119a6472000?locale=zh-cn&target_id=370609c5b39033`
- 请求体字段：与结果查询接口**完全相同，不含 `gjjZipData`**（只需业务标识 ywslbh/ywlsh）。
- 字段表同上第 2 节（去掉 `gjjZipData` 一行即可）。

## 4. 关键注意点（易踩坑）

- **yhdm 必须是 6 位**（已确认）：工行 `102000`、农行 `103000`。脚本已加校验——传入非 6 位时**拦截并提醒用户是否给错**，需 `--force` 才强行继续。早前口头说的 `10200`（5 位）是错的，不要再用。
- 同一批次 `ywslbh`/`ywlsh` 相同，每条 `ywmxlsh` 唯一（在明细 JSON 内，加密前）。
- 对私（zhlx=1）明细不填 `tyshxydm/zzjgdm/zzjgmc`；对公（zhlx=2）保留且 `zzjgmc` 与 `hm` 同值。
- **结果查询复用签约的 ywslbh/ywlsh**：必须先签约成功，再用同一组号查询，否则查不到。
- **三接口调用链**：签约(sign)成功 → 结果查询(query) → 当返回 `pczt=1 或 2` 时 → 明细下载(download)。三个接口共用同一组 `ywslbh`/`ywlsh`。

## 5. 自动化脚本

- 路径：`D:\Probject\Gjj\scripts\plqly_caller.py`（纯标准库，无需额外依赖）
- 三种模式：`--mode sign`（批量签约，默认，含 gjjZipData 加密字段）/ `--mode query`（结果查询）/ `--mode download`（明细下载，后两者不含加密字段）。
- 公共能力：`--dry-run` 打印请求体、`--encrypted`/`--encrypted-file` 传密文、`--insecure` 跳过 SSL、`--yhdm`/`--qdbm`/`--url`/`--host` 可调。
- 调用示例：
  ```bash
  # 1) 批量签约（默认 sign 模式）
  python scripts/plqly_caller.py \
    --ywlsh 20260814369258741036 \
    --qdbm gt --yhdm 102000 \
    --encrypted-file tmp/icbc_20260814.txt

  # 2) 结果查询（签约返回成功后，复用同一 ywslbh/ywlsh）
  python scripts/plqly_caller.py --mode query \
    --ywlsh 20260814369258741036 \
    --qdbm gt --yhdm 102000

  # 3) 明细下载（查询返回 pczt=1 或 2 时，复用同一 ywslbh/ywlsh）
  python scripts/plqly_caller.py --mode download \
    --ywlsh 20260814369258741036 \
    --qdbm gt --yhdm 102000
  ```
- 响应自动尝试 JSON 格式化，失败时回退原文。

## 6. 明细整理约定（AI 整理阶段）

- 字段顺序参考用户原始示例：`ywslbh/jbjgbh/jbjgmc/yhdm/zh/hm/khlx/zhlx/yhmc/yhlhh/zjlx/zjhm/.../dbxe/ywlsh/ywmxlsh/dqrq/kkzq/beizhu/sfsqyhkk`。
- 对私（zhlx=1）：省略 `tyshxydm/zzjgdm/zzjgmc`。
- 对公（zhlx=2）：保留三字段，`zzjgmc = hm`。
- 同批 `ywslbh=ywlsh`，`ywmxlsh` 逐条唯一。
