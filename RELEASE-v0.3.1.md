# Z33 ECU Lab v0.3.1 — live sensor resume fix

- Disabling the final selected sensor now pauses the live session rather than stopping it.
- Re-enabling an advertised sensor wakes the serialized `LMX0` polling scheduler immediately.
- Sensor labels now distinguish selection from active polling and prompt for Start Live when the session is stopped.
- Priority changes also wake the scheduler so the selected tier takes effect immediately.
- No BLE transport, diagnostic parser, command allowlist, or ECU-write behavior changed.
