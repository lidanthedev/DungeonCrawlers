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
import me.lidan.dungeonCrawlers.core.score.ScoreResultRenderer;
import me.lidan.dungeonCrawlers.core.state.InstanceState;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import me.lidan.dungeonCrawlers.integration.MythicMobGateway;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import me.lidan.dungeonCrawlers.integration.SpawnProvider;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.integration.cave.CaveActionBarAdapter;
import me.lidan.dungeonCrawlers.integration.cave.CaveItemsAdapter;
import me.lidan.dungeonCrawlers.integration.mythic.MythicMobsAdapter;
import me.lidan.dungeonCrawlers.integration.parties.PartyProviders;
import me.lidan.dungeonCrawlers.integration.spawn.BukkitSpawnProvider;
import me.lidan.dungeonCrawlers.integration.vault.VaultEconomyAdapter;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.io.IOException;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
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
    private final BooleanSupplier reloadBlocked;
    private final BooleanSupplier debugEnabled;
    private final Consumer<Boolean> debugUpdate;

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository) {
        this(plugin, compatibility, mainConfig, configRegistry, reservations, durableRepository,
                null, ignored -> { }, () -> false, () -> false, ignored -> { });
    }

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository, GenerationService generation,
                                  Consumer<UUID> preparationCancel) {
        this(plugin, compatibility, mainConfig, configRegistry, reservations, durableRepository, generation,
                preparationCancel, () -> false, () -> false, ignored -> { });
    }

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository, GenerationService generation,
                                  Consumer<UUID> preparationCancel, BooleanSupplier reloadBlocked) {
        this(plugin, compatibility, mainConfig, configRegistry, reservations, durableRepository, generation,
                preparationCancel, reloadBlocked, () -> false, ignored -> { });
    }

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility,
                                  BoostedCustomConfig mainConfig,
                                  ConfigRegistryService configRegistry, PlayerReservationService reservations,
                                  DurableRepository durableRepository, GenerationService generation,
                                  Consumer<UUID> preparationCancel, BooleanSupplier reloadBlocked,
                                  BooleanSupplier debugEnabled, Consumer<Boolean> debugUpdate) {
        this.plugin = plugin;
        this.compatibility = compatibility;
        this.mainConfig = mainConfig;
        this.configRegistry = configRegistry;
        this.reservations = reservations;
        this.durableRepository = durableRepository;
        this.generation = generation;
        this.preparationCancel = preparationCancel;
        this.reloadBlocked = reloadBlocked;
        this.debugEnabled = debugEnabled;
        this.debugUpdate = debugUpdate;
        this.parties = PartyProviders.forServer(plugin.getServer());
    }

    @Subcommand("help")
    @CommandPermission("dungeoncrawlers.use")
    public void help(CommandSender sender) {
        DungeonMessages.send(sender, DungeonMessages.info("Commands. Click a line to fill in a command."));
        DungeonGenerationCommand.suggest(sender, "<green>Start a solo or party dungeon</green>",
                "/dungeon start floor_1");
        DungeonGenerationCommand.suggest(sender, "<green>Choose your class</green>", "/dungeon class list");
        DungeonGenerationCommand.suggest(sender, "<green>Check your dungeon location</green>", "/dungeon whereami");
        DungeonGenerationCommand.suggest(sender, "<green>Open completed rewards</green>", "/dungeon reward open ");
        if (sender.hasPermission("dungeoncrawlers.admin.generation")) {
            DungeonMessages.send(sender, DungeonMessages.info("Admin commands"));
            DungeonGenerationCommand.suggest(sender, "<yellow>List active instances</yellow>",
                    "/dungeon instance list");
            DungeonGenerationCommand.suggest(sender, "<yellow>Inspect an instance</yellow>",
                    "/dungeon instance info ");
        }
        if (sender.hasPermission("dungeoncrawlers.admin.reload")) {
            DungeonGenerationCommand.suggest(sender, "<yellow>Validate and reload configuration</yellow>",
                    "/dungeon config validate");
            DungeonGenerationCommand.suggest(sender, "<yellow>Reload the registry</yellow>", "/dungeon reload");
        }
        if (sender.hasPermission("dungeoncrawlers.admin.debug")) {
            DungeonMessages.send(sender, DungeonMessages.warning(
                    "Debug tools require config.yml debug: true."));
        }
    }

    @Subcommand("config validate")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void configValidate(CommandSender sender) {
        DungeonMessages.send(sender, DungeonMessages.info("Validating DungeonCrawlers configuration..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ConfigLoadResult result = configRegistry.validate();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                result.warnings().forEach(warning -> DungeonMessages.send(sender,
                        DungeonMessages.warning(warning)));
                result.errors().forEach(error -> DungeonMessages.send(sender,
                        DungeonMessages.error(error)));
                if (result.successful()) DungeonMessages.send(sender,
                        Component.text("Configuration is valid.", NamedTextColor.GREEN)
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Active configuration hash: " + result.snapshot().hash()))));
            });
        });
    }

    @Subcommand("operations")
    @CommandPermission("dungeoncrawlers.admin.reload")
    public void operations(CommandSender sender) {
        if (!debug(sender)) return;
        if (generation == null || durableRepository == null) {
            DungeonMessages.send(sender, DungeonMessages.error(
                    "Operations diagnostics are unavailable during bootstrap."));
            return;
        }
        GenerationService.OperationsSnapshot operations = generation.operations();
        var repository = durableRepository.diagnostics();
        DungeonMessages.send(sender, String.join("\n",
                "<aqua><bold>Operations</bold></aqua>",
                "<gray>Active instances: <white>" + operations.activeInstances() + "</white></gray>",
                "<gray>Reservations: <white>" + operations.activeReservations() + "</white></gray>",
                "<gray>Occupied slots: <white>" + operations.occupiedSlots() + "</white></gray>",
                "<gray>Recovery: <white>" + (operations.recoveryRunning() ? "Running" : "Idle")
                        + "</white> · Starts: <white>" + (operations.startsEnabled() ? "Enabled" : "Paused")
                        + "</white></gray>",
                "<gray>Recovery blockers: <white>" + (operations.recoveryBlockers() == 0
                        ? "None" : operations.recoveryBlockers()) + "</white></gray>",
                "<gray>Cleanup started: <white>" + operations.cleanupStarted() + "</white> · Completed: <white>"
                        + operations.cleanupCompleted() + "</white> · Failed: <white>" + operations.cleanupFailed()
                        + "</white></gray>",
                "<gray>Cleanup deadline alerts: <white>" + operations.cleanupDeadlineAlerts()
                        + "</white> · Late callbacks: <white>" + operations.lateCallbacks() + "</white></gray>",
                "<gray>Repository in flight: <white>" + repository.normalInFlight() + "</white> · Queued: <white>"
                        + repository.queuedOperations() + "</white> · Terminal reservations: <white>"
                        + repository.terminalReservations() + "</white> · Terminal in flight: <white>"
                        + repository.terminalInFlight() + "</white> · Closed: <white>" + repository.closed()
                        + "</white></gray>"));
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
            sendReloadMessage(sender, DungeonMessages.error("Force reload is unavailable during bootstrap."));
            return;
        }
        sendReloadMessage(sender, "<yellow>Force reload: cancelling all running dungeons before reloading...</yellow>");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (reloadIsBlocked(sender)) return;
            reservations.pauseAdmission();
            List<UUID> active = generation.instances().stream()
                    .filter(instance -> instance.status() != GenerationService.InstanceStatus.DESTROYED)
                    .map(GenerationService.InstanceSnapshot::instanceId).toList();
            active.forEach(instanceId -> {
                try {
                    preparationCancel.accept(instanceId);
                } catch (RuntimeException exception) {
                    sendReloadMessage(sender, "<yellow>Cleanup callback failed for <white>"
                            + instanceId + "</white>; continuing generation cancellation</yellow>");
                }
                try {
                    generation.cancel(instanceId);
                } catch (RuntimeException exception) {
                    sendReloadMessage(sender, "<yellow>Generation cancellation failed for <white>"
                            + instanceId + "</white>: " + exception.getMessage() + "</yellow>");
                }
            });
            sendReloadMessage(sender, "<yellow>Force reload requested for <white>" + active.size()
                    + "</white> dungeon(s); waiting for cleanup...</yellow>");
            awaitForceReload(sender, 0);
        });
    }

    private boolean reloadIsBlocked(CommandSender sender) {
        try {
            if (!reloadBlocked.getAsBoolean()) return false;
        } catch (RuntimeException exception) {
            sendReloadMessage(sender, "<red>Force reload safety check failed; no state was changed: "
                    + exception.getMessage() + "</red>");
            return true;
        }
        sendReloadMessage(sender, "<red>Force reload is refused while reward completion is pending; "
                + "wait for the reward period to close.</red>");
        return true;
    }

    private void awaitForceReload(CommandSender sender, int polls) {
        boolean instancesCleared = generation.instances().stream()
                .allMatch(instance -> instance.status() == GenerationService.InstanceStatus.DESTROYED);
        if (reservations.activeReservationCount() == 0 && instancesCleared) {
            reloadAsync(sender, reservations::resumeAdmission);
            return;
        }
        if (polls >= FORCE_RELOAD_MAX_POLLS) {
            sendReloadMessage(sender, "<red>Force reload timed out while waiting for dungeon cleanup; "
                    + "admission remains paused. Resolve the clearing instance and retry force reload.</red>");
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
                mainConfig.reload();
                boolean updatedDebug = configuredDebug();
                ConfigRegistryService.ReloadResult result = reservations.withAdmissionPaused(configRegistry::reload);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.swapped()) debugUpdate.accept(updatedDebug);
                    reportReload(sender, result);
                    if (completion != null) completion.run();
                });
            } catch (IOException | RuntimeException exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sendReloadMessage(sender, "<red>Reload failed: " + exception.getMessage() + "</red>");
                    if (completion != null) completion.run();
                });
            }
        });
    }

    private void reportReload(CommandSender sender, ConfigRegistryService.ReloadResult result) {
        result.warnings().forEach(warning -> sendReloadMessage(sender, "<yellow>" + warning + "</yellow>"));
        result.errors().forEach(error -> sendReloadMessage(sender, "<red>Reload failed: " + error + "</red>"));
        if (result.swapped()) {
            sendReloadMessage(sender, "<green>Configuration reload complete.</green>");
            DungeonMessages.send(sender, MiniMessageUtils.miniMessage("<gray>Floors: <white>" + result.snapshot().floors().size()
                    + "</white>, rooms: <white>" + result.snapshot().rooms().size()
                    + "</white>, boss encounters: <white>" + result.snapshot().encounters().size()
                    + "</white>, rewards: <white>" + result.snapshot().floors().values().stream()
                    .mapToInt(floor -> floor.rewards().size()).sum()
                    + "</white>, warnings: <white>" + result.warnings().size()
                    + "</white></gray>").hoverEvent(HoverEvent.showText(Component.text(
                    "Active configuration hash: " + result.snapshot().hash()))));
        } else if (result.errors().stream().anyMatch(error -> error.contains("reservation(s) are active"))) {
            sendReloadMessage(sender, "<red>Reload refused because a dungeon is active. Use "
                    + "<click:suggest_command:'/dungeon reload force'><aqua>/dungeon reload force</aqua></click>"
                    + " to cancel all running dungeons and reload.</red>");
        }
    }

    private static void sendReloadMessage(CommandSender sender, String miniMessage) {
        DungeonMessages.send(sender, miniMessage);
    }

    @Subcommand("floor info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void floorInfo(CommandSender sender, @SuggestWith(FloorIdSuggestionProvider.class) String id) {
        var value = configRegistry.snapshot().floors().get(id);
        if (value == null) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown floor: <white>" + id + "</white>"));
            return;
        }
        DungeonMessages.send(sender, DungeonMessages.success("Floor details"));
        sendField(sender, "id", value.id());
        sendField(sender, "number", value.number());
        sendField(sender, "display name", value.displayName());
        sendField(sender, "start template", value.templates().start());
        sendField(sender, "portal template", value.templates().portal());
        sendField(sender, "boss template", value.templates().boss());
        sendField(sender, "rooms", value.generation().rooms());
        sendField(sender, "minibosses", value.generation().minibosses());
        sendField(sender, "final miniboss", value.generation().finalMiniboss());
        sendField(sender, "normal mobs", String.join(", ", value.normalMobs()));
        sendField(sender, "miniboss mobs", String.join(", ", value.minibossMobs()));
        sendField(sender, "boss mob", value.bossMob());
        sendField(sender, "encounter", value.encounterId());
        sendField(sender, "allowed classes", String.join(", ", value.allowedClasses()));
        sendField(sender, "rewards", value.rewards().size());
    }

    @Subcommand("room info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void roomInfo(CommandSender sender, @SuggestWith(RoomIdSuggestionProvider.class) String id) {
        var value = configRegistry.snapshot().rooms().get(id);
        if (value == null) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown room: <white>" + id + "</white>"));
            return;
        }
        DungeonMessages.send(sender, DungeonMessages.success("Room details"));
        sendField(sender, "id", value.id());
        sendField(sender, "type", value.type().name().toLowerCase(Locale.ROOT));
        sendField(sender, "capabilities", value.capabilities().stream().map(Enum::name)
                .map(name -> name.toLowerCase(Locale.ROOT)).sorted().toList());
        sendField(sender, "minimum floor", value.minFloor());
        sendField(sender, "maximum floor", value.maxFloor() == null ? "none" : value.maxFloor());
        sendField(sender, "weight", value.weight());
    }

    @Subcommand("class info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void classInfo(CommandSender sender, String id) {
        var value = configRegistry.snapshot().classes().get(id);
        if (value == null) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown class: <white>" + id + "</white>"));
            return;
        }
        DungeonMessages.send(sender, DungeonMessages.success("Class details"));
        sendField(sender, "id", value.id());
        sendField(sender, "display name", value.displayName());
        sendField(sender, "icon", value.icon().name());
        sendField(sender, "additive stats", formatMap(value.stats().add()));
        sendField(sender, "multiplicative stats", formatMap(value.stats().multiply()));
    }

    @Subcommand("blessing info")
    @CommandPermission("dungeoncrawlers.admin.config")
    public void blessingInfo(CommandSender sender, String id) {
        var value = configRegistry.snapshot().blessings().get(id);
        if (value == null) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown blessing: <white>" + id + "</white>"));
            return;
        }
        DungeonMessages.send(sender, DungeonMessages.success("Blessing details"));
        sendField(sender, "id", value.id());
        sendField(sender, "display name", value.displayName());
        sendField(sender, "icon", value.icon().name());
        sendField(sender, "stacking", value.stacking().name().toLowerCase(Locale.ROOT));
        sendField(sender, "maximum level", value.maxLevel());
        sendField(sender, "level range", value.levelRange().getMin() + "-" + value.levelRange().getMax());
        sendField(sender, "additive stats", formatMap(value.perLevel().add()));
        sendField(sender, "multiplicative stats", formatMap(value.perLevel().multiply()));
    }

    @Subcommand("state simulate")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void stateSimulate(CommandSender sender, String from, String to) {
        if (!debug(sender)) return;
        try {
            var result = transitions.transition(InstanceState.valueOf(from.toUpperCase(Locale.ROOT)),
                    InstanceState.valueOf(to.toUpperCase(Locale.ROOT)));
            DungeonMessages.send(sender, result.accepted()
                    ? DungeonMessages.success(readableDetail(result.detail())) : DungeonMessages.error(readableDetail(result.detail())));
        } catch (IllegalArgumentException exception) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown state. Valid values: "
                    + enumNames(InstanceState.values())));
        }
    }

    @Subcommand("score simulate")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void scoreSimulate(CommandSender sender, boolean successful, int deaths, long elapsedMinutes,
                              int foundSecrets, int totalSecrets) {
        if (!debug(sender)) return;
        try {
            var report = scores.calculateReport(new ScoreService.ScoreInput(successful, deaths,
                    Duration.ofMinutes(elapsedMinutes), foundSecrets, totalSecrets), List.of());
            DungeonMessages.send(sender, ScoreResultRenderer.render(report));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(exception.getMessage()));
        }
    }

    @Subcommand("repository")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void repository(CommandSender sender) {
        if (!debug(sender)) return;
        var diagnostics = durableRepository.diagnostics();
        DungeonMessages.send(sender, DungeonMessages.info("Repository diagnostics"));
        DungeonMessages.send(sender, String.join("\n",
                "<gray>Capacity: <white>" + diagnostics.normalCapacity() + "</white></gray>",
                "<gray>In flight: <white>" + diagnostics.normalInFlight() + "</white></gray>",
                "<gray>Queued: <white>" + diagnostics.queuedOperations() + "</white></gray>",
                "<gray>Terminal reservations: <white>" + diagnostics.terminalReservations() + "</white></gray>",
                "<gray>Terminal in flight: <white>" + diagnostics.terminalInFlight() + "</white></gray>",
                "<gray>Closed: <white>" + diagnostics.closed() + "</white></gray>"));
    }

    @Subcommand("reservation race")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void reservationRace(CommandSender sender) {
        if (!debug(sender)) return;
        DungeonMessages.send(sender, DungeonMessages.info("Running isolated reservation race..."));
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
                DungeonMessages.send(sender, error == null && wins == 1
                        ? DungeonMessages.success("Exactly one reservation won.")
                        : DungeonMessages.error("Reservation race failed: "
                        + (error == null ? "expected one winner, observed " + wins : error.getMessage())))));
    }

    @Subcommand("compatibility")
    @CommandPermission("dungeoncrawlers.admin.compatibility")
    public void compatibility(CommandSender sender) {
        CompatibilityReport report = compatibility.inspect();
        DungeonMessages.send(sender, DungeonMessages.info("Compatibility report"));
        DungeonMessages.send(sender, "<gray>Created: <white>" + report.createdAt() + "</white></gray>");
        for (ProbeResult result : report.results()) {
            String color = switch (result.status()) {
                case PASS, FALLBACK_PASS -> "green";
                case MANUAL_REQUIRED, ABSENT -> "yellow";
                case FAIL -> "red";
            };
            DungeonMessages.send(sender, "<" + color + ">" + result.id() + "</" + color + ">: <gray>"
                    + readableDetail(result.detail()) + "</gray>");
        }
        DungeonMessages.send(sender, "<gray>Automated checks: <white>" + (report.passesAutomatedChecks()
                ? "passed" : "failed") + "</white>; Human Gate 0: <white>"
                + (report.passesHumanGate() ? "passed" : "blocked") + "</white></gray>");
    }

    @Subcommand("compatibility item")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void item(CommandSender sender, String itemId) {
        if (!debug(sender)) return;
        ItemStack built = caveItems.build(itemId, 1).orElse(null);
        if (built == null) {
            DungeonMessages.send(sender, DungeonMessages.error(
                    "CaveCrawlers item is not configured or buildable: <white>" + itemId + "</white>"));
            return;
        }
        byte[] payload = built.serializeAsBytes();
        ItemStack restored = ItemStack.deserializeBytes(payload);
        DungeonMessages.send(sender, built.equals(restored)
                ? DungeonMessages.success("Item serialization passed. Item: <white>" + itemId
                        + "</white> · Bytes: <white>" + payload.length + "</white> · SHA-256: <white>"
                        + sha256(payload) + "</white>.")
                : DungeonMessages.error("Item serialization failed for <white>" + itemId + "</white>."));
    }

    @Subcommand("compatibility mythic")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mythic(Player player, String mobId) {
        if (!debug(player)) return;
        MythicMobGateway.SpawnResult spawned = mythic.spawn(mobId, player.getLocation(), 1);
        if (!spawned.successful()) {
            DungeonMessages.send(player, DungeonMessages.error(spawned.detail()));
            return;
        }
        Entity entity = spawned.entity();
        boolean identified = mythic.isMythicMob(entity);
        boolean removed = mythic.remove(entity);
        DungeonMessages.send(player, identified && removed
                ? DungeonMessages.success("Mythic mob probe passed. Identified: <white>" + identified
                        + "</white> · Removed: <white>" + removed + "</white>.")
                : DungeonMessages.error("Mythic mob probe failed. Identified: <white>" + identified
                        + "</white> · Removed: <white>" + removed + "</white>."));
    }

    @Subcommand("compatibility selection")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void selection(Player player) {
        if (!debug(player)) return;
        WorldEditGateway.SelectionResult result = worldEdit.selection(player);
        DungeonMessages.send(player, result.successful()
                ? DungeonMessages.success(readableDetail(result.detail()))
                : DungeonMessages.error(readableDetail(result.detail())));
    }

    @Subcommand("compatibility party")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void party(Player player) {
        if (!debug(player)) return;
        PartyProvider.PartyLookup result = parties.lookup(player.getUniqueId());
        String status = result.status() == PartyProvider.Status.ERROR
                ? "<red>Party lookup failed.</red>"
                : "<green>Party lookup passed.</green>";
        DungeonMessages.send(player, String.join("\n", status,
                "<gray>Status: <white>" + result.status().name().toLowerCase(Locale.ROOT) + "</white></gray>",
                "<gray>Leader: <white>" + result.leader() + "</white></gray>",
                "<gray>Online members: <white>" + result.onlineMembers() + "</white></gray>",
                "<gray>Details: <white>" + readableDetail(result.detail()) + "</white></gray>"));
    }

    @Subcommand("compatibility economy")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void economy(Player player, double amount) {
        if (!debug(player)) return;
        if (!Double.isFinite(amount) || amount <= 0) {
            DungeonMessages.send(player, DungeonMessages.error("Amount must be finite and positive."));
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            DungeonMessages.send(player, DungeonMessages.error("No Vault economy provider is available."));
            return;
        }
        String configuredAccount = mainConfig.getString("compatibility.economy-test-account-uuid", "").trim();
        UUID accountId;
        try {
            accountId = UUID.fromString(configuredAccount);
        } catch (IllegalArgumentException exception) {
            DungeonMessages.send(player, DungeonMessages.error(
                    "compatibility.economy-test-account-uuid must be a valid UUID."));
            return;
        }
        if (accountId.equals(player.getUniqueId())) {
            DungeonMessages.send(player, DungeonMessages.error(
                    "The disposable economy test account must not be the command sender."));
            return;
        }
        OfflinePlayer testAccount = plugin.getServer().getOfflinePlayer(accountId);
        if (!testAccount.hasPlayedBefore() && !testAccount.isOnline()) {
            DungeonMessages.send(player, DungeonMessages.error(
                    "The configured disposable economy test account has never joined this server."));
            return;
        }
        EconomyGateway gateway = new VaultEconomyAdapter(registration.getProvider());
        EconomyGateway.TransactionResult debit = gateway.withdraw(testAccount, amount);
        if (!debit.successful()) {
            DungeonMessages.send(player, DungeonMessages.error("Withdraw failed via <white>"
                    + gateway.providerIdentity() + "</white>: " + readableDetail(debit.detail())));
            return;
        }
        EconomyGateway.TransactionResult credit = gateway.deposit(testAccount, debit.amount());
        if (!credit.successful()) {
            EconomyGateway.TransactionResult recovery = recoverEconomyProbe(gateway, testAccount, debit.amount());
            DungeonMessages.send(player, DungeonMessages.error("Deposit failed via <white>"
                    + gateway.providerIdentity() + "</white>. Recovery: <white>"
                    + (recovery.successful() ? "Restored" : "Failed") + "</white>."));
            if (!recovery.successful()) {
                plugin.getLogger().severe("Economy probe recovery failed for disposable account " + accountId
                        + "; manually restore " + debit.amount() + " using provider " + gateway.providerIdentity());
            }
            return;
        }
        DungeonMessages.send(player, DungeonMessages.success("Economy round-trip passed via <white>"
                + gateway.providerIdentity() + "</white>. Account: <white>" + accountId
                + "</white> · Balance: <white>" + credit.balance() + "</white>."));
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
        DungeonMessages.send(sender, provider.spawn()
                .map(location -> DungeonMessages.success("Spawn provider: <white>" + provider.source()
                        + "</white> · World: <white>" + location.getWorld().getName() + "</white>."))
                .orElseGet(() -> DungeonMessages.error("Configured Bukkit fallback world is not loaded.")));
    }

    @Subcommand("compatibility actionbar")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void actionbar(Player player) {
        if (!debug(player)) return;
        new CaveActionBarAdapter().show(player, MiniMessageUtils.miniMessage(
                "<aqua>DungeonCrawlers action-bar probe</aqua>"));
        DungeonMessages.send(player, DungeonMessages.info(
                "Confirm the action bar is visible and restores its previous value after one second."));
    }

    @Subcommand("compatibility stats")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void stats(Player player) {
        if (!debug(player)) return;
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            DungeonMessages.send(player, DungeonMessages.error("Paper MAX_HEALTH attribute is unavailable."));
            return;
        }
        DungeonMessages.send(player, DungeonMessages.success("Paper MAX_HEALTH available: <white>"
                + maxHealth.getBaseValue() + "</white> · DungeonCrawlers health cap: <white>unbounded"
                + "</white> · CaveCrawlers version: <white>"
                + CaveCrawlers.getInstance().getPluginMeta().getVersion() + "</white>"));
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean debug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This administrative test command is unavailable while debug mode is disabled."));
        return false;
    }

    private boolean configuredDebug() {
        Object value = mainConfig.get("debug");
        if (value instanceof Boolean enabled) return enabled;
        throw new IllegalStateException("config.yml debug must be true or false");
    }

    private static void sendField(CommandSender sender, String name, Object value) {
        String rendered = value instanceof java.util.Collection<?> collection
                ? collection.stream().map(String::valueOf).sorted().collect(java.util.stream.Collectors.joining(", "))
                : String.valueOf(value);
        DungeonMessages.send(sender, "<gray>" + name + ": <white>" + rendered + "</white></gray>");
    }

    private static String formatMap(java.util.Map<?, ?> values) {
        if (values.isEmpty()) return "none";
        return values.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String readableDetail(String detail) {
        if (detail == null || detail.isBlank()) return "No additional details.";
        return detail.replace("=", ": ").replace("; ", " · ");
    }

    private static String enumNames(InstanceState[] values) {
        return java.util.Arrays.stream(values).map(value -> value.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(", "));
    }

}
