# Z33 ECU Lab v0.1.7 — adaptive count retry fix

- When fast LMX0 reads return CALID/CVN payload records but `0903`/`0905` counts are unavailable, retry those count PIDs under LMX1.
- Re-evaluate completeness after the exhaustive count and retry `0904`/`0906` as needed.
- If the exhaustive count remains unavailable, still retry a payload whose fast response proved multi-record completeness unknown.
- Preserve serialized requests, transport metadata, and verified LMX0 restoration.
- No new vehicle commands: the existing explicit generic read-only allowlist is unchanged.
