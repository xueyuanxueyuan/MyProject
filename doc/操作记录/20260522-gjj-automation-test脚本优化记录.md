# gjj-automation-test 脚本优化记录

## 背景
- 目标目录：`/home/source/Jetbrains/Probject/Gjj/gjj-automation-test`
- 依据：历史测试报告显示，嘉兴多家银行在原始批次中重复出现 `fkyhmc` 缺失、`zjlx` 格式不稳、HTTP 成功但业务语义异常未被脚本识别等问题。

## 历史问题归纳
- 测试数据层：
  - 旧模板参数读取后缺少统一归一化，容易把中文证件类型直接带入请求。
  - 银行编码与银行名称的兜底策略主要覆盖新模板，旧模板和部分回退场景不稳定。
- 测试断言层：
  - `test_main.py` 仅校验 HTTP 状态码，无法及时识别响应体中的明显业务异常文案。
- 执行体验层：
  - `run_main.py` 使用 `capture_output=True` 收集 pytest 输出，长跑任务中不利于实时观察，也没有显式透传子进程退出码。

## 本次计划改动
- `test_data_reader.py`
  - 为旧模板增加银行编码、银行名称、证件类型等字段的统一归一化。
  - 修复“排除首条对私账户随机取数”与注释不一致的问题。
- `test_main.py`
  - 抽取统一响应断言，保留原有 HTTP 状态校验。
  - 增加“业务异常告警 + 可选严格失败”能力，默认仅告警，兼容现有环境差异。
- `run_main.py`
  - 改为实时输出 pytest 日志。
  - 在子进程失败时透传退出码，避免外层误判为成功。

## 风险控制
- 仅做增量优化，不改接口入参结构和已有测试数据文件。
- “业务异常严格失败”默认关闭，通过环境变量显式开启，避免一次性打断现有批量联调流程。

## 实际改动
- `test_data_reader.py`
  - 新增旧模板归一化逻辑：统一补齐 `yhdm`、`fkyhdm_*`、`fkyhmc_*`，并把 `zjlx/zjlx_*` 归一为接口期望编码。
  - 新增“根据编码或名称解析标准银行名称”的兜底方法，兼容旧模板与回退文件。
  - 修复 `get_random_private_account_excluding_first()` 实现与注释不一致的问题：当存在多条对私账户时，随机池中不再包含首条。
- `test_main.py`
  - 抽取统一响应断言 `assert_test_case_response()`，替换重复的 HTTP 状态断言。
  - 增加响应 JSON 递归扫描，对“不能为空 / 不存在 / 失败 / 异常 / 错误 / 有误 / 不符”等历史高频业务异常文案发出警告。
  - 支持通过 `STRICT_BIZ_ASSERT=1` 将“疑似业务异常”从警告升级为测试失败。
- `run_main.py`
  - 新增 `run_pytest_command()`，改为实时透传 pytest 输出。
  - 子进程失败时通过 `sys.exit(exit_code)` 向外层返回真实失败码。
- `test_data_reader_regression.py`
  - 新增旧模板归一化回归测试。
  - 新增“排除首条随机取数”回归测试。

## 验证结果
- 代码诊断：
  - `test_data_reader.py` 无诊断错误。
  - `test_main.py` 无诊断错误。
  - `run_main.py` 无诊断错误。
  - `test_data_reader_regression.py` 无诊断错误。
- 单元/回归测试：
  - 执行命令：`python3 -m pytest test_data_reader_bank_name_support.py test_data_reader_regression.py -q`
  - 结果：`5 passed, 2 warnings in 0.10s`
  - 说明：警告为 pytest 误把 `test_data_reader.py` 中的 `TestDataReader` 普通类识别为候选测试类，属于既有收集告警，本次未新增失败。
- 交付门禁：
  - 执行命令：`scripts/guardrails/agent-delivery-guardrail.sh --scan-only`
  - 结果：通过，未发现偷懒式占位痕迹。

## 第二轮问题分析：批量明细数据生成不灵活
- 用户反馈方向：
  - 当前脚本在“读取数据并生成测试用例”这一层仍存在设计缺陷，尤其是批量业务接口的批量明细条数被写死，不适合多银行、多模板、多条数联调场景。

### 已确认的问题点
- `test_main.py` 的批量收款、批量扣款、批量付款用例，均直接手写 3 条明细模板：
  - 对私固定用 `fkzh_1 ~ fkzh_3`
  - 对公固定用 `fkzh_4 ~ fkzh_6`
  - 主单头部 `jybs`、`je` 也先写死为 `3`
- `batch_detail_utils.py` 虽然会在请求发送前做去重和回写 `jybs/je`，但本质只是“事后修补”，不是“按数据源动态生成”：
  - 仍依赖 `test_main.py` 先构造一份静态 3 条明细列表
  - 去重后最多保留 3 条，无法自然覆盖 1 条、2 条、4 条以上等场景
- `test_data_reader.py` 已经能读出：
  - 全量对私账户列表 `get_private_accounts_for_tests()`
  - 顺序/随机账户 `get_next_private_account()`、`get_next_public_account()`
  - 但这些能力没有真正接入 `test_main.py` 的批量明细构造阶段
- 当前架构的数据驱动粒度不一致：
  - 单笔接口是“模板变量替换”
  - 批量接口看似也是数据驱动，实际上只是把 3 组索引变量 `${fkzh_1}`、`${fkzh_2}`、`${fkzh_3}` 写死在脚本里
  - 这会导致 Excel 中即便维护了更多账户，测试脚本也吃不到

### 影响
- 用例灵活性差：
  - 无法按实际 Excel 数据量自动决定批量明细数
  - 无法方便地验证“只有 1 条明细”“恰好 2 条”“超过 3 条”的边界
- 可维护性差：
  - 新增一个批量场景时，往往需要手工复制 3 段明细 JSON
  - 头部 `jybs/je`、明细列表、去重规则分散在多个位置，容易失配
- 可扩展性差：
  - 如果未来要按银行、地区、业务类型选择不同条数策略，当前结构不适合扩展
- 可观测性差：
  - 当前报告只能看到最终发出去的请求体，不容易看出“原始计划条数”和“去重后实际条数”之间的差异来源

### 可选改造方案
- 方案 A：小改方案
  - 保留现有 `test_case["data"]` 结构
  - 约定批量用例支持新增字段，例如 `detail_source=private/public`、`detail_count`、`detail_mode=sequential/random`
  - 在请求发送前由统一构造器根据 `TestDataReader` 动态生成明细列表，再同步头部 `jybs/je`
  - 优点：改动面小，容易平滑接入当前脚本
  - 缺点：`test_case` 结构会逐步变成“半模板半配置”
- 方案 B：推荐方案
  - 抽出“批量请求构造器”，例如由独立模块根据：
    - 批量业务类型
    - 对私/对公
    - 条数策略
    - 明细字段模板
    - 数据源列表
    统一生成 `zjjsSFkywxxAddReqDTO + 明细列表`
  - `test_main.py` 只保留场景声明，不再手写 3 条明细 JSON
  - 优点：结构最清晰，真正做到按数据源动态生成
  - 缺点：需要重构批量相关用例定义方式
- 方案 C：重数据驱动方案
  - 把批量用例场景也外置到 Excel/JSON/YAML
  - 测试脚本只负责读取场景配置并组装请求
  - 优点：后续新增场景最灵活
  - 缺点：改造成本最高，短期不一定最划算

### 当前建议
- 优先选方案 B：
  - 它能解决“批量明细条数写死”这个根问题
  - 也能顺手收敛头部 `jybs/je` 与明细条数脱节的问题
  - 改造成本明显低于完全外置场景配置，但收益足够大

## 第二轮实际实现
- 新增 `batch_request_builder.py`
  - 引入动态批量构造入口 `build_dynamic_batch_request()`
  - 支持通过 `__batch_builder__` 声明：
    - `account_type=private/public`
    - `list_key`
    - `detail_count` 显式条数
    - `max_detail_count` 自动条数上限
    - `detail_template` 明细模板
  - 支持 `$account.xxx` 占位，从账户列表动态填充账号、户名、证件号、账户类型等字段
  - 自动同步主单头部 `jybs/je`
- 调整 `api_client.py`
  - 模板变量替换后，先执行动态批量构造，再执行原有去重与头部同步
- 调整 `test_data_reader.py`
  - 新增 `get_public_accounts_for_tests()`，让对公批量构造也能像对私一样直接基于账户列表生成
- 调整 `test_main.py`
  - 批量收款、批量扣款、批量付款场景全部改为声明式批量构造
  - 主测试脚本层面不再手写 3 条明细 JSON
  - 当前主测试为了控制联调影响，默认使用 `max_detail_count=3`
  - 同时保留显式 `detail_count` 能力，后续单场景可单独指定

## 第二轮验证结果
- TDD 首轮验证：
  - 新增 `test_batch_request_builder.py` 后首次执行失败，错误为 `ModuleNotFoundError: No module named 'batch_request_builder'`
  - 说明失败原因正确，符合先测后改预期
- 构造器回归测试：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py -q`
  - 结果：`2 passed`
- 综合单元回归：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py test_data_reader_regression.py test_data_reader_bank_name_support.py -q`
  - 结果：`7 passed, 2 warnings in 0.13s`
- 主测试脚本收集校验：
  - 执行命令：`python3 -m pytest test_main.py --collect-only -q`
  - 结果：`13 tests collected`
  - 说明：`test_main.py` 批量场景声明结构可正常被 pytest 收集，无语法或参数化错误

## 当前边界
- 已解决：
  - 批量明细条数在测试脚本层面被钉死的问题
  - 批量明细生成未直接复用账户列表数据的问题
- 暂未做：
  - 未把所有旧版测试文件（如 `test_ywgl.py`、`test_ywgl_v2.py`）同步迁移到新构造方式
  - 未直接对真实联调环境执行批量交易接口回归，避免在未确认环境数据前产生真实业务影响

## 第三轮持续优化：旧版批量脚本迁移
- 迁移范围：
  - `test_ywgl.py`
  - `test_ywgl_v2.py`
- 本轮新增能力：
  - `batch_request_builder.py` 增加 `account_indices` 支持，可按指定账户序号选取明细数据，而不只是“前 N 条”
  - 构造器增加账户参数补全能力，会结合 `load_core_parameters()` 为账户对象补齐：
    - `fkyhdm`
    - `fkyhmc`
    - `fkyhlhh`
    - `dfxyh`
    - `zjlx`
    - `zjh`
    - `zhlx`
- 迁移结果：
  - `test_ywgl.py` 的批量收款、批量扣款、批量付款场景已切换为 `__batch_builder__` 声明式生成
  - `test_ywgl_v2.py` 的批量收款、批量扣款、批量付款场景已切换为 `__batch_builder__` 声明式生成
  - 旧版脚本中原先依赖 `fkzh_2/fkzh_3`、`fkzh_4/fkzh_5` 的场景，现通过 `account_indices` 显式表达，更清晰也更可维护

## 第三轮验证结果
- TDD 验证：
  - 先新增 `account_indices` 单测，首次执行失败，证明构造器当时只支持“截前 N 条”
  - 补实现后再次回归通过
- 构造器回归：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py test_data_reader_regression.py test_data_reader_bank_name_support.py -q`
  - 结果：`8 passed, 2 warnings in 0.12s`
- 旧版脚本收集校验：
  - 执行命令：`python3 -m pytest test_ywgl.py --collect-only -q`
  - 结果：`14 tests collected`
  - 执行命令：`python3 -m pytest test_ywgl_v2.py --collect-only -q`
  - 结果：`15 tests collected`

## 更新后的边界
- 已完成：
  - `test_main.py` 批量场景迁移
  - `test_ywgl.py` 批量场景迁移
  - `test_ywgl_v2.py` 批量场景迁移
  - 批量构造器支持显式条数、自动条数上限、指定账户序号
- 尚未完成：
  - 真实联调环境下的批量接口实跑回归仍未执行
  - 若后续还有其他旁路脚本使用旧式明细写法，仍可继续复用当前构造器逐步迁移

## 第四轮持续优化：统一入口、默认范围与自然语言筛选
- 用户要求落地为以下规则：
  - 未特指时，默认执行“全量非签约类接口测试”
  - 可从自然语言描述中自动筛选任意一个、一类或多个接口
  - 无论本轮选择多少接口，都只运行一次 pytest，并产出一份统一测试报告
  - 银行当日流水下载在未特指特定交易时默认发起
  - 客户账户有效性校验在未特指特定交易且测试数据完整时默认发起

### 本轮实现
- 新增 `interface_test_selector.py`
  - 抽离接口选择逻辑，避免继续依赖 `run_main.py` 中的 `-k` 模糊匹配
  - 维护主测试与签约测试的接口目标清单（node id、标签、别名、类别）
  - 支持：
    - 默认全量非签约
    - 签约类默认全量
    - 自然语言筛选单接口
    - 自然语言筛选多接口
    - 自然语言筛选一类接口（如“付款类接口”）
  - 对主测试范围额外支持自动补入：
    - `zhdrlscx`
    - `khzhyxxCx`
  - 自动补入时会校验测试数据完整性：
    - `zhdrlscx` 需要 `yhdm + yhzhhm`
    - `khzhyxxCx` 需要 `yhdm + zhlx_1 + fkzh_1 + fkhm_1 + zjlx_1 + zjh_1`
- 调整 `run_main.py`
  - 新增 `--select` 参数，支持自然语言描述接口范围
  - `--single` 作为兼容旧入口保留，但内部同样走统一选择器
  - 当前入口统一改为：
    - 先解析地区/银行/测试数据
    - 再由 `resolve_selection()` 得到本轮 node id 列表
    - 最后一次性执行一条 pytest 命令
  - 这样本轮所有接口天然只会生成一份统一报告
- 新增 `test_interface_test_selector.py`
  - 覆盖默认全量非签约
  - 覆盖自然语言筛选多个接口
  - 覆盖“付款类接口”类目筛选
  - 覆盖 `zhdrlscx` 自动补入
  - 覆盖 `khzhyxxCx` 数据不完整时跳过自动补入

### 本轮验证结果
- TDD 首轮失败：
  - 新增 `test_interface_test_selector.py` 后首次执行失败，报 `ModuleNotFoundError: No module named 'interface_test_selector'`
  - 符合先测后改预期
- 选择器单测：
  - 执行命令：`python3 -m pytest test_interface_test_selector.py -q`
  - 结果：`6 passed in 0.03s`
- 默认模式选择验证：
  - 解析结果共 `9` 个节点
  - 包含交易类、常规查询类，并自动补入：
    - `zhdrlscx`
    - `khzhyxxCx`
- 类目筛选验证：
  - 输入：`付款类接口`
  - 结果：会选中单笔付款、批量付款，并按规则自动补入：
    - `zhdrlscx`
    - `khzhyxxCx`
- 诊断结果：
  - `interface_test_selector.py`、`run_main.py`、`test_interface_test_selector.py` 无诊断错误

### 当前效果
- 现在入口语义已经变为：
  - 不指定时：默认全量非签约
  - 指定“一个接口”：只跑该接口
  - 指定“一类接口”：跑该类接口
  - 指定“多个接口”：合并为一轮测试
  - 本轮所有已选接口：统一进同一次 pytest，统一生成一份报告

## 第五轮持续优化：补充自然语言别名字典
- 用户要求先继续增强自然语言筛选词典，让日常口语描述也能准确映射到接口选择器。

### 本轮新增别名/规则
- 主测试接口：
  - 单笔收款：
    - 新增 `单笔缴存`
  - 批量收款：
    - 新增 `批量缴存`
  - 客户账户有效查询：
    - 新增 `账户校验`
    - 新增 `账户有效校验`
    - 新增 `客户账户校验`
    - 新增 `账户有效查询`
  - 银行当日流水下载：
    - 新增 `当日交易流水`
    - 新增 `银行日流水`
    - 新增 `日流水`
- 签约接口：
  - 批量签约结果查询：
    - 新增 `批量签约结果`
    - 新增 `签约结果查询`
    - 新增 `签约结果`
  - 批量签约明细下载：
    - 新增 `批量签约下载`
    - 新增 `签约下载`
- 类目规则：
  - 新增 `缴存类` / `缴存类接口`，只映射：
    - 单笔收款
    - 批量收款

### 本轮规则修正
- 去掉过宽单词别名：
  - 从单笔收款移除过宽别名 `缴存`
  - 从单笔签约移除过宽别名 `签约`
- 目的：
  - 避免 `缴存类接口` 被误识别成单笔缴存
  - 避免 `签约类接口` 被误识别成单笔签约

### 本轮验证结果
- 选择器单测：
  - 执行命令：`python3 -m pytest test_interface_test_selector.py -q`
  - 结果：`10 passed in 0.02s`
- 口语别名实测：
  - 输入：`缴存类接口`
    - 结果：选中单笔收款、批量收款
  - 输入：`账户校验`
    - 结果：选中 `khzhyxxCx`
  - 输入：`当日交易流水`
    - 结果：选中 `zhdrlscx`
  - 输入：`批量签约结果和批量签约下载`
    - 结果：选中批量签约结果查询、批量签约明细下载

## 第六轮持续优化：批量明细重复账号前置杜绝
- 用户反馈：批量接口测试时仍能看到重复明细，希望在脚本中直接杜绝，而不是依赖后置补救。

### 根因分析
- `api_client.py` 当前链路是：
  - 模板变量替换
  - `build_dynamic_batch_request()` 动态生成批量明细
  - `normalize_batch_request_data()` 按明细账号做后置去重
- 问题在于：
  - `batch_request_builder.py` 在“选账户”阶段此前只按列表顺序/索引取数，不会先按实际账号排重
  - 如果测试数据里同一账号以不同索引重复出现，构造器会直接生成重复明细
  - 后置去重虽能在部分场景收敛，但会造成：
    - 请求体在构造阶段已经带有重复明细
    - `detail_count` 想要的条数可能因重复被压缩，无法自动向后补足唯一账号

### 本轮修复
- 修改 `batch_request_builder.py`
  - 新增 `_get_account_identity()`
    - 优先按 `fkzh / skzh / zh / kh` 提取账户唯一键
  - 新增 `_dedupe_accounts()`
    - 在账户选择阶段先按唯一键去重，保留首次出现的账户
  - 在 `_select_accounts()` 中：
    - 完成 `account_indices` 过滤后，先执行账户级排重
    - 再按 `detail_count / max_detail_count` 截取
- 修复结果：
  - 重复账号不会再进入批量明细构造结果
  - 若前面遇到重复账号，会自动向后补入后续唯一账号
  - 不再依赖发送前的后置去重“碰运气补救”

### 新增测试
- `test_batch_request_builder.py`
  - 新增：数据源存在重复对私账号时，构造器应去重并回填后续唯一账号
  - 新增：显式 `account_indices` 命中重复对公账号时，构造器应去重且头部 `jybs/je` 同步正确

### 本轮验证结果
- TDD 首轮失败：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py -q`
  - 初次结果：`2 failed, 3 passed`
  - 失败现象：
    - 构造结果中出现 `pri-1, pri-1, pri-2`
    - 构造结果中出现 `pub-1, pub-1, pub-2`
- 修复后专项测试：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py -q`
  - 结果：`5 passed in 0.02s`
- 关联回归：
  - 执行命令：`python3 -m pytest test_batch_request_builder.py test_data_reader_regression.py test_data_reader_bank_name_support.py test_interface_test_selector.py -q`
  - 结果：`20 passed, 2 warnings in 0.15s`
- 收集校验：
  - `python3 -m pytest test_main.py --collect-only -q`
    - 结果：`13 tests collected`
  - `python3 -m pytest test_ywgl.py --collect-only -q`
    - 结果：`14 tests collected`
  - `python3 -m pytest test_ywgl_v2.py --collect-only -q`
    - 结果：`15 tests collected`
