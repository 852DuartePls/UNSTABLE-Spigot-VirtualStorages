package net.duart.virtualstorage;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class CommandManager implements CommandExecutor {

    private final VirtualBackpack virtualBackpack;

    public CommandManager(VirtualBackpack virtualBackpack) {
        this.virtualBackpack = virtualBackpack;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backpack")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
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
                virtualBackpack.updatePermissions();
                sender.sendMessage(ChatColor.GREEN + "VirtualStorages permissions reloaded.");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        }
        return false;
    }
}