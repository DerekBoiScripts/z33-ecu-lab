# Z33 ECU Lab v0.1.6 — measured read-only monitoring

## Changes
- `versionCode` 7 / `versionName` 0.1.6.
- Records immutable command transport facts: round-trip milliseconds, BLE DATA fragment count, decoded byte count, and `complete`, `NO DATA`, `timeout/partial`, or `malformed` classification.
- The safe request allowlist is closed: Mode 01 bitmaps `0100`, `0120`, `0140`; advertised coolant, trims, RPM, speed, timing, intake temperature, MAF and throttle; and existing Mode 09 identification PIDs. No arbitrary text, manufacturer-specific, AT, LM (other than proven timing setup), KWP, security, memory, or write requests are exposed.
- `0120` is read only when `0100` advertises PID 20; `0140` only when `0120` advertises PID 40. Live PIDs are read only when the applicable bitmap advertises them.
- Normal LMX0 captures are used first. LMX1 is entered only when a known counted CALID/CVN/VIN reply has a known expected count but incomplete/conflicting records. Only incomplete reply PIDs are retried. LMX0 restoration is attempted after every entered exhaustive callback path and its result is exported.
- Sanitized text and JSON saves include protocol, adapter firmware, transport summary, CALID/CVN coverage, and LMX0 restoration status. They omit VIN/raw VIN records, BLE trace, device address, and user-entered VIN character.

## Safety and limits
This is a read-only generic OBD-II identifier/monitor, not a ROM dumper, Nissan diagnostic tool, or flasher. A failed/disconnected exhaustive session can prevent a restoration command; export labels that condition explicitly. No full VIN or BLE trace is exported by default.

Run `tests/run_protocol_tests.sh` for the pure-Java parser/allowlist fixtures. Android build status depends on a locally installed Android SDK.
