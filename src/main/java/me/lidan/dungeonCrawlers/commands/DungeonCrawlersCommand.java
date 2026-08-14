package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityReport;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.compatibility.ProbeResult;
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
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Command("dungeon")
public final class DungeonCrawlersCommand {
    private final JavaPlugin plugin;
    private final CompatibilityService compatibility;
    private final CaveItemsGateway caveItems = new CaveItemsAdapter();
    private final MythicMobGateway mythic = new MythicMobsAdapter();
    private final WorldEditGateway worldEdit = new WorldEditAdapter();
    private final PartyProvider parties;

    public DungeonCrawlersCommand(JavaPlugin plugin, CompatibilityService compatibility) {
        this.plugin = plugin;
        this.compatibility = compatibility;
        this.parties = PartyProviders.forServer(plugin.getServer());
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
        String configuredAccount = plugin.getConfig().getString("compatibility.economy-test-account-uuid", "").trim();
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
        SpawnProvider provider = new BukkitSpawnProvider(plugin.getServer(), plugin.getConfig().getString("fallback-spawn-world", ""));
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
        double original = maxHealth.getBaseValue();
        try {
            maxHealth.setBaseValue(CompatibilityService.V1_HEALTH_BALANCE_MAX);
            double applied = maxHealth.getBaseValue();
            player.sendMessage("[" + (applied == CompatibilityService.V1_HEALTH_BALANCE_MAX ? "PASS" : "FAIL")
                    + "] Paper MAX_HEALTH base boundary=" + applied + "; CaveCrawlers="
                    + CaveCrawlers.getInstance().getPluginMeta().getVersion());
        } catch (RuntimeException exception) {
            player.sendMessage("[FAIL] MAX_HEALTH boundary: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } finally {
            maxHealth.setBaseValue(original);
        }
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
