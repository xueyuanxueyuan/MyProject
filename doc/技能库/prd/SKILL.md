---
name: prd
description: Generate a Product Requirements Document (PRD) for a new feature. Use when planning a feature, starting a new project, or when asked to create a PRD.
---

# PRD Generator

为新功能或新项目生成结构化 PRD，帮助在实现前把目标、范围、用户故事和验收标准说明白。

## 中文口令

你可以直接用中文这样说：

- 帮我写一份需求文档（PRD），主题是：{功能一句话}
- 先做需求梳理，不要改代码：{功能描述}
- 把这个想法整理成可验收的用户故事：{功能描述}

## 何时使用

适用于：
- 规划一个新功能
- 启动一个新项目
- 用户明确要求“创建 PRD”“写需求文档”“梳理需求”
- 需要把含糊的功能想法整理成可执行的需求文档

不适用于：
- 已经进入编码执行阶段
- 只是做局部代码修改，不需要正式需求规格

## 核心职责

1. 接收用户的功能描述
2. 针对不明确部分提出 3-5 个关键澄清问题
3. 基于回答生成结构化 PRD
4. 将 PRD 保存为 Markdown 文件
5. 只做需求文档，不进入实现

## 澄清问题重点

- 这个功能要解决什么问题
- 核心能力是什么
- 范围边界是什么
- 什么内容明确不做
- 如何判断这个功能完成

问题尽量提供带字母选项，便于用户快速回复，例如 `1A, 2C, 3B`。

## 输出结构

PRD 应包含以下内容：

1. Introduction / Overview
2. Goals
3. User Stories
4. Functional Requirements
5. Non-Goals
6. Design Considerations
7. Technical Considerations
8. Success Metrics
9. Open Questions

## 用户故事要求

每个用户故事都应包含：

- 标题
- 描述：`As a [user], I want [feature] so that [benefit]`
- 可验证的验收标准

每条故事都应该足够小，能在一个专注实现周期内完成。

## 验收标准要求

- 必须可验证，不能写成“正常工作”“体验良好”这类模糊表述
- 对 UI 相关故事，必须明确界面行为和验证方式
- 对技术故事，应包含可执行的质量门禁，比如类型检查、测试或构建验证

## 文件输出

- 格式：Markdown
- 建议路径：`tasks/prd-[feature-name].md`
- 文件名使用 kebab-case

## 约束

- 不开始实现
- 不直接改代码
- 不跳过关键澄清问题
- 不生成模糊、不可验收的故事
