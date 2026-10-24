package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.DungeonTimings;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Bukkit boundary for active portal participants and their portal messages. */
public final class BukkitPortalParticipantGateway implements PortalEncounterService.ParticipantGateway {
    private final Server server;
    private final Supplier<World> generationWorld;
    private final String generationWorldName;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final CombatRoomService combat;
    private final TeleportPermitService teleportPermits;
    private final Clock clock;
    private final Duration teleportPermitDuration;

    public BukkitPortalParticipantGateway(Server server, Supplier<World> generationWorld,
                                         String generationWorldName, RunPreparationService runs,
                                         PlayerLifecycleService lifecycle,
                                         CombatRoomService combat,
                                         TeleportPermitService teleportPermits, Clock clock) {
        this(server, generationWorld, generationWorldName, runs, lifecycle, combat, teleportPermits, clock,
                DungeonTimings.defaults().teleportPermit());
    }

    public BukkitPortalParticipantGateway(Server server, Supplier<World> generationWorld,
                                         String generationWorldName, RunPreparationService runs,
                                         PlayerLifecycleService lifecycle,
                                         CombatRoomService combat,
                                         TeleportPermitService teleportPermits, Clock clock,
                                         Duration teleportPermitDuration) {
        this.server = Objects.requireNonNull(server, "server");
        this.generationWorld = Objects.requireNonNull(generationWorld, "generationWorld");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.teleportPermits = Objects.requireNonNull(teleportPermits, "teleportPermits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.teleportPermitDuration = Objects.requireNonNull(teleportPermitDuration, "teleportPermitDuration");
        if (teleportPermitDuration.isZero() || teleportPermitDuration.isNegative()) {
            throw new IllegalArgumentException("teleport permit duration must be positive");
        }
    }

    @Override
    public List<UUID> activePlayers(UUID instanceId) {
        return runs.info(instanceId).map(snapshot -> snapshot.participants().stream()
                .filter(playerId -> server.getPlayer(playerId) != null)
                .filter(playerId -> lifecycle.player(instanceId, playerId)
                        .map(value -> value.state() == PlayerLifecycleService.PlayerState.ALIVE)
                        .orElse(true))
                .sorted().toList()).orElse(List.of());
    }

    @Override
    public boolean portalDoorOpened(UUID instanceId) { return combat.portalDoorOpened(instanceId); }

    @Override
    public void killParty(UUID instanceId, String reason) {
        var result = lifecycle.killParty(instanceId, reason);
        if (!result.successful()) throw new IllegalStateException(result.detail());
    }

    @Override
    public boolean teleport(UUID playerId, Point target) {
        Player player = server.getPlayer(playerId);
        if (player == null) return false;
        World world;
        try {
            world = generationWorld.get();
        } catch (RuntimeException exception) {
            return false;
        }
        if (world == null) return false;
        teleportPermits.authorize(playerId, Set.of(new TeleportPermitService.Destination(
                        generationWorldName, new Point(target.x(), target.y() + 1, target.z()))),
                clock.instant().plus(teleportPermitDuration));
        return player.teleport(new Location(world, target.x() + 0.5,
                target.y() + 1.0, target.z() + 0.5));
    }

    @Override
    public String displayName(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player != null && !player.getName().isBlank()) return player.getName();
        String name = server.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? playerId.toString() : name;
    }

    @Override
    public void notice(UUID instanceId, String miniMessage) {
        runs.info(instanceId).ifPresent(snapshot -> snapshot.participants().stream()
                .map(server::getPlayer).filter(Objects::nonNull)
                .forEach(player -> DungeonMessages.send(player, miniMessage)));
    }

    @Override
    public void title(UUID instanceId, String miniMessageTitle, String miniMessageSubtitle) {
        runs.info(instanceId).ifPresent(snapshot -> snapshot.participants().stream()
                .map(server::getPlayer).filter(Objects::nonNull)
                .forEach(player -> player.showTitle(Title.title(
                        MiniMessageUtils.miniMessage(miniMessageTitle),
                        MiniMessageUtils.miniMessage(miniMessageSubtitle), 0, 20, 5))));
    }
}
