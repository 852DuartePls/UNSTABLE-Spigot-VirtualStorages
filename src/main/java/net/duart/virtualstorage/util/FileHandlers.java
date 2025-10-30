package net.duart.virtualstorage.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileHandlers {
    private final Plugin plugin;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final NamespacedKey NAV_KEY;

    public FileHandlers(Plugin plugin) {
        this.plugin = plugin;
        NAV_KEY = new NamespacedKey(plugin, "navarrow");
    }

    public void saveBackpackInventory(@Nonnull Player player,@Nonnull UUID playerId,@Nonnull ArrayList<Inventory> pages) {
        File playerFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml.gz");
        YamlConfiguration playerConfig = new YamlConfiguration();
        try {
            fileLock.lock();
            for (int i = 0; i < pages.size(); i++) {
                Inventory page = pages.get(i);
                for (int slot = 0; slot < page.getSize(); slot++) {
                    ItemStack item = page.getItem(slot);
                    if (item != null && isNotNavigationItem(item)) {
                        playerConfig.set("pages." + i + ".slot" + slot, item);
                    } else {
                        playerConfig.set("pages." + i + ".slot" + slot, null);
                    }
                }
            }
            File tempFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".tmp.yml");
            playerConfig.save(tempFile);
            try (FileOutputStream fileOutputStream = new FileOutputStream(playerFile);
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {
                Files.copy(tempFile.toPath(), gzipOutputStream);
            }
            Files.delete(tempFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving backpack YAML for player " + playerId, e);
        } finally {
            fileLock.unlock();
        }
    }

    public void saveBackpackInventoryForTarget(UUID targetId, ArrayList<Inventory> pages) {
        Player targetPlayer = Bukkit.getPlayer(targetId);
        if (targetPlayer == null) {
            plugin.getLogger().warning("Target player not found: " + targetId);
            return;
        }
        File targetFile = new File(plugin.getDataFolder(), targetPlayer.getName() + " - " + targetId + ".yml.gz");
        YamlConfiguration targetConfig = new YamlConfiguration();
        try {
            fileLock.lock();
            for (int i = 0; i < pages.size(); i++) {
                Inventory page = pages.get(i);
                for (int slot = 0; slot < page.getSize(); slot++) {
                    ItemStack item = page.getItem(slot);
                    if (item != null && isNotNavigationItem(item)) {
                        targetConfig.set("pages." + i + ".slot" + slot, item);
                    } else {
                        targetConfig.set("pages." + i + ".slot" + slot, null);
                    }
                }
            }
            File tempFile = new File(plugin.getDataFolder(), targetPlayer.getName() + " - " + targetId + ".tmp.yml");
            targetConfig.save(tempFile);
            try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {
                Files.copy(tempFile.toPath(), gzipOutputStream);
            }
            Files.delete(tempFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving backpack YAML for target " + targetId, e);
        } finally {
            fileLock.unlock();
        }
    }

    public void loadBackpackFromYAML(UUID playerId, ArrayList<Inventory> pages,@Nonnull File file) {
        try {
            fileLock.lock();
            YamlConfiguration playerConfig;
            if (file.getName().endsWith(".yml.gz")) {
                try (FileInputStream fileInputStream = new FileInputStream(file);
                     GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                     InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream);
                     BufferedReader reader = new BufferedReader(inputStreamReader)) {
                    playerConfig = YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                playerConfig = YamlConfiguration.loadConfiguration(file);
            }

            ConfigurationSection pagesSection = playerConfig.getConfigurationSection("pages");
            int storedPageCount = 0;
            if (pagesSection != null) {
                for (String key : pagesSection.getKeys(false)) {
                    try {
                        int idx = Integer.parseInt(key);
                        if (idx + 1 > storedPageCount) storedPageCount = idx + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (storedPageCount > pages.size()) {
                for (int i = pages.size(); i < storedPageCount; i++) {
                    Inventory page = Bukkit.createInventory(null, 54, "§9◆ Backpack - Page " + (i + 1) + " of " + storedPageCount + " ◆");
                    pages.add(page);
                }
            }

            for (int i = 0; i < pages.size(); i++) {
                Inventory page = pages.get(i);
                ConfigurationSection pageSection = playerConfig.getConfigurationSection("pages." + i);
                if (pageSection != null) {
                    for (String key : pageSection.getKeys(false)) {
                        ItemStack item = pageSection.getItemStack(key);
                        if (item != null) {
                            int slotIndex = Integer.parseInt(key.replace("slot", ""));
                            if (slotIndex >= 0 && slotIndex < page.getSize()) {
                                page.setItem(slotIndex, item);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading backpack YAML for player " + playerId, e);
        } finally {
            fileLock.unlock();
        }
    }

    public void saveOverflowItems(UUID playerId,@Nonnull List<ItemStack> overflowItems) {
        if (overflowItems.isEmpty()) return;

        File overflowFile = new File(plugin.getDataFolder(), playerId + "-overflow-" + ".yml.gz");
        try (FileOutputStream fos = new FileOutputStream(overflowFile);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(gos)) {
            oos.writeInt(overflowItems.size());
            for (ItemStack item : overflowItems) {
                oos.writeObject(item);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving overflow items for " + playerId, e);
        }
    }

    public List<ItemStack> loadOverflowItems(UUID playerId) {
        File overflowFile = new File(plugin.getDataFolder(), playerId + "-overflow-" + ".yml.gz");
        List<ItemStack> overflowItems = new ArrayList<>();
        if (!overflowFile.exists()) return overflowItems;

        try (FileInputStream fis = new FileInputStream(overflowFile);
             GZIPInputStream gis = new GZIPInputStream(fis);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(gis)) {

            int size = ois.readInt();
            for (int i = 0; i < size; i++) {
                overflowItems.add((ItemStack) ois.readObject());
            }

        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading overflow items for " + playerId, e);
            return overflowItems;
        }

        if (!overflowFile.delete()) {
            plugin.getLogger().warning("Could not delete overflow file for player " + playerId);
        }

        return overflowItems;
    }

    private boolean isNotNavigationItem(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return true;
        ItemMeta meta = item.getItemMeta();
        return meta == null ||
                !meta.getPersistentDataContainer().has(NAV_KEY, PersistentDataType.BYTE);
    }
}
