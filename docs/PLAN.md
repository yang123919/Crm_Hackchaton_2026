# QuickLead — Plan

> Step 1 of the vibe-coding workflow (Plan → Wireframes → Build → Review).
> Working name "QuickLead" — rename in question 6/.

> **Current status (2026-10-22).** The text below is the original plan and is partly out of date. Where the app differs:
> - The microphone/speech input was **removed**; capture is by typing notes, scanning an IC, or scanning a form.
> - **Login was added** (Email + password, stored locally; no Google or other third-party login).
> - **Added beyond the plan:** delete customer, IC and form scanning, **AI Data Health** (duplicates and wrong details, with one-tap fixes), a navy-and-mint visual style, and the "QL" app icon.
> - Not done yet: customers list with search, editing a saved customer, SuiteCRM sync, CI.
> - Nothing from the latest changes has been compiled or run yet. See [QuickLead-改进建议.md](QuickLead-改进建议.md) for the full progress table and open issues.

## Description

QuickLead is an Android companion app for the CS Group dealership CRM (a customised SuiteCRM 7 built by GCR). Today, salespeople have to wait until they are at a laptop to key in customers, so data is entered late, fields are left empty, and follow-ups are forgotten: the Kanban board shows many leads stuck in "Follow Up" with overdue dates, and follow-up history is typed as free text into one Description box. QuickLead lets a salesperson capture a customer in about 20 seconds by speaking after the meeting. AI converts the messy speech into clean, CRM-formatted fields (the "one format" they convert into by hand today), the salesperson confirms and saves, and the app creates a dated follow-up with a reminder and an AI-drafted WhatsApp message. The goal for the hackathon is one complete, reliable demo flow on a real phone, plus a visible "time saved" number for the business-impact slide.

## Problem → solution

| Pain they told us / we saw in the video | QuickLead feature |
|---|---|
| Must use a laptop to key in data | Mobile capture screen, one big mic button |
| Typing long forms is slow; many fields left empty | Voice → AI fills the fields; only required fields shown |
| Manually converting data into "one format" | AI outputs strict JSON matching CRM field names and dropdown values; code normalises phone/dates |
| Forget to follow up; overdue cards in Kanban | "Today" home screen: Overdue / Due today, reminder notification |
| Follow-up history is free text in Description | Structured, dated follow-up log per lead, added by voice |
| Writing follow-up messages takes time | AI-drafted WhatsApp message, one tap to open WhatsApp |
| Duplicate customers | Duplicate warning when the mobile number already exists |

## Scope

### Must have (the demo)
1. **Today screen**: counters (Overdue, Due today, Captured this week), list of follow-up cards, big "Capture customer" button.
2. **Capture**: tap mic → speech-to-text → live transcript; "type instead" fallback.
3. **AI extraction**: transcript → strict JSON (fields below), constrained to the real product/outlet/stage lists.
4. **Review & confirm**: AI-filled form, editable; missing required fields highlighted; duplicate warning; Save.
5. **Lead detail**: info, sales stage chip, follow-up timeline, Call / WhatsApp / "Add voice update".
6. **Voice follow-up update**: speak after a call → AI writes a dated log entry, next action, next date, suggested next stage → confirm.
7. **WhatsApp draft**: AI drafts a polite follow-up message (English or Malay), editable, opens WhatsApp via `wa.me` link.
8. **Seed data** from the GCR-provided sample (or the "GCR TEST" records) so it looks like their CRM.
9. **Demo safety**: "Demo mode" toggle that returns canned AI responses if the network/API fails on stage.

### Nice to have (only after must-haves work end to end)
10. **Time-saved counter**: measure mic-tap → save time per capture; show "Avg 25 s per lead vs ~5 min on laptop" on Today.
11. **Daily reminder notification** (WorkManager) for due/overdue follow-ups.
12. **IC / business card scan**: camera → vision model → fills name, IC, address.
13. **Leads list** with search and stage filter.

### Out of scope (mention in pitch as "next steps")
- Live sync to SuiteCRM via its REST API (V8) — confirm the API is enabled on their instance.
- Login, roles, manager dashboard, document generation.

## Architecture

- **Android**: Kotlin, Jetpack Compose, Material 3, single Activity, Compose Navigation.
- **Pattern**: MVVM — `ViewModel` + `StateFlow` → Repository → Room / AI service.
- **Storage**: Room (local, works offline, no setup). Tables below.
- **Speech**: Android `SpeechRecognizer` / `RecognizerIntent`, language `en-MY` (handles Malaysian English; Malay words pass through).
- **AI**: LLM API called over HTTPS (OkHttp/Retrofit + kotlinx.serialization), JSON output mode with a response schema. API key in `local.properties` → `BuildConfig` (demo only; production would proxy through GCR's backend so the key is never on the phone).
- **WhatsApp**: `Intent(ACTION_VIEW, Uri.parse("https://wa.me/60XXXXXXXXX?text=<urlencoded>"))`.
- **Reminders** (nice to have): WorkManager periodic worker → notification.

```
UI (Compose screens)
   │
ViewModels (StateFlow)
   │
LeadRepository ──── Room (Lead, FollowUpEntry, CaptureMetric)
   │
AiService ──── LLM API (JSON mode)      DemoAiService (canned JSON)
   │
Normalizer (phone, dates, enum matching, duplicate check)
```

## Data model (Room)

**Lead** — mirrors their SuiteCRM Lead/Opportunity fields
- `id`, `firstName`, `lastName`, `mobile` (normalised `60XXXXXXXXX`), `email?`
- `product` (from product list), `outlet` (from outlet list), `status` (`New`/`Converted`)
- `salesStage` (from stage list, default `Follow Up`), `expectedCloseDate?`, `budgetRm?`
- `tradeInUsedCar` (bool), `tradeInBrand?`, `tradeInModel?`, `tradeInYear?`
- `nextFollowUpAt?`, `createdAt`, `updatedAt`

**FollowUpEntry**
- `id`, `leadId`, `date`, `summary`, `nextAction`, `nextDate?`, `rawTranscript`

**CaptureMetric**
- `id`, `startedAt`, `savedAt`, `type` (`new_lead` / `follow_up`)

## AI contract

Prompt rules (system prompt):
- You convert a salesperson's spoken notes (Malaysian English, may mix Malay/Chinese words) into CRM fields.
- Today is `{today}` in Asia/Kuala_Lumpur; resolve relative dates ("next Tuesday", "lusa").
- `product`, `outlet`, `suggested_sales_stage` must be exactly one value from the provided lists, or `null`.
- Never invent data; unknown → `null`. Put uncertain fields in `low_confidence_fields`.
- `budget_rm` as a number ("120k" → 120000).

Response schema (new lead):
```json
{
  "first_name": "string|null",
  "last_name": "string|null",
  "mobile": "string|null",
  "email": "string|null",
  "product": "string|null",
  "outlet": "string|null",
  "budget_rm": "number|null",
  "expected_close_date": "YYYY-MM-DD|null",
  "trade_in": { "used_car": "boolean", "brand": "string|null", "model": "string|null", "year": "number|null" },
  "follow_up": { "summary": "string", "next_action": "string", "next_date": "YYYY-MM-DD|null" },
  "suggested_sales_stage": "string|null",
  "low_confidence_fields": ["string"]
}
```
Follow-up update uses the `follow_up` + `suggested_sales_stage` part only. WhatsApp draft is a separate short text call (input: lead + last log entry + language).

**Don't trust the AI blindly — validate in Kotlin:**
- Phone: strip spaces/dashes/`+`, convert `01x` → `601x`, check length; invalid → mark field red.
- Enums: if value not in list, fuzzy-match ("X50" → "Proton X50"), else `null`.
- Dates: parse; reject past dates for follow-ups.
- Duplicate: query Room by normalised mobile → banner "Existing customer: <name> — open instead?".

## Reference lists (replace with GCR's data if they give a file)

- **Products**: 5008 Allure Plus SUV, 3008 MidLife, 308 GTi, 2008 PureTech 130, Hyundai Ioniq Hev Plus, Hyundai AD Elantra 2.0, Santa Fe 2.4 Premium, Hyundai Starex, Omoda 5 Comfort, Tiggo 8 Pro, GWM Cannon, Proton Iriz, Proton Saga, Proton Exora, Proton X50, C3 Aircross SUV
- **Outlets**: CS Insta, Butterworth Sunway Carnival, SP Village Mall, Alor Setar Aeon Big, Alor Setar Outlet, Alor Setar Aman Central, Alor Setar Tesco Mergong, B.M AEON Alma
- **Sales stages**: Prospecting, Follow Up, Test Drive, Quotation, Follow up 2, Booking, Pending Booking Refund, Booking Refund, Bank Loan Submission, LOU, Allocation Complete, Full Downpayment, Issue Downpayment Receipt, Registration, Delivery, Finance Disbursements, Completed, Closed Won
- Use only test/fake customers in the demo (no real names from their system).

## Build order (each step leaves a demo-able app)

| # | Task | Est. |
|---|---|---|
| 1 | Project setup: Compose + M3 theme, navigation, Room, seed data | 30 min |
| 2 | Today screen + Lead detail (read-only, from seed) | 40 min |
| 3 | Capture screen: mic + transcript + type fallback | 30 min |
| 4 | AiService + DemoAiService + Normalizer | 50 min |
| 5 | Review & confirm screen, duplicate check, save | 40 min |
| 6 | Voice follow-up update on Lead detail | 30 min |
| 7 | WhatsApp draft sheet | 20 min |
| 8 | Polish: empty states, loading, errors, colours, icon | 30 min |
| 9 | Nice-to-haves (10 → 11 → 13 → 12) | if time |
| 10 | Record backup demo video on the phone | 10 min |

## Testing / demo checks

- Unit tests for `Normalizer` (phone formats, "120k", relative dates, enum fuzzy match).
- Test the AI with 10 messy sample sentences (below); all must parse without crashing.
- Run the full demo flow 3 times on a real phone with Wi-Fi off once (Demo mode).

Sample voice lines:
1. "Just met Mr Tan Ah Kow, 012-345 6789, interested in the X50, budget around 120k, wants test drive next Tuesday, call him back Friday."
2. "Puan Aisyah, one seven three two two eight nine nine one zero, nak Saga, trade in Myvi 2018, follow up esok."
3. "Mr Lim from Butterworth, wants the Tiggo 8 Pro, no budget yet, send him quotation, WhatsApp him tomorrow."
4. Follow-up: "Called Mr Tan, he's ok with the price, booking this weekend."

## Risks

| Risk | Mitigation |
|---|---|
| Wi-Fi or API fails on stage | Demo mode with canned responses + backup video |
| Speech recognition misses Malaysian names | Editable review form; type fallback |
| AI picks wrong dropdown value | Constrained enums + Kotlin validation + user confirms |
| API key exposed in APK | Demo only; production via backend proxy (say this in the pitch) |
| Running out of time | Strict build order; nice-to-haves only after step 8 |

## Clarifying questions

Answer like `1/a, 2/a, 3/a, 4/a,c, 5/<custom>, 6/<custom>, 7/a, 8/a`. Recommendations are marked.

**1/ Which LLM API?**
- a. Gemini (Flash model) — **Recommended**: GCR already uses Gemini (listed on their GoodFirms profile), it has a free tier, and it supports JSON schema output and audio/image input for nice-to-haves.
- b. Claude (Haiku) — fast and cheap, strong at following strict formats.
- c. OpenAI.

**2/ How do we turn voice into text?**
- a. Android `SpeechRecognizer` (`en-MY`) → text → LLM — **Recommended**: free, fast, no extra setup; the review screen fixes mistakes.
- b. Record audio and send it directly to Gemini — better with mixed English/Malay/Chinese, but slower and more to build.
- c. Typing only — not recommended; it removes the "wow" moment.

**3/ Where do we store data?**
- a. Room (local) — **Recommended**: zero setup, works offline (a pitch point: "captures even without signal").
- b. Firebase Firestore — you know it already; adds a web/manager view later, but needs setup and network.
- c. Live SuiteCRM API — not for today; mention it as the integration path.

**4/ Which nice-to-haves first, if there's time?** (pick in order)
- a. Time-saved counter — **Recommended first**: gives a real number for the business-impact slide.
- b. Reminder notification — **Recommended second**: proves "never forget a follow-up".
- c. IC / business card scan.
- d. Leads list with search/filter.

**5/ Seed data:** what did GCR give you (Excel/CSV/screenshots)? Please share the column names or a sample. If nothing, we use the "GCR TEST" style records above.

**6/ App name and brand colour:** custom input (e.g. "QuickLead", CS Group red/grey to match their CRM).

**7/ Voice / message language:**
- a. English with Malay words allowed; WhatsApp draft in English or Malay (toggle) — **Recommended**.
- b. English only.
- c. English + Malay + Chinese.

**8/ Minimum Android version:**
- a. minSdk 26 (Android 8) — **Recommended**: covers almost all phones; simpler APIs.
- b. minSdk 24.
