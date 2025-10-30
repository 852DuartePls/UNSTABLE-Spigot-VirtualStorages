package net.duart.virtualstorage.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import javax.annotation.Nonnull;

public class Messages {
    private static FileConfiguration config;

    public static void init(FileConfiguration config) {
        Messages.config = config;
    }

    public static String get(@Nonnull String path, Object... replacements) {
        String raw = config.getString("messages." + path);
        if (raw == null) {
            raw = getDefault(path);
        }
        raw = ChatColor.translateAlternateColorCodes('&', raw);
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return raw;
    }

    private static String getDefault(@Nonnull String path) {
        return switch (path) {
            case "title" -> "&9◆ Backpack - Page %page% of %maxpages% ◆";
            case "prevArrow" -> "&c<< Previous page";
            case "nextArrow" -> "&aNext page >>";
            case "waitToOpen" -> "&eWait a second to open your backpack again.";
            case "noPermission" -> "&cYou do not have permissions to open backpacks.";
            case "noCommandPermission" -> "&cYou do not have permissions to use this command.";
            case "noPermissionOther" -> "&cThis player does not have any permissions.";
            case "backpackInUse" -> "&cThat player's backpack is currently in use. Try again in a moment.";
            case "itemsRecovered" -> "&aYour previous stored items were recovered to your backpack!";
            case "itemsOverflowed" -> "&eOh no! You lost permission to access some pages in your backpack, so some items were safely stored until you can access them again.";
            case "reloadDone" -> "&aLanguage file reloaded.";
            default -> "";
        };
    }
}
