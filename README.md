# Z33 ECU Lab 0.7.0 — Android BlueDriver diagnostics
(Yes i used claude to quickly change repo's)

An independent, experimental **read-only** BlueDriver BLE app for the user's 2005 Nissan 350Z 5AT. The app is not a tune editor, ROM dumper, or ECU flasher.

**Confirmed with v0.1.4:** LMDP vehicle protocol 3 (ISO 9141-2 K-Line), eight indexed CALID records reporting `1CF010` and `RX1115`, two CVNs, and readable RPM.

**New in v0.1.6:** Per-command round-trip/fragment/decoded-byte metadata; explicit complete/NO DATA/timeout-partial/malformed labels; advertised safe Mode 01 live data only; and sanitized text/JSON exports. LMX1 is used only to retry incomplete counted Mode 09 CALID/CVN/VIN records and LMX0 restoration is recorded.

**Fixed in v0.1.7:** If CALID/CVN count PIDs return `NO DATA` under LMX0 while payload records are present, the app retries the counts and payloads under LMX1 before restoring LMX0.

**New in v0.1.8:** Native four-tab dark automotive UI. Dashboard retains connection, protocol, scan, and LMX0 restore state; Live Data presents one advertised-PID snapshot (not continuous polling); identity, privacy checks, sanitized exports, raw responses, and BLE logs are separated.

**Fixed in v0.1.9:** Native system-bar and display-cutout insets keep the shared header and bottom navigation clear of camera cutouts, status overlays, and gesture/navigation bars.

**New in v0.2.0:** Live Data has an opt-in, Park/read-only polling session after a snapshot Scan has loaded Mode 01 support bitmaps. It verifies `LMX0` before polling only advertised existing allowlisted PIDs, sends one request at a time, uses overdue deadlines rather than a fixed loop, shows sample age/staleness and compact transport/session statistics, and never uses `LMX1` for live polling.

**New in v0.3.0:** advertised-only generic live sensors, bounded native traces, explicit bounded CSV recording/export, and read-only Mode 03 DTC retrieval. Raw K-Line limitations remain; dedicated validated K-Line hardware evaluation is future research only, not ROM-backup capability.

**Fixed in v0.3.1:** Disabling the last selected sensor pauses live polling instead of ending the session. Re-enabling any advertised sensor wakes polling immediately, with clearer selected-versus-polling labels.

**Fixed in v0.3.2:** A completed Mode 03 `NO DATA` response is displayed as no stored DTCs rather than an unavailable request.

**New in v0.4.0:** One serialized generic-emissions scan reports stored Mode 03, pending Mode 07, and permanent Mode 0A DTC categories separately. Mode 04 clearing remains blocked. Nissan-enhanced module diagnostics remain excluded because BlueDriver raw K-Line addressing and exact read-only CONSULT requests are not yet verified.

**Fixed in v0.4.1:** Generic DTC scans use exhaustive `LMX1` timing and a 9.5-second per-mode timeout, then always attempt to restore `LMX0`. This addresses a real Mode 07 response that delivered fragments but missed the prompt under the previous 5-second fast-mode limit.

**New in v0.4.2:** DTC scans freshly gate exact read-only Mode 01 PID `0101` on the `0100` bitmap, then show ECU-commanded MIL and reported stored-DTC count separately. `NO DATA` is conservative and a MIL/count-versus-Mode-03 mismatch is explicitly flagged.

**New in v0.5.0:** Structured generic DTC rows provide common offline SAE descriptions, tap-to-copy, and long-press sharing. A separate advertised-only Mode 02/frame-00 freeze-frame scan reads a strict diagnostic PID subset. Normal scans adaptively retry missing Mode 01 support bitmaps under `LMX1` and restore `LMX0`, avoiding the observed need to scan twice.

**New in v0.6.0:** Live traces use a bounded 30-second elapsed-time axis (not evenly spaced samples). Enabled allowlisted sensors and their tiers persist locally but advertised-PID gating remains authoritative. Each valid live value shows its measured refresh rate. Explicit recording exports at most 10,000 normalized `elapsed_ms,timestamp_utc,pid,sensor,value` valid-sample rows; elapsed time begins when recording starts and UTC is derived from that start instant. No raw BLE, VIN, or address data is recorded.

**New in v0.7.0:** Compact live graphs label the sensor/value trace and visibly mark `-30s … now`; tap still clears the bounded timestamp-scaled trace. Offline descriptions now cover substantially more common generic powertrain, chassis, body, and network DTCs while manufacturer-specific codes stay unbundled. The local history holds at most 20 sanitized export metadata records and 20 live-session summaries (UTC/start duration, valid sample count, selected sensor count), never raw values, VIN, BLE data, or export bodies. System-picker destination URIs are intentionally not retained, so prior exports are shown as unavailable for re-share rather than weakening storage/privacy controls.

See [RELEASE-v0.7.0.md](RELEASE-v0.7.0.md), [RELEASE-v0.6.0.md](RELEASE-v0.6.0.md), [RELEASE-v0.5.0.md](RELEASE-v0.5.0.md), [RELEASE-v0.4.2.md](RELEASE-v0.4.2.md), [RELEASE-v0.4.1.md](RELEASE-v0.4.1.md), [RELEASE-v0.4.0.md](RELEASE-v0.4.0.md), [RELEASE-v0.3.2.md](RELEASE-v0.3.2.md), [RELEASE-v0.3.1.md](RELEASE-v0.3.1.md), [RELEASE-v0.3.0.md](RELEASE-v0.3.0.md), [RELEASE-v0.2.0.md](RELEASE-v0.2.0.md), [RELEASE-v0.1.9.md](RELEASE-v0.1.9.md), [RELEASE-v0.1.8.md](RELEASE-v0.1.8.md), [RELEASE-v0.1.7.md](RELEASE-v0.1.7.md), [RELEASE-v0.1.6.md](RELEASE-v0.1.6.md), and [BLUEDRIVER_CAPABILITY_AUDIT.md](BLUEDRIVER_CAPABILITY_AUDIT.md) for limits, sources, and safety notes.

Source layout:
- `app/src/main/java/com/z33eculab/BlueDriverBle.java`: BLE protocol; automated handshake, notifications and fragment acknowledgments.
- `app/src/main/java/com/z33eculab/BlueProtocol.java`: encode/decode, command allowlist.
- `app/src/main/java/com/z33eculab/ResponseParser.java`: safe Mode 01/02/03/07/09/0A parsing, bitmap gating, CALID/CVN/VIN validation and VIN last-byte inspection.
- `app/src/main/java/com/z33eculab/MainActivity.java`: native Java Android user interface.
- `tests/run_protocol_tests.sh`: offline JDK test harness.

The BlueDriver low-level protocol was independently documented by https://github.com/sjalkote/bluedriver-reverse-engineering ; this is a separate implementation, not endorsed by BlueDriver.
