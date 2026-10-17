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
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EntityAiRangePlugin extends JavaPlugin implements Listener {
    private NamespacedKey managedKey;
    private NamespacedKey manualDespawnKey;
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();
    private double disableDistanceSquared;
    private long scanIntervalTicks;
    private int disableDistance;
    private int itemDespawnSeconds;
    private boolean scanningEnabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        managedKey = new NamespacedKey(this, "disabled_by_entity_ai_range");
        manualDespawnKey = new NamespacedKey(this, "manual_despawn_at_ticks");
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                scheduleDespawn(entity);
            }
        }

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
        despawnTasks.values().forEach(BukkitTask::cancel);
        despawnTasks.clear();
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
        itemDespawnSeconds = Math.max(15, Math.min(300,
                getConfig().getInt("item-despawn-seconds", 300)));
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
        if (command.getName().equalsIgnoreCase("entity")) {
            return handleDespawnCommand(sender, args);
        }
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
        holder.page = SettingsPage.RANGE;

        inventory.setItem(10, item(MaterialChoice.REDUCE, "− 64 Blöcke", "Großer Schritt"));
        inventory.setItem(11, item(MaterialChoice.REDUCE, "− 16 Blöcke", "Kleiner Schritt"));
        inventory.setItem(13, item(MaterialChoice.RANGE,
                "Reichweite: " + disableDistance + " Blöcke",
                "AI wird deaktiviert, wenn kein Spieler innerhalb dieser Distanz ist.",
                "Änderungen werden sofort gespeichert."));
        inventory.setItem(15, item(MaterialChoice.INCREASE, "+ 16 Blöcke", "Kleiner Schritt"));
        inventory.setItem(16, item(MaterialChoice.INCREASE, "+ 64 Blöcke", "Großer Schritt"));
        inventory.setItem(22, item(MaterialChoice.RESET, "Standardeinstellung: 128", "Setzt die Reichweite auf 128 Blöcke."));
        inventory.setItem(4, item(org.bukkit.Material.CLOCK, "Item-Despawn einstellen",
                "Aktuelle Zeit: " + itemDespawnSeconds + " Sekunden"));
        inventory.setItem(26, item(MaterialChoice.CLOSE, "Schließen"));
        player.openInventory(inventory);
    }

    private void openDespawnSettings(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Item-Despawnzeit"));
        holder.inventory = inventory;
        holder.page = SettingsPage.DESPAWN;
        inventory.setItem(10, item(org.bukkit.Material.REDSTONE, "− 60 Sekunden"));
        inventory.setItem(11, item(org.bukkit.Material.REDSTONE, "− 15 Sekunden"));
        inventory.setItem(13, item(org.bukkit.Material.CLOCK,
                "Item-Despawn: " + formatDuration(itemDespawnSeconds),
                "Gilt für gedroppte Items.", "Bereich: 15 Sekunden bis 5 Minuten.",
                "Änderungen werden sofort gespeichert."));
        inventory.setItem(15, item(org.bukkit.Material.EMERALD, "+ 15 Sekunden"));
        inventory.setItem(16, item(org.bukkit.Material.EMERALD, "+ 60 Sekunden"));
        inventory.setItem(22, item(org.bukkit.Material.PAPER, "Auf 5 Minuten setzen"));
        inventory.setItem(18, item(org.bukkit.Material.ARROW, "Zurück"));
        inventory.setItem(26, item(MaterialChoice.CLOSE, "Schließen"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= 27 || event.getRawSlot() < 0) {
            return;
        }

        if (holder.page == SettingsPage.DESPAWN) {
            switch (event.getRawSlot()) {
                case 10 -> setItemDespawnSeconds(itemDespawnSeconds - 60);
                case 11 -> setItemDespawnSeconds(itemDespawnSeconds - 15);
                case 15 -> setItemDespawnSeconds(itemDespawnSeconds + 15);
                case 16 -> setItemDespawnSeconds(itemDespawnSeconds + 60);
                case 22 -> setItemDespawnSeconds(300);
                case 18 -> { openSettings(player); return; }
                case 26 -> { player.closeInventory(); return; }
                default -> { return; }
            }
            openDespawnSettings(player);
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> adjustDistance(-64);
            case 11 -> adjustDistance(-16);
            case 15 -> adjustDistance(16);
            case 16 -> adjustDistance(64);
            case 22 -> setDistance(128);
            case 4 -> { openDespawnSettings(player); return; }
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

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        scheduleDespawn(event.getEntity());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            scheduleDespawn(entity);
        }
        if (scanningEnabled) {
            return;
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Mob mob
                    && mob.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                mob.setAI(true);
                mob.getPersistentDataContainer().remove(managedKey);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            BukkitTask task = despawnTasks.remove(entity.getUniqueId());
            if (task != null) task.cancel();
        }
    }

    private boolean handleDespawnCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("despawn")
                || !args[1].equalsIgnoreCase("add") || args.length > 4) {
            sender.sendMessage("Verwendung: /entity despawn add <Selektor> [Sekunden]");
            sender.sendMessage("Beispiel: /entity despawn add @e[type=item,limit=1,sort=nearest] 60");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Bitte gib einen Vanilla-Selektor an, z. B. @e[type=item,limit=1,sort=nearest].");
            return true;
        }
        int seconds = itemDespawnSeconds;
        if (args.length == 4) {
            try {
                seconds = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Die Zeit muss eine Zahl zwischen 15 und 300 Sekunden sein.");
                return true;
            }
        }
        if (seconds < 15 || seconds > 300) {
            sender.sendMessage("Die Zeit muss zwischen 15 Sekunden und 300 Sekunden liegen.");
            return true;
        }
        final List<Entity> selected;
        try {
            selected = Bukkit.selectEntities(sender, args[2]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("Ungültiger Selektor: " + args[2]);
            return true;
        }
        if (selected.isEmpty()) {
            sender.sendMessage("Der Selektor hat keine Entities gefunden.");
            return true;
        }
        int ticks = seconds * 20;
        for (Entity entity : selected) {
            entity.getPersistentDataContainer().set(manualDespawnKey, PersistentDataType.LONG,
                    (long) entity.getTicksLived() + ticks);
            scheduleDespawn(entity);
        }
        sender.sendMessage("Despawn-Timer für " + selected.size() + " Entity(s) auf "
                + formatDuration(seconds) + " gesetzt.");
        return true;
    }

    private void scheduleDespawn(Entity entity) {
        if (!entity.isValid()) return;
        long remainingTicks = Long.MAX_VALUE;
        if (entity instanceof Item) {
            remainingTicks = (long) itemDespawnSeconds * 20L - entity.getTicksLived();
        }
        Long manualAt = entity.getPersistentDataContainer().get(manualDespawnKey, PersistentDataType.LONG);
        if (manualAt != null) {
            remainingTicks = Math.min(remainingTicks, manualAt - entity.getTicksLived());
        }
        if (remainingTicks == Long.MAX_VALUE) return;

        BukkitTask previous = despawnTasks.remove(entity.getUniqueId());
        if (previous != null) previous.cancel();
        if (remainingTicks <= 0) {
            entity.remove();
            return;
        }
        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            despawnTasks.remove(entity.getUniqueId());
            if (entity.isValid()) entity.remove();
        }, remainingTicks);
        despawnTasks.put(entity.getUniqueId(), task);
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

    private void setItemDespawnSeconds(int seconds) {
        itemDespawnSeconds = Math.max(15, Math.min(300, seconds));
        getConfig().set("item-despawn-seconds", itemDespawnSeconds);
        saveConfig();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) scheduleDespawn(entity);
            }
        }
    }

    private String formatDuration(int seconds) {
        return seconds < 60 ? seconds + " Sekunden" : (seconds / 60) + " Min."
                + (seconds % 60 == 0 ? "" : " " + (seconds % 60) + " Sek.");
    }

    private ItemStack item(MaterialChoice choice, String name, String... lore) {
        return item(choice.material, name, lore);
    }

    private ItemStack item(org.bukkit.Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
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
        private SettingsPage page;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum SettingsPage { RANGE, DESPAWN }

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
