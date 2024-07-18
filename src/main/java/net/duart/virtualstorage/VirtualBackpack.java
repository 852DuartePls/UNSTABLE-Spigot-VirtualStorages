package net.duart.virtualstorage;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class VirtualBackpack implements Listener {

    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Integer> currentPageIndexMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ArrayList<Inventory>> backpacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Player> adminToTargetMap = new ConcurrentHashMap<>();
    private final FileHandlers fileHandlers;

    public VirtualBackpack(Plugin plugin, FileHandlers fileHandlers) {
        this.plugin = plugin;
        this.fileHandlers = fileHandlers;
    }

    public void openBackpack(Player player) {
        UUID playerId = player.getUniqueId();
        currentPageIndexMap.put(playerId, 0);
        ArrayList<Inventory> pages = getBackpackPages(playerId);

        CompletableFuture.runAsync(() -> {
            File gzippedFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml.gz");
            File yamlFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml");

            try {
                if (gzippedFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(playerId, pages, gzippedFile);
                } else if (yamlFile.exists() || !gzippedFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(playerId, pages, yamlFile);
                } else {
                    fileHandlers.saveBackpackInventory(player, playerId, pages);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading/saving backpack for player " + player.getName(), e);
            }
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            moveItemsToNextPageIfNecessary(pages);
            player.openInventory(pages.get(0));
            adminToTargetMap.remove(player);
        }));
    }

    public void openTargetBackpack(Player admin, Player target) {
        boolean hasPermission = false;

        for (int i = 999; i >= 1; i--) {
            if (target.hasPermission("virtualstorages.use." + i)) {
                hasPermission = true;
                break;
            }
        }

        if (!hasPermission) {
            admin.sendMessage(ChatColor.RED + "This player does not have any permissions.");
            return;
        }

        UUID targetId = target.getUniqueId();
        ArrayList<Inventory> targetPages = getBackpackPages(targetId);

        File gzippedFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml.gz");
        File yamlFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml");

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                if (gzippedFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(targetId, targetPages, gzippedFile);
                } else if (yamlFile.exists() || !gzippedFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(targetId, targetPages, yamlFile);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error handling backpack files for player " + target.getName(), e);
            }

            adminToTargetMap.put(admin, target);
        });
        future.thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (adminToTargetMap.containsKey(admin)) {
                admin.openInventory(targetPages.get(0));
            } else {
                admin.sendMessage(ChatColor.RED + "An error happened while trying to open the target's inventory");
            }
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
        CompletableFuture.runAsync(() -> {
            saveAllBackpacks();
            updatePlayerInventories();
        });
    }

    public boolean updatePlayerInventories() {
        AtomicBoolean success = new AtomicBoolean(true);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                try {
                    int newMaxPages = getMaxPages(playerId);
                    ArrayList<Inventory> pages = backpacks.computeIfAbsent(playerId, k -> createNewBackpackPages(newMaxPages));
                    int oldMaxPages = pages.size();

                    if (newMaxPages > oldMaxPages) {
                        for (int i = oldMaxPages; i < newMaxPages; i++) {
                            Inventory page = Bukkit.createInventory(null, 54, "§9◆ Backpack - Page " + (i + 1) + " of " + newMaxPages + " ◆");
                            pages.add(page);
                        }
                    } else if (newMaxPages < oldMaxPages) {
                        pages.subList(newMaxPages, oldMaxPages).clear();
                    }

                    for (int i = 0; i < pages.size(); i++) {
                        Inventory page = pages.get(i);
                        page.clear();
                        page = Bukkit.createInventory(null, 54, "§9◆ Backpack - Page " + (i + 1) + " of " + pages.size() + " ◆");
                        pages.set(i, page);
                    }

                    moveItemsToNextPageIfNecessary(pages);
                    addNavigationItems(pages);
                } catch (Exception e) {
                    success.set(false);
                    plugin.getLogger().log(Level.SEVERE, "Error updating player inventories", e);
                }
            }
        });
        return success.get();
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        InventoryView inventoryView = event.getView();
        String inventoryTitle = inventoryView.getTitle();

        if (!inventoryTitle.contains("Backpack - Page")) {
            return;
        }

        UUID targetId = playerId;
        if (adminToTargetMap.containsKey(player)) {
            targetId = adminToTargetMap.get(player).getUniqueId();
        }

        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);
        Inventory currentPage = pages.get(currentPageIndex);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(currentPage)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.ARROW) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        String displayName = meta.getDisplayName();
        if (!displayName.equals("§c<< ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ") && !displayName.equals("§aɴᴇxᴛ ᴘᴀɢᴇ >>")) {
            return;
        }

        int slot = event.getSlot();
        if (slot != 45 && slot != 53) {
            return;
        }

        boolean isLeftClick = event.getClick().isLeftClick();
        boolean isShiftClick = event.isShiftClick();
        boolean isNumericKey = event.getClick().isKeyboardClick();
        if (!isLeftClick && !isShiftClick && !isNumericKey) {
            return;
        }

        event.setCancelled(true);
        int direction = slot == 45 ? -1 : 1;
        changePage(targetId, direction);

        int updatedPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);
        Inventory updatedPage = getBackpackPages(targetId).get(updatedPageIndex);
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(updatedPage));
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

        if (!inventoryTitle.contains("Backpack - Page")) {
            return;
        }

        UUID targetId = playerId;
        if (adminToTargetMap.containsKey(player)) {
            targetId = adminToTargetMap.get(player).getUniqueId();
        }

        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);

        Inventory closedInventory = event.getInventory();
        if (currentPageIndex >= pages.size() || !closedInventory.equals(pages.get(currentPageIndex))) {
            return;
        }

        pages.set(currentPageIndex, closedInventory);
        fileHandlers.saveBackpackInventoryForTarget(targetId, pages);
        currentPageIndexMap.put(targetId, 0);
    }

    public void saveAllBackpacks() {
        if (!adminToTargetMap.isEmpty()) {
            for (Player admin : adminToTargetMap.keySet()) {
                Player target = adminToTargetMap.get(admin);
                UUID targetId = target.getUniqueId();
                ArrayList<Inventory> pages = getBackpackPages(targetId);
                CompletableFuture.runAsync(() -> fileHandlers.saveBackpackInventoryForTarget(targetId, pages));
            }
        } else {
            for (UUID playerId : backpacks.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    ArrayList<Inventory> pages = backpacks.get(playerId);
                    CompletableFuture.runAsync(() -> fileHandlers.saveBackpackInventory(player, playerId, pages));
                }
            }
        }
    }
}