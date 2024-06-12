package net.duart.virtualstorage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.List;


public class CommandManager implements CommandExecutor, TabCompleter {

    private final VirtualBackpack virtualBackpack;

    public CommandManager(VirtualBackpack virtualBackpack) {
        this.virtualBackpack = virtualBackpack;
    }

    public boolean onCommand(@NonNull CommandSender sender,@NonNull Command command,@NonNull String label,@NonNull String[] args) {
        if (command.getName().equalsIgnoreCase("backpack")) {
            if (sender instanceof Player player) {
                for (int i = 1; i <= 999; i++) {
                    if (player.hasPermission("virtualstorages.use." + i)) {
                        virtualBackpack.openBackpack(player);
                        return true;
                    }
                }
                player.sendMessage(ChatColor.RED + "You are not allowed to use the backpack.");
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("vsreload")) {
            if (sender.hasPermission("virtualstorages.admin.reload")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    InventoryView openInventory = player.getOpenInventory();
                    if (openInventory.getTitle().contains("Backpack - Page")) {
                        player.closeInventory();
                    }
                }
                virtualBackpack.updatePermissions();
                sender.sendMessage(ChatColor.GREEN + "VirtualStorages permissions reloaded.");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("backpack");
            completions.add("vsreload");
        }
        return completions;
    }
}