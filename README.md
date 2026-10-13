# Z33 ECU Lab 0.2.0 — Android BlueDriver diagnostics
(Yes i used claude to quickly change repo's)

An independent, experimental **read-only** BlueDriver BLE app for the user's 2005 Nissan 350Z 5AT. The app is not a tune editor, ROM dumper, or ECU flasher.

**Confirmed with v0.1.4:** LMDP vehicle protocol 3 (ISO 9141-2 K-Line), eight indexed CALID records reporting `1CF010` and `RX1115`, two CVNs, and readable RPM.

**New in v0.1.6:** Per-command round-trip/fragment/decoded-byte metadata; explicit complete/NO DATA/timeout-partial/malformed labels; advertised safe Mode 01 live data only; and sanitized text/JSON exports. LMX1 is used only to retry incomplete counted Mode 09 CALID/CVN/VIN records and LMX0 restoration is recorded.

**Fixed in v0.1.7:** If CALID/CVN count PIDs return `NO DATA` under LMX0 while payload records are present, the app retries the counts and payloads under LMX1 before restoring LMX0.

**New in v0.1.8:** Native four-tab dark automotive UI. Dashboard retains connection, protocol, scan, and LMX0 restore state; Live Data presents one advertised-PID snapshot (not continuous polling); identity, privacy checks, sanitized exports, raw responses, and BLE logs are separated.

**Fixed in v0.1.9:** Native system-bar and display-cutout insets keep the shared header and bottom navigation clear of camera cutouts, status overlays, and gesture/navigation bars.

**New in v0.2.0:** Live Data has an opt-in, Park/read-only polling session after a snapshot Scan has loaded Mode 01 support bitmaps. It verifies `LMX0` before polling only advertised existing allowlisted PIDs, sends one request at a time, uses overdue deadlines rather than a fixed loop, shows sample age/staleness and compact transport/session statistics, and never uses `LMX1` for live polling.

See [RELEASE-v0.2.0.md](RELEASE-v0.2.0.md), [RELEASE-v0.1.9.md](RELEASE-v0.1.9.md), [RELEASE-v0.1.8.md](RELEASE-v0.1.8.md), [RELEASE-v0.1.7.md](RELEASE-v0.1.7.md), [RELEASE-v0.1.6.md](RELEASE-v0.1.6.md), and [BLUEDRIVER_CAPABILITY_AUDIT.md](BLUEDRIVER_CAPABILITY_AUDIT.md) for limits, sources, and safety notes.

Source layout:
- `app/src/main/java/com/z33eculab/BlueDriverBle.java`: BLE protocol; automated handshake, notifications and fragment acknowledgments.
- `app/src/main/java/com/z33eculab/BlueProtocol.java`: encode/decode, command allowlist.
- `app/src/main/java/com/z33eculab/ResponseParser.java`: safe Mode 01/09 parsing, bitmap gating, CALID/CVN/VIN validation and VIN last-byte inspection.
- `app/src/main/java/com/z33eculab/MainActivity.java`: native Java Android user interface.
- `tests/run_protocol_tests.sh`: offline JDK test harness.

The BlueDriver low-level protocol was independently documented by https://github.com/sjalkote/bluedriver-reverse-engineering ; this is a separate implementation, not endorsed by BlueDriver.
