package net.duart.virtualstorage;

import net.duart.virtualstorage.commands.CommandManager;
import net.duart.virtualstorage.listener.VirtualBackpack;
import net.duart.virtualstorage.util.FileHandlers;
import net.duart.virtualstorage.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

import org.bukkit.command.ConsoleCommandSender;

public final class VirtualStorages extends JavaPlugin {

    public static ConsoleCommandSender cCSender;
    private VirtualBackpack virtualBackpack;

    @Override
    public void onEnable() {
        cCSender = getServer().getConsoleSender();
        saveDefaultConfig();

        Messages.init(getConfig());

        FileHandlers fileHandlers = new FileHandlers(this);
        virtualBackpack = new VirtualBackpack(this, fileHandlers);
        CommandManager commandManager = new CommandManager(virtualBackpack, this);

        List<String> commands = Arrays.asList("backpack", "vsreload" , "backpackview");

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

        cCSender.sendMessage(ChatColor.YELLOW + "||   VirtualStorages   ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||    by DaveDuart     ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||  Enabled correctly  ||");
    }

    public void reloadLanguage() {
        reloadConfig();
        Messages.init(getConfig());
    }

    @Override
    public void onDisable() {
        if (virtualBackpack != null) {
            try {
                virtualBackpack.saveAllBackpacks();
                virtualBackpack.createBackup();
                virtualBackpack.unloadAllBackpacks();
                getLogger().info("All backpacks saved and unloaded successfully");
            } catch (Exception e) {
                getLogger().severe("Error during shutdown: " + e.getMessage());
            }
        }

        cCSender.sendMessage(ChatColor.YELLOW + "||   VirtualStorages   ||");
        cCSender.sendMessage(ChatColor.YELLOW + "||      Disabled       ||");
    }
}
