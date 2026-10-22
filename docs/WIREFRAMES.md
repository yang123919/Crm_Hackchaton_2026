# QuickLead — Wireframes

> Step 2 of the vibe-coding workflow. Based on `docs/PLAN.md`.

## UX flow map

```
                    ┌──────────────┐
                    │ 1. Today     │◄──────────────────────────┐
                    └──┬────────┬──┘                           │
          [Capture]    │        │  tap a follow-up card        │
                       ▼        ▼                              │
            ┌──────────────┐  ┌──────────────┐                 │
            │ 2. Capture   │  │ 5. Lead      │                 │
            │ (mic / type) │  │    detail    │                 │
            └──────┬───────┘  └─┬─────┬────┬─┘                 │
          [Process]│  [Add voice│     │    │[Call]             │
                   ▼    update] ▼     │    └──► phone dialer   │
            ┌──────────────┐ ┌────────┴─────┐                  │
            │ 3. AI working│ │ 6. Voice     │                  │
            └──────┬───────┘ │    update    │                  │
                   ▼         └──────┬───────┘                  │
            ┌──────────────┐        │ AI log entry             │
            │ 4. Review &  │        ▼                          │
            │    confirm   │  confirm → back to 5              │
            └──────┬───────┘                                   │
          [Save]   │   duplicate? → "Open existing" → 5        │
                   ▼                                           │
            Saved toast ─► 5. Lead detail ─[WhatsApp]─► 7. WhatsApp draft
                                                          │ [Send]
                                                          ▼
                                                  WhatsApp app opens
                                                          │ return
                                                          └──────────► 1

 Settings (gear on Today): Demo mode on/off, WhatsApp language EN/BM
 Optional: 8. Leads list (search/filter) from Today top bar
```

Primary demo path: **1 → 2 → 3 → 4 → 5 → 7 → 1**, then **5 → 6** for the follow-up update.

---

## 1. Today (home)

```
┌────────────────────────────────────┐
│ QuickLead                 [⌕] [⚙]  │
│ Good morning, Kelvin               │
│                                    │
│ ┌─────────┐┌─────────┐┌─────────┐  │
│ │   3     ││   5     ││   12    │  │
│ │ Overdue ││ Today   ││ This wk │  │
│ │  (red)  ││ (amber) ││captured │  │
│ └─────────┘└─────────┘└─────────┘  │
│                                    │
│ ⏱ Avg 24 s per lead (vs ~5 min)    │  ← nice-to-have 10
│                                    │
│ OVERDUE                            │
│ ┌────────────────────────────────┐ │
│ │ Mr Tan Ah Kow       2 days late│ │
│ │ Proton X50 · Follow Up         │ │
│ │ "Call back about test drive"   │ │
│ │              [Call] [WhatsApp] │ │
│ └────────────────────────────────┘ │
│                                    │
│ DUE TODAY                          │
│ ┌────────────────────────────────┐ │
│ │ Puan Aisyah            Today   │ │
│ │ Proton Saga · Quotation        │ │
│ │ "Send quotation, trade-in Myvi"│ │
│ │              [Call] [WhatsApp] │ │
│ └────────────────────────────────┘ │
│                                    │
│        ┌──────────────────────┐    │
│        │ 🎤  Capture customer │    │  ← large primary button
│        └──────────────────────┘    │
└────────────────────────────────────┘
```
- Empty state: "No follow-ups today. Capture your next customer."
- Tapping a card body → 5. Lead detail.

## 2. Capture

```
┌────────────────────────────────────┐
│ ←  New customer                    │
│                                    │
│  Tell me about the customer:       │
│  name, phone, car, budget,         │
│  next step.                        │
│                                    │
│            ┌────────┐              │
│            │   🎤   │  ← pulsing   │
│            │ 00:14  │    when on   │
│            └────────┘              │
│         Tap to stop                │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ "Just met Mr Tan Ah Kow, 012-  │ │
│ │ 345 6789, interested in the    │ │
│ │ X50, budget around 120k, test  │ │
│ │ drive next Tuesday…"           │ │
│ └────────────────────────────────┘ │
│   (live transcript, editable)      │
│                                    │
│  [⌨ Type instead]  [📷 Scan card]  │  ← scan = nice-to-have 12
│                                    │
│ ┌────────────────────────────────┐ │
│ │        Process with AI  →      │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```
- Mic permission denied → show type field + "Enable mic in Settings".
- Process disabled until transcript has text.

## 3. AI working

```
┌────────────────────────────────────┐
│ ←  New customer                    │
│                                    │
│                                    │
│            ◌ ◌ ◌                   │
│   Converting to CRM format…        │
│                                    │
│   ✓ Reading your notes             │
│   ✓ Matching product & outlet      │
│   … Setting follow-up date         │
│                                    │
│                                    │
│ (if > 8 s or error:)               │
│ ┌────────────────────────────────┐ │
│ │ Couldn't reach AI.             │ │
│ │ [Retry]   [Fill in manually]   │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

## 4. Review & confirm

```
┌────────────────────────────────────┐
│ ←  Check details                   │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ ⚠ Existing customer: Tan Ah Kow│ │  ← only if mobile matches
│ │   [Open existing] [Save as new]│ │
│ └────────────────────────────────┘ │
│                                    │
│ ✨ = filled by AI   ! = please check│
│                                    │
│ First name ✨  [ Tan Ah         ]  │
│ Last name  ✨  [ Kow            ]  │
│ Mobile     ✨  [ 60123456789    ]  │
│ Email          [                ]  │
│ Product    ✨  [ Proton X50    ▼]  │
│ Outlet     !   [ Select…       ▼]  │  ← required, missing = red
│ Budget RM  ✨  [ 120,000        ]  │
│ Close date ✨  [ 15 Nov 2026   📅] │
│                                    │
│ Trade-in   ✨  ( ) No  (•) Yes     │
│   Brand [Perodua] Model [Myvi]     │
│   Year  [2018]                     │
│                                    │
│ ── Follow-up ───────────────────── │
│ Next action ✨ [ Test drive      ] │
│ Next date   ✨ [ Tue 27 Oct    📅] │
│ Stage       ✨ [ Test Drive    ▼]  │
│ Note: "Interested in X50, budget   │
│  ~RM120k, wants test drive."       │
│                                    │
│ ┌────────────────────────────────┐ │
│ │        ✓ Save customer         │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```
- Low-confidence fields get "!" and an amber outline.
- Save disabled until required fields (first name, mobile, product, outlet) are valid.

## 5. Lead detail

```
┌────────────────────────────────────┐
│ ←  Tan Ah Kow              [✎]     │
│ 60123456789 · Proton X50           │
│ Alor Setar Aman Central            │
│ [ Test Drive ▼ ]  ← stage chip     │
│                                    │
│ ┌──────────┐┌──────────┐┌────────┐ │
│ │ 📞 Call  ││💬WhatsApp││🎤Update│ │
│ └──────────┘└──────────┘└────────┘ │
│                                    │
│ Next: Test drive · Tue 27 Oct      │
│                                    │
│ ── Follow-up history ───────────── │
│ ● 22 Oct 2026                      │
│   Interested in X50, budget        │
│   ~RM120k, wants test drive.       │
│   Next: test drive Tue 27 Oct      │
│                                    │
│ ● (older entries…)                 │
│                                    │
│ ── Details ─────────────────────── │
│ Budget RM 120,000                  │
│ Trade-in Perodua Myvi 2018         │
│ Close date 15 Nov 2026             │
└────────────────────────────────────┘
```

## 6. Voice update (follow-up)

```
┌────────────────────────────────────┐
│ ←  Update · Tan Ah Kow             │
│                                    │
│            ┌────────┐              │
│            │   🎤   │              │
│            └────────┘              │
│ "Called Mr Tan, he's ok with the   │
│  price, booking this weekend."     │
│                                    │
│        [ Process with AI → ]       │
│ ────────────────────────────────── │
│ AI suggestion:                     │
│ Summary  ✨ [ OK with price,     ] │
│             [ booking this wkend ] │
│ Next     ✨ [ Collect booking    ] │
│ Date     ✨ [ Sat 31 Oct       📅] │
│ Stage    ✨ Test Drive → Booking   │
│                                    │
│ ┌────────────────────────────────┐ │
│ │       ✓ Add to history         │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

## 7. WhatsApp draft (bottom sheet)

```
┌────────────────────────────────────┐
│ ─────                              │
│ Message to Tan Ah Kow   [EN | BM]  │
│ ┌────────────────────────────────┐ │
│ │ Hi Mr Tan, thanks for visiting │ │
│ │ us today! Just a reminder about│ │
│ │ your Proton X50 test drive on  │ │
│ │ Tuesday 27 Oct. Let me know a  │ │
│ │ time that suits you. – Kelvin  │ │
│ └────────────────────────────────┘ │
│   (editable)       [↻ Regenerate]  │
│                                    │
│ ┌────────────────────────────────┐ │
│ │     💬 Open in WhatsApp        │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```
- Opens `https://wa.me/60123456789?text=…`; logs "WhatsApp sent" to history.

## 8. Leads list (nice-to-have)

```
┌────────────────────────────────────┐
│ ←  Customers                       │
│ [⌕ Search name or phone        ]   │
│ (All)(Follow Up)(Test Drive)(…)    │  ← stage filter chips
│ ┌────────────────────────────────┐ │
│ │ Tan Ah Kow    Proton X50       │ │
│ │ Test Drive · next Tue 27 Oct   │ │
│ ├────────────────────────────────┤ │
│ │ Aisyah        Proton Saga      │ │
│ │ Quotation · next today         │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

## Settings (gear icon)

```
┌────────────────────────────────────┐
│ ←  Settings                        │
│ Salesperson name   [ Kelvin      ] │
│ Default outlet     [ Select…    ▼] │
│ Demo mode (offline AI)      [ ON ] │
│ WhatsApp language  (•)EN  ( )BM    │
└────────────────────────────────────┘
```
