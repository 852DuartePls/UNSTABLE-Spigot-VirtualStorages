package net.duart.virtualstorage;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
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
        virtualBackpack = new VirtualBackpack(this);
        CommandManager commandManager = new CommandManager(virtualBackpack);
        Objects.requireNonNull(getCommand("backpack")).setExecutor(commandManager);
        Objects.requireNonNull(getCommand("vsreload")).setExecutor(commandManager);
        Objects.requireNonNull(getCommand("backpack")).setTabCompleter(commandManager);
        Objects.requireNonNull(getCommand("vsreload")).setTabCompleter(commandManager);
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