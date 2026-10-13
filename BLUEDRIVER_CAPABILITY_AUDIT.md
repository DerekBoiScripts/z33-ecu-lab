# BlueDriver capability audit (updated for v0.4.0)

## Sources and method
This audit is limited to the public protocol documentation/source at
https://github.com/sjalkote/bluedriver-reverse-engineering and this project's own recorded hardware test evidence. It does not infer undocumented behavior from generic ELM327 adapters.

The public project documents the BLE service, CMD/DATA characteristics, XOR encoding, DATA credits/ACKs, and adapter `LM*` commands used here. This app's hardware evidence demonstrates scanner-managed generic commands: `LMDP` reported protocol 3 (ISO 9141-2/K-Line), Mode 01 RPM, and Mode 09 identification data. It also demonstrates that adapter timing modes `LMX0` and `LMX1` affect record completeness.

## Conservative finding
- **Demonstrated:** scanner-managed generic OBD requests transported through the documented BLE protocol, plus the proven initialization/timing commands already in this app.
- **Not demonstrated by the cited public source or project evidence:** raw K-Line pass-through, arbitrary K-Line header/address control, ISO/KWP start communication control, manufacturer-specific Nissan services, security access, memory reads, RAM-kernel upload, ROM reads, or writes.
- **Conclusion:** raw K-Line pass-through/header control is **unknown**, not supported by this application. A BLE command channel must not be treated as proof that arbitrary vehicle-bus frames are accepted or safe.

Accordingly, the app sends only its explicit generic read-only allowlist. It does not probe unknown `LM`, `AT`, Nissan, KWP, security, memory, kernel, programming, reset, or write commands. Any future capability work requires a separately documented, recorded-response, hardware-specific and recoverable procedure.

## v0.4.0 DTC boundary and Nissan research
- Generic SAE emissions requests `03` (stored), `07` (pending), and `0A` (permanent) can use the already demonstrated scanner-managed command path. They are exact allowlist entries; Mode `04` clearing remains blocked. A 2005 ECU may not support Mode `0A`, and `NO DATA` is reported without inferring absence of enhanced faults.
- The public BlueDriver reverse-engineering source documents headers-off generic replies, the proprietary `LM` initialization, transport credits/ACKs, and the fact that a second command aborts an active request. It does **not** document arbitrary ELM header commands or Nissan module addressing.
- `fenugrec/nisprog` `USING.txt` demonstrates that Nissan K-Line work with dedicated hardware explicitly configures an interface, ISO 14230 layer, fast initialization, tester ID `0xFC`, destination `0x10`, and physical addressing before connecting. Its memory/kernel/flash functions are outside this application's diagnostic scope and are not copied or invoked.
- Those sources do not provide sufficient evidence that this BlueDriver firmware accepts the required raw framing or that one generic request safely covers ECM, TCM, ABS, SRS, and BCM. Therefore no CONSULT/manufacturer-specific command is present in the app.

Sources:
- https://github.com/sjalkote/bluedriver-reverse-engineering
- https://github.com/fenugrec/nisprog/blob/master/USING.txt

Safe validation path: obtain passive captures of the official app reading a known code, identify module addresses and service semantics offline, verify them against public protocol/tooling, create recorded-response parser fixtures, and only then consider a separately reviewed read-only capability build. Do not discover services by probing the live vehicle.
