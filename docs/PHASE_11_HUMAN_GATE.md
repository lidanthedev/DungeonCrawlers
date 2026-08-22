# Phase 11 Human Gate — Entitlements, Rolls, and Preview GUI

Status: PASS — command, GUI, completed-chest, score-lock, final-score, recovery, and removal
checks passed; the two-player simultaneous race remains deferred to the final phase.

Staging evidence: server `fa696721` (Modern Cave Crawl), tester `LidanTheGamer`, 2026-08-22,
JAR SHA-256 `D46217B98571125D207D94D934285A545B57F0E96823C857FA14A22FA017C825`.
`cc reload all` and `/dungeon config validate` completed successfully. The reported human checks
reset a generated instance, showed two configured offers (`gold` and `wooden`), opened the reward
view, opened the `wooden` preview, confirmed rolls/amounts remain unchanged without inventory
changes, confirmed the completed-run Ender Chest opens the reward view, and confirmed the final
score message is shown after boss completion. Follow-up checks confirmed arbitrary GUI order,
Back, preview-only BUY, locked-entry denial, recovered offline access, and removed-player denial.

The automated reward tests cover immutable registration, active-at-completion eligibility,
offline-active recovery, disabled/unconfigured omission, score-locked offers, deterministic
per-player rolls, idempotent reopen, and legal stack splitting. The human gate must verify the
Paper/CaveItems boundary and the preview UI without claiming anything.

## Required checks

- [x] Build, deploy, and run `cc reload all`; record the JAR SHA-256 and tester/date.
- [x] Create a generated run and run `/dungeon reward reset-test <instance-id>`.
- [x] Run `/dungeon reward info <instance-id>` and confirm only enabled/configured rewards appear.
  This command is the score/lock evidence (`score=...` and `locked=true|false`); the GUI does not
  currently display the final score.
- [x] Confirm a below-`min-score` reward is listed as locked when configured (`score=200`,
  `gold locked=true`, `rolls=[]` with `gold.min-score: 301`).
- [x] In the opened reward GUI, click reward entries in arbitrary order.
  Confirm the GUI shows the exact rolled item IDs and amounts, Back returns to the list, and
  BUY only reports that claiming is Phase 12 behavior.
- [x] Run `/dungeon reward open <instance-id>` and `/dungeon reward preview <instance-id> wooden`;
  the reported command smoke opened both views and the GUI checks confirmed the exact rolls and
  amounts.
- [x] Reopen the GUI and confirm the rolls and amounts are unchanged without inventory, Ender
  Chest, or mailbox changes.
- [x] Click the completed-run Ender Chest and confirm it cancels the vanilla chest screen and
  opens the same reward overview as `/dungeon reward open`.
- [x] Click a locked reward entry and confirm it reports the score lock without opening a fake
  redstone preview; the fix is deployed with the red name `Reward Locked` and lore showing
  `Score Required: 301` followed by a blank line.
- [x] Complete a boss encounter and confirm the same final score shown by `reward info` is sent
  to each online, non-removed participant with the hover details.
- [x] Test an active participant who was offline at completion: reconnect, open the reward view,
  and confirm a fresh five-minute session within the 24-hour recovered window.
- [x] Confirm a removed participant has no entitlement and cannot open or preview rewards.
- [x] After boss completion, confirm the Red boss marker is not used as the reward location and
  the Lime marker becomes an Ender Chest only after completion.

Successful boss completion now registers the active participant entitlements and final score
snapshot before the completion notice; the `reset-test` command remains available for isolated
preview testing without replaying a full boss encounter.

Two-player simultaneous preview/reconnect is deferred to the final concurrency phase.

Player-facing starts now receive a fresh seed per dungeon instance; repeated reward resets on one
instance remain deterministic, while separate `/dungeon start` runs can produce different rolls.
