# MemSearch 使用指南

## 1. 推荐索引源

```bash
doc/记忆库
```

## 2. 推荐索引命令

```bash
memsearch index doc/记忆库 --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks
```

## 3. 推荐检索命令

```bash
memsearch search "你的问题" --provider onnx --milvus-uri /home/source/.memsearch/gjj-doc-memory.db --collection gjj_doc_memory_chunks --top-k 5
```

## 4. 无向量检索时的降级方案

```bash
grep -R "关键词" -n doc/记忆库
find doc/记忆库 -name '*.md' -maxdepth 3 -print
```

## 5. 注意事项

- 首次 ONNX 索引需要下载 HuggingFace 模型。
- 如果网络无法访问 HuggingFace，可切换 OpenAI、Ollama 或其他 embedding provider。
- 不要把 Milvus Lite 数据库或模型缓存提交到仓库。
- 如果使用其他 RAG 工具，索引源仍应保持为 `doc/记忆库`。
