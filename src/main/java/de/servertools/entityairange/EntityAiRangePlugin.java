package de.servertools.entityairange;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public final class EntityAiRangePlugin extends JavaPlugin implements Listener {
    private NamespacedKey managedKey;
    private double disableDistanceSquared;
    private long scanIntervalTicks;
    private int disableDistance;
    private boolean scanningEnabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        managedKey = new NamespacedKey(this, "disabled_by_entity_ai_range");
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskTimer(this, this::scanLoadedEntities,
                scanIntervalTicks, scanIntervalTicks);
        getLogger().info("Enabled; mobs farther than " + Math.sqrt(disableDistanceSquared)
                + " blocks from every online player will have AI disabled.");
    }

    @Override
    public void onDisable() {
        // Restore entities in currently loaded chunks. Markers on unloaded entities are
        // deliberately retained, so a later plugin start can restore them when nearby.
        int restored = restoreManagedEntities();
        getLogger().info("Restored AI for " + restored + " managed loaded entities.");
    }

    private void loadSettings() {
        double distance = getConfig().getDouble("disable-distance", 128.0);
        if (!Double.isFinite(distance) || distance < 1.0) {
            getLogger().warning("disable-distance must be a finite number >= 1; using 128.");
            distance = 128.0;
        }
        disableDistance = (int) Math.round(distance);
        disableDistanceSquared = (double) disableDistance * disableDistance;
        scanIntervalTicks = Math.max(20L, getConfig().getLong("scan-interval-ticks", 100L));
    }

    private void scanLoadedEntities() {
        if (!scanningEnabled) {
            return;
        }
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int disabled = 0;
        int restored = 0;
        int checked = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (!(living instanceof Mob mob) || mob instanceof Player || !mob.isValid()) {
                    continue;
                }
                checked++;

                boolean managed = mob.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE);
                boolean nearby = isWithinRange(mob, onlinePlayers);

                if (managed) {
                    if (nearby) {
                        mob.setAI(true);
                        mob.getPersistentDataContainer().remove(managedKey);
                        restored++;
                    }
                } else if (mob.hasAI() && !nearby) {
                    // Only mark mobs whose AI was enabled before this plugin changed them.
                    // Existing NoAI villagers (for example from Stillagers) are untouched.
                    mob.setAI(false);
                    mob.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
                    disabled++;
                }
            }
        }

        if (getConfig().getBoolean("log-scan-summary", false)) {
            getLogger().info("Scan: checked " + checked + " mobs, disabled " + disabled
                    + " AI, restored " + restored + ".");
        }
    }

    private boolean isWithinRange(Entity entity, List<Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            if (!player.getWorld().equals(entity.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(entity.getLocation()) <= disableDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private int restoreManagedEntities() {
        int restored = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (!(living instanceof Mob mob)
                        || !mob.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                    continue;
                }
                mob.setAI(true);
                mob.getPersistentDataContainer().remove(managedKey);
                restored++;
            }
        }
        return restored;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                openSettings(player);
            } else {
                sender.sendMessage("Die Einstellungen können nur im Spiel geöffnet werden.");
            }
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Usage: /" + label + " [gui|status|restore|resume]");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            int managed = countManagedEntities();
            sender.sendMessage("EntityAiRange: " + managed + " geladene Mobs werden aktuell von diesem Plugin verwaltet.");
            return true;
        }

        if (args[0].equalsIgnoreCase("restore")) {
            scanningEnabled = false;
            int restored = restoreManagedEntities();
            sender.sendMessage("AI für " + restored + " verwaltete, geladene Mobs wiederhergestellt.");
            sender.sendMessage("Mobs in ungeladenen Chunks werden beim Laden durch dieses Plugin wiederhergestellt.");
            sender.sendMessage("Die automatische Deaktivierung ist bis zum Neustart pausiert.");
            return true;
        }

        if (args[0].equalsIgnoreCase("resume")) {
            scanningEnabled = true;
            sender.sendMessage("Automatische AI-Verwaltung aktiviert.");
            return true;
        }

        sender.sendMessage("Usage: /" + label + " [gui|status|restore|resume]");
        return true;
    }

    private void openSettings(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Entity-AI Reichweite"));
        holder.inventory = inventory;

        inventory.setItem(10, item(MaterialChoice.REDUCE, "− 64 Blöcke", "Großer Schritt"));
        inventory.setItem(11, item(MaterialChoice.REDUCE, "− 16 Blöcke", "Kleiner Schritt"));
        inventory.setItem(13, item(MaterialChoice.RANGE,
                "Reichweite: " + disableDistance + " Blöcke",
                "AI wird deaktiviert, wenn kein Spieler innerhalb dieser Distanz ist.",
                "Änderungen werden sofort gespeichert."));
        inventory.setItem(15, item(MaterialChoice.INCREASE, "+ 16 Blöcke", "Kleiner Schritt"));
        inventory.setItem(16, item(MaterialChoice.INCREASE, "+ 64 Blöcke", "Großer Schritt"));
        inventory.setItem(22, item(MaterialChoice.RESET, "Standardeinstellung: 128", "Setzt die Reichweite auf 128 Blöcke."));
        inventory.setItem(26, item(MaterialChoice.CLOSE, "Schließen"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= 27 || event.getRawSlot() < 0) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> adjustDistance(-64);
            case 11 -> adjustDistance(-16);
            case 15 -> adjustDistance(16);
            case 16 -> adjustDistance(64);
            case 22 -> setDistance(128);
            case 26 -> {
                player.closeInventory();
                return;
            }
            default -> { return; }
        }
        openSettings(player);
    }

    @EventHandler
    public void onSettingsDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsHolder) {
            event.setCancelled(true);
        }
    }

    private void adjustDistance(int amount) {
        setDistance(disableDistance + amount);
    }

    private void setDistance(int distance) {
        disableDistance = Math.max(16, Math.min(1024, distance));
        disableDistanceSquared = (double) disableDistance * disableDistance;
        getConfig().set("disable-distance", disableDistance);
        saveConfig();
    }

    private ItemStack item(MaterialChoice choice, String name, String... lore) {
        ItemStack stack = new ItemStack(choice.material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore)
                    .map(Component::text)
                    .toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private enum MaterialChoice {
        REDUCE(org.bukkit.Material.REDSTONE),
        RANGE(org.bukkit.Material.COMPASS),
        INCREASE(org.bukkit.Material.EMERALD),
        RESET(org.bukkit.Material.CLOCK),
        CLOSE(org.bukkit.Material.BARRIER);

        private final org.bukkit.Material material;
        MaterialChoice(org.bukkit.Material material) { this.material = material; }
    }

    private static final class SettingsHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private int countManagedEntities() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (living instanceof Mob mob
                        && mob.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                    count++;
                }
            }
        }
        return count;
    }
}
