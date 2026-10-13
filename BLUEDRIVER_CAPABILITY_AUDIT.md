# BlueDriver capability audit (v0.1.6)

## Sources and method
This audit is limited to the public protocol documentation/source at
https://github.com/sjalkote/bluedriver-reverse-engineering and this project's own recorded hardware test evidence. It does not infer undocumented behavior from generic ELM327 adapters.

The public project documents the BLE service, CMD/DATA characteristics, XOR encoding, DATA credits/ACKs, and adapter `LM*` commands used here. This app's hardware evidence demonstrates scanner-managed generic commands: `LMDP` reported protocol 3 (ISO 9141-2/K-Line), Mode 01 RPM, and Mode 09 identification data. It also demonstrates that adapter timing modes `LMX0` and `LMX1` affect record completeness.

## Conservative finding
- **Demonstrated:** scanner-managed generic OBD requests transported through the documented BLE protocol, plus the proven initialization/timing commands already in this app.
- **Not demonstrated by the cited public source or project evidence:** raw K-Line pass-through, arbitrary K-Line header/address control, ISO/KWP start communication control, manufacturer-specific Nissan services, security access, memory reads, RAM-kernel upload, ROM reads, or writes.
- **Conclusion:** raw K-Line pass-through/header control is **unknown**, not supported by this application. A BLE command channel must not be treated as proof that arbitrary vehicle-bus frames are accepted or safe.

Accordingly, v0.1.6 sends only its explicit generic read-only allowlist. It does not probe unknown `LM`, `AT`, Nissan, KWP, security, memory, kernel, programming, reset, or write commands. Any future capability work requires a separately documented, recorded-response, hardware-specific and recoverable procedure.
