# Z33 ECU Lab v0.3.0 — extended read-only monitoring

- Adds advertised-only Mode 01 calculated load, MAP, fuel level, and control-module voltage, with chained 0140 support discovery.
- Live sensor taps enable/disable advertised allowlisted PIDs; long press selects fast/default/slow scheduling. Requests remain serialized and LMX0-only.
- Adds bounded native RPM/MAF/throttle/STFT/LTFT traces and explicit recording (10,000-row cap) with sanitized CSV export.
- Adds read-only standard Mode 03 DTC display. Mode 04 and all clearing, programming, memory, security, kernel, flash, and tuning services remain forbidden.
- Final live session duration/rate freezes at Stop. Recording also stops on polling stop, disconnect, and lifecycle shutdown.
- Generic OBD over BlueDriver is not raw K-Line or ROM backup. Dedicated validated K-Line hardware evaluation is future research only.

Current limits: traces are sample-indexed rather than time-scaled, recording is bounded in memory, sensor choices last only for the current app session, and these additions require on-vehicle validation before release to `main`.
