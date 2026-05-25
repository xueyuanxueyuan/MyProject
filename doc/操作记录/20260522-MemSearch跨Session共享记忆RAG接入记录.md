# MemSearch 跨 Session 共享记忆 RAG 接入记录

## 1. 目标

参考腾讯云文章《Agent Harness长期记忆插件开源，Claude Code与Codex终于能共享同一套项目记忆》中介绍的 MemSearch 思路，为 Gjj 工作区接入跨对话 session、跨 Agent 可复用的长期记忆 RAG。

## 2. 方案原则

- Markdown 作为记忆源文件，保证可读、可改、可审计。
- Milvus Lite 作为可重建的影子索引，不把索引当作唯一事实来源。
- 使用 MemSearch 的 `index`、`search`、`expand`、`transcript` 渐进式召回能力，避免一次性污染上下文。
- 优先使用本地 ONNX embedding，避免把 OpenAI 等云端密钥写入仓库。
- 项目级配置与本地记忆目录不纳入当前 Git 跟踪范围，避免提交本地环境数据。

## 3. 已执行操作

### 3.1 安装 CLI

通过 `uv tool install 'memsearch[onnx]' --python 3.12 -v` 安装 MemSearch CLI，安装结果显示：

- `memsearch==0.4.4`
- 可执行文件路径：`/home/source/.local/bin/memsearch`
- Python 运行环境：`/home/source/.local/share/uv/tools/memsearch/bin/python`

### 3.2 创建项目级配置

创建 `.memsearch.toml`，内容如下：

```toml
[embedding]
provider = "onnx"
model = "gpahal/bge-m3-onnx-int8"

[milvus]
uri = "/home/source/.memsearch/gjj-milvus.db"
collection = "gjj_memsearch_chunks"
```

配置含义：

- 使用 ONNX 本地嵌入模型。
- 使用用户级 Milvus Lite 文件 `/home/source/.memsearch/gjj-milvus.db` 存放向量索引。
- 使用独立集合 `gjj_memsearch_chunks` 隔离 Gjj 项目记忆。

### 3.3 创建项目记忆源目录

创建 `.memsearch/memory/2026-05-22.md`，作为当前项目的 Markdown 记忆源文件。

该文件记录了本次接入基线：

- 目标：为 Gjj 工作区提供跨对话 session、跨 Agent 可复用的长期记忆 RAG。
- 架构：Markdown 文件作为记忆源，Milvus Lite 作为可重建影子索引。
- 检索：MemSearch CLI 提供 index/search/expand/transcript 渐进式召回。
- 嵌入：使用本地 ONNX provider，首次索引需要从 HuggingFace 下载模型文件。

## 4. 验证情况

### 4.1 已验证

- MemSearch Python 包可导入。
- `MemSearch` API 可实例化到模型下载前步骤。
- 项目配置文件 `.memsearch.toml` 已生成。
- 记忆源文件 `.memsearch/memory/2026-05-22.md` 已生成。
- 当前仓库 `.gitignore` 默认只跟踪 `doc/` 与 `README.md`，因此 `.memsearch` 与 `.memsearch.toml` 不会被误提交。

### 4.2 当前阻塞

执行索引验证时，MemSearch 需要从 HuggingFace 下载默认 ONNX 模型 `gpahal/bge-m3-onnx-int8`，当前环境请求 `https://huggingface.co/gpahal/bge-m3-onnx-int8/resolve/main/tokenizer.json` 出现 `Connection reset by peer`，因此索引和语义检索未完成端到端验证。

这不是配置文件缺失，而是首次模型下载所需的外部网络访问失败。

## 5. 后续可选处理

### 5.1 继续本地 ONNX 方案

网络可访问 HuggingFace 后，在 Gjj 根目录执行：

```bash
memsearch index .memsearch/memory --provider onnx --milvus-uri /home/source/.memsearch/gjj-milvus.db --collection gjj_memsearch_chunks
memsearch search "跨 session 共享记忆 RAG 使用什么架构" --provider onnx --milvus-uri /home/source/.memsearch/gjj-milvus.db --collection gjj_memsearch_chunks --top-k 3
```

### 5.2 切换到 OpenAI Embedding

如果后续配置了 OpenAI API Key，可切换为：

```bash
memsearch config set embedding.provider openai --project
memsearch config set embedding.model text-embedding-3-small --project
memsearch index .memsearch/memory --provider openai --force
```

注意：密钥应通过 IDE 集成或环境变量配置，不能写入仓库文件。

### 5.3 切换到远程 Zilliz Cloud / Milvus

如果需要多人或多机器共享索引，可把 `milvus.uri` 与 `milvus.token` 指向 Zilliz Cloud 或自建 Milvus。Markdown 记忆源仍保留在项目中或共享目录中。

## 6. 使用方式

写入记忆：

```bash
cat >> .memsearch/memory/$(date +%Y-%m-%d).md <<'MEMORY'

## 标题
- 关键事实或决策。
MEMORY
```

索引记忆：

```bash
memsearch index .memsearch/memory --provider onnx --milvus-uri /home/source/.memsearch/gjj-milvus.db --collection gjj_memsearch_chunks
```

召回记忆：

```bash
memsearch search "你要查的问题" --provider onnx --milvus-uri /home/source/.memsearch/gjj-milvus.db --collection gjj_memsearch_chunks --top-k 5
```

## 7. 结论

本次已完成 MemSearch 的安装与本地项目配置落地，形成了 Markdown 记忆源与 Milvus Lite 影子索引的接入基线。当前仅剩首次 ONNX 模型下载受网络影响，导致端到端索引与检索验证未完成；待 HuggingFace 访问恢复或切换云端 embedding 后即可完成最终验证。

## 8. 追加调整：改为 doc 下通用记忆库

根据用户进一步要求，记忆库不应只放在 `.memsearch/memory` 这类工具私有目录，而应放在 `doc` 下成为所有 Agent 都能读取的通用知识资产。

### 8.1 新增目录

已创建 `doc/记忆库`，目录结构如下：

```text
doc/记忆库/
├── README.md
├── AGENT_MEMORY.md
├── 项目记忆/
│   └── 000-项目长期记忆.md
├── 决策记录/
│   └── 000-架构与工具决策.md
├── 会话记录/
│   └── 20260522-MemSearch通用记忆库接入.md
└── 检索指南/
    └── MemSearch使用指南.md
```

### 8.2 新定位

- `doc/记忆库` 是跨 Agent 长期记忆事实源。
- `.memsearch` 仅作为 MemSearch 或其他工具的本地缓存与索引目录。
- 任意 Agent 不需要安装 MemSearch，也可以直接读取 `doc/记忆库/AGENT_MEMORY.md` 和其他 Markdown 文件。
- 支持 MemSearch、grep、其他 RAG 工具共同复用同一套 Markdown 事实源。

### 8.3 配置调整

`.memsearch.toml` 已调整为使用文档记忆库对应的本地索引库：

```toml
[embedding]
provider = "onnx"
model = "gpahal/bge-m3-onnx-int8"

[milvus]
uri = "/home/source/.memsearch/gjj-doc-memory.db"
collection = "gjj_doc_memory_chunks"
```

推荐索引源统一改为：

```bash
doc/记忆库
```

### 8.4 验证结果

- `doc/记忆库` 下 6 个 Markdown 文件均已创建且非空。
- Git 状态显示 `doc/记忆库` 可被当前仓库跟踪。
- `.memsearch.toml` 与 `.memsearch/memory/2026-05-22.md` 仍被 `.gitignore` 忽略，避免提交工具私有缓存。
- 文本检索 `grep -R "跨 Agent" -n doc/记忆库` 可命中通用记忆内容。
- 交付守卫扫描通过，未发现偷懒式占位痕迹。

### 8.5 使用入口

所有 Agent 进入仓库后，优先读取：

```bash
sed -n '1,200p' doc/记忆库/AGENT_MEMORY.md
sed -n '1,200p' doc/记忆库/项目记忆/000-项目长期记忆.md
```

如果支持语义检索，可执行：

```bash
memsearch index doc/记忆库 --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks
memsearch search "你的问题" --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks --top-k 5
```

