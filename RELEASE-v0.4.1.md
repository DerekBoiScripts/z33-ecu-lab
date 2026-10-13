# Z33 ECU Lab v0.4.1 — exhaustive DTC timing

- Runs the one-time generic DTC sequence under exhaustive `LMX1` timing instead of live-data `LMX0` timing.
- Extends exact allowlisted DTC request timeouts from 5.0 to 9.5 seconds after a real Mode 07 response delivered three fragments but no prompt before the old deadline.
- Always attempts to restore `LMX0` after success, rejection, malformed data, timeout, or lifecycle cancellation while connected.
- A disconnect resets the adapter through the proven initialization sequence on reconnect.
- No additional ECU services, Nissan-enhanced commands, clearing, or write behavior was added.
