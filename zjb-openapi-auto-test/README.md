# 住建部 OpenAPI 通用自动化测试工具

本目录提供一个零第三方依赖的自动化测试脚本，用于把 Apipost 分享出的 OpenAPI 示例与各银行 Excel 测试数据合成可执行测试用例。

## 输入

- OpenAPI/Swagger JSON：默认使用当前 Apipost 分享地址。
- 银行测试数据 Excel：默认示例为 `D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx`。
- 接口服务地址：统一写在 `city-config.json` 的 `serviceBaseUrl`，也可以用 `--base-url` 临时覆盖。

## 城市固定参数配置

城市相关固定参数统一维护在：

```text
D:\Probject\Gjj\zjb-openapi-auto-test\city-config.json
```

配置格式：

```json
{
  "defaultCity": "zaozhuang",
  "cities": {
    "zaozhuang": {
      "name": "枣庄",
      "serviceBaseUrl": "http://localhost:8082",
      "fixedParams": {
        "zxbh": "370400000000000",
        "jbjgbh": "37040102",
        "jbjgmc": "市中管理部",
        "qdlx": "01",
        "qdbm": "gt",
        "wdbh": null,
        "wdmc": null,
        "ywjbjgbh": "37040102",
        "ywqd": "gt"
      }
    }
  }
}
```

新增城市时，在 `cities` 下复制一个节点，例如 `jiaxing`，同时修改 `serviceBaseUrl` 和 `fixedParams`。脚本会递归覆盖请求体中同名字段，因此批量接口头信息里的 `zxbh/jbjgbh` 等也会一起生效。

运行时通过 `--city` 指定城市；不传时使用 `defaultCity`。`run` 模式会优先使用命令行 `--base-url`，未传时自动使用该城市配置里的 `serviceBaseUrl`。

## 常用命令

只生成请求用例和报告，不实际调用服务：

```powershell
py zjb-openapi-auto-test\zjb_openapi_runner.py `
  --city "zaozhuang" `
  --excel "D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx" `
  --output-dir ".codex-run\zjb-openapi-test"
```

只生成“住建部”标签下的用例：

```powershell
py zjb-openapi-auto-test\zjb_openapi_runner.py `
  --city "zaozhuang" `
  --tag-filter "住建部" `
  --excel "D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx"
```

实际调用目标服务并生成报告：

```powershell
py zjb-openapi-auto-test\zjb_openapi_runner.py `
  --mode run `
  --excel "D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx" `
  --bank-code "313000" `
  --expect-http "2xx" `
  --city "zaozhuang"
```

生成银行测试数据 Excel 模板：

```powershell
py zjb-openapi-auto-test\zjb_openapi_runner.py `
  --mode template `
  --template-output "D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_模板.xlsx"
```

覆盖接口请求中的指定字段，例如批量扣款与批量收款共用 `/api/v1/ywgl/addSkywPlsk`，仅 `xtlx/ywlx` 不同，可使用：

```powershell
py zjb-openapi-auto-test\zjb_openapi_runner.py `
  --mode run `
  --city "jiaxing" `
  --summary-filter "新增批量收款请求" `
  --excel "D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx" `
  --bank-code "313331" `
  --bank-name "杭州银行" `
  --xtlx "GDGL" `
  --ywlx "11305" `
  --set-field "jslx=wksk"
```

批量扣款结算类型为 `jslx=wksk`。`--xtlx/--ywlx` 是 `--set-field xtlx=...`、`--set-field ywlx=...` 的快捷写法；`--set-field` 会递归覆盖请求体里同名字段。
## 输出

默认输出到 `.codex-run/zjb-openapi-test`：

- `cases/*.json`：每个接口一个可查看的请求用例。
- `report.json`：机器可读报告。
- `report.html`：人工查看报告。

## 替换规则

脚本会自动处理：

- 嘉兴环境发起交易类接口会在请求外层额外补 `yhdm`，取值为当前运行的 `--bank-code`。

- Apipost 示例中的 `{{$fakerjs.String.numeric(length=20)}}`、`{{$mockjs.now()...}}`。
- JSON 示例中的 `//`、`/* */` 注释。
- Excel 中的对私/对公账号、户名/姓名、证件号、联行号、手机号。
- 常见字段：`zh/fkzh/skzh`、`hm/fkhm/skhm`、`zjlx/zjh`、`yhdm/fkyhdm/skyhdm`、`yhmc/fkyhmc/skyhmc`、`lhh/fkyhlhh/skyhlhh`、`jyrq/jstjsj`、各类流水号。
- 批量明细的 `jybs` 和 `je` 汇总。

## 注意事项

- 默认 `dry-run` 不会请求任何服务，适合先检查生成的请求数据。
- `run` 模式会真实请求接口服务地址；默认读取所选城市的 `serviceBaseUrl`，如需临时覆盖可传 `--base-url`。请确认测试环境和测试数据可用后再执行。
- 如果银行编码无法从 Excel 得到，请通过 `--bank-code` 显式传入。
- 当前工具只新增测试脚本，不修改业务代码。
