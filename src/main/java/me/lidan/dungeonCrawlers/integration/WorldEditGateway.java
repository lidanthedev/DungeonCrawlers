package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Selection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import org.bukkit.entity.Player;

import java.util.Optional;

public interface WorldEditGateway {
    SelectionResult selection(Player player);

    ScanResult scan(Player player, int maximumDimension, long maximumVolume);

    CaptureResult capture(Player player, int maximumDimension, long maximumVolume);

    ScanResult read(byte[] schematic, int maximumDimension, long maximumVolume);

    OperationResult paste(Player player, byte[] schematic, Point origin, Rotation rotation);

    record SelectionResult(boolean successful, String detail) {}

    record ScanResult(boolean successful, String detail, Optional<Selection> selection) {
        public ScanResult {
            selection = selection == null ? Optional.empty() : selection;
            if (successful != selection.isPresent()) {
                throw new IllegalArgumentException("a successful scan must contain a selection");
            }
        }
    }

    record CaptureResult(boolean successful, String detail, Optional<Selection> selection, byte[] schematic) {
        public CaptureResult {
            selection = selection == null ? Optional.empty() : selection;
            schematic = schematic == null ? new byte[0] : schematic.clone();
            if (successful != (selection.isPresent() && schematic.length > 0)) {
                throw new IllegalArgumentException("a successful capture must contain a selection and schematic");
            }
        }

        @Override
        public byte[] schematic() {
            return schematic.clone();
        }
    }

    record OperationResult(boolean successful, String detail) { }
}

