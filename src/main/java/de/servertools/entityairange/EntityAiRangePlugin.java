package de.servertools.entityairange;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntitySpawnEvent;
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
import java.util.Locale;
import java.util.TreeSet;

public final class EntityAiRangePlugin extends JavaPlugin implements Listener, TabCompleter {
    private NamespacedKey managedKey;
    private NamespacedKey despawnRuleKey;
    private NamespacedKey despawnRuleIdKey;
    private NamespacedKey despawnRuleStartAgeKey;
    private NamespacedKey legacyManualDespawnKey;
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
        despawnRuleKey = new NamespacedKey(this, "despawn_rule_key");
        despawnRuleIdKey = new NamespacedKey(this, "despawn_rule_id");
        despawnRuleStartAgeKey = new NamespacedKey(this, "despawn_rule_start_age");
        legacyManualDespawnKey = new NamespacedKey(this, "manual_despawn_at_ticks");
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("entity") != null) getCommand("entity").setTabCompleter(this);
        if (getCommand("entityairange") != null) getCommand("entityairange").setTabCompleter(this);

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("entityairange.admin")) return List.of();
        if (command.getName().equalsIgnoreCase("entityairange")) {
            if (args.length == 1) {
                return partialMatches(List.of("gui", "status", "restore", "resume"), args[0]);
            }
            return List.of();
        }
        if (!command.getName().equalsIgnoreCase("entity")) return List.of();
        if (args.length == 1) return partialMatches(List.of("despawn"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("despawn")) {
            return partialMatches(List.of("add", "remove"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("despawn")) {
            if (args[1].equalsIgnoreCase("add")) {
                TreeSet<String> keys = new TreeSet<>();
                for (EntityType type : EntityType.values()) {
                    // Paper's UNKNOWN sentinel deliberately has no NamespacedKey.
                    if (type == EntityType.UNKNOWN) continue;
                    NamespacedKey key = type.getKey();
                    if (key != null) keys.add(key.toString());
                }
                for (Material material : Material.values()) {
                    NamespacedKey key = material.getKey();
                    if (key != null) keys.add(key.toString());
                }
                return partialMatches(new ArrayList<>(keys), args[2]);
            }
            if (args[1].equalsIgnoreCase("remove")) {
                var rules = getConfig().getConfigurationSection("despawn-rules");
                List<String> keys = rules == null ? List.of() : new ArrayList<>(rules.getKeys(false));
                return partialMatches(keys, args[2]);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("despawn")
                && args[1].equalsIgnoreCase("add")) {
            return partialMatches(List.of("15", "30", "60", "120", "300"), args[3]);
        }
        return List.of();
    }

    private List<String> partialMatches(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void openSettings(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("EntityAiRange"));
        holder.inventory = inventory;
        holder.page = SettingsPage.HOME;
        inventory.setItem(11, item(org.bukkit.Material.VILLAGER_SPAWN_EGG, "Mob AI",
                "Einstellungen für die Mob-AI-Reichweite"));
        inventory.setItem(15, item(org.bukkit.Material.PAPER, "Item-Despawn Regeln",
                "Liste der aufgenommenen Item- und Entity-Typen"));
        inventory.setItem(26, item(MaterialChoice.CLOSE, "Schließen"));
        player.openInventory(inventory);
    }

    private void openAiSettings(Player player) {
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
        inventory.setItem(18, item(org.bukkit.Material.ARROW, "Zurück zu den Kategorien"));
        inventory.setItem(26, item(MaterialChoice.CLOSE, "Schließen"));
        player.openInventory(inventory);
    }

    private void openDespawnSettings(Player player, int page) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Despawn-Regeln"));
        holder.inventory = inventory;
        holder.page = SettingsPage.DESPAWN;
        holder.rulePage = page;
        var section = getConfig().getConfigurationSection("despawn-rules");
        List<String> rules = new ArrayList<>(section == null ? List.of() : section.getKeys(false));
        rules.sort(String::compareTo);
        int pageCount = Math.max(1, (rules.size() + 17) / 18);
        holder.rulePage = Math.max(0, Math.min(holder.rulePage, pageCount - 1));
        int from = holder.rulePage * 18;
        for (int i = from; i < Math.min(from + 18, rules.size()); i++) {
            String key = rules.get(i);
            int seconds = getConfig().getInt("despawn-rules." + key, 0);
            inventory.setItem(i - from, item(org.bukkit.Material.PAPER, key,
                    "Despawn nach " + formatDuration(seconds)));
        }
        if (rules.isEmpty()) {
            inventory.setItem(13, item(org.bukkit.Material.PAPER, "Noch keine Regeln vorhanden",
                    "Hinzufügen: /entity despawn add minecraft:diamond 60"));
        }
        if (pageCount > 1) {
            inventory.setItem(20, item(org.bukkit.Material.ARROW, "Vorherige Regelseite"));
            inventory.setItem(24, item(org.bukkit.Material.ARROW, "Nächste Regelseite"));
        }
        inventory.setItem(18, item(org.bukkit.Material.ARROW, "Zurück zu den Kategorien"));
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

        if (holder.page == SettingsPage.HOME) {
            switch (event.getRawSlot()) {
                case 11 -> openAiSettings(player);
                case 15 -> openDespawnSettings(player, 0);
                case 26 -> player.closeInventory();
            }
            return;
        }
        if (holder.page == SettingsPage.DESPAWN) {
            switch (event.getRawSlot()) {
                case 18 -> { openSettings(player); return; }
                case 20 -> { openDespawnSettings(player, holder.rulePage - 1); return; }
                case 24 -> { openDespawnSettings(player, holder.rulePage + 1); return; }
                case 26 -> { player.closeInventory(); return; }
                default -> { return; }
            }
        }
        switch (event.getRawSlot()) {
            case 10 -> adjustDistance(-64);
            case 11 -> adjustDistance(-16);
            case 15 -> adjustDistance(16);
            case 16 -> adjustDistance(64);
            case 22 -> setDistance(128);
            case 18 -> { openSettings(player); return; }
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
    public void onEntitySpawn(EntitySpawnEvent event) {
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
        if (args.length < 3 || !args[0].equalsIgnoreCase("despawn") || args.length > 4) {
            sender.sendMessage("Verwendung: /entity despawn add <minecraft:typ> [Sekunden]");
            sender.sendMessage("          /entity despawn remove <minecraft:typ>");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(args[2].toLowerCase(Locale.ROOT));
        if (key == null || !key.getNamespace().equals("minecraft") || !isKnownRuleKey(key)) {
            sender.sendMessage("Unbekannter Minecraft-Item- oder Entity-Typ: " + args[2]);
            sender.sendMessage("Beispiele: minecraft:item, minecraft:diamond, minecraft:zombie");
            return true;
        }
        String keyString = key.toString();
        if (action.equals("remove")) {
            if (args.length != 3) {
                sender.sendMessage("Verwendung: /entity despawn remove <minecraft:typ>");
                return true;
            }
            if (!getConfig().contains("despawn-rules." + keyString)) {
                sender.sendMessage("Für " + keyString + " ist keine Despawn-Regel gesetzt.");
                return true;
            }
            getConfig().set("despawn-rules." + keyString, null);
            getConfig().set("despawn-rule-ids." + keyString, null);
            saveConfig();
            refreshRuleEntities(keyString, null);
            sender.sendMessage("Despawn-Regel für " + keyString + " entfernt. Der normale Timer gilt wieder.");
            return true;
        }
        if (!action.equals("add")) {
            sender.sendMessage("Nur 'add' und 'remove' werden unterstützt.");
            return true;
        }
        int seconds = itemDespawnSeconds;
        if (args.length == 4) {
            try { seconds = Integer.parseInt(args[3]); }
            catch (NumberFormatException exception) {
                sender.sendMessage("Die Zeit muss eine Zahl zwischen 15 und 300 Sekunden sein.");
                return true;
            }
        }
        if (seconds < 15 || seconds > 300) {
            sender.sendMessage("Die Zeit muss zwischen 15 und 300 Sekunden liegen.");
            return true;
        }
        String id = UUID.randomUUID().toString();
        getConfig().set("despawn-rules." + keyString, seconds);
        getConfig().set("despawn-rule-ids." + keyString, id);
        saveConfig();
        refreshRuleEntities(keyString, id);
        sender.sendMessage("Despawn-Regel für " + keyString + " auf " + formatDuration(seconds)
                + " gesetzt. Sie gilt für passende geladene und neu erscheinende Entities.");
        return true;
    }

    private boolean isKnownRuleKey(NamespacedKey key) {
        for (EntityType type : EntityType.values()) {
            // EntityType.UNKNOWN.getKey() throws IllegalArgumentException on Paper.
            if (type == EntityType.UNKNOWN) continue;
            if (key.equals(type.getKey())) return true;
        }
        for (Material material : Material.values()) {
            if (key.equals(material.getKey())) return true;
        }
        return false;
    }

    private void refreshRuleEntities(String key, String id) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (id == null) {
                    String savedKey = entity.getPersistentDataContainer().get(despawnRuleKey, PersistentDataType.STRING);
                    if (key.equals(savedKey)) {
                        clearRuleMarker(entity);
                        scheduleDespawn(entity);
                    }
                } else if (key.equals(getMostSpecificRuleKey(entity))) {
                    setRuleMarker(entity, key, id, entity.getTicksLived());
                    scheduleDespawn(entity);
                }
            }
        }
    }

    private void setRuleMarker(Entity entity, String key, String id, long baseAge) {
        var data = entity.getPersistentDataContainer();
        data.set(despawnRuleKey, PersistentDataType.STRING, key);
        data.set(despawnRuleIdKey, PersistentDataType.STRING, id);
        data.set(despawnRuleStartAgeKey, PersistentDataType.LONG, baseAge);
    }

    private void clearRuleMarker(Entity entity) {
        var data = entity.getPersistentDataContainer();
        data.remove(despawnRuleKey);
        data.remove(despawnRuleIdKey);
        data.remove(despawnRuleStartAgeKey);
    }

    private String getMostSpecificRuleKey(Entity entity) {
        if (entity instanceof Item item) {
            String materialKey = item.getItemStack().getType().getKey().toString();
            if (getConfig().contains("despawn-rules." + materialKey)) return materialKey;
        }
        String typeKey = entity.getType().getKey().toString();
        return getConfig().contains("despawn-rules." + typeKey) ? typeKey : null;
    }

    private void scheduleDespawn(Entity entity) {
        if (!entity.isValid()) return;
        BukkitTask previous = despawnTasks.remove(entity.getUniqueId());
        if (previous != null) previous.cancel();
        entity.getPersistentDataContainer().remove(legacyManualDespawnKey);

        String ruleKey = getMostSpecificRuleKey(entity);
        int seconds = ruleKey == null ? (entity instanceof Item ? itemDespawnSeconds : 0)
                : getConfig().getInt("despawn-rules." + ruleKey, 0);
        if (seconds <= 0) {
            clearRuleMarker(entity);
            return;
        }

        long baseAge = 0L;
        if (ruleKey != null) {
            String currentId = getConfig().getString("despawn-rule-ids." + ruleKey, "");
            String savedKey = entity.getPersistentDataContainer().get(despawnRuleKey, PersistentDataType.STRING);
            String savedId = entity.getPersistentDataContainer().get(despawnRuleIdKey, PersistentDataType.STRING);
            Long savedBase = entity.getPersistentDataContainer().get(despawnRuleStartAgeKey, PersistentDataType.LONG);
            if (!ruleKey.equals(savedKey) || !currentId.equals(savedId) || savedBase == null) {
                baseAge = entity.getTicksLived();
                setRuleMarker(entity, ruleKey, currentId, baseAge);
            } else {
                baseAge = savedBase;
            }
        }
        long remainingTicks = baseAge + seconds * 20L - entity.getTicksLived();
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
        private int rulePage;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum SettingsPage { HOME, RANGE, DESPAWN }

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
