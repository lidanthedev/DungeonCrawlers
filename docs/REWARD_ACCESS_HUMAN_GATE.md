# Rewards remain available until instance closure

The owner reported that completed chest access expired while they were still in the boss arena. Modern Cave Crawl's live configuration has a 120-second live reward window and a 300-second completion period. These independent deadlines allowed the entitlement to expire before the instance closed.

Live reward opening, preview and purchases now follow the native run's existence. They remain available beyond the old timer until instance cleanup removes that run. The adapter binds this policy to RunPreparationService; persisted offers keep their prices and rolls. Existing live claim records refresh their access deadlines before purchase so an older preflight cannot preserve the early expiration. Offline completion recovery keeps its existing deadlines.

Purchase preparation rechecks entitlement access before recording a debit attempt and immediately before calling the economy provider. If cleanup finishes during persistence, no currency is charged. Already purchased or pending rewards retain their ownership and delivery recovery after closure. Restored live rewards cannot start another purchase after reload closes their instance.

## Verification, 2026-10-09

- Java 21 build passed with 321 tests, no failures, errors or skips, including external API shading verification.
- New regression coverage opens and previews the same frozen live offers ten minutes after completion, then denies them when the instance closes. Offline recovery remains available, and restart with no open instance denies live access.
- Claim coverage restores an older provider-blocked preflight, purchases after its original deadline, checks that closing access prevents additional purchases, and delivers the owned reward after closure. Closing during either preparation or debit-attempt persistence never calls the economy withdrawal and leaves no selected claim group.
- JAR `build/libs/DungeonCrawlers-1.0.jar`, SHA-256 `f68232f77b22721851c50f67d3a594b4648d7902eea249a68dc39ca4a7051f84`, uploaded to Modern Cave Crawl (`fa696721`). A console check found zero active instances immediately before `cc reload all` at 19:37:29 UTC. DungeonCrawlers enabled at 19:37:32 and admission reopened at 19:37:38. Recovery IDLE and configuration validation passed.

No commands were executed as a player. Live chest interaction and purchase remain for the owner to verify during their own run; the automated checks cover the reported timeout and payment boundaries.
