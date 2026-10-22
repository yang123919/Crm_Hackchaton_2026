# Vibe Coding 工作流与 Prompt 提示词指南

> **核心原则**：One Does Not Simply Vibe Code（绝不能盲目/随性地使用 AI 编码）。
> 
> 本工作流通过将 AI 编码拆分为 **规划 (Plan) -> 线框图 (Wireframes) -> 构建 (Build) -> 审查 (Review)** 四个步骤，确保代码质量、架构规范与业务逻辑的严谨性。

---

## 工作流全景图 (Workflow Overview)

```
[1. Plan (规划)]  -->  [2. Wireframes (线框图)]  -->  [3. Build (构建)]  -->  [4. Review (审查)]
```

---

## 阶段一：Plan（规划阶段）

### 目的
让 AI 深入读取现有代码库，在编写任何代码之前澄清需求并制定实施方案。**在此阶段严禁直接修改或新建文件**。

### Prompt 模板
```text
{__PUT_WHAT_YOU_WANT_TO_DO_HERE__}

Read the codebase, and help me come up with a plan to implement everything above.

Make sure to include a short description for this plan in paragraph format at the beginning of the plan.

IMPORTANT: DON'T WRITE OR EDIT ANY FILES.

Use web search if you need to find solutions to problems you encounter, or look for the latest documentation.

Ask me clarifying questions until you are 95% confident you can complete this task successfully.

a. If the question is about choosing different options, please provide me with a list of options to choose from. Mark the option with a clear label, like a, b, c, etc.
b. If the question need custom input that is not in the list of options, please ask me to provide the custom input.

Always mark each question with a number, like 1/, 2/, 3/, etc. so that I can easily refer to the question number when I answer.

For each question, add your recommendation (with reason why) below each options. This would help me in making a better decision.
```

---

## 阶段二：Wireframes（线框图阶段）

### 目的
基于已确定的规划方案，生成 UX 交互流程图以及基于 ASCII 字符的逐页线框图，明确前端页面与交互结构。

### Prompt 模板
```text
Put the plan at _plan_, then give me the ux flow map and screen-by-screen content using the ASCII wireframe. Put it at _wireframes_
```

---

## 阶段三：Build（构建阶段）

### 目的
结合前面生成的 `_plan_`（规划）和 `_wireframes_`（线框图），指导 AI 开始编写代码实现。

### Prompt 模板
```text
Read the plan at _plan_ and _wireframes_, and implement the plan accordingly. Use the wireframes as a guide for the implementation.
```

---

## 阶段四：Review（审查阶段）

### 目的
让 AI 扮演首席工程师（Principal Engineer）的角色，对 `git diff` 及具体代码变更进行严格的 Code Review（涵盖正确性、安全性、性能、可维护性、架构、测试和文档等领域）。

### Prompt 模板
```text
Act as a principal engineer. Read the git diff and review the implementation plan for correctness, security, performance, maintainability, architecture, tests, and docs.
Report only concrete issues with minimal fixes.

Return EXACTLY:

Full review comments:

- [P?] <Title> - <path>:<start>-<end>
  <Impact in 1-2 sentences.>
  Fix: <Smallest precise change/snippet.>
  Ref: <Spec/test/ADR if found; else omit.>

Severity: P0 (break/exploit/plan-defeating), P1 (high-risk/spec mismatch), P2 (should-fix), P3 (nice-to-have).
One issue per bullet, 4-7 lines each, precise paths, no preface, no summary. If context is thin, add "Note: Assumption-...".
```