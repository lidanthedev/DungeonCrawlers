package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.door.DoorService;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Objects;

/** Main-thread renderer for the logical 3x3 coal door. */
public final class BukkitDoorBlockService {
    public void render(World world, DoorService.DoorSnapshot door) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(door, "door");
        Material material = door.state() == DoorService.DoorState.OPEN ? Material.AIR : Material.COAL_BLOCK;
        door.blocks().forEach(point -> world.getBlockAt(point.x(), point.y(), point.z()).setType(material, false));
    }
}
