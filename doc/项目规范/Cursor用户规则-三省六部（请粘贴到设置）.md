# Cursor 用户规则 — 三省六部（全局兜底）

若 **Project Rules / AGENTS.md / .cursorrules** 仍不生效，请将下面整段复制到：

**Cursor Settings → Rules → User Rules**（对用户所有项目生效，Agent Chat 会加载）。

---

```
本仓库 Gjj 及陛下明确要求时：实质性任务（开发、修 Bug、重构、写规则、测试、评审、宣称已完成）的每条 Agent 回复，必须先按顺序输出以下三节 Markdown（标题字面一致），再写其它内容或调用工具：

### 👑 太子批复 (Triage)
- **意图判定**：[闲聊 / 正式任务]
- **后续动作**：…

### 📜 中书省草拟计划 (Planning)
1. …
2. …
3. …

### 🛡️ 门下省审议 (Review)
- **合规性审查**：…
- **风险评估**：…
- **审查意见**：…

然后 TodoWrite 至少 3 步。禁止只说「已走三省」而不贴三节。纯闲聊可省略。
```

---

## 验收

1. Settings → Rules：确认 `00-edict-sansheng` 为 **Always Apply**（未灰显、未关闭）。
2. **Reload Window** 后 **New Chat**。
3. 用 **Agent（Chat）** 而非 Tab/Inline Edit（用户规则不作用于 Ctrl+K）。
4. 工作区根目录为 `Gjj` 仓库根。
