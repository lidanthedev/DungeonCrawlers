package me.lidan.dungeonCrawlers.core.protection;

import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure authorization policy for instance geometry and player movement. */
public final class WorldProtectionService {
    public Decision canModifyGeometry(String world, Point point, List<InstanceRegion> regions) {
        return regionAt(world, point, regions).isPresent()
                ? Decision.deny("instance geometry is protected") : Decision.allow();
    }

    public Decision canEnter(UUID playerId, String world, Point target, List<InstanceRegion> regions) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<InstanceRegion> region = regionAt(world, target, regions);
        if (region.isEmpty()) return Decision.allow();
        return region.get().participants().contains(playerId)
                ? Decision.allow() : Decision.deny("player is not a participant in this instance");
    }

    public Decision canDamage(UUID attackerId, String attackerWorld, Point attackerPoint,
                              UUID victimId, String victimWorld, Point victimPoint,
                              List<InstanceRegion> regions) {
        Optional<InstanceRegion> attacker = regionAt(attackerWorld, attackerPoint, regions);
        Optional<InstanceRegion> victim = regionAt(victimWorld, victimPoint, regions);
        if (attacker.isEmpty() && victim.isEmpty()) return Decision.allow();
        if (attacker.isEmpty() || victim.isEmpty()) return Decision.deny("cross-instance damage is blocked");
        if (!attacker.get().instanceId().equals(victim.get().instanceId())) {
            return Decision.deny("cross-instance damage is blocked");
        }
        return Decision.deny("player-versus-player damage is blocked in dungeon instances");
    }

    public Decision canTeleport(UUID playerId, String fromWorld, Point from, String targetWorld, Point target,
                                boolean pluginAuthorized, List<InstanceRegion> regions) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<InstanceRegion> source = regionAt(fromWorld, from, regions);
        Optional<InstanceRegion> destination = regionAt(targetWorld, target, regions);
        if (destination.isPresent() && !destination.get().participants().contains(playerId)) {
            return Decision.deny("unauthorized teleport into a dungeon instance");
        }
        if (pluginAuthorized) return Decision.allow();
        if (source.isPresent() && (destination.isEmpty()
                || !source.get().instanceId().equals(destination.get().instanceId()))) {
            return Decision.deny("teleport out of a dungeon instance requires plugin authorization");
        }
        if (source.isPresent() && destination.isPresent()
                && !source.get().instanceId().equals(destination.get().instanceId())) {
            return Decision.deny("cross-instance teleport is blocked");
        }
        return Decision.allow();
    }

    public Optional<InstanceRegion> regionAt(String world, Point point, List<InstanceRegion> regions) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(regions, "regions");
        return regions.stream().filter(region -> region.world().equals(world) && region.bounds().contains(point))
                .sorted(Comparator.comparing(InstanceRegion::instanceId)).findFirst();
    }

    public record InstanceRegion(String world, UUID instanceId, Bounds bounds, Set<UUID> participants) {
        public InstanceRegion {
            Objects.requireNonNull(world); Objects.requireNonNull(instanceId); Objects.requireNonNull(bounds);
            if (world.isBlank()) throw new IllegalArgumentException("world must not be blank");
            participants = Set.copyOf(participants);
        }

        public static InstanceRegion from(GenerationService.InstanceRegion region) {
            return new InstanceRegion(region.world(), region.instanceId(), region.bounds(), region.participants());
        }
    }

    public record Decision(boolean allowed, String reason) {
        public Decision {
            Objects.requireNonNull(reason);
            if (allowed && !reason.equals("allowed")) throw new IllegalArgumentException("allowed reason mismatch");
        }

        private static Decision allow() { return new Decision(true, "allowed"); }
        private static Decision deny(String reason) { return new Decision(false, reason); }
    }
}
