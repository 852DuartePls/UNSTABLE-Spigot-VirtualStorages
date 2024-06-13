package net.duart.virtualstorage;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Objects;

public final class VirtualStorages extends JavaPlugin {

    private VirtualBackpack virtualBackpack;

    @Override
    public void onEnable() {
        getLogger().info("||   VirtualStorages   ||");
        getLogger().info("||  Enabled correctly  ||");
        getLogger().info("||    by DaveDuart     ||");
        virtualBackpack = new VirtualBackpack(this);

        CommandManager commandManager = new CommandManager(new VirtualBackpack(this));
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
            virtualBackpack.createBackup();
            virtualBackpack.saveAllBackpacks();
        }
        getLogger().info("VirtualStorages Disabled.");
    }
}