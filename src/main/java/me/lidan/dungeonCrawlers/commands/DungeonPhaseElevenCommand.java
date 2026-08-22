package me.lidan.dungeonCrawlers.commands;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.cavecrawlers.utils.StringUtils;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.reward.RewardEntitlementService;
import me.lidan.dungeonCrawlers.core.reward.RewardRoller;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.score.DungeonRank;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import me.lidan.dungeonCrawlers.integration.cave.CaveItemsAdapter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Phase 11 reward diagnostics and the claim-free preview UI. */
@Command("dungeon")
public final class DungeonPhaseElevenCommand {
    private static final int PREVIEW_PAGE_SIZE = 1;
    private static final int PREVIEW_REWARD_SLOT = 13;
    private static final int PREVIEW_BACK_SLOT = 18;
    private static final int PREVIEW_PREVIOUS_SLOT = 20;
    private static final int PREVIEW_PAGE_SLOT = 21;
    private static final int PREVIEW_BUY_SLOT = 22;
    private static final int PREVIEW_NEXT_SLOT = 24;

    private final RewardEntitlementService rewards;
    private final GenerationService generation;
    private final RunPreparationService runs;
    private final ConfigRegistryService config;
    private final CaveItemsGateway caveItems;
    private final PlayerLifecycleService lifecycle;
    private final Function<UUID, Boolean> onlinePresence;

    public DungeonPhaseElevenCommand(RewardEntitlementService rewards, GenerationService generation,
                                     RunPreparationService runs, ConfigRegistryService config) {
        this(rewards, generation, runs, config, new CaveItemsAdapter(), null,
                playerId -> Bukkit.getPlayer(playerId) != null);
    }

    public DungeonPhaseElevenCommand(RewardEntitlementService rewards, GenerationService generation,
                                     RunPreparationService runs, ConfigRegistryService config,
                                     CaveItemsGateway caveItems) {
        this(rewards, generation, runs, config, caveItems, null,
                playerId -> Bukkit.getPlayer(playerId) != null);
    }

    public DungeonPhaseElevenCommand(RewardEntitlementService rewards, GenerationService generation,
                                     RunPreparationService runs, ConfigRegistryService config,
                                     PlayerLifecycleService lifecycle) {
        this(rewards, generation, runs, config, new CaveItemsAdapter(), lifecycle,
                playerId -> Bukkit.getPlayer(playerId) != null);
    }

    DungeonPhaseElevenCommand(RewardEntitlementService rewards, GenerationService generation,
                              RunPreparationService runs, ConfigRegistryService config,
                              CaveItemsGateway caveItems, PlayerLifecycleService lifecycle,
                              Function<UUID, Boolean> onlinePresence) {
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.config = Objects.requireNonNull(config, "config");
        this.caveItems = Objects.requireNonNull(caveItems, "caveItems");
        this.lifecycle = lifecycle;
        this.onlinePresence = Objects.requireNonNull(onlinePresence, "onlinePresence");
    }

    @Subcommand("reward info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void rewardInfo(CommandSender sender,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        RewardEntitlementService.RunSnapshot snapshot = rewards.info(id).orElse(null);
        if (snapshot == null) {
            send(sender, false, "no reward entitlements registered");
            return;
        }
        send(sender, true, "instance=" + id + " score=" + snapshot.score().total()
                + " players=" + snapshot.players().size());
        snapshot.players().values().stream().sorted(java.util.Comparator.comparing(value -> value.playerId().toString()))
                .forEach(player -> sender.sendMessage(MiniMessageUtils.miniMessage(
                        "<gray>player=<white>" + player.playerId() + "</white> mode=<white>" + player.mode()
                                + "</white> offers=<white>" + player.offers().size() + "</white></gray>")));
        snapshot.players().values().stream().flatMap(player -> player.offers().values().stream())
                .sorted(java.util.Comparator.comparing(RewardEntitlementService.RewardOffer::rewardId))
                .forEach(offer -> sender.sendMessage(MiniMessageUtils.miniMessage(
                        "<gray>  reward=<white>" + offer.rewardId() + "</white> locked=<white>"
                                + offer.locked() + "</white> price=<white>" + offer.price() + "</white> rolls=<white>"
                                + offer.rolls() + "</white></gray>")));
    }

    @Subcommand("reward roll")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void rewardRoll(CommandSender sender,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                           @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        UUID playerId = player.getUniqueId();
        var entitlement = rewards.open(id, playerId).orElse(null);
        if (entitlement == null) {
            send(sender, false, "player has no active reward entitlement");
            return;
        }
        send(sender, true, "player=" + playerLabel(player) + " mode=" + entitlement.mode());
        entitlement.offers().values().stream().sorted(java.util.Comparator.comparing(
                        RewardEntitlementService.RewardOffer::rewardId))
                .forEach(offer -> sender.sendMessage(MiniMessageUtils.miniMessage(
                        "<gray>reward=<white>" + offer.rewardId() + "</white> locked=<white>" + offer.locked()
                                + "</white> rolls=<white>" + offer.rolls() + "</white></gray>")));
    }

    @Subcommand("reward open")
    @CommandPermission("dungeoncrawlers.use")
    public void rewardOpen(Player player,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(player, instanceId);
        if (id == null) return;
        openRewards(player, id);
    }

    /** Opens the reward view for an already-resolved instance, used by the completion chest listener. */
    public void openRewards(Player player, UUID instanceId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(instanceId, "instanceId");
        RewardEntitlementService.PlayerEntitlement entitlement = rewards.open(instanceId, player.getUniqueId()).orElse(null);
        if (entitlement == null) {
            send(player, false, "no active reward entitlement");
            return;
        }
        openOverview(player, instanceId, entitlement);
    }

    @Subcommand("reward preview")
    @CommandPermission("dungeoncrawlers.use")
    public void rewardPreview(Player player,
                              @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                              @SuggestWith(RewardIdSuggestionProvider.class) String rewardId) {
        UUID id = parse(player, instanceId);
        if (id == null) return;
        var offer = rewards.preview(id, player.getUniqueId(), rewardId).orElse(null);
        if (offer == null) {
            send(player, false, "reward is unavailable or expired");
            return;
        }
        if (offer.locked()) {
            send(player, false, "reward is locked; score requirement not met");
            return;
        }
        openPreview(player, id, offer);
    }

    @Subcommand("reward reset-test")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void rewardResetTest(CommandSender sender,
                                @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var context = generation.layoutContext(id).orElse(null);
        var run = runs.info(id).orElse(null);
        if (context == null || run == null) {
            send(sender, false, "generated run not found");
            return;
        }
        var lifecycleSnapshot = lifecycle == null ? null : lifecycle.info(id).orElse(null);
        List<RewardEntitlementService.Participant> participants = rewardResetParticipants(
                run, lifecycleSnapshot, onlinePresence);
        rewards.resetTest(id);
        rewards.register(new RewardEntitlementService.Completion(id, context.seed(), Instant.now(), maxScore(),
                participants, context.floor().rewards()));
        send(sender, true, "reward entitlements reset; use /dungeon reward open " + id);
    }

    static List<RewardEntitlementService.Participant> rewardResetParticipants(
            RunPreparationService.RunSnapshot run, PlayerLifecycleService.InstanceSnapshot lifecycleSnapshot,
            Function<UUID, Boolean> onlinePresence) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(onlinePresence, "onlinePresence");
        if (lifecycleSnapshot == null) return List.of();
        Map<UUID, PlayerLifecycleService.PlayerSnapshot> lifecyclePlayers = lifecycleSnapshot.players().stream()
                .collect(java.util.stream.Collectors.toMap(PlayerLifecycleService.PlayerSnapshot::playerId,
                        value -> value));
        return run.participants().stream()
                .map(lifecyclePlayers::get)
                .filter(Objects::nonNull)
                .filter(player -> player.state() != PlayerLifecycleService.PlayerState.REMOVED)
                .map(player -> new RewardEntitlementService.Participant(player.playerId(), true,
                        Boolean.TRUE.equals(onlinePresence.apply(player.playerId()))))
                .toList();
    }

    private void openOverview(Player player, UUID instanceId,
                              RewardEntitlementService.PlayerEntitlement entitlement) {
        Gui gui = Gui.gui().rows(3).title(MiniMessageUtils.miniMessage("<dark_purple>Dungeon Rewards</dark_purple>"))
                .disableAllInteractions().create();
        int slot = 10;
        for (RewardEntitlementService.RewardOffer offer : entitlement.offers().values()) {
            if (slot >= 17) break;
            int previewSlot = slot++;
            gui.setItem(previewSlot, rewardItem(offer, event -> {
                event.setCancelled(true);
                if (offer.locked()) {
                    player.sendMessage(MiniMessageUtils.miniMessage(
                            "<yellow>This reward is locked; your score does not meet its requirement.</yellow>"));
                    return;
                }
                openPreview(player, instanceId, offer);
            }));
        }
        gui.setItem(22, ItemBuilder.from(Material.BARRIER)
                .name(MiniMessageUtils.miniMessage("<red>Close</red>"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                }));
        gui.open(player);
    }

    private void openPreview(Player player, UUID instanceId, RewardEntitlementService.RewardOffer offer) {
        openPreview(player, instanceId, offer, 0);
    }

    private void openPreview(Player player, UUID instanceId, RewardEntitlementService.RewardOffer offer, int page) {
        int pageCount = previewPageCount(offer);
        int currentPage = Math.max(0, Math.min(page, pageCount - 1));
        Gui gui = Gui.gui().rows(3).title(MiniMessageUtils.miniMessage(previewTitle(offer)))
                .disableAllInteractions().create();
        gui.setItem(PREVIEW_REWARD_SLOT, ItemBuilder.from(previewIcons(offer, caveItems, currentPage).getFirst())
                .asGuiItem(event -> event.setCancelled(true)));
        gui.setItem(PREVIEW_BACK_SLOT, ItemBuilder.from(Material.ARROW)
                .name(MiniMessageUtils.miniMessage("<yellow>Back</yellow>"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    rewards.open(instanceId, player.getUniqueId()).ifPresent(value -> openOverview(player, instanceId, value));
                }));
        if (currentPage > 0) {
            gui.setItem(PREVIEW_PREVIOUS_SLOT, ItemBuilder.from(Material.SPECTRAL_ARROW)
                    .name(MiniMessageUtils.miniMessage("<yellow>Previous Page</yellow>"))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        openPreview(player, instanceId, offer, currentPage - 1);
                    }));
        }
        if (pageCount > 1) {
            gui.setItem(PREVIEW_PAGE_SLOT, ItemBuilder.from(Material.PAPER)
                    .name(MiniMessageUtils.miniMessage("<gray>Page <white>" + (currentPage + 1) + "</white>/<white>"
                            + pageCount + "</white></gray>"))
                    .asGuiItem(event -> event.setCancelled(true)));
        }
        gui.setItem(PREVIEW_BUY_SLOT, ItemBuilder.from(Material.EMERALD)
                .name(MiniMessageUtils.miniMessage("<green>BUY</green>"))
                .lore(List.of(MiniMessageUtils.miniMessage("<gray>Claiming is enabled in Phase 12.</gray>")))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.sendMessage(MiniMessageUtils.miniMessage("<yellow>BUY is preview-only until Phase 12.</yellow>"));
                }));
        if (currentPage + 1 < pageCount) {
            gui.setItem(PREVIEW_NEXT_SLOT, ItemBuilder.from(Material.SPECTRAL_ARROW)
                    .name(MiniMessageUtils.miniMessage("<yellow>Next Page</yellow>"))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        openPreview(player, instanceId, offer, currentPage + 1);
                    }));
        }
        gui.open(player);
    }

    private dev.triumphteam.gui.guis.GuiItem rewardItem(RewardEntitlementService.RewardOffer offer,
                                                        Consumer<InventoryClickEvent> action) {
        return ItemBuilder.from(rewardMenuItem(offer, caveItems))
                .asGuiItem(action::accept);
    }

    private static ItemStack rewardMenuItem(RewardEntitlementService.RewardOffer offer,
                                            CaveItemsGateway caveItems) {
        ItemStack item = rewardIcon(offer, caveItems).clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(MiniMessageUtils.miniMessage("<gray>Price: <gold>" + priceLabel(offer.price())
                + "</gold></gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static String previewTitle(RewardEntitlementService.RewardOffer offer) {
        String price = priceLabel(offer.price());
        return "<dark_purple>Reward Preview - <gold>" + price + "</gold></dark_purple>";
    }

    private static String priceLabel(long price) {
        return price == 0 ? "FREE" : StringUtils.getNumberFormat(price);
    }

    static List<RewardRoller.RolledReward> previewRolls(RewardEntitlementService.RewardOffer offer, int page) {
        Objects.requireNonNull(offer, "offer");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (offer.rolls().isEmpty()) return List.of();
        int from = Math.min(page * PREVIEW_PAGE_SIZE, offer.rolls().size());
        int to = Math.min(from + PREVIEW_PAGE_SIZE, offer.rolls().size());
        return List.copyOf(offer.rolls().subList(from, to));
    }

    static List<ItemStack> previewIcons(RewardEntitlementService.RewardOffer offer,
                                        CaveItemsGateway caveItems, int page) {
        Objects.requireNonNull(caveItems, "caveItems");
        List<RewardRoller.RolledReward> rolls = previewRolls(offer, page);
        if (rolls.isEmpty()) return List.of(rewardIcon(offer, caveItems));
        return rolls.stream().map(roll -> caveItems.build(roll.itemId(), roll.amount())
                .orElseGet(() -> new ItemStack(Material.BARRIER))).toList();
    }

    private static int previewPageCount(RewardEntitlementService.RewardOffer offer) {
        return Math.max(1, (offer.rolls().size() + PREVIEW_PAGE_SIZE - 1) / PREVIEW_PAGE_SIZE);
    }

    static ItemStack rewardIcon(RewardEntitlementService.RewardOffer offer,
                                CaveItemsGateway caveItems) {
        if (offer.locked()) {
            ItemStack locked = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta meta = locked.getItemMeta();
            if (meta != null) {
                meta.displayName(MiniMessageUtils.miniMessage("<red>Reward Locked</red>"));
                meta.lore(List.of(
                        MiniMessageUtils.miniMessage("<gray>Score Required: <white>" + offer.minScore()
                                + "</white></gray>"),
                        MiniMessageUtils.miniMessage("")));
                locked.setItemMeta(meta);
            }
            return locked;
        }
        if (offer.rolls().isEmpty()) return new ItemStack(Material.REDSTONE_BLOCK);
        RewardRoller.RolledReward roll = offer.rolls().getFirst();
        return caveItems.build(roll.itemId(), roll.amount())
                .orElseGet(() -> new org.bukkit.inventory.ItemStack(Material.BARRIER));
    }

    private UUID parse(CommandSender sender, String value) {
        return DungeonInstanceResolver.resolveOrNotify(sender, value, runs);
    }

    private static String playerLabel(OfflinePlayer player) {
        return player.getName() == null || player.getName().isBlank() ? player.getUniqueId().toString() : player.getName();
    }

    private static ScoreService.FinalScoreSnapshot maxScore() {
        ScoreService.ScoreResult result = new ScoreService.ScoreResult(100, 100, 100, 0, 300,
                DungeonRank.S_PLUS, List.of());
        return new ScoreService.FinalScoreSnapshot(
                new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(8), 0, 0),
                result.skill(), result.time(), result.exploration(), result.bonus(), result.total(),
                result.rank(), result.bonusFacts());
    }

    private static void send(CommandSender sender, boolean pass, String message) {
        sender.sendMessage(MiniMessageUtils.miniMessage("<" + (pass ? "green" : "red") + ">["
                + (pass ? "PASS" : "FAIL") + "] " + message + "</" + (pass ? "green" : "red") + ">"));
    }
}
