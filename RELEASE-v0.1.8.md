# Z33 ECU Lab v0.1.8 — four-tab read-only UI

- Native Java Dashboard, Live Data, ECU Identity, and Research/Logs tabs with a persistent bottom tab bar.
- Dashboard keeps connection status, protocol/adapter summary, scan completion, and LMX0 restore state visible.
- Live Data is a single read-only scan snapshot: parsed advertised PID values are shown, while bitmap-skipped PIDs explicitly show `Not advertised`. No continuous polling was added.
- Identity, VIN final-byte comparison, sanitized exports, raw responses, and BLE logs are separated for safer phone use.
- No BLE transport, parser, adaptive LMX0/LMX1, request sequencing, or command allowlist behavior changed. No vehicle commands were added.
