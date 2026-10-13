# Z33 ECU Lab v0.1.2 — read-only

Validated against user-captured 2005 350Z/BlueDriver responses:

- `010C`: `0D 48` = 850 RPM.
- `0904`: `01 31 43 46 30` = a leading 01 count/record byte followed by ASCII `1CF0`. Display as **reported**, not verified as an exact full Nissan ROM ID.
- Adds read-only `0903` CALID message count query; response might be unsupported on this ECU.
- Retains full nine-step automatic scanner initialization and `01 length 00` BLE data-fragment ACKs.
- Keeps ECU writing, reflashing, tuning, and arbitrary diagnostic commands disabled.

**Unverified**: APK compilation and physical BlueDriver behavior of this release. Earlier v0.1.1 reliably retrieved 850 RPM and the raw calibration response in a user road-side idle test.
