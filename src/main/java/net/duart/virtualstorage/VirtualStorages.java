package net.duart.virtualstorage;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.ConsoleCommandSender;

public final class VirtualStorages extends JavaPlugin {

    public static ConsoleCommandSender cCSender;
    private VirtualBackpack virtualBackpack;

    @Override
    public void onEnable() {
        cCSender = getServer().getConsoleSender();
        cCSender.sendMessage(ChatColor.YELLOW + "||   VirtualStorages   ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||    by DaveDuart     ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||  Enabled correctly  ||");

        FileHandlers fileHandlers = new FileHandlers(this);
        virtualBackpack = new VirtualBackpack(this, fileHandlers);
        CommandManager commandManager = new CommandManager(virtualBackpack);

        List<String> commands = Arrays.asList("backpack", "vsreload", "backpackview");

        commands.forEach(command -> {
            PluginCommand cmd = Objects.requireNonNull(getCommand(command));
            cmd.setExecutor(commandManager);
            cmd.setTabCompleter(commandManager);
        });

        getServer().getPluginManager().registerEvents(virtualBackpack, this);

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            if (dataFolder.mkdirs()) {
                getLogger().info("VirtualStorages folder created.");
            } else {
                getLogger().warning("Failed to create VirtualStorages folder.");
            }
        } else {
            getLogger().info("VirtualStorages folder detected.");
        }
    }

    @Override
    public void onDisable() {
        if (virtualBackpack != null) {
            virtualBackpack.saveAllBackpacks();
            virtualBackpack.createBackup();
        }
        cCSender.sendMessage(ChatColor.YELLOW + "||   VirtualStorages   ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||      Disabled       ||");
    }
}