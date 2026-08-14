package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Connection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournal.PlannedBounds;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GenerationWorldGateway {
    WorldCheck ensureDedicatedVoidWorld(String worldName);

    CompletableFuture<Void> paste(String worldName, byte[] schematic, Point origin, Rotation rotation);

    CompletableFuture<Void> setupConnections(String worldName, List<Connection> connections);

    CompletableFuture<Void> clear(String worldName, List<PlannedBounds> bounds);

    record WorldCheck(boolean successful, String detail, int minimumY, int maximumY) { }
}
