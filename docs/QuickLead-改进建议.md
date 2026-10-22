# QuickLead 改进建议

> 依据：`Hackathon 2026` 资料（视觉风格、线框图、实现计划）与当前代码（只读检查，本文件不涉及代码改动）。
> 优先级：**P0** 演示前必须处理 · **P1** 强烈建议 · **P2** 有时间再做。
> 说明：我没有在本机成功编译和运行 App（Gradle 在当前环境报 loopback 错误），所以下面都是基于代码阅读的判断，标了「待验证」的需要你在真机上确认。

---

## 〇、当前进度（截至 2026-10-22）

### 已完成（已提交）
| 事项 | 提交 | 说明 |
|---|---|---|
| 删除客户 | `5dd0354` | 详情页垃圾桶图标 + 确认框；客户的跟进历史一并删除 |
| 跟进历史改为 `LazyColumn` | `70ba9a8`、`a1ae3e8` | 历史区固定约两条高度，可在框内滚动 |
| 修复重复的「WhatsApp sent」 | `05bfdf1` | 同一客户同一天只保留一条，重复点击会替换而不是新增 |
| AI Data Health | `05bfdf1` | 查重复客户 / 重复历史 / 非法手机号 / 细节矛盾 / 过期跟进；规则找问题，AI 解释；可一键合并或删除重复；13 个单元测试 |
| 改进建议文档 | `b407078` | 本文档 |
| 视觉改版（深蓝薄荷） | `399b4ed` | 主题、顶栏、按钮、Today 头部、登录页、详情页头像 |
| App 图标 A 版 | `1db6843` | 自适应图标 + 各分辨率旧版图标，原图作为母版入库 |
| Today 三个计数卡对齐 + 配色 | `1db6843` | 三张卡同高，白底 + 淡色图标圆圈 |
| 页面切换「刷新感」修复 | `1db6843` | 只有最上层页面做动画，下层保持不透明 |
| 登录方式说明更正 | `1db6843` | 登录/注册为 **Email + 密码**，不做 Google 登录 |

### 已改但**没有验证**
- 以上所有代码**都没有编译、没有运行过**：Gradle 在 Claude 所在环境报 `Unable to establish loopback connection`，包括 `DataHealthTest` 的 13 个用例。**下一步第一件事：在 Android Studio 里 Sync → Build → 跑单元测试 → 真机看一遍。**
- 真机上需要重点确认：Today 三张卡是否等高；页面切换是否还有闪烁；图标在你的桌面上是否被圆形/方形遮罩裁到；Data Health 的合并是否保留了历史。

### 尚未开始
客户列表 + 搜索（二.1）· 资料编辑（二.2）· 单条历史编辑/删除与撤销 · 通话/WhatsApp 记录确认 · CSV 导出 · IC 加密与同意 · GitHub Actions CI · Room DAO/UI 测试 · 字符串国际化。

### 静态检查发现的问题（只检查，未修改）
| # | 问题 | 依据 | 建议 |
|---|---|---|---|
| 1 | **当前构建里的 AI 实际上全是 Demo（离线预设）**：`local.properties` 里没有 `GEMINI_API_KEY`，`AiRouter` 会一直走 `DemoAiService`。Data Health 的「AI 解释」目前只是内置模板文字 | `app/build.gradle.kts`、`local.properties` | 演示若要真 AI，在 `local.properties` 加 `GEMINI_API_KEY=...`（该文件已被 `.gitignore` 忽略，不会被提交） |
| 2 | Release 构建**没开混淆/压缩**（`isMinifyEnabled = false`） | `app/build.gradle.kts:41` | 发布前开启并补 keep 规则；同时让 APK 里的 Key 更难被提取 |
| 3 | `fallbackToDestructiveMigration(dropAllTables = true)` 仍在；`exportSchema = false` | `data/AppDatabase.kt` | 改表结构前必须先写 `Migration`，否则客户数据会被静默清空 |
| 4 | 搜索入口仍被隐藏（`onOpenLeads = null`），不在「逾期/今天/本周」的客户找不到 | `QuickLeadNavHost.kt` | 见二.1 |
| 5 | 删除客户后，Room 先发出「客户不存在」，页面可能在返回动画前闪一下 *Customer not found*（读代码推断，未在真机验证） | `LeadDetailScreen.kt`、`LeadDetailViewModel.delete` | 删除时先设「正在删除」状态，或在返回后再删除 |
| 6 | Data Health 页：AI 解释回来后，每张卡的建议文字会从内置文案**替换**成 AI 文案，进度条/摘要出现会让布局下移，这一页仍可能有轻微的「刷新」感 | `ui/health/` | 先隐藏建议行直到 AI 返回，或固定占位高度 |
| 7 | 登录失败计数只在内存（`HashMap`），重启 App 后清零 | `auth/AuthRepository.kt:70` | 已确认；把计数和锁定时间存进 DataStore |
| 8 | 视觉风格文档仍写「演示避免 AI 依赖、不宣传 AI」，与 App 现状和黑客松题目（要求 AI）冲突 | `QuickLead-视觉风格.md` 「产品演示边界」 | 和队友确认叙事后改文档 |
| 9 | `docs/PLAN.md`、`docs/WIREFRAMES.md` 仍写语音输入、不含登录等旧内容 | `docs/` | 已在 `PLAN.md` 顶部加了「当前状态」说明，正文待重写 |
| 10 | 图标母版文件名是 `exec-138a6c25-….png`，看不出用途 | `Hackathon 2026/.../v3-整合演示/` | 改名为 `app-icon-A-master.png`（改名会影响 git 历史，所以没动） |
| 11 | `Hackathon 2026` 文件夹约 183 MB（32 个文件），其中 `整理前完整资料包.zip`（77 MB）与其他原件内容重复，`CRM演示视频.mp4` 76 MB | 文件夹本身 | 已决定下次提交整包带上；推送到 GitHub 时会触发 >50 MB 警告，可考虑 Git LFS 或去掉重复的 zip。视频/录音若含客户信息，注意保密 |
| 12 | 禁用状态的薄荷按钮（深蓝字 45% 透明度叠在 35% 薄荷上）对比度低 | `ui/components/Brand.kt` | 可接受（禁用态本来就弱），若要更清晰可加深禁用文字 |

---

## 一、资料与实现不一致（先对齐，再继续做）

| # | 问题 | 位置 | 建议 | 优先级 |
|---|---|---|---|---|
| 1 | ~~登录方式文档与代码不一致~~ **已确认：登录/注册使用 Email + 密码，功能保持不变，不做 Google 或其他第三方登录。** 视觉风格文档里「仅手机号」的描述是错的，已更正为 Email | `QuickLead-视觉风格.md`、`v3-整合演示/README.md` | 演示时注意：线框图 `02-手机号登录注册.png` 是早期手机号版概念稿，和实机不同，讲解时说明即可 | 已处理 |
| 2 | 视觉文档说「演示避免 AI 依赖，不宣传 AI」，但 App 里到处是 *Process with AI*、✨ 标记、Data Health 的 AI 解释；而黑客松题目恰恰要求 **AI 改进流程** | `CaptureScreen`、`ReviewScreen`、`ui/health/` | 先和队友定叙事：题目要求 AI，建议保留 AI，但文档里的「不宣传 AI」改掉；同时保留 Demo mode 作为断网兜底 | P0 |
| 3 | 文档写「主要制作网页、实机演示网页版本」，实际是原生 Android（Compose） | `Hackathon 2026/.../v3-整合演示/README.md` | 统一口径：演示的就是 Android App，避免现场被问 | P1 |
| 4 | `docs/PLAN.md` 仍写「麦克风语音输入」「登录不在范围内」，代码里麦克风已移除、登录已加入 | `docs/PLAN.md` | 同步计划文档，评审会读 | P1 |
| 5 | 线框图的客户列表（搜索 + 阶段筛选）没有入口：`TodayScreen` 的 `onOpenLeads = null`，搜索图标被隐藏 | `QuickLeadNavHost.kt`（`TodayScreen(...)` 调用处） | 不在「逾期/今天/本周」里的客户目前**找不到**。这是最影响真实使用的缺口，见 2.1 | P0 |

---

## 二、功能改进

### 2.1 客户列表 + 搜索 + 阶段筛选（P0）
- 现状：只能通过 Today 的三个计数器进入客户，没有全部客户列表，也没有搜索。
- 建议：新增 `CustomersScreen`：搜索姓名/手机号，阶段芯片筛选（All / New / Quotation …）。`LeadRepository.observeLeads()` 已有全量数据，`LeadGroups` 的思路可复用。
- 理由：线框图第 6 屏就是它；销售最常见的场景是「客户打电话来，我要立刻找到他」。

### 2.2 客户资料可编辑（P0）
- 现状：`LeadDetailScreen` 只读；资料录错只能删除重来（这也是「数据有问题」的主要来源之一）。
- 建议：详情页加「编辑」，复用 `ReviewScreen` 的表单组件（`FormTextField / Dropdown / DateField`）。
- 同时：历史记录支持**单条编辑/删除**（长按），删除客户加**撤销**（Snackbar "UNDO"，延迟 5 秒再真正删除）。

### 2.3 Data Health（已做）的下一步（P1）
- 保存客户时就做一次校验（现在只在 Review 页查手机号重复），把 Data Health 的规则前置，错误数据就不会进库。
- Today 顶部显示「3 个数据问题」小徽标，点进去直接修。
- 手机号重复规则可能误判家人共用号码，已在卡片上提示；可增加「忽略此项」按钮并持久化。

### 2.4 WhatsApp / 通话记录的真实性（P1）
- 现状：点「Open in WhatsApp」就写入「WhatsApp sent」，但 App 无法确认是否真的发出。当天重复点击已合并为一条。
- 建议：回到 App 时弹一个小提示「刚才发送了吗？」再记录；或把记录文案改成「WhatsApp opened」。
- 打电话同理：`Intents.dial` 之后回到 App，提示「记录这次通话？」一键写入跟进。

### 2.5 提醒（P1，待验证）
- 每天 9:00 的 WorkManager 周期任务**不是精确闹钟**，省电模式下可能延迟。演示前用「Send test reminder now」确认通知可点击跳转到逾期列表。
- Android 13+ 通知权限被拒后没有引导；设置页可加「去系统设置开启通知」。

### 2.6 手机号与国际化（P2）
- `Normalizer.isValidMobile` 只接受马来西亚手机号；新加坡/国际客户会被判为非法。可允许「其他国家」手动放行。
- 所有界面文字是写死在 Compose 里的英文，没有 `strings.xml`。目标用户在马来西亚，建议抽取字符串并提供 BM / 中文，WhatsApp 草稿语言设置已经有了，界面语言也该一致。

---

## 三、数据与安全

| # | 问题 | 位置 | 建议 | 优先级 |
|---|---|---|---|---|
| 1 | `fallbackToDestructiveMigration(dropAllTables = true)`：以后改表结构忘记写迁移，**所有客户数据会被静默清空** | `data/AppDatabase.kt:58` | 发布前移除；每次改表写 `Migration`；`exportSchema = true` 并提交 schema 文件，方便做迁移测试 | P0 |
| 2 | Gemini API Key 打包进 APK（代码注释也承认是 demo） | `GeminiAiService`、`QuickLeadApp.kt` | 演示可以；写进「下一步」：经 GCR 后端代理，Key 不落手机，并做限流 | P1 |
| 3 | IC 号码明文存 Room（只在界面遮码）。IC 属于个人敏感数据（PDPA） | `Entities.kt` `icNumber` | 加密数据库（SQLCipher）或只存哈希 + 末四位；增加「已获客户同意」勾选；设置页提供「清除全部数据」 | P1 |
| 4 | 登录失败次数只存在内存 `attempts`（`HashMap`）里，重启 App 即清零（已确认） | `auth/AuthRepository.kt` | 把计数和锁定时间持久化到 DataStore | P2 |
| 5 | 没有备份/导出：手机丢了或卸载，数据全部消失 | — | 增加 CSV 导出（分享给自己/邮箱），后续对接 SuiteCRM REST（PLAN 已列为下一步） | P1 |
| 6 | 手机号、IC 在数据库层没有唯一约束，查重只靠界面流程 | `LeadDao`、`Entities.kt` | 对规范化后的手机号加索引查重（保存前在事务内再查一次），避免快速连点产生重复 | P1 |

---

## 四、性能与稳定性

1. **全量加载再内存过滤**：`TodayViewModel`、`LeadListViewModel` 每次变化都拉全部客户再用 Kotlin 过滤。几百条没问题，上千条会卡。把逾期/今天/本周下推成 SQL `WHERE`（P2）。
2. **AI 调用没有重试/退避**：`GeminiAiService` 超时 30 秒、失败就报错。加 1 次自动重试 + 指数退避；离开页面时取消请求（协程取消已处理一部分）（P1）。
3. **AI 结果可缓存**：Data Health 已按问题 ID 缓存解释，WhatsApp 草稿同理可按（客户 + 阶段 + 语言）缓存，减少等待与费用（P2）。
4. **Release 构建**：已确认 `isMinifyEnabled = false`，没有混淆/压缩。发布前开启，并为 kotlinx.serialization / Room / ML Kit 配好 keep 规则。
5. **崩溃与指标**：接入 Crashlytics；`CaptureMetric` 已经在记录录入耗时，可导出做「节省时间」的演示数据（P2）。

---

## 五、体验与视觉

1. **页面切换有明显「刷新」感**：原因与修复见本文第七节（已做修复）。
2. **加载态**：`Today / 客户列表 / 详情 / Data Health` 在数据出来前先显示转圈，再「弹出」内容。建议改成骨架屏（灰色占位卡片），或延迟 300ms 再显示转圈（已做后者）。
3. **颜色对比度**：薄荷青 `#55DFC2` 在白底上对比度约 1.6:1，**不能当文字/链接色**。规范中的「薄荷青用于链接」建议改为：薄荷做按钮底色 + 深蓝文字（对比度 > 10:1），链接用更深的青（本次改版已采用 `#0A7F73`，对比度约 4.9:1）。
4. **深色模式**：规范只定义了浅色；本次顺势做了一套深海军蓝的深色配色，建议在真机两种模式下都过一遍。
5. **触控与无障碍**：多数图标 `contentDescription = null`（装饰性可以），但纯图标按钮（返回、刷新、设置）都有描述，保持；计数卡、状态点只靠颜色区分，建议同时有文字（「逾期」「今天」已有）。
6. **大字体**：详情页三个按钮已处理；客户卡片的标签在大字体下可能挤压，建议用 `FlowRow`。
7. **空状态**：Today 无数据时可加「导入示例客户」按钮，演示时一键填充。

---

## 六、工程与 SDLC（黑客松题目重点）

题目要求展示「AI 如何改进开发流程」，建议在演示里准备这些**可量化**的证据：

| 阶段 | 做法 | 证据 |
|---|---|---|
| 需求 | 用会议录音/文字让 AI 整理需求 → `CRM实现计划.md` | 文档 + 耗时对比 |
| 编码 | AI 对照线框图生成 Compose 界面 | 本次 UI 改版 |
| 测试 | AI 生成单元测试（已有 Normalizer / Data Health 等测试） | 用例数、覆盖的 bug 类型 |
| 调试 | AI 定位问题（如重复 WhatsApp 记录、页面刷新感） | 前后对比 |
| 部署 | GitHub Actions：每次提交自动 build + 单测 + lint | 绿色流水线截图 |
| 维护 | AI 做代码审查、更新文档 | PR 评论 |

补充：
- **CI（P0 对演示最有价值）**：`.github/workflows/android.yml` → `./gradlew testDebugUnitTest lintDebug assembleDebug`。
- **测试缺口**：目前只有纯逻辑单测；缺 Room DAO 测试（`logWhatsApp`、`mergeLeads` 这类事务逻辑最该测）、ViewModel 测试、Compose UI 测试（登录、保存客户主流程）。
- **静态检查**：加 ktlint 或 detekt，避免风格不一致。
- **提交习惯**：保持小而清晰的提交（已经这样做了）；`.idea/misc.xml` 经常是本地改动，建议加入 `.gitignore`。

---

## 七、为什么每次进新界面都有明显的「刷新」效果

我读了导航与各页面代码，原因有四个，按影响大小排序：

1. **默认转场是 700ms 的交叉淡入淡出。** `QuickLeadNavHost` 里的 `NavHost` 没有设置任何转场，Navigation Compose 默认让旧页面淡出、新页面淡入，各 700ms。每个页面都有自己的 `Scaffold + TopAppBar`，所以过程中**两套标题栏和背景会半透明叠在一起**，看起来就像整页闪了一下。
2. **窗口底色与页面底色不一致。** `themes.xml` 的 `windowBackground` 固定是白色，而 Compose 页面（尤其深色模式）是另一种颜色。淡入淡出的中间帧会透出白色窗口底，深色模式下尤其明显，像「白闪」。
3. **新页面先显示转圈，再弹出内容。** 每个页面的 ViewModel 初始状态都是 `loading = true`（`stateIn(..., TodayUiState())` 等），Room 的数据几十毫秒后才到，于是先出现转圈/空白，再一下子出现列表，布局发生跳变。
4. **每个目的地都会新建 ViewModel 并重新订阅数据库。** 这本身是正常的，但和第 3 点叠加，每次前进都是一次「冷启动」。

**已做的修复（只动界面层）**：
- 第一版修复把转场缩短成「新旧页面同时淡入淡出」，但这样中间帧**两个页面都半透明**，窗口底色透出来，深蓝顶栏后面闪一下浅色，仍然像刷新。现在改为：**只有最上层的页面做动画**（淡入 + 轻微滑入；返回时淡出 + 滑出），下面的页面保持完全不透明、不做任何动画，直到上层动画结束。
- 窗口底色改成与 App 背景一致（浅色/深色各一套），转场中间帧不再透白。
- 加载转圈改为**延迟 300ms 才出现**，数据很快到达时用户根本看不到转圈。
- 状态栏统一为深色样式，配合深蓝顶栏，避免顶部闪烁。

**还可以继续做（会碰数据层，所以我没动）**：
- 在 ViewModel 里用 `SharingStarted.Eagerly` 或在上一页预取数据，让新页面第一帧就有内容。
- 把顶栏提到外层共享（一个 `Scaffold` 包住 `NavHost`），切换时顶栏不重建。

---

## 八、建议的推进顺序（一周内）

1. P0：统一登录方式 / AI 叙事（一）→ 客户列表与搜索（2.1）→ 资料编辑（2.2）→ 移除破坏性迁移（三.1）→ CI（六）。
2. P1：Data Health 前置到保存环节、通话/WhatsApp 记录确认、导出 CSV、IC 加密/同意。
3. P2：国际化、SQL 下推、骨架屏、崩溃监控。

> 最值得做的三件事：**客户列表+搜索**、**资料可编辑**、**移除破坏性迁移**。前两件解决「找不到、改不了」，第三件避免演示前最后一次改表把数据清空。
