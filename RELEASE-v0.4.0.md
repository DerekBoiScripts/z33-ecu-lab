# Z33 ECU Lab v0.4.0 — generic DTC categories

- Adds one serialized, `LMX0`-verified read-only scan for stored Mode 03, pending Mode 07, and permanent Mode 0A emissions DTCs.
- Displays each category separately and distinguishes no response data, rejection, malformed data, and timeout/partial transport outcomes.
- A 2005 ECU may not implement Mode 0A; `NO DATA` does not establish protocol support or rule out Nissan-enhanced/module faults.
- Adds sanitized category summaries to text and JSON reports without raw DTC payloads.
- Strictly allows only exact `03`, `07`, and `0A`; Mode 04 clearing and manufacturer-specific commands remain blocked.
- Nissan-enhanced ECM/TCM/ABS/SRS/BCM access remains research-only because exact read-only CONSULT services, module addressing, and BlueDriver raw-frame control are not verified.
