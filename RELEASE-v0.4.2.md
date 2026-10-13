# Z33 ECU Lab v0.4.2 — monitor status cross-check

- Freshly reads `0100` under `LMX1` and sends exact read-only `0101` only when PID 01 is advertised.
- Displays ECU-commanded MIL and reported stored generic-DTC count without exporting raw payloads.
- Labels `NO DATA` conservatively and flags a MIL-on/nonzero-count versus Mode 03 `NO DATA` inconsistency; Mode 0A support remains uncertain.
- Incomplete `0101` transport stops the sequence and restores `LMX0`; no Nissan-enhanced, clearing, or write commands were added.
