package net.duart.virtualstorage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class VirtualBackpack implements Listener {

    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Integer> currentPageIndexMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ArrayList<Inventory>> backpacks = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public VirtualBackpack(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openBackpack(Player player) {
        UUID playerId = player.getUniqueId();
        currentPageIndexMap.put(playerId, 0);
        ArrayList<Inventory> pages = getBackpackPages(playerId);

        CompletableFuture.runAsync(() -> {
            File gzippedFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml.gz");
            File yamlFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml");

            if (gzippedFile.exists()) {
                loadBackpackFromYAML(playerId, pages, gzippedFile);
            } else if (yamlFile.exists()) {
                loadBackpackFromYAML(playerId, pages, yamlFile);
            } else {
                saveBackpackInventory(player, playerId, pages);
            }
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            moveItemsToNextPageIfNecessary(pages);
            player.openInventory(pages.get(0));
        }));
    }

    private void moveItemsToNextPageIfNecessary(ArrayList<Inventory> pages) {
        int totalPages = pages.size();
        for (int i = 0; i < totalPages - 1; i++) {
            Inventory currentPage = pages.get(i);
            Inventory nextPage = pages.get(i + 1);
            ItemStack itemToMove = currentPage.getItem(53);

            if (itemToMove != null && isNavigationItem(itemToMove) && nextPage.firstEmpty() != -1) {
                currentPage.setItem(53, null);
                nextPage.addItem(itemToMove);
            }
        }
        addNavigationItems(pages);
    }

    private ArrayList<Inventory> getBackpackPages(UUID playerId) {
        int maxPages = getMaxPages(playerId);
        return backpacks.computeIfAbsent(playerId, k -> createNewBackpackPages(maxPages));
    }

    private ArrayList<Inventory> createNewBackpackPages(int maxPages) {
        ArrayList<Inventory> pages = new ArrayList<>();
        for (int i = 0; i < maxPages; i++) {
            Inventory page = Bukkit.createInventory(null, 54, "§9◆ Backpack - Page " + (i + 1) + " of " + maxPages + " ◆");
            pages.add(page);
        }
        addNavigationItems(pages);
        return pages;
    }

    private int getMaxPages(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            for (int i = 999; i >= 1; i--) {
                if (player.hasPermission("virtualstorages.use." + i)) {
                    return i;
                }
            }
        }
        return 1;
    }

    public void reloadVirtualStorages() {
        closeAllBackpackInventories();
        updatePermissions();
    }

    private void closeAllBackpackInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory.getTitle().contains("Backpack - Page")) {
                player.closeInventory();
            }
        }
    }

    public void updatePermissions() {
        createBackup();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveAllBackpacks();
            updatePlayerInventories();
        }, 30L);
    }

    public void updatePlayerInventories() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                int newMaxPages = getMaxPages(playerId);
                ArrayList<Inventory> pages = backpacks.computeIfAbsent(playerId, k -> new ArrayList<>());
                int oldMaxPages = pages.size();
                if (newMaxPages > oldMaxPages) {
                    for (int i = oldMaxPages; i < newMaxPages; i++) {
                        Inventory page = Bukkit.createInventory(null, 54, "§9◆ Backpack - Page " + (i + 1) + " of " + newMaxPages + " ◆");
                        pages.add(page);
                    }
                } else if (newMaxPages < oldMaxPages) {
                    pages.subList(newMaxPages, oldMaxPages).clear();
                }
                moveItemsToNextPageIfNecessary(pages);
                addNavigationItems(pages);
            }
        });
    }

    public void createBackup() {
        File dataFolder = plugin.getDataFolder();
        File backupFolder = new File(dataFolder, "backup");
        if (!backupFolder.exists()) {
            if (!backupFolder.mkdirs()) {
                plugin.getLogger().log(Level.SEVERE, "Error creating backup directory: " + backupFolder.getAbsolutePath());
                return;
            }
        }

        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yml.gz"));
        if (files != null) {
            for (File file : files) {
                File backupFile = new File(backupFolder, file.getName());
                try {
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error creating backup for file: " + file.getName(), e);
                }
            }
        }
    }

    private ItemStack createNavigationItem(String displayName) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addNavigationItems(List<Inventory> pages) {
        int totalPages = pages.size();
        for (int i = 0; i < totalPages; i++) {
            Inventory page = pages.get(i);

            if (i > 0) {
                ItemStack leftArrow = createNavigationItem("§c<< ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ");
                page.setItem(45, leftArrow);
            }

            if (i < totalPages - 1) {
                ItemStack rightArrow = createNavigationItem("§aɴᴇxᴛ ᴘᴀɢᴇ >>");
                page.setItem(53, rightArrow);
            }
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        InventoryView inventoryView = event.getView();
        String inventoryTitle = inventoryView.getTitle();
        if (inventoryTitle.contains("Backpack - Page")) {
            ArrayList<Inventory> pages = getBackpackPages(playerId);
            int currentPageIndex = currentPageIndexMap.getOrDefault(playerId, 0);
            Inventory currentPage = pages.get(currentPageIndex);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(currentPage)) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() == Material.ARROW) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        String displayName = meta.getDisplayName();
                        if (displayName.equals("§c<< ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ") || displayName.equals("§aɴᴇxᴛ ᴘᴀɢᴇ >>")) {
                            int slot = event.getSlot();
                            if (slot == 45 || slot == 53) {
                                boolean isLeftClick = event.getClick().isLeftClick();
                                boolean isShiftClick = event.isShiftClick();
                                boolean isNumericKey = event.getClick().isKeyboardClick();
                                if (isLeftClick || isShiftClick || isNumericKey) {
                                    event.setCancelled(true);
                                    int direction = slot == 45 ? -1 : 1;
                                    changePage(playerId, direction);

                                    ArrayList<Inventory> updatedPages = getBackpackPages(playerId);
                                    Inventory updatedPage = updatedPages.get(currentPageIndexMap.getOrDefault(playerId, 0));
                                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(updatedPage));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void changePage(UUID playerId, int direction) {
        ArrayList<Inventory> pages = getBackpackPages(playerId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(playerId, 0);
        int newPageIndex = currentPageIndex + direction;
        if (newPageIndex >= 0 && newPageIndex < pages.size()) {
            currentPageIndexMap.put(playerId, newPageIndex);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        InventoryView inventoryView = event.getView();
        String inventoryTitle = inventoryView.getTitle();

        if (inventoryTitle.contains("Backpack - Page")) {
            ArrayList<Inventory> pages = getBackpackPages(playerId);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveBackpackInventory(player, playerId, pages));
        }
    }

    private void saveBackpackInventory(Player player, UUID playerId, ArrayList<Inventory> pages) {
        File playerFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId.toString() + ".yml.gz");
        File tempFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".tmp.yml");
        YamlConfiguration playerConfig = new YamlConfiguration();

        try {
            synchronized (fileLock) {
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
                playerConfig.save(tempFile);
            }

            try (FileInputStream fileInputStream = new FileInputStream(tempFile);
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(playerFile))) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = fileInputStream.read(buffer)) > 0) {
                    gzipOutputStream.write(buffer, 0, len);
                }
            }

            if (!tempFile.delete()) {
                plugin.getLogger().log(Level.WARNING, "Could not delete temporary file: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving backpack YAML for player " + playerId, e);
        }
    }

    private void loadBackpackFromYAML(UUID playerId, ArrayList<Inventory> pages, File file) {
        synchronized (fileLock) {
            try {
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
            }
        }
    }

    public void saveAllBackpacks() {
        for (UUID playerId : backpacks.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            ArrayList<Inventory> pages = backpacks.get(playerId);
            CompletableFuture.runAsync(() -> saveBackpackInventory(player, playerId, pages));
            }
        }
    }
}