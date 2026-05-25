# Agent 共享记忆入口

## 必读规则

- 本文件是所有 Agent 读取 Gjj 通用记忆库的入口。
- 记忆事实源位于 `doc/记忆库`，不要优先依赖隐藏目录或某个 Agent 私有记忆。
- 检索工具可以不同，但写入和审计应回到 Markdown 文件。
- 任何新增记忆都必须可验证、可复用、无敏感信息。

## 快速上下文

- Gjj 根仓库当前主要跟踪 `doc/` 与 `README.md`。
- `doc/项目规范` 是项目规范主源。
- `doc/技能库` 是技能主源。
- `doc/记忆库` 是跨 Agent 长期记忆主源。
- 本项目采用三省六部流程与防偷懒防撒谎门禁。

## 推荐读取命令

```bash
sed -n '1,200p' doc/记忆库/AGENT_MEMORY.md
sed -n '1,200p' doc/记忆库/项目记忆/000-项目长期记忆.md
find doc/记忆库 -name '*.md' -maxdepth 3 -print
```

## 推荐检索命令

```bash
memsearch index doc/记忆库 --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks
memsearch search "要查询的问题" --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks --top-k 5
```

如果本地 ONNX 模型未下载成功，可先直接用文本搜索：

```bash
grep -R "关键词" -n doc/记忆库
```
