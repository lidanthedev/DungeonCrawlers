# Difficulty and Runic boss chest prices

Difficulty discounts increase by five percentage points per tier. Normal uses the configured base price; Impossible discounts it by 45%. A successful Runic boss clear grants +20 in the Bonus score category and multiplies the discounted price by 0.8. This bonus stacks with the Runic pet's +2. Failed runs receive no Runic boss bonus or reward entitlement.

Prices round down once after applying both factors. Free chests remain free. Offers freeze their price when completion registers rewards, so the menu, affordability check and debit use the same value. Reopening, reconnecting or restoring persisted rewards does not reprice existing offers. Legacy completions without a difficulty field default to Normal while retaining their saved offers.

The difficulty selector now displays each tier's chest discount and explains purple glow/particles, x10 health and x5 damage after difficulty scaling, Runic mobs/minibosses from Demonic, and Runic bosses from Void. Eligible tiers explain fragment drops, four fragments for bosses, +20 Bonus score and the additional 20% discount.

## Verification, 2026-10-09

- Java 21 build passed with 318 tests, no failures, errors or skips, including external API shading verification.
- Regression coverage verifies all ten tier prices, exact rounding, free chests, negative input rejection and overflow-safe long prices. Impossible converts 100,000 to 55,000, or 44,000 with a Runic boss.
- Entitlement tests cover online/offline offers, idempotent registration, restart recovery, legacy completion records and frozen Runic boss discounts. Claim tests verify the displayed price, affordability and actual economy withdrawal agree.
- Score tests verify the once-per-run +20 boss bonus, +22 when combined with the pet, retained death penalties, and no boss bonus for failures or ordinary bosses. Adapter coverage verifies Runic boss tracking survives death-time entity removal. Inventory tests verify the new lore.
- JAR `build/libs/DungeonCrawlers-1.0.jar`, SHA-256 `d7eed5521b649e8cac89d5dab36b4a9e899a4dcb4d38bdf548987220909dd448`, uploaded to Modern Cave Crawl (`fa696721`). `cc reload all` ran at 19:08:18 UTC; DungeonCrawlers enabled at 19:08:24 and reopened admission after recovery at 19:08:32. Recovery IDLE and configuration validation passed.
- A solo debug Impossible run reached the active boss. The owner asked to stop executing commands as their player before Runic forcing, boss completion or reward inspection. No further test commands, player mode changes or cleanup were performed after that request. The owner was informed that the debug run remained in Creative mode.

Live Runic completion, chest purchase, multiplayer behavior and client menu appearance remain unchecked. Automated checks cover prices and debits; no live coins were spent. Do not mark this human gate passed until the remaining player checks are completed.
