# Z33 ECU Lab v0.6.0 — live telemetry clarity

- Draws bounded native live traces against a rolling 30-second `elapsedRealtime` axis, retaining at most 120 valid samples per graph.
- Persists each allowlisted live sensor's enabled state and fast/default/slow tier in local Activity preferences. Restored preferences are still gated by the ECU's current advertised-PID bitmap.
- Shows a bounded-EWMA measured refresh rate from valid samples only; rate state resets at live-session start.
- CSV recording remains explicit and selected-PID-only. It now writes one valid sample per long-form row: `elapsed_ms,timestamp_utc,pid,sensor,value`, with a 10,000-data-row limit. Elapsed time starts at recording start; UTC is derived from that same instant.
- No BLE transport, DTC/freeze-frame, identity, VIN, raw-response, ECU-command, dependency, or safety behavior changed. No Nissan-enhanced, write, memory, security, reset, or tuning commands were added.
