/*
 * This file is part of ChatEx
 * Copyright (C) 2022 ChatEx Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package de.jeter.chatex.utils;

import de.jeter.chatex.ChatEx;
import de.jeter.chatex.plugins.PluginManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class Utils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Parses user input, converting legacy/hex codes to components and minimessage tags if permitted.
     */
    public static Component parsePlayerMessage(String message, Player p) {
        if (!p.hasPermission("chatex.chat.color")) {
            return Component.text(message);
        }
        
        Component legacyComponent = LEGACY_SERIALIZER.deserialize(message);
        return parseMiniMessageDeep(legacyComponent);
    }

    private static Component parseMiniMessageDeep(Component component) {
        if (component instanceof net.kyori.adventure.text.TextComponent) {
            net.kyori.adventure.text.TextComponent textComp = (net.kyori.adventure.text.TextComponent) component;
            String content = textComp.content();
            
            Component parsedContent;
            if (content.contains("<") && content.contains(">")) {
                parsedContent = MINI_MESSAGE.deserialize(content);
                parsedContent = parsedContent.style(textComp.style().merge(parsedContent.style()));
            } else {
                parsedContent = textComp;
            }
            
            List<Component> children = textComp.children();
            if (children.isEmpty()) {
                return parsedContent;
            }
            
            List<Component> processedChildren = new ArrayList<>();
            for (Component child : children) {
                processedChildren.add(parseMiniMessageDeep(child));
            }
            
            List<Component> newChildren = new ArrayList<>(parsedContent.children());
            newChildren.addAll(processedChildren);
            return parsedContent.children(newChildren);
        }
        
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return component;
        }
        List<Component> processedChildren = new ArrayList<>();
        for (Component child : children) {
            processedChildren.add(parseMiniMessageDeep(child));
        }
        return component.children(processedChildren);
    }

    public static String translateColorCodes(String string, Player p) {
        if (!p.hasPermission("chatex.chat.color")) return string;
        Component component = parsePlayerMessage(string, p);
        return SECTION_SERIALIZER.serialize(component);
    }

    public static String replaceColors(String message) {
        String mm = convertLegacyToMiniMessage(message);
        return SECTION_SERIALIZER.serialize(MINI_MESSAGE.deserialize(mm));
    }

    public static List<Player> getLocalRecipients(Player sender) {
        Location playerLocation = sender.getLocation();
        List<Player> recipients = new ArrayList<>();

        double squaredDistance = Math.pow(Config.RANGE.getInt(), 2);
        for (Player recipient : sender.getWorld().getPlayers()) {
            if (Config.RANGE.getInt() > 0 && (playerLocation.distanceSquared(recipient.getLocation()) > squaredDistance)) {
                continue;
            }
            recipients.add(recipient);
        }

        return recipients;
    }

    public static String replacePlayerPlaceholders(Player player, String format) {
        if (player == null) {
            return format;
        }
        String result = format;

        String prefix = PluginManager.getInstance().getPrefix(player);
        String suffix = PluginManager.getInstance().getSuffix(player);
        String group = PluginManager.getInstance().getGroupNames(player).length > 0 ? PluginManager.getInstance().getGroupNames(player)[0] : "none";

        result = result.replace("%displayname", player.getDisplayName());
        result = result.replace("%prefix", prefix != null ? prefix : "");
        result = result.replace("%suffix", suffix != null ? suffix : "");
        result = result.replace("%player", player.getName());
        result = result.replace("%world", player.getWorld().getName());
        result = result.replace("%group", group != null ? group : "none");

        if (HookManager.checkPlaceholderAPI()) {
            LogHelper.debug("PlaceholderAPI is installed! Replacing...");
            result = PlaceholderAPI.setPlaceholders(player, result);
            LogHelper.debug("Result: " + result);
        }

        if ((HookManager.checkEssentials() || HookManager.checkPurpur()) && Config.AFK_PLACEHOLDER.getBoolean()) {
            result = result.replace("%afk", "");
        }

        String mm = convertLegacyToMiniMessage(result);
        return SECTION_SERIALIZER.serialize(MINI_MESSAGE.deserialize(mm));
    }

    private static Component parseLegacyPrefixSuffix(String text) {
        if (text == null) return Component.empty();
        String translated = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
        return SECTION_SERIALIZER.deserialize(translated);
    }

    /**
     * Replaces standard ChatEx placeholders but prepares them for MiniMessage Component formatting.
     */
    public static Component formatMessageToComponent(Player player, String format, Component message) {
        if (player == null) return MINI_MESSAGE.deserialize(format);
        
        String prefix = PluginManager.getInstance().getPrefix(player);
        String suffix = PluginManager.getInstance().getSuffix(player);
        String group = PluginManager.getInstance().getGroupNames(player).length > 0 ? PluginManager.getInstance().getGroupNames(player)[0] : "none";

        String parsedFormat = format;
        if (HookManager.checkPlaceholderAPI()) {
            Pattern placeholderPattern = Pattern.compile("%([^%]+)%");
            Matcher matcher = placeholderPattern.matcher(parsedFormat);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String placeholder = matcher.group(0);
                String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
                resolved = resolved.replace("<", "\\<").replace(">", "\\>");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            }
            matcher.appendTail(sb);
            parsedFormat = sb.toString();
        }
        
        if ((HookManager.checkEssentials() || HookManager.checkPurpur()) && Config.AFK_PLACEHOLDER.getBoolean()) {
            parsedFormat = parsedFormat.replace("%afk", "");
        }

        parsedFormat = parsedFormat.replace("%displayname", "<displayname>")
                                   .replace("%prefix", "<prefix>")
                                   .replace("%suffix", "<suffix>")
                                   .replace("%player", "<player>")
                                   .replace("%world", "<world>")
                                   .replace("%group", "<group>")
                                   .replace("%message", "<message>");

        String minimessageFormat = convertLegacyToMiniMessage(parsedFormat);

        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("displayname", parseLegacyPrefixSuffix(player.getDisplayName())))
                .resolver(Placeholder.component("prefix", parseLegacyPrefixSuffix(prefix)))
                .resolver(Placeholder.component("suffix", parseLegacyPrefixSuffix(suffix)))
                .resolver(Placeholder.unparsed("player", player.getName()))
                .resolver(Placeholder.unparsed("world", player.getWorld().getName()))
                .resolver(Placeholder.unparsed("group", group != null ? group : "none"))
                .resolver(Placeholder.component("message", message))
                .build();

        return MINI_MESSAGE.deserialize(minimessageFormat, resolver);
    }

    public static String convertLegacyToMiniMessage(String message) {
        if (message == null) return "";
        
        // 1. Translate Bungee/Kyori legacy hex colors like &#rrggbb or §#rrggbb to MiniMessage format <#rrggbb>
        Pattern hexPattern1 = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");
        Matcher matcher1 = hexPattern1.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher1.find()) {
            matcher1.appendReplacement(sb, "<#" + matcher1.group(1) + ">");
        }
        matcher1.appendTail(sb);
        message = sb.toString();

        // 2. Translate Bukkit hex format like &x&r&g&b or §x§r§g§b
        Pattern hexPattern2 = Pattern.compile("[&§]x[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])");
        Matcher matcher2 = hexPattern2.matcher(message);
        sb = new StringBuffer();
        while (matcher2.find()) {
            String hex = matcher2.group(1) + matcher2.group(2) + matcher2.group(3) + matcher2.group(4) + matcher2.group(5) + matcher2.group(6);
            matcher2.appendReplacement(sb, "<#" + hex + ">");
        }
        matcher2.appendTail(sb);
        message = sb.toString();

        // 3. Replace standard legacy formatting codes (both & and §)
        Pattern colorPattern = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
        Matcher matcher3 = colorPattern.matcher(message);
        sb = new StringBuffer();
        while (matcher3.find()) {
            char code = Character.toLowerCase(matcher3.group(1).charAt(0));
            String tag;
            switch (code) {
                case '0': tag = "<black>"; break;
                case '1': tag = "<dark_blue>"; break;
                case '2': tag = "<dark_green>"; break;
                case '3': tag = "<dark_aqua>"; break;
                case '4': tag = "<dark_red>"; break;
                case '5': tag = "<dark_purple>"; break;
                case '6': tag = "<gold>"; break;
                case '7': tag = "<gray>"; break;
                case '8': tag = "<dark_gray>"; break;
                case '9': tag = "<blue>"; break;
                case 'a': tag = "<green>"; break;
                case 'b': tag = "<aqua>"; break;
                case 'c': tag = "<red>"; break;
                case 'd': tag = "<light_purple>"; break;
                case 'e': tag = "<yellow>"; break;
                case 'f': tag = "<white>"; break;
                case 'k': tag = "<obfuscated>"; break;
                case 'l': tag = "<bold>"; break;
                case 'm': tag = "<strikethrough>"; break;
                case 'n': tag = "<underlined>"; break;
                case 'o': tag = "<italic>"; break;
                case 'r': tag = "<reset>"; break;
                default: tag = matcher3.group(0);
            }
            matcher3.appendReplacement(sb, tag);
        }
        matcher3.appendTail(sb);
        return sb.toString();
    }

    public static String escape(String string) {
        return string.replace("%", "%%");
    }

    public static boolean checkForBypassString(String message) {
        for (String block : Config.ADS_BYPASS.getStringList()) {
            if (message.toLowerCase().contains(block.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static void notifyOps(String msg) {
        for (Player op : ChatEx.getInstance().getServer().getOnlinePlayers()) {
            if (!op.hasPermission("chatex.notifyad")) {
                continue;
            }
            op.sendMessage(msg);
        }
    }
}