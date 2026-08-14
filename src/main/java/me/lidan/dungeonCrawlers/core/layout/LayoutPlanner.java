package me.lidan.dungeonCrawlers.core.layout;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.random.NamedRandomFactory;
import me.lidan.dungeonCrawlers.core.random.WeightedChooser;
import me.lidan.dungeonCrawlers.core.template.TemplateModels;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

public final class LayoutPlanner {
    public static final String ALGORITHM_VERSION = "phase2-v1";

    public PlanResult plan(PlanRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> trace = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        validateCatalog(request, errors);
        if (!errors.isEmpty()) return new PlanResult(Optional.empty(), errors, trace);

        NamedRandomFactory randomFactory = new NamedRandomFactory(request.seed());
        SplittableRandom layoutRandom = randomFactory.stream("layout");
        SplittableRandom choiceRandom = randomFactory.stream("room-choice");
        List<EncounterCapability> composition = composition(request.floor(), layoutRandom);
        trace.add("version=" + ALGORITHM_VERSION + " seed=" + request.seed() + " config=" + request.configHash()
                + " content=" + contentHash(request.catalog()));
        trace.add("composition=" + composition);

        List<Placement> placements = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        CatalogEntry start = request.catalog().get(request.floor().templates().start());
        Placement startPlacement = placement(request.instanceId(), 0, start.template(), Rotation.NONE,
                request.slotOrigin(), null);
        String startError = validatePlacement(startPlacement, placements, request.slotBounds(),
                request.floor().generation().collisionPadding(), Set.of(), -1);
        if (startError != null) return failed(trace, "START " + start.template().id() + ": " + startError);
        placements.add(startPlacement);
        trace.add("place[0] START " + start.template().id() + " origin=" + startPlacement.origin());

        String previousCombatId = null;
        int generatedIndex = 1;
        for (int position = 0; position < composition.size(); position++) {
            EncounterCapability capability = composition.get(position);
            List<CatalogEntry> candidates = candidates(request, capability, previousCombatId);
            if (candidates.isEmpty()) {
                return failed(trace, "position " + position + " has no " + capability + "-capable template");
            }
            Placement chosen = null;
            String lastFailure = "no candidate attempted";
            List<CatalogEntry> remaining = new ArrayList<>(candidates);
            for (int attempt = 1; attempt <= request.floor().generation().maxAttemptsPerPosition(); attempt++) {
                if (remaining.isEmpty()) remaining.addAll(candidates);
                CatalogEntry candidate = chooseAndRemove(remaining, choiceRandom);
                Placement connected = connect(request.instanceId(), generatedIndex, placements.getLast(),
                        candidate.template(), capability);
                Connection interfaceBounds = connection(placements.getLast(), connected);
                if (!interfaceBounds.valid()) {
                    lastFailure = interfaceBounds.detail();
                } else {
                    lastFailure = validatePlacement(connected, placements, request.slotBounds(),
                            request.floor().generation().collisionPadding(), interfaceBounds.bounds(),
                            interfaceBounds.fromIndex());
                }
                trace.add("try[" + position + ":" + attempt + "] " + candidate.template().id()
                        + (lastFailure == null ? " accepted" : " rejected=" + lastFailure));
                if (lastFailure == null) {
                    chosen = connected;
                    connections.add(interfaceBounds);
                    break;
                }
            }
            if (chosen == null) return failed(trace, "position " + position + " exhausted attempts: " + lastFailure);
            placements.add(chosen);
            previousCombatId = chosen.templateId();
            generatedIndex++;
        }

        CatalogEntry portal = request.catalog().get(request.floor().templates().portal());
        Placement portalPlacement = connect(request.instanceId(), generatedIndex, placements.getLast(),
                portal.template(), null);
        Connection portalConnection = connection(placements.getLast(), portalPlacement);
        String portalError = portalConnection.valid() ? validatePlacement(portalPlacement, placements,
                request.slotBounds(), request.floor().generation().collisionPadding(), portalConnection.bounds(),
                portalConnection.fromIndex())
                : portalConnection.detail();
        if (portalError != null) return failed(trace, "PORTAL " + portal.template().id() + ": " + portalError);
        placements.add(portalPlacement);
        connections.add(portalConnection);
        trace.add("place[" + generatedIndex + "] PORTAL " + portal.template().id()
                + " origin=" + portalPlacement.origin());
        generatedIndex++;

        CatalogEntry boss = request.catalog().get(request.floor().templates().boss());
        var offset = request.floor().templates().bossOffset();
        Point bossOrigin = request.slotOrigin().add(new Point(offset.x(), offset.y(), offset.z()));
        Placement bossPlacement = placement(request.instanceId(), generatedIndex, boss.template(), Rotation.NONE,
                bossOrigin, null);
        String bossError = validatePlacement(bossPlacement, placements, request.slotBounds(),
                request.floor().generation().collisionPadding(), Set.of(), -1);
        if (bossError != null) return failed(trace, "BOSS " + boss.template().id() + ": " + bossError);
        placements.add(bossPlacement);
        trace.add("place[" + generatedIndex + "] BOSS " + boss.template().id() + " origin=" + bossOrigin);

        LayoutPlan plan = new LayoutPlan(ALGORITHM_VERSION, request.instanceId(), request.seed(), request.configHash(),
                contentHash(request.catalog()), placements, connections, trace);
        return new PlanResult(Optional.of(plan), List.of(), trace);
    }

    public ConnectionTest connectTest(Template from, Rotation fromRotation, Point fromOrigin, Template to) {
        Objects.requireNonNull(from); Objects.requireNonNull(fromRotation); Objects.requireNonNull(fromOrigin);
        Objects.requireNonNull(to);
        Placement first = placement(new UUID(0, 0), 0, from, fromRotation, fromOrigin, null);
        if (first.exit().isEmpty()) return new ConnectionTest(Optional.empty(), Optional.empty(), "source has no exit");
        if (to.entrance().isEmpty()) return new ConnectionTest(Optional.empty(), Optional.empty(), "target has no entrance");
        Placement second = connect(new UUID(0, 0), 1, first, to, null);
        Connection connection = connection(first, second);
        return connection.valid() ? new ConnectionTest(Optional.of(second), Optional.of(connection), "connected")
                : new ConnectionTest(Optional.empty(), Optional.of(connection), connection.detail());
    }

    private static void validateCatalog(PlanRequest request, List<String> errors) {
        requireSpecial(request, request.floor().templates().start(), RoomType.START, "START", errors);
        requireSpecial(request, request.floor().templates().portal(), RoomType.PORTAL, "PORTAL", errors);
        requireSpecial(request, request.floor().templates().boss(), RoomType.BOSS, "BOSS", errors);
        for (CatalogEntry entry : request.catalog().values()) {
            if (!entry.definition().id().equals(entry.template().id())) {
                errors.add("catalog key metadata mismatch for " + entry.definition().id());
            }
            if (entry.definition().type() != entry.template().type()) {
                errors.add("room/template type mismatch for " + entry.definition().id());
            }
            if (!entry.definition().capabilities().equals(entry.template().capabilities())) {
                errors.add("room/template capabilities mismatch for " + entry.definition().id());
            }
        }
    }

    private static void requireSpecial(PlanRequest request, String id, RoomType type, String label,
                                       List<String> errors) {
        CatalogEntry entry = request.catalog().get(id);
        if (entry == null) errors.add(label + " template is missing: " + id);
        else if (entry.template().type() != type) errors.add(label + " template " + id + " must be " + type);
    }

    private static List<EncounterCapability> composition(FloorDefinition floor, SplittableRandom random) {
        int roomCount = floor.generation().rooms();
        List<Integer> positions = new ArrayList<>(roomCount);
        for (int index = 0; index < roomCount; index++) positions.add(index);
        for (int index = positions.size() - 1; index > 0; index--) {
            int selected = random.nextInt(index + 1);
            Integer value = positions.get(index);
            positions.set(index, positions.get(selected));
            positions.set(selected, value);
        }
        Set<Integer> minibosses = Set.copyOf(positions.subList(0, floor.generation().minibosses()));
        List<EncounterCapability> result = new ArrayList<>(roomCount + (floor.generation().finalMiniboss() ? 1 : 0));
        for (int index = 0; index < roomCount; index++) {
            result.add(minibosses.contains(index) ? EncounterCapability.MINIBOSS : EncounterCapability.NORMAL);
        }
        if (floor.generation().finalMiniboss()) result.add(EncounterCapability.MINIBOSS);
        return List.copyOf(result);
    }

    private static List<CatalogEntry> candidates(PlanRequest request, EncounterCapability capability,
                                                  String previousId) {
        List<CatalogEntry> all = request.catalog().values().stream()
                .filter(entry -> entry.template().type() == RoomType.NORMAL)
                .filter(entry -> entry.template().capabilities().contains(capability))
                .filter(entry -> supportsFloor(entry.definition(), request.floor().number()))
                .sorted(Comparator.comparing(entry -> entry.template().id())).toList();
        if (all.size() < 2 || previousId == null) return all;
        List<CatalogEntry> withoutRepeat = all.stream()
                .filter(entry -> !entry.template().id().equals(previousId)).toList();
        return withoutRepeat.isEmpty() ? all : withoutRepeat;
    }

    private static boolean supportsFloor(RoomDefinition definition, int floor) {
        return definition.minFloor() <= floor && (definition.maxFloor() == null || definition.maxFloor() >= floor);
    }

    private static CatalogEntry chooseAndRemove(List<CatalogEntry> candidates, SplittableRandom random) {
        List<WeightedChooser.Weighted<CatalogEntry>> weighted = candidates.stream()
                .map(entry -> new WeightedChooser.Weighted<>(entry, entry.definition().weight())).toList();
        CatalogEntry selected = WeightedChooser.choose(weighted, random);
        candidates.remove(selected);
        return selected;
    }

    private static Placement connect(UUID instanceId, int index, Placement previous, Template next,
                                     EncounterCapability encounter) {
        Connector exit = previous.exit().orElseThrow(() -> new IllegalArgumentException("previous placement has no exit"));
        Connector entrance = next.entrance().orElseThrow(() -> new IllegalArgumentException("next template has no entrance"));
        Rotation rotation = Rotation.mapping(entrance.outward(), exit.outward().opposite());
        Point target = exit.point().add(exit.outward().vector());
        Point origin = target.subtract(rotation.apply(entrance.point()));
        return placement(instanceId, index, next, rotation, origin, encounter);
    }

    private static Placement placement(UUID instanceId, int index, Template template, Rotation rotation,
                                       Point origin, EncounterCapability encounter) {
        Bounds bounds = template.bounds().rotate(rotation).translate(origin);
        Optional<Connector> entrance = template.entrance().map(value -> value.transform(rotation, origin));
        Optional<Connector> exit = template.exit().map(value -> value.transform(rotation, origin));
        Set<Point> solids = transform(template.solidBlocks(), rotation, origin);
        if (exit.isPresent()) solids = union(solids, TemplateModels.plane(exit.orElseThrow().point(), exit.orElseThrow().outward()));
        Set<Point> reserved = new LinkedHashSet<>();
        entrance.ifPresent(value -> {
            reserved.addAll(TemplateModels.plane(value.point(), value.outward()));
            reserved.addAll(TemplateModels.plane(value.point().add(value.outward().vector()), value.outward()));
        });
        exit.ifPresent(value -> {
            reserved.addAll(TemplateModels.plane(value.point(), value.outward()));
            reserved.addAll(TemplateModels.plane(value.point().add(value.outward().vector()), value.outward()));
        });
        List<PlacedSecret> secrets = template.secrets().stream()
                .map(secret -> new PlacedSecret(new SecretId(instanceId, index, secret.point()),
                        rotation.apply(secret.point()).add(origin), secret.kind())).toList();
        return new Placement(index, template.id(), template.type(), encounter, rotation, origin, bounds,
                entrance, exit, solids, Set.copyOf(reserved), transformList(template.normalMobs(), rotation, origin),
                transformList(template.minibossMobs(), rotation, origin),
                transformList(template.playerSpawns(), rotation, origin),
                template.bossSpawn().map(point -> rotation.apply(point).add(origin)),
                template.rewardChest().map(point -> rotation.apply(point).add(origin)),
                transform(template.portalBlocks(), rotation, origin), secrets);
    }

    private static Set<Point> transform(Set<Point> points, Rotation rotation, Point origin) {
        Set<Point> result = new LinkedHashSet<>();
        for (Point point : points) result.add(rotation.apply(point).add(origin));
        return Set.copyOf(result);
    }

    private static List<Point> transformList(List<Point> points, Rotation rotation, Point origin) {
        return points.stream().map(point -> rotation.apply(point).add(origin)).sorted().toList();
    }

    private static Set<Point> union(Set<Point> first, Set<Point> second) {
        Set<Point> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static Connection connection(Placement first, Placement second) {
        if (first.exit().isEmpty() || second.entrance().isEmpty()) {
            return new Connection(first.index(), second.index(), Set.of(), Set.of(), Set.of(), false,
                    "connection endpoints are missing");
        }
        Connector exit = first.exit().orElseThrow();
        Connector entrance = second.entrance().orElseThrow();
        Set<Point> door = TemplateModels.plane(exit.point(), exit.outward());
        Set<Point> exitClearance = TemplateModels.plane(exit.point().add(exit.outward().vector()), exit.outward());
        Set<Point> entrancePlane = TemplateModels.plane(entrance.point(), entrance.outward());
        Set<Point> entranceClearance = TemplateModels.plane(entrance.point().add(entrance.outward().vector()),
                entrance.outward());
        boolean valid = exit.outward() == entrance.outward().opposite()
                && exitClearance.equals(entrancePlane) && entranceClearance.equals(door)
                && java.util.Collections.disjoint(door, entrancePlane);
        Set<Point> bounds = union(door, entrancePlane);
        return new Connection(first.index(), second.index(), door, entrancePlane, bounds, valid,
                valid ? "connected" : "two-plane connector equality failed");
    }

    private static String validatePlacement(Placement candidate, List<Placement> existing, Bounds slot,
                                            int padding, Set<Point> allowedInterface, int connectedPlacementIndex) {
        if (!slot.contains(candidate.bounds().minimum()) || !slot.contains(candidate.bounds().maximum())) {
            return "bounds leave slot: " + candidate.bounds();
        }
        if (!candidate.reservedConnectorCells().stream().allMatch(slot::contains)) {
            return "connector clearance leaves slot";
        }
        Set<Point> candidateOccupied = union(candidate.solidBlocks(), candidate.reservedConnectorCells());
        for (Placement placed : existing) {
            if (candidate.bounds().intersects(placed.bounds())) {
                return "bounds collision with placement " + placed.index();
            }
            int effectivePadding = placed.index() == connectedPlacementIndex ? 0 : padding;
            if (!candidate.bounds().expand(effectivePadding + 1)
                    .intersects(placed.bounds().expand(effectivePadding + 1))) continue;
            Set<Point> placedOccupied = union(placed.solidBlocks(), placed.reservedConnectorCells());
            for (Point point : candidateOccupied) {
                for (int x = -effectivePadding; x <= effectivePadding; x++) {
                    for (int y = -effectivePadding; y <= effectivePadding; y++) {
                        for (int z = -effectivePadding; z <= effectivePadding; z++) {
                            Point other = point.add(new Point(x, y, z));
                            if (!placedOccupied.contains(other)) continue;
                            if (allowedInterface.contains(point) && allowedInterface.contains(other)) continue;
                            return "collision with placement " + placed.index() + " near " + point;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static PlanResult failed(List<String> trace, String error) {
        trace.add("FAIL " + error);
        return new PlanResult(Optional.empty(), List.of(error), trace);
    }

    private static String contentHash(Map<String, CatalogEntry> catalog) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            catalog.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> digest.update(
                    (entry.getKey() + "=" + entry.getValue().template().contentHash() + "\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CatalogEntry(RoomDefinition definition, Template template) {
        public CatalogEntry { Objects.requireNonNull(definition); Objects.requireNonNull(template); }
    }

    public record PlanRequest(UUID instanceId, long seed, FloorDefinition floor, Map<String, CatalogEntry> catalog,
                              Point slotOrigin, Bounds slotBounds, String configHash) {
        public PlanRequest {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(floor); catalog = Map.copyOf(catalog);
            Objects.requireNonNull(slotOrigin); Objects.requireNonNull(slotBounds); Objects.requireNonNull(configHash);
        }
    }

    public record Placement(int index, String templateId, RoomType type, EncounterCapability encounter,
                            Rotation rotation, Point origin, Bounds bounds, Optional<Connector> entrance,
                            Optional<Connector> exit, Set<Point> solidBlocks, Set<Point> reservedConnectorCells,
                            List<Point> normalMobs, List<Point> minibossMobs, List<Point> playerSpawns,
                            Optional<Point> bossSpawn, Optional<Point> rewardChest, Set<Point> portalBlocks,
                            List<PlacedSecret> secrets) {
        public Placement {
            Objects.requireNonNull(templateId); Objects.requireNonNull(type); Objects.requireNonNull(rotation);
            Objects.requireNonNull(origin); Objects.requireNonNull(bounds); Objects.requireNonNull(entrance);
            Objects.requireNonNull(exit); solidBlocks = Set.copyOf(solidBlocks);
            reservedConnectorCells = Set.copyOf(reservedConnectorCells); normalMobs = List.copyOf(normalMobs);
            minibossMobs = List.copyOf(minibossMobs); playerSpawns = List.copyOf(playerSpawns);
            Objects.requireNonNull(bossSpawn); Objects.requireNonNull(rewardChest);
            portalBlocks = Set.copyOf(portalBlocks); secrets = List.copyOf(secrets);
        }
    }

    public record PlacedSecret(SecretId id, Point worldPoint, SecretKind kind) {
        public PlacedSecret { Objects.requireNonNull(id); Objects.requireNonNull(worldPoint); Objects.requireNonNull(kind); }
    }

    public record Connection(int fromIndex, int toIndex, Set<Point> doorBounds, Set<Point> entranceBounds,
                             Set<Point> bounds, boolean valid, String detail) {
        public Connection {
            doorBounds = Set.copyOf(doorBounds); entranceBounds = Set.copyOf(entranceBounds);
            bounds = Set.copyOf(bounds); Objects.requireNonNull(detail);
        }
    }

    public record LayoutPlan(String algorithmVersion, UUID instanceId, long seed, String configHash,
                             String contentHash, List<Placement> placements, List<Connection> connections,
                             List<String> trace) {
        public LayoutPlan {
            Objects.requireNonNull(algorithmVersion); Objects.requireNonNull(instanceId);
            Objects.requireNonNull(configHash); Objects.requireNonNull(contentHash);
            placements = List.copyOf(placements); connections = List.copyOf(connections); trace = List.copyOf(trace);
        }
    }

    public record PlanResult(Optional<LayoutPlan> plan, List<String> errors, List<String> trace) {
        public PlanResult {
            Objects.requireNonNull(plan); errors = List.copyOf(errors); trace = List.copyOf(trace);
            if (plan.isPresent() == !errors.isEmpty()) {
                throw new IllegalArgumentException("a plan result must contain either a plan or errors");
            }
        }
        public boolean successful() { return plan.isPresent(); }
    }

    public record ConnectionTest(Optional<Placement> placement, Optional<Connection> connection, String detail) {
        public ConnectionTest {
            Objects.requireNonNull(placement); Objects.requireNonNull(connection); Objects.requireNonNull(detail);
        }
        public boolean successful() { return placement.isPresent() && connection.map(Connection::valid).orElse(false); }
    }
}
