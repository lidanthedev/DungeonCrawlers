# Phase 0 human gate

Phase 1 is blocked until every row below is recorded as PASS in `docs/SUPPORTED_STACK.md` on the complete staging stack. Run these commands as an operator on Paper 1.21.11 with Vault, ProtocolLib, MythicMobs, an economy provider, CaveCrawlers, and FastAsyncWorldEdit enabled.

## Automated overview

1. Install `build/libs/DungeonCrawlers-1.0.jar` and boot the server without console errors.
2. Run `/dungeon compatibility`.
3. Save the complete output. Required plugins and built-in schemas must report PASS. `Parties` and `Essentials` may report ABSENT only if the corresponding fallback test below passes.

## Live probes

1. CaveCrawlers item: choose a configured item and run `/dungeon compatibility item <item-id>`. Record the payload byte count and SHA-256; the serialized round trip must PASS.
2. MythicMobs: choose a disposable configured mob, stand in a safe test area, and run `/dungeon compatibility mythic <mob-id>`. Validate/spawn/identify/remove must PASS and leave no entity.
3. WorldEdit: make an asymmetric cuboid selection and run `/dungeon compatibility selection`. The printed minimum, maximum, and volume must match the selection.
4. FAWE round trip/cancel: copy and paste that selection through FAWE, then cancel a deliberately slow test paste. Record the FAWE build, whether closing/awaiting flushes changes, the cancellation call used, callback thread, and whether partial blocks require explicit clearing. Do not run this in production.
5. Vault: use a disposable funded test player and a small exact amount. Run `/dungeon compatibility economy <amount>`. Confirm one checked withdrawal and one checked deposit in both command output and provider ledger, with the original balance restored.
6. Spawn: run `/dungeon compatibility spawn` with Essentials disabled and confirm the configured loaded Bukkit world spawn. Then enable the pinned Essentials stack and separately confirm its server spawn behavior; record which stable API is available. An unresolved Essentials API path blocks Gate 0.
7. Action bar: run `/dungeon compatibility actionbar`. Confirm the alert is visible, the CaveCrawlers default returns after its 1000 ms cooldown, and a second dungeon alert is not emitted within one second.
8. Stats: run `/dungeon compatibility stats`. Confirm 2048 MAX_HEALTH is accepted and restored. Exercise each downstream CaveCrawlers consumer at its fixed boundary, confirm SPEED 500 produces Bukkit walk speed at most 1, and record every `StatsCalculateEvent` listener/priority. Any later mutating listener blocks the gate.
9. Parties: run `/dungeon compatibility party` as a party leader, non-leader, player with no party, and with one member offline. Repeat with Parties absent and disabled. Record the exact rank/leader API behavior. Absence and a positive no-party response must select solo; disabled/error must fail and never become solo.
10. Record Java/Paper/plugin jar SHA-256 values and attach the staging console log.

Do not create the Phase 0 PR until the table is complete and the tester marks the gate PASS.
