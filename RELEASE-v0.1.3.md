# Z33 ECU Lab 0.1.3 — CALID record analysis and timing comparison

**Purpose:** Android + bonded BlueDriver BLE scanner, diagnostics only. No calibration modifications or ECU firmware writes. This build is not yet tested on a live vehicle.

## v0.1.3 changes

- Runs previous validated 9-step BlueDriver initialization unchanged, including credit-based fragment ACKs and first-LMI retry.
- Reads RPM (010C), Mode 09 bitmap (0900), legacy CALID message count (0903), calibration ID (0904), CVN (0906), and battery voltage PID (0142).
- Captures indexed, headerless or `49 04`-headed 0904 lines as four-byte ASCII chunks. Reconstructs a 16-character calibration string ONLY if all four required consecutive records actually arrive. Tracks expected count, missing indexes, and disagreeing duplicates.
- After normal-timing read, sends documented BlueDriver **adapter-only** `LMX1`, requests 0904 and 0906 again, then sends `LMX0` to restore the interface timing. Aborts the comparison if `LMX1` doesn't return `OK`.
- Records whether 0900 claims 0906 support even if the CVN request returns `NO DATA`.
- Renders separate CALID capture counts and raw responses for both timing modes.

## Interpretation of the initial 2005 Nissan 350Z capture

`0903: 08` indicates the ECU reports eight CALID messages. `0904: 01 31 43 46 30` is **one** indexed record (sequence 1, ASCII `1CF0`). Until sequence 2–8 are observed, those characters are unknown and no complete ROM ID can be asserted. The new app will never synthesize missing text.

## Limitations and safety

- The scanner or ECU may still return only the first 0904 record in both timing modes. This update helps diagnose that and reconstruct records *if delivered*; it does not promise the remaining frames can be read with this adapter.
- `LMX1` and `LMX0` are adapter-side timing configurations, not ECU calibration writes. They may not affect the response completeness.
- The app does not include arbitrary command entry, firmware reading, bootloader entry, unlocking, ECU reflashing or tuning.
- A successful GitHub build does not prove communication on your specific hardware; test carefully while parked outdoors. Engine may be OFF with ignition ON for ECU identity tests. If idling, keep the car in Park with parking brake engaged and avoid enclosed spaces.

## Updating with only your phone

1. Upload `Z33-ECU-Lab-source-v0.1.3.zip` to the root of your existing private GitHub repository. Keep prior ZIPs as backups.
2. In GitHub edit `.github/workflows/build.yml` and replace its contents with `Z33-ECU-Lab-build-v0.1.3.yml`.
3. Commit the change. Go to Actions → the new successful run → Summary → Artifacts, and download `Z33-ECU-Lab-v0.1.3-APK`.
4. Extract and install `app-debug.apk`. If Android reports an app-signature mismatch, export old logs first, then uninstall the prior debug APK before installing v0.1.3.
5. Connect, wait for READY, run Read ECU ID once, and share the raw normal and exhaustive 0904/CVN results.

## Validation

29 local Java protocol/parser checks pass. Android APK compilation and real BlueDriver timings for 0.1.3 are unverified at time of packaging.
