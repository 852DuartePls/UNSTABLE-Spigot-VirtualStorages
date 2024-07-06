package net.duart.virtualstorage;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final VirtualBackpack virtualBackpack;

    public CommandManager(VirtualBackpack virtualBackpack) {
        this.virtualBackpack = virtualBackpack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backpack")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                return true;
            }

            for (int i = 1; i <= 999; i++) {
                if (player.hasPermission("virtualstorages.use." + i)) {
                    virtualBackpack.openBackpack(player);
                    return true;
                }
            }

            player.sendMessage(ChatColor.RED + "You do not have the permissions to open a Virtual Backpack");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vsreload")) {
            if (!sender.hasPermission("virtualstorages.admin.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            virtualBackpack.reloadVirtualStorages();
            sender.sendMessage(ChatColor.GREEN + "Updating player inventories...");
            boolean success = virtualBackpack.updatePlayerInventories();
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Backpack Permissions have been successfully reloaded");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to reload Virtual Storages. Check console for errors.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("backpackview")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                return true;
            }

            if (!admin.hasPermission("virtualstorage.admin")) {
                admin.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                admin.sendMessage(ChatColor.RED + "Usage: /backpackview <player>");
                return true;
            }

            Player target = admin.getServer().getPlayer(args[0]);
            if (target == null) {
                admin.sendMessage(ChatColor.RED + "Player not found or offline.");
                return true;
            }

            virtualBackpack.openTargetBackpack(admin, target);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("backpackview")) {
            if (args.length == 1) {
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 0) {
            completions.add("backpack");
            completions.add("vsreload");
        }
        return completions;
    }
}