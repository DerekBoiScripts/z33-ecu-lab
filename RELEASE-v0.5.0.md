# Z33 ECU Lab v0.5.0 — DTC presentation and freeze frame

- Renders each decoded generic DTC as an individual row with a common offline SAE description; tap copies and long-press shares only that code and description.
- Adds a separately triggered generic Mode 02 frame-00 scan with exact allowlisted support, DTC, load, coolant, RPM, speed, timing, intake, MAF, and throttle requests.
- Sends Mode 02 data requests only when the fresh frame-00 bitmap advertises them and rejects ambiguous frame numbers or malformed payloads.
- Uses serialized `LMX1` timing with 9.5-second Mode 02 timeouts and restores `LMX0` after every connected diagnostic path.
- Adaptively retries `0100`, `0120`, or `0140` support bitmaps under `LMX1` when a normal scan gets `NO DATA`/incomplete results, then restores `LMX0` before continuing. This addresses the observed first-scan `0100 NO DATA` followed by a successful later `LMX1` read.
- Descriptions are a deliberately limited common SAE subset; unknown and manufacturer-specific codes are labeled `Description not bundled`.
- No Nissan-enhanced, Mode 04 clearing, actuator, security, memory, kernel, flash, VIN-write, or tuning commands were added.
