# Z33 ECU Lab v0.2.0 — read-only live polling

- Adds opt-in Live Data Start/Stop controls, Park/read-only labeling, sample age/stale indicators, and compact samples/sec, rolling RTT, active-PID, and last-result statistics.
- Requires a snapshot Scan to establish Mode 01 support before polling. Only already allowlisted and ECU-advertised generic Mode 01 PIDs are scheduled.
- Verifies `LMX0` returns `OK` before live polling; live polling never sends `LMX1`.
- Uses one `requestPidResult` at a time and a most-overdue deadline schedule: RPM 250 ms; speed/throttle/MAF 500 ms; timing/trims 1000 ms; coolant/intake 2000 ms. Slow transport reduces actual rate without overlapping requests.
- Stops safely before identity scans, disconnect, lifecycle shutdown, and BLE errors/not-ready. Exports contain only a compact live-session summary, not sample history.
- No new ECU commands, allowlist entries, writes, background polling, or automatic engine-related behavior.
