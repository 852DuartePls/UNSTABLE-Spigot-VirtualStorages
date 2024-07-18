package net.duart.virtualstorage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileHandlers {
    private final Plugin plugin;
    private final ReentrantLock fileLock = new ReentrantLock();

    public FileHandlers(Plugin plugin) {
        this.plugin = plugin;
    }

    public void saveBackpackInventory(Player player, UUID playerId, ArrayList<Inventory> pages) {
        File playerFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId.toString() + ".yml.gz");
        YamlConfiguration playerConfig = new YamlConfiguration();
        try {
            fileLock.lock();
            for (int i = 0; i < pages.size(); i++) {
                Inventory page = pages.get(i);
                for (int slot = 0; slot < page.getSize(); slot++) {
                    ItemStack item = page.getItem(slot);
                    if (item != null && isNavigationItem(item)) {
                        playerConfig.set("pages." + i + ".slot" + slot, item);
                    } else {
                        playerConfig.set("pages." + i + ".slot" + slot, null);
                    }
                }
            }
            File tempFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".tmp.yml");
            playerConfig.save(tempFile);

            try (FileOutputStream fileOutputStream = new FileOutputStream(playerFile); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {

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
                    if (item != null && isNavigationItem(item)) {
                        targetConfig.set("pages." + i + ".slot" + slot, item);
                    } else {
                        targetConfig.set("pages." + i + ".slot" + slot, null);
                    }
                }
            }
            File tempFile = new File(plugin.getDataFolder(), targetPlayer.getName() + " - " + targetId + ".tmp.yml");
            targetConfig.save(tempFile);

            try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {

                Files.copy(tempFile.toPath(), gzipOutputStream);
            }
            Files.delete(tempFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving backpack YAML for target " + targetId, e);
        } finally {
            fileLock.unlock();
        }
    }

    public void loadBackpackFromYAML(UUID playerId, ArrayList<Inventory> pages, File file) {
        try {
            fileLock.lock();
            YamlConfiguration playerConfig;
            if (file.getName().endsWith(".yml.gz")) {
                try (FileInputStream fileInputStream = new FileInputStream(file); GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream); InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream); BufferedReader reader = new BufferedReader(inputStreamReader)) {

                    playerConfig = YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                playerConfig = YamlConfiguration.loadConfiguration(file);
            }
            for (int i = 0; i < pages.size(); i++) {
                Inventory page = pages.get(i);
                ConfigurationSection pageSection = playerConfig.getConfigurationSection("pages." + i);
                if (pageSection != null) {
                    for (String key : pageSection.getKeys(false)) {
                        ItemStack item = pageSection.getItemStack(key);
                        if (item != null) {
                            page.setItem(Integer.parseInt(key.replace("slot", "")), item);
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

    private boolean isNavigationItem(ItemStack item) {
        if (item.getType() == Material.ARROW) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                return !displayName.equals("§c<< ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ") && !displayName.equals("§aɴᴇxᴛ ᴘᴀɢᴇ >>");
            }
        }
        return true;
    }
}
