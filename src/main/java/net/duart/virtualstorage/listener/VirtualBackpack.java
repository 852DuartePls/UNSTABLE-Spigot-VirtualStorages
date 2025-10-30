package net.duart.virtualstorage.listener;

import net.duart.virtualstorage.util.FileHandlers;
import net.duart.virtualstorage.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class VirtualBackpack implements Listener {

    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Integer> currentPageIndexMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ArrayList<Inventory>> backpacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Player> adminToTargetMap = new ConcurrentHashMap<>();
    private final Set<Inventory> backpackInventories = new HashSet<>();

    private final FileHandlers fileHandlers;
    private final NamespacedKey NAV_KEY;

    public VirtualBackpack(Plugin plugin, FileHandlers fileHandlers) {
        this.plugin = plugin;
        this.fileHandlers = fileHandlers;
        NAV_KEY = new NamespacedKey(plugin, "nav");
    }

    public void openBackpack(@Nonnull Player player) {
        UUID playerId = player.getUniqueId();

        if (isBackpackOpen(playerId)) {
            player.sendMessage(Messages.get("waitToOpen"));
            return;
        }

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
            ensurePageCountMatchesPermissions(playerId, pages, false);
            refreshPagesAndNavigation(pages);
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
            admin.sendMessage(Messages.get("noPermissionOther"));
            return;
        }

        UUID targetId = target.getUniqueId();

        if (isBackpackOpen(targetId)) {
            admin.sendMessage(Messages.get("backpackInUse"));
            return;
        }

        ArrayList<Inventory> targetPages = getBackpackPages(targetId);

        File gzippedFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml.gz");
        File yamlFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml");

        CompletableFuture.runAsync(() -> {
            try {
                if (gzippedFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(targetId, targetPages, gzippedFile);
                } else if (yamlFile.exists()) {
                    fileHandlers.loadBackpackFromYAML(targetId, targetPages, yamlFile);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error handling backpack files for player " + target.getName(), e);
            }
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            ensurePageCountMatchesPermissions(targetId, targetPages, true);
            refreshPagesAndNavigation(targetPages);
            adminToTargetMap.put(admin, target);
            admin.openInventory(targetPages.get(0));
        }));
    }

    private void ensurePageCountMatchesPermissions(UUID playerId, @Nonnull ArrayList<Inventory> pages, boolean isAdmin) {
        int allowedPages = getMaxPages(playerId);
        int currentPages = pages.size();

        if (currentPages < allowedPages) {
            for (int i = currentPages; i < allowedPages; i++) {
                Inventory page = Bukkit.createInventory(null, 54, buildTitle(i + 1, pages.size()));
                pages.add(page);
                registerBackpackInventory(page);
            }

            List<ItemStack> overflowItems = fileHandlers.loadOverflowItems(playerId);
            if (!overflowItems.isEmpty()) {
                Inventory lastPage = pages.get(pages.size() - 1);
                for (ItemStack item : new ArrayList<>(overflowItems)) {
                    int slot = findFirstFreeNonNavSlot(lastPage, false);
                    if (slot != -1) {
                        lastPage.setItem(slot, item);
                        overflowItems.remove(item);
                    } else break;
                }

                if (overflowItems.isEmpty()) {
                    try {
                        Files.deleteIfExists(Paths.get(plugin.getDataFolder().getPath(), playerId + ".overflow.yml.gz"));
                    } catch (IOException e) {
                        plugin.getLogger().warning("Failed to delete overflow file for player: " + playerId);
                    }
                } else {
                    fileHandlers.saveOverflowItems(playerId, overflowItems);
                }

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Messages.get("itemsRecovered"));
                }
            }
        } else if (currentPages > allowedPages) {
            List<ItemStack> overflowItems = new ArrayList<>();

            while (pages.size() > allowedPages) {
                Inventory lastPage = pages.remove(pages.size() - 1);
                unregisterBackpackInventory(lastPage);
                for (ItemStack item : lastPage.getContents()) {
                    if (item != null && !isNavigationItem(item)) {
                        Inventory lastAllowed = pages.get(pages.size() - 1);
                        int freeSlot = findFirstFreeNonNavSlot(lastAllowed, true);
                        if (freeSlot != -1) {
                            lastAllowed.setItem(freeSlot, item);
                        } else {
                            overflowItems.add(item.clone());
                        }
                    }
                }
            }

            if (!overflowItems.isEmpty()) {
                fileHandlers.saveOverflowItems(playerId, overflowItems);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Messages.get("itemsOverflowed"));
                }
            }
        }

        if (isAdmin) {
            List<ItemStack> overflowItems = fileHandlers.loadOverflowItems(playerId);
            while (!overflowItems.isEmpty()) {
                Inventory overflowPage = Bukkit.createInventory(null, 54, buildTitle(pages.size() + 1, "OVERFLOW"));
                registerBackpackInventory(overflowPage);

                for (ItemStack item : new ArrayList<>(overflowItems)) {
                    int slot = findFirstFreeNonNavSlot(overflowPage, false);
                    if (slot != -1) {
                        overflowPage.setItem(slot, item);
                        overflowItems.remove(item);
                    } else break;
                }

                pages.add(overflowPage);
            }
        }

        for (int i = 0; i < pages.size(); i++) {
            Inventory oldPage = pages.get(i);
            Inventory newPage = Bukkit.createInventory(null, 54, buildTitle(i + 1, pages.size()));
            newPage.setContents(oldPage.getContents());
            pages.set(i, newPage);
            registerBackpackInventory(newPage);
        }

        addNavigationItems(pages);
    }

    private int findFirstFreeNonNavSlot(@Nonnull Inventory inv, boolean allowSlot53IfOccupied) {
        for (int s = 0; s < inv.getSize(); s++) {
            if (s == 45) continue;

            ItemStack it = inv.getItem(s);

            if (s == 53) {
                if (isNavigationItem(it)) continue;

                if (it == null) return s;

                if (allowSlot53IfOccupied) return s;
                continue;
            }

            if (it == null) return s;
        }
        return -1;
    }

    private ArrayList<Inventory> getBackpackPages(UUID playerId) {
        int maxPages = getMaxPages(playerId);
        return backpacks.computeIfAbsent(playerId, k -> createNewBackpackPages(maxPages));
    }

    @Nonnull
    private ArrayList<Inventory> createNewBackpackPages(int maxPages) {
        ArrayList<Inventory> pages = new ArrayList<>();
        for (int i = 0; i < maxPages; i++) {
            Inventory page = Bukkit.createInventory(null, 54, buildTitle(i + 1, maxPages));
            pages.add(page);
            registerBackpackInventory(page);
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

    @Nonnull
    private ItemStack createNavigationItem(String displayName) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addNavigationItems(@Nonnull List<Inventory> pages) {
        int totalPages = pages.size();
        UUID playerId = getPlayerIdByInventory(pages);

        for (int i = 0; i < totalPages; i++) {
            Inventory page = pages.get(i);

            ItemStack cur45 = page.getItem(45);
            if (i > 0) {
                if (cur45 == null || isNavigationItem(cur45)) {
                    page.setItem(45, createNavigationItem(Messages.get("prevArrow")));
                }
            } else {
                if (isNavigationItem(cur45)) page.setItem(45, null);
            }

            ItemStack cur53 = page.getItem(53);
            if (i < totalPages - 1) {
                if (cur53 == null || isNavigationItem(cur53)) {
                    page.setItem(53, createNavigationItem(Messages.get("nextArrow")));
                } else {
                    ItemStack toMove = cur53.clone();
                    page.setItem(53, null);
                    handleSlotItem(pages, i, totalPages, playerId, toMove);
                    page.setItem(53, createNavigationItem(Messages.get("nextArrow")));
                }
            } else {
                if (isNavigationItem(cur53)) page.setItem(53, null);
            }
        }
    }

    private void handleSlotItem(@Nonnull List<Inventory> pages, int pageIndex, int totalPages, UUID playerId) {
        Inventory sourcePage = pages.get(pageIndex);
        ItemStack item = sourcePage.getItem(53);
        if (item == null || isNavigationItem(item)) return;

        for (int j = pageIndex + 1; j < totalPages; j++) {
            int free = findFirstFreeNonNavSlot(pages.get(j), true);
            if (free != -1) {
                pages.get(j).setItem(free, item);
                return;
            }
        }

        if (playerId != null) {
            List<ItemStack> overflow = new ArrayList<>(fileHandlers.loadOverflowItems(playerId));
            overflow.add(item);
            fileHandlers.saveOverflowItems(playerId, overflow);
        }
    }

    private void handleSlotItem(List<Inventory> pages, int pageIndex, int totalPages, UUID playerId, ItemStack item) {

        for (int j = pageIndex + 1; j < totalPages; j++) {
            int free = findFirstFreeNonNavSlot(pages.get(j), true);
            if (free != -1) {
                pages.get(j).setItem(free, item);
                return;
            }
        }

        if (playerId != null) {
            List<ItemStack> overflow = new ArrayList<>(fileHandlers.loadOverflowItems(playerId));
            overflow.add(item);
            fileHandlers.saveOverflowItems(playerId, overflow);
        }
    }

    private void refreshPagesAndNavigation(@Nonnull ArrayList<Inventory> pages) {
        int totalPages = pages.size();
        UUID playerId = getPlayerIdByInventory(pages);

        for (int i = 0; i < totalPages; i++) {
            Inventory page = pages.get(i);

            if (i < totalPages - 1) {
                handleSlotItem(pages, i, totalPages, playerId);
            }

            if (totalPages == 1) {
                for (int navSlot : new int[]{45, 53}) {
                    ItemStack navItem = page.getItem(navSlot);
                    if (isNavigationItem(navItem)) page.setItem(navSlot, null);
                }
            } else {
                if (i > 0) page.setItem(45, createNavigationItem(Messages.get("prevArrow")));
                if (i < totalPages - 1) page.setItem(53, createNavigationItem(Messages.get("nextArrow")));
            }
        }
    }

    @Nullable
    private UUID getPlayerIdByInventory(List<Inventory> pages) {
        for (UUID id : backpacks.keySet()) {
            if (backpacks.get(id) == pages) return id;
        }
        return null;
    }

    private boolean isNavigationItem(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null &&
                meta.getPersistentDataContainer().has(NAV_KEY, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(@Nonnull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null || !isBackpackInventory(clickedInventory)) {
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
        if (clickedItem == null || clickedItem.getType() != Material.ARROW) return;
        if (!isNavigationItem(clickedItem)) return;

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
        changePage(targetId, direction, player);

        int updatedPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);
        Inventory updatedPage = getBackpackPages(targetId).get(updatedPageIndex);
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(updatedPage));
    }

    private void changePage(UUID targetId, int direction, Player viewer) {
        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);
        int newPageIndex = currentPageIndex + direction;

        boolean viewerIsAdminViewingTarget = adminToTargetMap.containsKey(viewer) && adminToTargetMap.get(viewer).getUniqueId().equals(targetId);

        int allowedMax;
        if (viewerIsAdminViewingTarget) {
            allowedMax = pages.size();
        } else {
            allowedMax = getMaxPages(targetId);
            if (allowedMax <= 0) allowedMax = 1;
        }

        if (newPageIndex >= 0 && newPageIndex < allowedMax) {
            currentPageIndexMap.put(targetId, newPageIndex);
        }
    }

    @EventHandler
    public void onInventoryClose(@Nonnull InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        Inventory closedInventory = event.getInventory();

        if (!isBackpackInventory(closedInventory)) {
            return;
        }

        UUID targetId = playerId;
        boolean isAdmin = false;
        if (adminToTargetMap.containsKey(player)) {
            targetId = adminToTargetMap.get(player).getUniqueId();
            isAdmin = true;
        }

        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(targetId, 0);

        if (currentPageIndex >= pages.size() || !closedInventory.equals(pages.get(currentPageIndex))) {
            return;
        }

        pages.set(currentPageIndex, closedInventory);

        if (isAdmin) {
            int maxPages = getMaxPages(targetId);
            List<ItemStack> overflowItems = new ArrayList<>();

            for (int i = maxPages; i < pages.size(); i++) {
                for (ItemStack item : pages.get(i).getContents()) {
                    if (item != null && !isNavigationItem(item)) {
                        overflowItems.add(item.clone());
                    }
                }
            }

            if (!overflowItems.isEmpty()) {
                fileHandlers.saveOverflowItems(targetId, overflowItems);
            } else {
                try {
                    Files.deleteIfExists(Paths.get(plugin.getDataFolder().getPath(), targetId + ".overflow.yml.gz"));
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to delete overflow file for player: " + targetId);
                }
            }

            ArrayList<Inventory> allowedPages = new ArrayList<>();
            for (int i = 0; i < maxPages && i < pages.size(); i++) {
                allowedPages.add(pages.get(i));
            }
            fileHandlers.saveBackpackInventoryForTarget(targetId, allowedPages);
        } else {
            fileHandlers.saveBackpackInventoryForTarget(targetId, pages);
        }

        currentPageIndexMap.put(targetId, 0);
    }

    public void saveAllBackpacks() {
        if (!adminToTargetMap.isEmpty()) {
            for (Player admin : adminToTargetMap.keySet()) {
                Player target = adminToTargetMap.get(admin);
                UUID targetId = target.getUniqueId();
                ArrayList<Inventory> pages = getBackpackPages(targetId);
                fileHandlers.saveBackpackInventoryForTarget(targetId, pages);
            }
        } else {
            for (UUID playerId : backpacks.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    ArrayList<Inventory> pages = backpacks.get(playerId);
                    fileHandlers.saveBackpackInventory(player, playerId, pages);
                }
            }
        }
    }

    private boolean isBackpackOpen(UUID targetId) {
        List<String> validTitles = new ArrayList<>();
        for (Inventory inv : getBackpackPages(targetId)) {
            validTitles.add(inv.getViewers().isEmpty() ? "" : inv.getViewers().get(0).getOpenInventory().getTitle());
            if (validTitles.get(validTitles.size() - 1).isEmpty()) {
                int idx = getBackpackPages(targetId).indexOf(inv);
                validTitles.set(validTitles.size() - 1,
                        buildTitle(idx + 1, getBackpackPages(targetId).size()));
            }
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            String open = target.getOpenInventory().getTitle();
            if (validTitles.contains(open)) return true;
        }
        for (Player admin : adminToTargetMap.keySet()) {
            if (adminToTargetMap.get(admin).getUniqueId().equals(targetId)) {
                String open = admin.getOpenInventory().getTitle();
                if (validTitles.contains(open)) return true;
            }
        }
        return false;
    }

    private String buildTitle(int page, Object maxPages) {
        return Messages.get("title", "%page%", String.valueOf(page), "%maxpages%", String.valueOf(maxPages));
    }

    private void registerBackpackInventory(@Nonnull Inventory inventory) {
        backpackInventories.add(inventory);
    }

    private boolean isBackpackInventory(@Nonnull Inventory inventory) {
        return backpackInventories.contains(inventory);
    }

    private void unregisterBackpackInventory(@Nonnull Inventory inventory) {
        backpackInventories.remove(inventory);
    }
}