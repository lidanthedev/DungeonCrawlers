# Phase 12 Human Gate - Checked purchase and inventory capacity

Status: PASS

The phase 12 gate covers durable claim locking, the provider debit boundary, reconciliation,
inventory-capacity preflight, exact serialized item delivery, and restart recovery on server `fa696721`
(`Modern Cave Crawl`).

New purchases must fit the player's storage inventory before the claim is persisted or Vault is
called. Existing pending-delivery records remain protected as a recovery path for interrupted or
legacy claims, but full inventory is no longer an accepted purchase flow.

## Required checks

- [x] Build, deploy, run `cc reload all`, and record the JAR SHA-256.
- [x] Reset a generated reward entitlement and claim a free reward from the BUY button.
- [x] Claim a paid reward with enough balance and confirm exactly one debit and inventory delivery.
- [x] Confirm delivered rewards no longer retain DungeonCrawlers provenance tags and can stack as
  ordinary identical items.
- [x] Confirm an insufficient-balance purchase is rejected without entering delivery, releases the
  claim group, and leaves another reward selectable.
- [x] Fill the player's inventory and confirm BUY is rejected before debit, with no claim record
  and no delivery attempt.
- [x] Confirm capacity preflight treats temporary delivery provenance markers as part of stack
  identity, so a partial unmarked stack cannot hide a full-inventory rejection.
- [x] Spam BUY and confirm the claim group allows only one winner and no second debit.
- [x] Confirm a definite provider failure releases the claim for a later retry. An insufficient
  balance leaves the expensive offer available, allowing a different reward to be selected; the
  same reward can still be retried after `/eco give` supplies the balance.
- [x] Confirm the in-game insufficient-funds result plays the deny sound and shows
  `Not enough money for this reward.`; reopen the chest and select a different reward.
- [x] Review the ambiguous-provider timeout scenario against the standard Vault + EssentialsX setup.
  Vault exposes synchronous success/failure responses and EssentialsX performs the default economy
  operation in-process, so there is no normal timeout path to reproduce in this human gate. Keep
  `/dungeon reward reconcile <claim-id> charged|not-charged <evidence>` as defensive recovery, and
  defer provider/chaos fault-injection validation.
- [x] Confirm recovery from an owned claim without duplication. The development-only
  `/dungeon reward delivery-pause-test on` command pauses delivery after the durable `OWNED` claim;
  `/dungeon reward delivery-recover-test` clears the process-local pause and invokes the same
  recovery scan without waiting for a full server restart. The fast live check delivered the claim
  once and `dungeon reward info` reached `DELIVERED`; automated restart coverage remains in place.

Automated coverage must retain the one-debit claim-group, persistence-failure, reconciliation,
payload round-trip, inventory-capacity preflight, and interrupted-delivery checks.

## Recorded evidence

- 2026-08-22: Java 21 `clean build` passed, including tests and external-plugin shading
  verification. Latest deployed JAR SHA-256: `aa1da97b26c8fe81cfee392323f013f5108e3f41436086d34289594a3a1fedc9`.
- 2026-08-22 (prior build): Paid purchase with insufficient balance produced
  `Purchase processing...` followed by `Reward claim: REJECTED - purchase failed: Loan was not permitted!`.
  This exposed that the UI/service path still consumed the claim group; the corrected behavior is
  recorded below.
- 2026-08-22: Paid purchase human check with enough balance produced
  `Purchase processing...`, `Reward purchased. Delivering items...`, and
  `Reward delivered to your inventory.`
- 2026-08-22: Automated full-inventory check rejected BUY before creating a claim or calling
  the economy provider. Clearing one storage slot allowed the same reward to be purchased.
- 2026-08-22: Automated delivery regression confirmed that durable delivery ACK cleanup removes
  `dungeoncrawlers:reward-owner`, `reward-claim`, `reward-item`, and `reward-pending`. Join/reload
  recovery also strips those markers from already-delivered items while preserving pending items.
- 2026-08-22: Full-inventory preflight now simulates the temporary pending markers used during
  insertion. Overflow no longer returns a mailbox-pending result; a late capacity change blocks
  delivery for retry instead.
- 2026-08-22: Live generated run `9465d98d-5f7c-43b2-993f-095ce52ff195` filled the player's
  storage inventory with temporary items. The player-only reward open and claim commands ran,
  `dungeon reward info` showed no claim state for the attempted reward, and cleanup completed
  with the slot free.
- 2026-08-22: After the marked-item preflight fix, live run `3faa69c5-295e-4497-9148-1f89b7665c88`
  was filled with temporary barriers. `/sudo LidanTheGamer dungeon reward claim ... wooden` was
  attempted, `dungeon reward info` showed no claim record, and cleanup completed with the instance
  DESTROYED and its slot free.
- 2026-08-22: JAR uploaded successfully to the development server. `cc reload all` reported
  `DungeonCrawlers reloaded!`; the new JAR was remapped, loaded, and enabled.
- 2026-08-22: `dungeon config validate` passed; `dungeon repository` reported an open repository
  with zero in-flight or queued operations; Vault was detected and enabled.
- 2026-08-22: On generated run `8284dde4-8188-4839-aac2-e176adbadf05`, the player-only paths
  `/sudo LidanTheGamer dungeon reward open ...` and `/sudo LidanTheGamer dungeon reward claim ... wooden`
  completed. `dungeon reward info` showed `wooden` in `DELIVERED` with one claim ID. A repeated
  claim left that same claim ID and `DELIVERED` state.
- 2026-08-22: BUY spam produced only `PROCESSING - another claim is still processing` while one
  purchase completed and delivered; no duplicate reward was observed.
- 2026-08-22 (prior build): The `expensive` reward first rejected with `Loan was not permitted!`;
  after `/eco give LidanTheGamer 10000000000`, retrying the same reward purchased and delivered it.
- 2026-08-22: After the definite-failure fix, live run `e99e22cf-0495-487e-86ca-4a173e2e46f9`
  attempted `expensive` with insufficient balance; `dungeon reward info` showed it still
  `AVAILABLE`, and selecting `wooden` afterward reached `DELIVERED`.
- 2026-08-22: Final player check confirmed the insufficient-funds deny sound and
  `Not enough money for this reward.`; reopening the chest allowed another reward to purchase
  and deliver, and adding money allowed the expensive reward retry to purchase and deliver.
- 2026-08-22: Upstream Vault and EssentialsX review confirmed that the standard integration uses a
  synchronous `EconomyResponse` with `SUCCESS`/`FAILURE`/`NOT_IMPLEMENTED`; EssentialsX calls its
  local economy implementation directly and maps known exceptions to a definite failure. A server
  stall, plugin/provider exception, or process crash can still leave an application-level ambiguous
  debit, but a normal network-style Vault timeout is not expected for this setup.
- 2026-08-22: Added the process-local development-server delivery pause hook. It returns a pending
  delivery result while retaining durable `OWNED`; recreating the plugin clears the flag, allowing
  the existing join recovery path to deliver the exact payload once.
- 2026-08-22: Recovery now reports successful/pending/failed delivery to the player on join and
  plugin reload. The player-only `delivery-recover-test` command invokes that recovery scan without
  waiting for a full server restart.
- 2026-08-22: Fast live recovery check on instance `6992641a-896d-4ba0-b176-a4cd5cec721c`: after
  pausing delivery, the player ran `delivery-recover-test`; chat reported `Reward delivered to your
  inventory.` and `dungeon reward info this` showed the owned `wooden` claim
  `432c108f-82d4-38f8-8bea-7d95c3afbcbb` in `DELIVERED` state.
- Automated phase 12 tests cover free claims, one-debit ownership, definite failure retry,
  ambiguous reconciliation, restart recovery after an ownership ACK failure, exact delivery
  provenance cleanup, marked-item inventory-capacity rejection before debit, and no-overflow
  delivery handling.
