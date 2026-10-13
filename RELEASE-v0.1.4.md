# Z33 ECU Lab 0.1.4 — ECU identification report

Read-only Android build for the user's 2005 Nissan 350Z 5AT and BlueDriver BLE adapter. No ECU flashing, ROM reads, arbitrary commands, or tuning writes. The previous 0.1.3 hardware-tested BLE initialization and LMX1/LMX0 flow are preserved.

## What's added

- Mode 09 PID 05: CVN-message count in fast and exhaustive timing. Parse indexed four-byte CVN records from 0906, display exact hex values and missing/conflicting records without guessing.
- Mode 09 PID 01: VIN-message count in fast and exhaustive timing. Mode 09 PID 02 VIN retrieval under LMX1, with five indexed four-byte frames and strict 17-character VIN validation. VIN might not be available on this vehicle even though calibration info is supported.
- Protocol label from BlueDriver's actual LMDP response: protocol 3 -> ISO 9141-2 / K-Line.
- Local CALID-to-definition match: confirmed full 1CF010 maps to published CF010.xml metadata (2005 USDM 350Z, SH7058 listed, transmission unknown). This is not confirmation of actual ROM flash bytes or that the ECU is stock.
- Save a timestamped TXT report through Android's document picker or share a plain-text report. Reports mask VIN to last four characters and intentionally omit raw BLE traffic / VIN hex; raw diagnostic data remains inspectable within the app.
- Read-only allowlist adds 0901, 0902 and 0905; the existing controls remain.

## Tested captured data (previous 0.1.3 user session)

- LMDP: 3 => ISO 9141-2 over K-Line.
- 0903: 08, 0904 under LMX1: 8/8 => 1CF010 and RX1115.
- 0906 under LMX1: 01 0E 76 95 09 / 02 00 00 61 69 => 0E769509 and 00006169.
- 0901, 0902, 0905 have NOT been tested on the live vehicle in this version.

## Phone-only GitHub build (existing private repository with trailing period)

1. Upload `Z33-ECU-Lab-source-v0.1.4.zip` to the root of your private repository `DerekBoiScripts/z33-ecu-lab.`. Keep previous ZIPs.
2. Edit `.github/workflows/build.yml` and replace its CONTENTS with the separate `Z33-ECU-Lab-build-v0.1.4.yml` file.
3. Commit. From GitHub Actions, open the run named "Build Z33 ECU Lab v0.1.4 APK" and wait for success.
4. From the run Summary -> Artifacts download `Z33-ECU-Lab-v0.1.4-APK`. Extract its `app-debug.apk`, then install. If signature mismatches the current app, export your previous logs before removing the earlier test APK.
5. With ignition ON, engine OFF and the official BlueDriver app closed, connect and tap `Read ECU ID`. Wait for the LMX1 tests and LMX0 restore before disconnecting.
6. Inspect VIN/CVN COUNT, coverage, protocol, local ROM match, and export a TXT report. Share the redacted report as needed. If VIN is missing, it is not necessarily an ECU fault.

## Build/test status

- Local Java protocol and parsing tests pass (including actual Z33 LMX1 data and synthetic indexed VIN data).
- Android build and new live Bluetooth behavior are UNVERIFIED before the GitHub Actions run. If the GitHub run fails, capture the exact compiler error; don't change the Android SDK setup just for a generic Gradle failure.

## Technical limitations

- VIN/CVN 0901/0902/0905 messages may return NO DATA even when 0904/0906 work.
- The parser refuses missing records or conflicting frames, and cannot independently prove ECU software integrity. A matching CALID is metadata, not a read-out flash backup.
- Report omits raw trace deliberately because the raw VIN can be reconstructed from 0902 bytes; a masked VIN label alone would not prevent that.
- App is intended for parked diagnostics only. Avoid unnecessary idling or running in enclosed spaces; ignition ON, engine OFF suffices for identification.
