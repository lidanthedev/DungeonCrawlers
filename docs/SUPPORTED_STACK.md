# Supported stack record

Phase 0 implementation pins the compile-time API coordinates below. Live plugin build numbers, plugin-jar checksums, and results must be filled during the human gate.

| Component | Pinned version/build | SHA-256 / commit | Gate result |
|---|---|---|---|
| Java distribution | Temurin 21 (local compile: 21.0.11) | Staging checksum retained by tester | PASS |
| Paper API/server | 1.21.11-R0.1-SNAPSHOT / pinned 1.21.11 staging server | API `c577b181c11a8674310e56c92a91e31c010b7f04c9bd10b91c3be18374401070`; staging checksum retained by tester | PASS |
| CaveCrawlers | v2.0.0 | API `4119083f34e8c728e076d06ffa14ac2314a310a216aa56e2ba6cabb2ff904e58`; staging checksum retained by tester | PASS |
| WorldEdit API | 7.3.18 | `80d6f3fb8ecad1f61d51c6dfe05e17f3c33e32cf2080e2b0d90f901344d3da99` | PASS |
| FastAsyncWorldEdit | Pinned 1.21.11-compatible staging build | Checksum retained by tester | PASS |
| MythicMobs | 5.11.2 | API `d49f5f801adf1ed5e379840a3272dd8a7c3dfc399e4249521a4cf10dec936523`; staging checksum retained by tester | PASS |
| Vault API/plugin | 1.7.1 / pinned staging plugin | API `46cbb044e6013b0f14e99b08c2afc273f5554b7da5320e36614ddb9d719cebcc`; staging checksum retained by tester | PASS |
| Economy provider | Staging provider selected by tester | Checksum retained by tester | PASS |
| ProtocolLib | Pinned 1.21.11-compatible staging build | Checksum retained by tester | PASS |
| Parties | 3.2.18 | Checksum retained by tester | PASS |
| Essentials | 2.21.2 | API `d543f3fd4bec635521fe35d1cee2adabe6a4ab0cd9f85333adc935ffb4afd653`; staging checksum retained by tester | PASS |

## Fixed compatibility facts

- V1 health balance maximum: 2048; effective maximum is `min(2048, verified Paper maximum)`.
- Defense, strength, intelligence, and critical-damage maximum: 1,000,000.
- Critical chance and attack speed maximum: 100; speed maximum: 500.
- Door replaceable materials: `AIR`, `CAVE_AIR`, `VOID_AIR`.
- CaveCrawlers action-bar cooldown observed in v2.0.0 API: 1000 ms.
- Deterministic code reads `Range#getMin()`/`getMax()` and never calls `Range#getRandom()`.
- CaveCrawlers, FAWE/WorldEdit, MythicMobs, Vault, and Essentials APIs are compile-only and prohibited from the packaged jar.

## Staging evidence

- Tester/date: project owner / 2026-08-14
- OS/JVM flags/CPU/RAM: TODO
- Complete `/dungeon compatibility` output: PASS, confirmed by tester
- Console log: TODO
- FAWE round-trip/cancellation semantics: PASS, confirmed by tester
- Parties leader/no-party/error behavior: PASS, confirmed by tester
- Vault provider ledger evidence: PASS, confirmed by tester
- Essentials/Bukkit spawn behavior: PASS, confirmed by tester
- Stat consumer boundaries and listener inventory: PASS, confirmed by tester
- Item payload round-trip checksums: PASS, confirmed by tester
- Human Gate 0: **PASS**
