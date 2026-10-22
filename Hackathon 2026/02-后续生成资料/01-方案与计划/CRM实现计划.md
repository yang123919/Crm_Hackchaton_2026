# 02｜CRM 原型实现计划

## 目标

在 10 小时内完成一个手机优先的 CRM 外勤跟进原型：销售现场记录客户沟通，AI 将自然语言整理为可确认的活动记录，并由用户选择是否创建 Opportunity。

**产品名：** CRM Pocket Follow-up

**核心价值：** 减少“先跟客户谈、回办公室再补录”造成的延迟和遗忘，同时不把复杂 CRM 全量搬到手机。

## 用户流程

1. 销售从 Contact 列表选择客户。
2. 点击 Capture，用手机语音转写或手动输入客户资料/沟通笔记；麦克风不可用时可直接打字。
3. AI 将输入整理成 CRM 字段及跟进记录；Review 页面展示需检查字段、低置信度提示和可能重复客户。
4. 销售编辑并确认；取消或改写都可以。
5. 确认后保存 Lead 与一条独立 Follow-up 记录，刷新 Lead 详情及 Today 待办。
6. 可选点击「创建 Opportunity」，核对预填内容后确认创建并关联 Contact。

## 页面与交互

### A. Contact 列表

- 搜索姓名/公司，展示 2–3 条虚构 mock 客户。
- 每行显示客户名、公司、最近跟进时间和待办提示。
- 页面首屏突出「快速记录」。

### B. Contact 详情

- 展示联系方式、状态、最近活动、关联 Opportunity。
- 主按钮「记录跟进」。不要显示大量汽车库存字段。

### C. 跟进输入/确认

- 大文本框；示例提示：“客户想比较两款车，周五前需要报价，偏好深蓝色。”
- 「AI 整理」按钮；加载中、失败、空文本和成功状态清晰。
- 结果以可编辑字段呈现：摘要、意向、下一步、跟进日期、商机标题/金额（选填）。
- 明示“AI 建议，确认后才保存”；不自动改写数据库资料。

### D. 保存结果

- 成功 toast/状态卡；新活动显示在 Contact 时间线。
- Opportunity 创建为二次可选操作，含确认页/确认按钮。

## 技术实现建议

沿用组长目前正在 Vibe Coding 的项目和技术栈，不为线框图切换框架。WIREFRAMES.md 定义的是手机端交互，Android 或响应式网页都可承载。若接 MySQL，必须经后端/API，不能由客户端直连数据库；没有现成接口时明确用 mock data/local storage 演示，不能声称已写入 MySQL。

### 前端状态数据结构

```js
Lead = { id, firstName, lastName, mobile, email?, product, outlet, budget?, closeDate?, stage }
FollowUp = { id, leadId, rawNote, summary, nextAction, followUpAt, createdAt }
Opportunity = { id, leadId, title, stage, estimatedValue?, nextAction, createdAt }
```

金额及汽车详情字段都可以省略；若演示商机内容，使用非敏感的虚构资料。

### AI 返回格式

```json
{
  "summary": "客户希望比较两款车，并在周五前收到报价。",
  "intent": "正在比较方案，意向中等偏高",
  "next_action": "准备并发送报价",
  "follow_up_date": "YYYY-MM-DD",
  "opportunity_title": "虚构车型报价跟进",
  "confidence_note": "请销售确认车型与日期"
}
```

- 指令要求模型只抽取原文信息；缺少的信息标为 null，不要自行编造金额、车型、日期或承诺。
- 前端验证 JSON；解析失败则展示可编辑的通用摘要/错误提示和预设 mock 结果。
- API Key 走环境变量或主办方指定的安全配置，不写入前端仓库或提交包。
- 若只有浏览器端快速原型，应避免把长期有效密钥放到公开部署环境；可用本地 mock 模式演示。

## 10 小时工程安排与验收

| 小时 | 实现事项 | 验收标准 |
|---|---|---|
| 0–1 | 写验收流程、草图、分工；确认技术栈与模型接入参数 | 所有人明确只做 Contact → Activity → 可选 Opportunity；确认比赛开赛许可后才写代码 |
| 1–2 | 建项目、创建虚构数据、做 Contact 列表和详情骨架 | 手机上能打开并选中客户 |
| 2–4 | 做跟进输入、AI 结果编辑卡和保存活动 | 即使不接 API 也能通过预置结果完整保存一条跟进 |
| 4–5.5 | 接赞助商模型 API；配置提示词、解析与异常回退 | 成功响应可填表；断网/无权限/错误 JSON 时用户仍能手动完成 |
| 5.5–6.5 | 实现活动时间线和 Opportunity 确认创建 | 新活动可见且关联正确 Contact；Opportunity 可选、不重复误建 |
| 6.5–7.5 | 端到端走查及响应式调整 | 测试空输入、编辑、取消、重复点击、AI 失败、日期和窄屏布局 |
| 7.5–8.5 | 准备 README、架构/流程图、演示 slides | README 有启动方式、mock/API 模式说明；slides 对应评分标准 |
| 8.5–9.25 | 冻结功能、排练、截图/录屏备用 | 90 秒内跑完；刷新或重新启动能恢复演示数据 |
| 9.25–10 | 打包提交及检查缓冲 | 代码、项目文档、slides 均可打开；按较早的 09:00 内部目标提交 |

> 时长按 H0 起算。比赛简报规定 Day 1 开幕致辞后才可开始编写项目代码。若 H0 早于开赛许可，只讨论、草图、分工与资料阅读；等待许可后再写代码。简报一处写提交 09:00，日程表写 09:30，应向主办方核实，团队内部用 09:00 作为截止。

## AI API 接入步骤

1. 由主办方确认团队可用的 provider、套餐、API Key、Base URL、API format、准确 Model ID。
2. 按 workshop 所示的 OpenCode / CC Switch 配置流程添加对应 provider；示例中的 Meta、QwenCloud、模型名不能替代现场发放的参数。
3. 先做最小请求验证；再验证生成结构化 JSON；最后验证文件编辑/应用流程与用量归属。
4. 设置超时和错误状态。API 失败时仍可用 mock 分支完成演示。
5. 确保凭证未进入源代码、截图或提交仓库。

## 演示与测试清单

- Contact 详情可见，用户能输入会谈笔记。
- AI 生成摘要后可以编辑；明确需人确认。
- 点击保存后，活动出现在正确 Contact 时间线。
- Opportunity 由用户主动创建且关联正确。
- 空输入有提示；API 不可用时 mock fallback 可完成全部主流程。
- 手机窄屏无横向溢出；关键按钮容易点到。
- 所有展示数据虚构，不含真实个人信息或凭证。

## 如何体现 SDLC 改进

比赛主题是用 AI 改善 SDLC，CRM 是问题场景。演示及讲稿要讲清：团队如何用 AI API/开发 Harness 加速需求转成可用流程、生成结构化逻辑，并在短周期中通过人工审阅、JSON 校验、异常回退和流程验收尽早发现问题。AI 面向客户的摘要功能解决销售痛点，AI 辅助开发和验证则连接比赛的 SDLC 要求。展示人工审核和故障回退，避免声称模型完全可靠。

## 对照评审标准

从现场照片识别的评分比例：

- Innovation & Next-Gen Thinking：30%
- Technical Execution & Code Quality：25%
- User Experience & Presentation：15%
- Product Vertical Alignment：15%
- Business Value & Commercial Viability：15%

因此优先确保方案有 AI + CRM 工作流创新（30）、主流程真实可运行且错误处理清楚（25）、手机现场录入体验直观（15）、明确关联 CRM 的 Contact/Opportunity（15）、说明减少漏记/加快跟进的业务价值（15）。

## 新增合并：组长 WIREFRAMES.md

原始线框图保存在 `01-原件/04-文字资料/QuickLead-线框图说明.md`。它把方案命名为 **QuickLead**，明确了移动端页面与演示路径。以下交互设计以该线框图为当前 UI 主稿；技术栈继续跟随组长正在使用的项目，不因文档强制换成 Android 或 Web。

### 页面与演示闭环

1. **Today 首页**：逾期、今天、当周记录摘要；列出 overdue 与 due today；底部突出 Capture。
2. **Capture**：麦克风语音转文字并实时显示可编辑 transcript；支持「Type instead」文字输入。无麦克风权限或语音失败时必须可手动输入。
3. **AI working**：显示整理进度；超时或失败时提供 Retry / Fill in manually。
4. **Review & confirm**：AI 预填客户、产品、Outlet、预算、预计成交日、Trade-in 和 Follow-up 字段；用户可以修改。缺失/低置信度字段标记检查；手机号疑似匹配时提示打开旧客户或仍保存新客户。保存前由用户确认。
5. **Lead detail**：显示当前阶段、电话/WhatsApp/语音更新操作、下一步和独立 Follow-up history。
6. **Voice update**：为已有 Lead 添加新的摘要、下一步、跟进日期及阶段建议；确认后追加记录，不覆盖历史。
7. **WhatsApp 草稿**：可编辑消息后用 `wa.me` 打开 WhatsApp，由用户检查并发送。
8. **Leads list / Settings**：搜索筛选及演示模式、语言设置为可选。

主 Demo 路径为 **Today → Capture → AI → Review → Lead detail → WhatsApp draft → Today**；若时间充足再演示 Lead detail → Voice update。演讲仍按已知的 7 分钟结构排练，不把这条 UI 流程误当成完整演讲时长。

### 划分范围

**核心必须完成：** Today 待办、语音+手动 Capture、AI Review 人工确认、Lead 保存、独立跟进记录、电话/WhatsApp 跳转、AI 失败时手动录入。

**可选加分：** 线索搜索筛选、设置、拍名片、完整 Outlet/产品列表、stage 自动推荐。名片扫描是线框图标注的 nice-to-have，不要抢核心流程时间。

**待确认后才定字段：** 线框图里的姓名、手机号、产品、Outlet、预算、预计成交日、Trade-in、Stage 是示例设计；必填项与准确下拉选项要以甲方提供的真实字段/脱敏样例为准。

### 线框图中的数字和交互注意

- “Avg 24 s per lead vs ~5 min”没有实测依据，删除对外宣称；如保留在 UI 草稿，标注为目标值并在测试后填真实测量结果。
- `wa.me` 只负责打开 WhatsApp 并预填草稿，不代表用户已发送。返回 App 后只有用户确认发送，才可将状态记为已联系。
- Duplicate check 可先用规范化手机号匹配 mock records；不要在未确认隐私/数据规则前用 IC 号码做查重。
- AI 不得臆造空字段、车型、日期、预算；`confidence`/缺失提示仅供复核，保存前人审。
- “离线 AI”设置若实际只是 mock fallback，应命名为 Demo/Mock mode，避免让人误解设备本地运行了模型。

### 需要对接的技术前置项

- 语音输入需确认使用 Android speech recognizer、浏览器能力或键盘听写；明确麦克风权限和断网回退。
- 产品、Outlet、Stage 的枚举值要由甲方给出或用虚构 mock 列表；模型只能从提供列表中选择，不自行生成新值。
- MySQL 仍须经 API/后端。演示只使用授权脱敏数据或虚构数据。
