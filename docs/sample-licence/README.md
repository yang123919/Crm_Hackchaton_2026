# Sample driving licences for testing the scanner

Both cards are drawn with **made-up data** (names, IC numbers and addresses are invented). They are not real
licences and exist only so "Scan licence" and the CDL-only booking rule can be tried without a real person's card.
Never add a photo of a real licence to this repository.

| File | Who | Type | Expected when booking |
|---|---|---|---|
| `sample_licence_CDL_FAKE.png` | Tan Ah Kow (seed customer) | CDL, class D, valid to 11 Mar 2029 | Allowed |
| `sample_licence_PDL_FAKE.png` | Aisyah binti Rahman (seed customer) | PDL (red P), class D, valid to 4 May 2027 | Refused: CDL only; the type is locked, so it can't be switched to CDL |
| `sample_licence_UNCLEAR_FAKE.png` | Lim Wei Jie (new customer) | No "P" and exactly 2 years: the photo can't tell | The salesperson picks the type; the booking log notes it |

Push them to the phone's gallery, then use Capture › Scan licence › From gallery, or a customer's
**Book test drive / booking** › Scan licence:

    adb push docs/sample-licence/sample_licence_CDL_FAKE.png /sdcard/Pictures/
    adb push docs/sample-licence/sample_licence_PDL_FAKE.png /sdcard/Pictures/
