package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityReport;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.compatibility.ProbeResult;
import me.lidan.dungeonCrawlers.config.registry.ConfigLoadResult;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.core.state.InstanceState;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import me.lidan.dungeonCrawlers.integration.MythicMobGateway;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import me.lidan.dungeonCrawlers.integration.SpawnProvider;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import me.lidan.dungeonCrawlers.integration.cave.CaveActionBarAdapter;
import me.lidan.dungeonCrawlers.integration.cave.CaveItemsAdapter;
import me.lidan.dungeonCrawlers.integration.mythic.MythicMobsAdapter;
import me.lidan.dungeonCrawlers.integration.parties.PartyProviders;
import me.lidan.dungeonCrawlers.integration.spawn.BukkitSpawnProvider;
import me.lidan.dungeonCrawlers.integration.vault.VaultEconomyAdapter;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Command("dungeon")
public final class DungeonCrawlersCommand {
    private static final int FORCE_RELOAD_MAX_POLLS = 600;
    private final JavaPlugin plugin;
    private final CompatibilityService compatibility;
    private final CaveItemsGateway caveItems = new CaveItemsAdapter();
    private final MythicMobGateway mythic = new MythicMobsAdapter();
    private final WorldEditGateway worldEdit = new WorldEditAdapter();
    private final PartyProvider parties;
    private final ConfigRegistryService configRegistry;
    private final PlayerReservationService reservations;
    private final DurableRepository durableRepository;
    private final BoostedCustomConfig mainConfig;
    private final StateTransitionService transitions = new StateTransitionService();
    private final ScoreService scores = new ScoreService();
    private final GenerationService generation;
    private final Consumer<UUID> preparationCancel;

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository) {
        this(plugin, compatibility, mainConfig, configRegistry, reservations, durableRepository,
                null, ignored -> { });
    }

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository, GenerationService generation,
                                  Consumer<UUID> preparationCancel) {
        this.plugin = plugin;
        this.compatibility = compatibility;
        this.mainConfig = mainConfig;
        this.configRegistry = configRegistry;
        this.reservations = reservations;
        this.durableRepository = durableRepository;
        this.generation = generation;
        this.preparationCancel = preparationCancel;
        this.parties = PartyProviders.forServer(plugin.getServer());
    }

    @Subcommand("config validate")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void configValidate(CommandSender sender) {
        sender.sendMessage("Validating DungeonCrawlers configuration...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ConfigLoadResult result = configRegistry.validate();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                result.warnings().forEach(warning -> sender.sendMessage("[WARN] " + warning));
                result.errors().forEach(error -> sender.sendMessage("[FAIL] " + error));
                if (result.successful()) sender.sendMessage("[PASS] hash=" + result.snapshot().hash());
            });
        });
    }

    @Subcommand("reload")
    @CommandPermission("dungeoncrawlers.admin.reload")
    public void reload(CommandSender sender) {
        sendReloadMessage(sender, "<yellow>Reloading DungeonCrawlers configuration...</yellow>");
        reloadAsync(sender);
    }

    @Subcommand("reload force")
    @CommandPermission("dungeoncrawlers.admin.reload")
    public void reloadForce(CommandSender sender) {
        if (generation == null) {
            sendReloadMessage(sender, "<red>[FAIL] force reload is unavailable during bootstrap</red>");
            return;
        }
        sendReloadMessage(sender, "<yellow>Force reload: cancelling all running dungeons before reloading...</yellow>");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            reservations.pauseAdmission();
            List<UUID> active = generation.instances().stream()
                    .filter(instance -> instance.status() != GenerationService.InstanceStatus.DESTROYED)
                    .map(GenerationService.InstanceSnapshot::instanceId).toList();
            active.forEach(instanceId -> {
                preparationCancel.accept(instanceId);
                generation.cancel(instanceId);
            });
            sendReloadMessage(sender, "<yellow>Force reload requested for <white>" + active.size()
                    + "</white> dungeon(s); waiting for cleanup...</yellow>");
            awaitForceReload(sender, 0);
        });
    }

    private void awaitForceReload(CommandSender sender, int polls) {
        if (reservations.activeReservationCount() == 0) {
            reloadAsync(sender, reservations::resumeAdmission);
            return;
        }
        if (polls >= FORCE_RELOAD_MAX_POLLS) {
            reservations.resumeAdmission();
            sendReloadMessage(sender, "<red>[FAIL] force reload timed out while waiting for dungeon cleanup</red>");
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> awaitForceReload(sender, polls + 1), 1L);
    }

    private void reloadAsync(CommandSender sender) {
        reloadAsync(sender, null);
    }

    private void reloadAsync(CommandSender sender, Runnable completion) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ConfigRegistryService.ReloadResult result = reservations.withAdmissionPaused(configRegistry::reload);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    reportReload(sender, result);
                    if (completion != null) completion.run();
                });
            } catch (RuntimeException exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sendReloadMessage(sender, "<red>[FAIL] reload: " + exception.getMessage() + "</red>");
                    if (completion != null) completion.run();
                });
            }
        });
    }

    private void reportReload(CommandSender sender, ConfigRegistryService.ReloadResult result) {
        result.warnings().forEach(warning -> sendReloadMessage(sender, "<yellow>[WARN] " + warning + "</yellow>"));
        result.errors().forEach(error -> sendReloadMessage(sender, "<red>[FAIL] reload: " + error + "</red>"));
        if (result.swapped()) {
            sendReloadMessage(sender, "<green>[PASS] active hash=" + result.snapshot().hash() + "</green>");
        } else if (result.errors().stream().anyMatch(error -> error.contains("reservation(s) are active"))) {
            sendReloadMessage(sender, "<red>Reload refused because a dungeon is active. Use "
                    + "<click:suggest_command:'/dungeon reload force'><aqua>/dungeon reload force</aqua></click>"
                    + " to cancel all running dungeons and reload.</red>");
        }
    }

    private static void sendReloadMessage(CommandSender sender, String miniMessage) {
        sender.sendMessage(MiniMessageUtils.miniMessage(miniMessage));
    }

    @Subcommand("floor info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void floorInfo(CommandSender sender, @SuggestWith(FloorIdSuggestionProvider.class) String id) {
        var value = configRegistry.snapshot().floors().get(id);
        sender.sendMessage(value == null ? "[FAIL] unknown floor " + id : "[PASS] " + value);
    }

    @Subcommand("room info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void roomInfo(CommandSender sender, @SuggestWith(RoomIdSuggestionProvider.class) String id) {
        var value = configRegistry.snapshot().rooms().get(id);
        sender.sendMessage(value == null ? "[FAIL] unknown room " + id : "[PASS] " + value);
    }

    @Subcommand("class info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void classInfo(CommandSender sender, String id) {
        var value = configRegistry.snapshot().classes().get(id);
        sender.sendMessage(value == null ? "[FAIL] unknown class " + id : "[PASS] " + value);
    }

    @Subcommand("blessing info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void blessingInfo(CommandSender sender, String id) {
        var value = configRegistry.snapshot().blessings().get(id);
        sender.sendMessage(value == null ? "[FAIL] unknown blessing " + id : "[PASS] " + value);
    }

    @Subcommand("state simulate")
    @CommandPermission("dungeoncrawlers.admin.simulate")
    public void stateSimulate(CommandSender sender, String from, String to) {
        try {
            var result = transitions.transition(InstanceState.valueOf(from.toUpperCase(Locale.ROOT)),
                    InstanceState.valueOf(to.toUpperCase(Locale.ROOT)));
            sender.sendMessage("[" + (result.accepted() ? "PASS" : "FAIL") + "] " + result.detail());
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[FAIL] unknown state; valid=" + List.of(InstanceState.values()));
        }
    }

    @Subcommand("score simulate")
    @CommandPermission("dungeoncrawlers.admin.simulate")
    public void scoreSimulate(CommandSender sender, boolean successful, int deaths, long elapsedMinutes,
                              int foundSecrets, int totalSecrets) {
        try {
            var result = scores.calculate(new ScoreService.ScoreInput(successful, deaths,
                    Duration.ofMinutes(elapsedMinutes), foundSecrets, totalSecrets), List.of());
            sender.sendMessage("[PASS] skill=" + result.skill() + ", time=" + result.time()
                    + ", exploration=" + result.exploration() + ", bonus=" + result.bonus()
                    + ", total=" + result.total() + ", rank=" + result.rank());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            sender.sendMessage("[FAIL] " + exception.getMessage());
        }
    }

    @Subcommand("repository")
    @CommandPermission("dungeoncrawlers.admin.diagnostics")
    public void repository(CommandSender sender) {
        sender.sendMessage("[PASS] " + durableRepository.diagnostics());
    }

    @Subcommand("reservation race")
    @CommandPermission("dungeoncrawlers.admin.simulate")
    public void reservationRace(CommandSender sender) {
        sender.sendMessage("Running isolated reservation race...");
        CompletableFuture.supplyAsync(() -> {
            PlayerReservationService isolated = new PlayerReservationService();
            UUID shared = UUID.randomUUID();
            PartySnapshot first = new PartySnapshot(shared, List.of(shared, UUID.randomUUID()), false);
            PartySnapshot second = new PartySnapshot(shared, List.of(shared, UUID.randomUUID()), false);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var a = executor.submit(() -> isolated.reserve(UUID.randomUUID(), first).successful());
                var b = executor.submit(() -> isolated.reserve(UUID.randomUUID(), second).successful());
                return (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).whenComplete((wins, error) -> plugin.getServer().getScheduler().runTask(plugin, () ->
                sender.sendMessage(error == null && wins == 1 ? "[PASS] exactly one reservation won"
                        : "[FAIL] reservation race: " + (error == null ? "wins=" + wins : error.getMessage()))));
    }

    @Subcommand("compatibility")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void compatibility(CommandSender sender) {
        CompatibilityReport report = compatibility.inspect();
        sender.sendMessage("DungeonCrawlers compatibility report @ " + report.createdAt());
        for (ProbeResult result : report.results()) {
            sender.sendMessage("[" + result.status() + "] " + result.id() + ": " + result.detail());
        }
        sender.sendMessage("Automated checks=" + (report.passesAutomatedChecks() ? "PASS" : "FAIL")
                + "; Human Gate 0=" + (report.passesHumanGate() ? "PASS" : "BLOCKED"));
    }

    @Subcommand("compatibility item")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void item(CommandSender sender, String itemId) {
        ItemStack built = caveItems.build(itemId, 1).orElse(null);
        if (built == null) {
            sender.sendMessage("[FAIL] CaveCrawlers item is not configured/buildable: " + itemId);
            return;
        }
        byte[] payload = built.serializeAsBytes();
        ItemStack restored = ItemStack.deserializeBytes(payload);
        sender.sendMessage("[" + (built.equals(restored) ? "PASS" : "FAIL") + "] item=" + itemId
                + ", payloadBytes=" + payload.length + ", sha256=" + sha256(payload));
    }

    @Subcommand("compatibility mythic")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void mythic(Player player, String mobId) {
        MythicMobGateway.SpawnResult spawned = mythic.spawn(mobId, player.getLocation(), 1);
        if (!spawned.successful()) {
            player.sendMessage("[FAIL] " + spawned.detail());
            return;
        }
        Entity entity = spawned.entity();
        boolean identified = mythic.isMythicMob(entity);
        boolean removed = mythic.remove(entity);
        player.sendMessage("[" + (identified && removed ? "PASS" : "FAIL") + "] validate/spawn/identify/remove; "
                + spawned.detail() + ", identified=" + identified + ", removed=" + removed);
    }

    @Subcommand("compatibility selection")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void selection(Player player) {
        WorldEditGateway.SelectionResult result = worldEdit.selection(player);
        player.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
    }

    @Subcommand("compatibility party")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void party(Player player) {
        PartyProvider.PartyLookup result = parties.lookup(player.getUniqueId());
        player.sendMessage("[" + (result.status() == PartyProvider.Status.ERROR ? "FAIL" : "PASS") + "] status="
                + result.status() + ", leader=" + result.leader() + ", onlineMembers=" + result.onlineMembers()
                + ", detail=" + result.detail());
    }

    @Subcommand("compatibility economy")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void economy(Player player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage("[FAIL] amount must be finite and positive");
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            player.sendMessage("[FAIL] no Vault economy provider");
            return;
        }
        String configuredAccount = mainConfig.getString("compatibility.economy-test-account-uuid", "").trim();
        UUID accountId;
        try {
            accountId = UUID.fromString(configuredAccount);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("[FAIL] compatibility.economy-test-account-uuid must be a valid UUID");
            return;
        }
        if (accountId.equals(player.getUniqueId())) {
            player.sendMessage("[FAIL] the disposable economy test account must not be the command sender");
            return;
        }
        OfflinePlayer testAccount = plugin.getServer().getOfflinePlayer(accountId);
        if (!testAccount.hasPlayedBefore() && !testAccount.isOnline()) {
            player.sendMessage("[FAIL] configured disposable economy test account has never joined this server");
            return;
        }
        EconomyGateway gateway = new VaultEconomyAdapter(registration.getProvider());
        EconomyGateway.TransactionResult debit = gateway.withdraw(testAccount, amount);
        if (!debit.successful()) {
            player.sendMessage("[FAIL] checked withdraw via " + gateway.providerIdentity() + ": " + debit.detail());
            return;
        }
        EconomyGateway.TransactionResult credit = gateway.deposit(testAccount, debit.amount());
        if (!credit.successful()) {
            EconomyGateway.TransactionResult recovery = recoverEconomyProbe(gateway, testAccount, debit.amount());
            player.sendMessage("[FAIL] provider=" + gateway.providerIdentity() + ", account=" + accountId
                    + ", withdrawBalance=" + debit.balance() + ", depositDetail=" + credit.detail()
                    + ", recovery=" + (recovery.successful() ? "restored" : "FAILED: " + recovery.detail()));
            if (!recovery.successful()) {
                plugin.getLogger().severe("Economy probe recovery failed for disposable account " + accountId
                        + "; manually restore " + debit.amount() + " using provider " + gateway.providerIdentity());
            }
            return;
        }
        player.sendMessage("[PASS] provider=" + gateway.providerIdentity() + ", account=" + accountId
                + ", withdrawBalance=" + debit.balance() + ", depositBalance=" + credit.balance()
                + ", depositDetail=" + credit.detail());
    }

    private static EconomyGateway.TransactionResult recoverEconomyProbe(
            EconomyGateway gateway, OfflinePlayer testAccount, double amount) {
        EconomyGateway.TransactionResult recovery = gateway.deposit(testAccount, amount);
        for (int attempt = 1; attempt < 3 && !recovery.successful(); attempt++) {
            recovery = gateway.deposit(testAccount, amount);
        }
        return recovery;
    }

    @Subcommand("compatibility spawn")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void spawn(CommandSender sender) {
        SpawnProvider provider = new BukkitSpawnProvider(plugin.getServer(), mainConfig.getString("fallback-spawn-world", ""));
        sender.sendMessage(provider.spawn()
                .map(location -> "[PASS] " + provider.source() + " -> " + location.getWorld().getName() + " " + location.toVector())
                .orElse("[FAIL] configured Bukkit fallback world is not loaded"));
    }

    @Subcommand("compatibility actionbar")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void actionbar(Player player) {
        new CaveActionBarAdapter().show(player, Component.text("DungeonCrawlers action-bar probe"));
        player.sendMessage("[MANUAL_REQUIRED] confirm alert visibility and default restoration after one second");
    }

    @Subcommand("compatibility stats")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void stats(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            player.sendMessage("[FAIL] MAX_HEALTH attribute unavailable");
            return;
        }
        player.sendMessage("[PASS] Paper MAX_HEALTH available=" + maxHealth.getBaseValue()
                + "; DungeonCrawlers health cap=unbounded; CaveCrawlers="
                + CaveCrawlers.getInstance().getPluginMeta().getVersion());
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

}
