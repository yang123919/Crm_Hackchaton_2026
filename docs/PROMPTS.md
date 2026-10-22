# QuickLead — Workflow prompts (Plan → Wireframes → Build → Review)

Put `PLAN.md`, `WIREFRAMES.md` and this file in your Android project's `docs/` folder, then open Claude Code in the project folder.

**Fast path (recommended today):** the plan and wireframes are already done. Answer the clarifying questions at the bottom of `docs/PLAN.md` (or accept the recommendations), then go straight to **Step 3**.

Keep every output (the plan, the wireframes, the review comments, time taken per step). These are your evidence for the "AI across the SDLC" slide.

---

## Step 1: Plan (only if you want Claude Code to re-plan from scratch)

```
Build "QuickLead", an Android app (Kotlin, Jetpack Compose, Material 3) that is a mobile
companion for the CS Group dealership CRM (a customised SuiteCRM 7 built by GCR).

Problem: salespeople must use a laptop to key in customers, so data is entered late or
forgotten, and follow-ups are missed. Follow-up history is typed as free text into one
Description box, and they manually convert messy customer info into the CRM's format.

What the app must do:
1. Today screen: counters (Overdue / Due today / Captured this week), follow-up cards with
   Call and WhatsApp buttons, a big "Capture customer" button.
2. Capture: tap mic -> Android speech-to-text (en-MY) -> editable live transcript, with a
   "type instead" fallback.
3. AI extraction: send the transcript to an LLM in JSON mode and get CRM fields: first/last
   name, mobile, email, product, outlet, budget_rm, expected_close_date, trade-in
   (used_car, brand, model, year), follow_up (summary, next_action, next_date),
   suggested_sales_stage, low_confidence_fields. Product, outlet and stage must be chosen
   only from fixed lists. Resolve relative dates using today's date in Asia/Kuala_Lumpur.
4. Review & confirm screen: AI-filled editable form, highlight missing/low-confidence
   fields, duplicate warning if the mobile number already exists, Save.
5. Lead detail: info, sales stage chip, dated follow-up timeline, Call / WhatsApp / voice
   update buttons.
6. Voice follow-up update: speech -> AI -> dated log entry + next date + suggested stage.
7. WhatsApp: AI-drafted message (EN or BM), editable, opened with a wa.me link.
8. Room database seeded with sample leads; a "Demo mode" that returns canned AI JSON when
   offline; Kotlin validation for phone numbers (normalise to 60XXXXXXXXX), dates, and enum
   values (never trust the AI blindly).
Nice to have: time-saved counter (mic tap -> save), daily reminder notification
(WorkManager), IC/business card scan, leads list with search.
Store the API key in local.properties via BuildConfig. Use MVVM (ViewModel + StateFlow +
Repository). Write unit tests for the validation/normalisation code.
See docs/PLAN.md for the full field lists, sample lines, and build order.

Read the codebase, and help me come up with a plan to implement everything above.

Make sure to include a short description for this plan in paragrah format at the beginning of the plan.

IMPORTANT: DON'T WRITE OR EDIT ANY FILES.

Use web search if you need to find solutions to problems you encounter, or look for the latest documentation.

Ask me clarifying questions until you are 95% confident you can complete this task successfully.

a. If the question is about choosing different options, please provide me with a list of options to choose from. Mark the option with a clear label, like a, b, c, etc.
b. If the question need custom input that is not in the list of options, please ask me to provide the custom input.

Always mark each question with a number, like 1/, 2/, 3/, etc. so that I can easily refer to the question number when I answer.

For each question, add your recommendation (with reason why) below each options. This would help me in making a better decision.
```

Answer example: `1/a, 2/a, 3/a, 4/a,b, 5/no file - use test data, 6/QuickLead, CS red, 7/a, 8/a`

---

## Step 2: Wireframes (only if you re-planned in Step 1)

```
Put the plan at docs/PLAN.md, then give me the ux flow map and screen-by-screen content using the ASCII wireframe. Put it at docs/WIREFRAMES.md
```

---

## Step 3: Build

```
Read the plan at docs/PLAN.md and docs/WIREFRAMES.md, and implement the plan accordingly. Use the wireframes as a guide for the implementation.

My answers to the clarifying questions in the plan: 1/a, 2/a, 3/a, 4/a,b, 5/<your answer>, 6/<your answer>, 7/a, 8/a

Follow the "Build order" table exactly, one step at a time. After each step: make sure the
project builds, tell me what to test on the phone, and commit to git before moving on.
Do the "Nice to have" items only after steps 1-8 all work end to end.
```

Tips while building:
- Run on a real phone after each step, not only the emulator (the mic works properly on a phone).
- If something breaks, paste the exact error into Claude Code.
- Commit after every working step so you can roll back.

---

## Step 4: Review

```
Act as a principal engineer. Read the git diff and review the implementation plan for correctness, security, performance, maintainability, architecture, tests, and docs.
Report only concrete issues with minimal fixes.

Return EXACTLY:

Full review comments:

  - [P?] <Title> — <path>:<start>-<end>
    <Impact in 1–2 sentences.>
    Fix: <Smallest precise change/snippet.>
    Ref: <Spec/test/ADR if found; else omit.>

Severity: P0 (break/exploit/plan-defeating), P1 (high-risk/spec mismatch), P2 (should-fix), P3 (nice-to-have).
One issue per bullet, 4–7 lines each, precise paths, no preface, no summary. If context is thin, add "Note: Assumption—…".
```

Fix all P0 and P1 issues before the demo. Screenshot the review output: it shows AI catching bugs before release, which is exactly what the problem statement asks for.
