# Z33 ECU Lab v0.1.5 — VIN byte investigation and 2-step feasibility roadmap

## Confirmed from user's v0.1.4 live 2005 Nissan 350Z automatic scan
- Diagnostic vehicle protocol: ISO 9141-2 over K-Line (BlueDriver LMDP protocol 3).
- CALID records: **8/8**, two reconstructed IDs `1CF010` and `RX1115`.
- CVN records: **2/2**, `0E769509` and `00006169`.
- Corresponding CF010 ROM definition lists an SH7058 for a 2005 USDM 350Z, but the ROM binary and chip inside the particular ECU have not been verified.
- UpRev's published ROM support table explicitly includes **2005 350Z VQ35DE AT CF010**: https://www.uprev.com/support/

## New in v0.1.5
- **VIN terminal-field inspection** using the same read-only `0902` data already captured; no new vehicle commands.
- Displays five-record coverage, number of valid VIN bytes (e.g., 16/17), final byte as hexadecimal, and notes `0x0D` as a carriage-return/control value that might also be a transport framing artifact.
- Optional one-character entry to compare the observed final VIN byte against a known vehicle-label character. The value is kept in RAM during the app session, **never sent to the vehicle**; it does not alter any ECU contents.
- Redacted report contains last-byte diagnostic status but not the raw VIN, BLE trace, or stored full VIN.
- New in-app **2-step project status** explains that ROM access is *not* yet available through the BlueDriver adapter, even though generic OBD diagnostics work.
- v0.1.4 CALID/CVN reconstruction, exhaustive LMX1 timing, automatic ACKs, and the BlueDriver read-only allowlist remain unchanged.

## VIN nuance
A complete count of five indexed VIN records is **not proof of 17 valid VIN characters**. A terminal `0x0D` may be an improperly reported payload field, or could arise from the diagnostic interface/framing. This program does not substitute the user-provided last character into the ECU response and cannot establish which underlying explanation is correct without further low-level validation. Keep the full VIN out of screenshots and shared reports.

## Original goal: aggressive stationary two-step sound on 2005 350Z 5AT
The next engineering deliverable is a **validated, non-destructive ROM backup plan**, not a flash button. While BlueDriver can issue these Mode 01/09 read-only diagnostic requests, a raw ROM read requires verified access to manufacturer-specific Nissan K-Line services. It is NOT established that the existing BlueDriver BLE wrapper allows those services. `nisprog` and `npkern` document such access for some SH705x Nissan ECUs, including the SH7058 family; using a RAM-resident flash kernel has risks even during ROM reading. Never try unsolicited programming/kernels over BlueDriver.

### Candidate routes (investigate, do not assume interchangeability)
1. **DIY read-only ROM feasibility:** verify actual ECU processor and ROM size, Nissan K-line security/diagnostic capabilities, whether a supported USB K-Line device plus Android USB OTG can perform reliable reads, and that a recovery option exists. Only then attempt a ROM backup. Do not attempt writes before checksums, map definitions, power stability, and recovery validation.
2. **Professional route:** UpRev officially lists 2005 automatic CF010, but its **$575 remote kit expressly excludes flames, burbles, pops** and needs Windows. The **$675 self-tuning kit** also needs Windows; neither listing guarantees 2-step/ARC support on this specific ROM/5AT. Ask UpRev or a qualified tuner about compatibility and legality before any purchase.
3. **Mechanical readiness:** resolve any current misfires, oil intrusion, or ignition issues first. Aggressive sustained spark/fuel cut or heavy exhaust pops risk catalysts/exhaust, and brake-loading an automatic transmission can overheat/damage the drivetrain. Tune validation should be professional and controlled, and local emissions/noise laws matter.

Sources:
- https://www.uprev.com/support/
- https://www.uprev.com/shop/uprev-standard-kit/
- https://www.uprev.com/shop/uprev-tuner-kit/
- https://github.com/fenugrec/nisprog
- https://github.com/fenugrec/npkern
- https://github.com/Pytrex/NissanDefinitions/blob/master/Nissan%20Definitions/350Z/CF010.xml

## Cloud build from Android phone
1. Upload `Z33-ECU-Lab-source-v0.1.5.zip` to the root of your existing private `z33-ecu-lab.` repository. Keep all prior ZIPs for recovery.
2. Replace the contents of `.github/workflows/build.yml` with the companion `Z33-ECU-Lab-build-v0.1.5.yml` (copy text, don't merely upload it to the root).
3. Commit. From Actions run **Build Z33 ECU Lab v0.1.5 APK** if not triggered automatically.
4. Download artifact `Z33-ECU-Lab-v0.1.5-APK` from the build **Summary** page; extract and install the APK on Android.
5. With the ignition ON or at safe idle outdoors in Park + brake engaged, tap Connect, then Read ECU ID. Scroll to VIN final-byte inspection. Enter only the expected final character (e.g. 5) and tap Compare.

Note: this source passes the bundled pure-Java tests, but Android APK compilation and on-device runtime behavior are not yet verified. GitHub debug build keys might differ between runs; preserve/export any logs before uninstalling a prior version.
