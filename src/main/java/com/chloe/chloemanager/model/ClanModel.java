package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * MODEL — ClanModel
 *
 * Stores clans, their members, and a shared 27-slot inventory per clan.
 * Persists to clan_data.yml.
 *
 * A player can only belong to one clan at a time.  The clan creator is the
 * leader; only the leader can add, kick, or delete.
 */
public class ClanModel {

    private static final String FILE_NAME = "clan_data.yml";
    private static final String CLANS_KEY = "clans";
    public  static final int    INV_SIZE  = 27;

    private final ChloeManagerPlugin plugin;
    private final File              dataFile;
    private       FileConfiguration  cfg;

    /** Lower-case clan name → ClanData */
    private final Map<String, ClanData>   clans          = new HashMap<>();
    /** Player UUID → lower-case clan name (quick reverse lookup) */
    private final Map<UUID, String>       playerClan     = new HashMap<>();
    /** Every clan Inventory object, for fast isClanInventory() checks */
    private final Set<Inventory>          allInventories = new HashSet<>();

    // ── ClanData ──────────────────────────────────────────────────────────────

    public static class ClanData {
        private final String      name;          // display name
        private       UUID        leader;
        private final Set<UUID>   members = new LinkedHashSet<>();
        private       Inventory   inventory;

        public ClanData(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }

        public String      getName()      { return name; }
        public UUID         getLeader()   { return leader; }
        public Set<UUID>    getMembers()  { return members; }
        public Inventory    getInventory(){ return inventory; }
        public void         setInventory(Inventory inv) { this.inventory = inv; }
    }

    // ── Constructor / persistence ──────────────────────────────────────────────

    public ClanModel(ChloeManagerPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public boolean   clanExists(String name) { return clans.containsKey(name.toLowerCase()); }
    public ClanData getClan(String name)    { return clans.get(name.toLowerCase()); }

    public String   getPlayerClan(UUID uuid) { return playerClan.get(uuid); }
    public boolean  isInClan(UUID uuid)      { return playerClan.containsKey(uuid); }

    public boolean isLeader(String clanName, UUID uuid) {
        ClanData c = getClan(clanName);
        return c != null && c.getLeader().equals(uuid);
    }

    public boolean isMember(String clanName, UUID uuid) {
        ClanData c = getClan(clanName);
        return c != null && c.getMembers().contains(uuid);
    }

    public boolean isClanInventory(Inventory inv) {
        return allInventories.contains(inv);
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public ClanData createClan(String name, UUID leader) {
        String key = name.toLowerCase();
        ClanData clan = new ClanData(name, leader);
        Inventory inv = Bukkit.createInventory(null, INV_SIZE,
                ChatColor.DARK_AQUA + "Clan: " + ChatColor.AQUA + name);
        clan.setInventory(inv);
        clans.put(key, clan);
        playerClan.put(leader, key);
        allInventories.add(inv);
        save();
        return clan;
    }

    public void deleteClan(String name) {
        String key = name.toLowerCase();
        ClanData clan = clans.get(key);
        if (clan == null) return;
        for (UUID member : clan.getMembers()) playerClan.remove(member);
        allInventories.remove(clan.getInventory());
        clans.remove(key);
        save();
    }

    /** Adds a player to a clan. Returns false if already a member. */
    public boolean addMember(String clanName, UUID player) {
        String key = clanName.toLowerCase();
        ClanData clan = clans.get(key);
        if (clan == null || clan.getMembers().contains(player)) return false;
        clan.getMembers().add(player);
        playerClan.put(player, key);
        save();
        return true;
    }

    /** Removes a player from a clan. Returns false if not a member. */
    public boolean kickMember(String clanName, UUID player) {
        String key = clanName.toLowerCase();
        ClanData clan = clans.get(key);
        if (clan == null || !clan.getMembers().remove(player)) return false;
        playerClan.remove(player);
        save();
        return true;
    }

    /** A non-leader member leaves their clan. */
    public boolean leaveClan(UUID player) {
        String key = playerClan.get(player);
        if (key == null) return false;
        ClanData clan = clans.get(key);
        if (clan == null) return false;
        clan.getMembers().remove(player);
        playerClan.remove(player);
        save();
        return true;
    }

    // ── Save / load ───────────────────────────────────────────────────────────

    public void save() {
        cfg = new YamlConfiguration();
        for (Map.Entry<String, ClanData> entry : clans.entrySet()) {
            String base = CLANS_KEY + "." + entry.getKey();
            ClanData clan = entry.getValue();

            cfg.set(base + ".name",   clan.getName());
            cfg.set(base + ".leader", clan.getLeader().toString());

            List<String> memberList = new ArrayList<>();
            for (UUID m : clan.getMembers()) memberList.add(m.toString());
            cfg.set(base + ".members", memberList);

            Inventory inv = clan.getInventory();
            if (inv != null) {
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType().isItem()) {
                        cfg.set(base + ".inventory." + i, item);
                    }
                }
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save clan_data.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) { cfg = new YamlConfiguration(); return; }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection(CLANS_KEY)) return;

        for (String key : cfg.getConfigurationSection(CLANS_KEY).getKeys(false)) {
            String base = CLANS_KEY + "." + key;
            String name = cfg.getString(base + ".name", key);

            UUID leader;
            try { leader = UUID.fromString(cfg.getString(base + ".leader")); }
            catch (Exception e) { continue; }

            ClanData clan = new ClanData(name, leader);   // leader auto-added
            playerClan.put(leader, key);

            // Load additional members
            for (String s : cfg.getStringList(base + ".members")) {
                try {
                    UUID m = UUID.fromString(s);
                    if (!m.equals(leader)) {
                        clan.getMembers().add(m);
                        playerClan.put(m, key);
                    }
                } catch (Exception ignored) { }
            }

            // Create inventory and load contents
            Inventory inv = Bukkit.createInventory(null, INV_SIZE,
                    ChatColor.DARK_AQUA + "Clan: " + ChatColor.AQUA + name);

            String invPath = base + ".inventory";
            if (cfg.isConfigurationSection(invPath)) {
                for (String slot : cfg.getConfigurationSection(invPath).getKeys(false)) {
                    try {
                        int i = Integer.parseInt(slot);
                        ItemStack item = cfg.getItemStack(invPath + "." + slot);
                        if (item != null) inv.setItem(i, item);
                    } catch (Exception ignored) { }
                }
            }

            clan.setInventory(inv);
            allInventories.add(inv);
            clans.put(key, clan);
        }
    }
}
