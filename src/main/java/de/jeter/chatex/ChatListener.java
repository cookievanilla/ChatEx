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
package de.jeter.chatex;

import de.jeter.chatex.api.events.*;
import de.jeter.chatex.plugins.PluginManager;
import de.jeter.chatex.utils.*;
import de.jeter.chatex.utils.adManager.AdManager;
import de.jeter.chatex.utils.adManager.SimpleAdManager;
import de.jeter.chatex.utils.adManager.SmartAdManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final AdManager adManager = Config.ADS_SMART_MANAGER.getBoolean() ? new SmartAdManager() : new SimpleAdManager();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLowest(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("LOWEST")) {
            executeChatEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLow(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("LOW")) {
            executeChatEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNormal(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("NORMAL")) {
            executeChatEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHigh(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("HIGH")) {
            executeChatEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHighest(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("HIGHEST")) {
            executeChatEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonitor(final AsyncChatEvent event) {
        if (Config.PRIORITY.getString().equalsIgnoreCase("MONITOR")) {
            executeChatEvent(event);
        }
    }

    private void executeChatEvent(AsyncChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("chatex.allowchat")) {
            String msg = Locales.COMMAND_RESULT_NO_PERM.getString(player).replaceAll("%perm", "chatex.allowchat");
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }

        String format = PluginManager.getInstance().getMessageFormat(event.getPlayer());
        String chatMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (!AntiSpamManager.getInstance().isAllowed(event.getPlayer())) {
            long remainingTime = AntiSpamManager.getInstance().getRemainingSeconds(event.getPlayer());
            String message = Locales.ANTI_SPAM_DENIED.getString(event.getPlayer()).replaceAll("%time%", remainingTime + "");
            MessageBlockedBySpamManagerEvent messageBlockedBySpamManagerEvent = new MessageBlockedBySpamManagerEvent(event.getPlayer(), chatMessage, message, remainingTime, event.isAsynchronous());
            Bukkit.getPluginManager().callEvent(messageBlockedBySpamManagerEvent);
            if (!messageBlockedBySpamManagerEvent.isCancelled()) {
                event.getPlayer().sendMessage(messageBlockedBySpamManagerEvent.getPluginMessage());
                event.setCancelled(true);
                return;
            }
            chatMessage = messageBlockedBySpamManagerEvent.getMessage();
        }
        AntiSpamManager.getInstance().put(player);

        if (adManager.checkForAds(chatMessage, player)) {
            String message = Locales.MESSAGES_AD.getString(null).replaceAll("%perm", "chatex.bypassads");
            MessageBlockedByAdManagerEvent messageBlockedByAdManagerEvent = new MessageBlockedByAdManagerEvent(player, chatMessage, message, event.isAsynchronous());
            Bukkit.getPluginManager().callEvent(messageBlockedByAdManagerEvent);
            if (!messageBlockedByAdManagerEvent.isCancelled()) {
                event.getPlayer().sendMessage(messageBlockedByAdManagerEvent.getPluginMessage());
                event.setCancelled(true);
                return;
            }
            chatMessage = messageBlockedByAdManagerEvent.getMessage();
        }

        for (String block : Config.BLOCKED_WORDS.getStringList()) {
            if (chatMessage.toLowerCase().contains(block.toLowerCase())) {
                String message = Locales.MESSAGES_BLOCKED.getString(null);
                MessageContainsBlockedWordEvent messageContainsBlockedWordEvent = new MessageContainsBlockedWordEvent(player, chatMessage, message, event.isAsynchronous());
                Bukkit.getPluginManager().callEvent(messageContainsBlockedWordEvent);
                if (!messageContainsBlockedWordEvent.isCancelled()) {
                    event.getPlayer().sendMessage(messageContainsBlockedWordEvent.getPluginMessage());
                    event.setCancelled(true);
                    return;
                }
                chatMessage = messageContainsBlockedWordEvent.getMessage();
            }
        }

        boolean global = false;
        String finalFormat = format;
        String finalChatMessage = chatMessage;

        if (Config.RANGEMODE.getBoolean() || Config.BUNGEECORD.getBoolean()) {
            if ((Config.RANGEMODE.getBoolean() && chatMessage.startsWith(Config.RANGEPREFIX.getString())) || Config.BUNGEECORD.getBoolean()) {
                if (player.hasPermission("chatex.chat.global")) {
                    chatMessage = chatMessage.replaceFirst(Pattern.quote(Config.RANGEPREFIX.getString()), "");
                    finalFormat = PluginManager.getInstance().getGlobalMessageFormat(player);
                    global = true;

                    PlayerUsesGlobalChatEvent playerUsesGlobalChatEvent = new PlayerUsesGlobalChatEvent(player, chatMessage, event.isAsynchronous());
                    Bukkit.getPluginManager().callEvent(playerUsesGlobalChatEvent);
                    if (playerUsesGlobalChatEvent.isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }
                    chatMessage = playerUsesGlobalChatEvent.getMessage();
                    finalChatMessage = chatMessage;
                } else {
                    player.sendMessage(Locales.COMMAND_RESULT_NO_PERM.getString(player).replaceAll("%perm", "chatex.chat.global"));
                    event.setCancelled(true);
                    return;
                }
            } else {
                if (Config.RANGEMODE.getBoolean()) {
                    event.setCancelled(true);
                    String rangeMessage = chatMessage;
                    String rangeFormat = format;

                    ChatEx.getFoliaLib().getScheduler().runAtEntity(player, (task) -> {
                        List<Player> recipients = Utils.getLocalRecipients(player);
                        if (recipients.size() <= 1 && Config.SHOW_NO_RECEIVER_MSG.getBoolean()) {
                            player.sendMessage(Locales.NO_LISTENING_PLAYERS.getString(player));
                            return;
                        }

                        PlayerUsesRangeModeEvent playerUsesRangeModeEvent = new PlayerUsesRangeModeEvent(player, rangeMessage);
                        Bukkit.getPluginManager().callEvent(playerUsesRangeModeEvent);
                        if (playerUsesRangeModeEvent.isCancelled()) {
                            return;
                        }
                        String messageToSend = playerUsesRangeModeEvent.getMessage();

                        Component builtMessage = Utils.formatMessageToComponent(player, rangeFormat, Utils.parsePlayerMessage(messageToSend, player));
                        
                        for (Player recipient : recipients) {
                            recipient.sendMessage(builtMessage);
                        }
                        Bukkit.getConsoleSender().sendMessage(builtMessage);
                        ChatLogger.writeToFile(player, messageToSend);
                    });
                    return;
                }
            }
        }

        if (global && Config.BUNGEECORD.getBoolean()) {
            Component toSend = Utils.formatMessageToComponent(player, finalFormat, Utils.parsePlayerMessage(finalChatMessage, player));
            String msgToSend = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(toSend);
            ChannelHandler.getInstance().sendMessage(player, msgToSend);
        }

        String rendererFormat = finalFormat;
        String rendererMessage = finalChatMessage;
        
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            return Utils.formatMessageToComponent(player, rendererFormat, Utils.parsePlayerMessage(rendererMessage, player));
        });

        ChatLogger.writeToFile(player, chatMessage);
    }
}