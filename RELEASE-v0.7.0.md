# Z33 ECU Lab v0.7.0 — local history and live readability

- Improved phone-scale live-data hierarchy and graph labels. Bounded, timestamp-scaled graphs now show a `-30s … now` cue and retain tap-to-clear behavior.
- Added more offline SAE-generic P/C/B/U descriptions for common ignition, fuel/air, oxygen, catalyst, EVAP, cooling, speed, transmission, chassis, body, and network codes. Manufacturer-specific and unknown codes still say `Description not bundled`.
- Added bounded local history: 20 sanitized export metadata entries and 20 live-session summaries. It stores no report body, VIN, BLE address/trace, URI, or sample values. Prior system-picker exports cannot be safely re-shared because their URI is intentionally not retained; the UI states that status. Clearing requires confirmation.
- No vehicle command, BlueDriver transport, PID allowlist, advertised-PID gate, DTC, freeze-frame, privacy, permission, or export-trigger behavior was broadened.
