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
import java.util.*;
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

    public record BackpackData(Map<Integer, Map<Integer, ItemStack>> pages, int pageCount) { }

    /* REGULAR SAVING */

    public void saveBackpackInventory(@Nonnull Player player, @Nonnull UUID playerId, @Nonnull ArrayList<Inventory> pages) {
        savePlayerBackpackAtomic(player.getName(), playerId, pages);
    }

    public void saveBackpackInventoryForTarget(UUID targetId, ArrayList<Inventory> pages) {
        Player targetPlayer = Bukkit.getPlayer(targetId);
        if (targetPlayer == null) {
            String playerName;
            File dataFolder = plugin.getDataFolder();
            File[] files = dataFolder.listFiles((dir, name) ->
                    name.contains(targetId.toString()) && (name.endsWith(".yml.gz") || name.endsWith(".yml"))
            );

            if (files != null && files.length > 0) {
                String fileName = files[0].getName();
                playerName = fileName.substring(0, fileName.indexOf(" - "));
            } else {
                plugin.getLogger().warning("Target player not found and no existing file: " + targetId);
                return;
            }

            savePlayerBackpackAtomic(playerName, targetId, pages);
        } else {
            savePlayerBackpackAtomic(targetPlayer.getName(), targetId, pages);
        }
    }

    private void savePlayerBackpackAtomic(String playerName, UUID playerId, @Nonnull ArrayList<Inventory> pages) {
        File playerFile = new File(plugin.getDataFolder(), playerName + " - " + playerId + ".yml.gz");
        File tempFile = new File(plugin.getDataFolder(), playerName + " - " + playerId + ".tmp.yml.gz");

        YamlConfiguration targetConfig = new YamlConfiguration();
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
        String yamlContent = targetConfig.saveToString();

        byte[] compressedData;
        try {
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteOutput);
                 OutputStreamWriter outputWriter = new OutputStreamWriter(gzipOutputStream)) {
                outputWriter.write(yamlContent);
                outputWriter.flush();
                gzipOutputStream.finish();
            }
            compressedData = byteOutput.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error compressing backpack data for player " + playerId, e);
            return;
        }

        try {
            fileLock.lock();
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
                fileOutputStream.write(compressedData);
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();
            }
            Files.move(tempFile.toPath(), playerFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving backpack YAML for player " + playerId, e);
            if (tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete temp file", ex);
                }
            }
        } finally {
            fileLock.unlock();
        }
    }

    public BackpackData loadBackpackData(UUID playerId, @Nonnull File file) {
        Map<Integer, Map<Integer, ItemStack>> pagesData = new HashMap<>();
        int storedPageCount = 0;

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
            if (pagesSection != null) {
                for (String key : pagesSection.getKeys(false)) {
                    try {
                        int pageIdx = Integer.parseInt(key);
                        if (pageIdx + 1 > storedPageCount) storedPageCount = pageIdx + 1;

                        ConfigurationSection pageSection = playerConfig.getConfigurationSection("pages." + pageIdx);
                        if (pageSection != null) {
                            Map<Integer, ItemStack> pageItems = new HashMap<>();
                            for (String slotKey : pageSection.getKeys(false)) {
                                ItemStack item = pageSection.getItemStack(slotKey);
                                if (item != null) {
                                    int slotIndex = Integer.parseInt(slotKey.replace("slot", ""));
                                    pageItems.put(slotIndex, item);
                                }
                            }
                            pagesData.put(pageIdx, pageItems);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading backpack data for player " + playerId, e);
        } finally {
            fileLock.unlock();
        }

        return new BackpackData(pagesData, storedPageCount);
    }

    /* OVERFLOW */

    public void saveOverflowItems(UUID playerId, @Nonnull List<ItemStack> overflowItems) {
        if (overflowItems.isEmpty()) return;

        File overflowFile = new File(plugin.getDataFolder(), playerId + "-overflow-" + ".yml.gz");
        File tempFile = new File(plugin.getDataFolder(), playerId + "-overflow-" + ".tmp.yml.gz");

        byte[] serializedData;
        try {
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(byteOutput);
                 BukkitObjectOutputStream oos = new BukkitObjectOutputStream(gos)) {

                oos.writeInt(overflowItems.size());
                for (ItemStack item : overflowItems) {
                    oos.writeObject(item);
                }

                oos.flush();
                gos.finish();
            }
            serializedData = byteOutput.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error serializing overflow items for " + playerId, e);
            return;
        }

        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(serializedData);
                fos.flush();
                fos.getFD().sync();
            }

            Files.move(tempFile.toPath(), overflowFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving overflow items for " + playerId, e);

            if (tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete temp overflow file", ex);
                }
            }
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

    /* HELPER */

    private boolean isNotNavigationItem(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return true;
        ItemMeta meta = item.getItemMeta();
        return meta == null ||
                !meta.getPersistentDataContainer().has(NAV_KEY, PersistentDataType.BYTE);
    }
}
