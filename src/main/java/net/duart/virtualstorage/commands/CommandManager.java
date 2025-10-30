package net.duart.virtualstorage.commands;

import net.duart.virtualstorage.VirtualStorages;
import net.duart.virtualstorage.listener.VirtualBackpack;
import net.duart.virtualstorage.util.Messages;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final VirtualBackpack virtualBackpack;
    private final VirtualStorages virtualStorages;

    public CommandManager(VirtualBackpack virtualBackpack, VirtualStorages virtualStorages) {
        this.virtualStorages = virtualStorages;
        this.virtualBackpack = virtualBackpack;
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender,@Nonnull Command command,@Nonnull String label,@Nonnull String[] args) {
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

            player.sendMessage(Messages.get("noPermission"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("backpackview")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                return true;
            }

            if (!admin.hasPermission("virtualstorages.admin")) {
                admin.sendMessage(Messages.get("noCommandPermission"));
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

        if (command.getName().equalsIgnoreCase("vsreload")) {
            if (!sender.hasPermission("virtualstorages.admin")) {
                sender.sendMessage(Messages.get("noCommandPermission"));
                return true;
            }
            virtualStorages.reloadLanguage();
            sender.sendMessage(Messages.get("reloadDone"));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String alias, @Nonnull String[] args) {

        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("backpackview") &&
                sender.hasPermission("virtualstorages.admin") &&
                args.length == 1) {

            String partial = args[0].toLowerCase();
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        return completions;
    }
}
