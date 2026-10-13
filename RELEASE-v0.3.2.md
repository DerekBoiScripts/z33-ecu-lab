# Z33 ECU Lab v0.3.2 — Mode 03 no-codes fix

- Treats a completed Mode 03 `NO DATA` response as `No stored DTCs reported`.
- Transport errors, malformed responses, timeouts, and ECU rejections remain distinct.
- Mode 04 clearing remains blocked; no transport or ECU-write behavior changed.
