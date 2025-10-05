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
package de.jeter.chatex.utils.adManager;

import de.jeter.chatex.utils.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartAdManager implements AdManager {
    private static final Map<UUID, Double> uuidErrorMap = new ConcurrentHashMap<>();
    private static final Pattern ipPattern = Pattern.compile("((?<![0-9])(?:(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[.,-:; ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[., ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[., ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2}))(?![0-9]))");
    private static final Pattern webPattern = Pattern.compile("((([a-zA-Z0-9_-]{2,256}\\.)*)?[a-zA-Z0-9_-]{2,256}\\.[a-zA-Z_-]{2,256})(\\/[-a-zA-Z0-9@:%_\\\\+~#?&\\/=]*)?");

    private static final String urlCompactorPatternString = "[\\(\\)\\]\\[]|([\\s:\\/](?=.{0," + Config.ADS_MAX_LENGTH.getInt() + "}[\\.]))|((?<=[\\.].{0,4})\\s*)";

    private static boolean checkForIPPattern(String message) {
        message = message.replaceAll(" ", "");
        Matcher regexMatcher = ipPattern.matcher(message);
        while (regexMatcher.find()) {
            if (regexMatcher.group().length() != 0) {
                String text = regexMatcher.group().trim().replaceAll("http://", "").replaceAll("https://", "").split("/")[0];
                if (ipPattern.matcher(text).find()) {
                    if (!Utils.checkForBypassString(regexMatcher.group().trim())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double checkForWebPattern(String message) {
        double messageLength = message.length();
        double error = 0;
        if (message.contains(",") || message.contains(".")) {
            message = Config.ADS_REPLACE_COMMAS.getBoolean() ? message.replaceAll(",", ".") : message;
            message = message.replaceAll(urlCompactorPatternString, "");
            Matcher regexMatcher = webPattern.matcher(message);
            while (regexMatcher.find()) {
                if (regexMatcher.group().length() != 0) {
                    String text = regexMatcher.group().trim();
                    if (!Utils.checkForBypassString(text)) {
                        error += text.length();
                        if (DomainDictionary.containsTopLevelEnding(text)) {
                            error *= Config.ADS_SMART_MULTIPLIER.getDouble();
                        } else {
                            error *= Config.ADS_SMART_UN_MULTIPLIER.getDouble();
                        }
                    }
                }
            }
            error = error > 0 ? error / messageLength : 0;
        }
        return error;
    }

    @Override
    public boolean checkForAds(String msg, Player p) {
        if (p.hasPermission("chatex.bypassads")) {
            return false;
        }
        if (!Config.ADS_ENABLED.getBoolean()) {
            return false;
        }
        uuidErrorMap.putIfAbsent(p.getUniqueId(), 0.0);
        double error = checkForWebPattern(msg);
        uuidErrorMap.compute(p.getUniqueId(), (uuid, currentError) -> (currentError == null ? 0 : currentError) + error);
        boolean canceled = uuidErrorMap.get(p.getUniqueId()) > Config.ADS_THRESHOLD.getDouble() || checkForIPPattern(msg);
        if (canceled) {
            uuidErrorMap.put(p.getUniqueId(), Config.ADS_THRESHOLD.getDouble());
            String message = Locales.MESSAGES_AD_NOTIFY.getString(p)
                    .replaceAll("%player", Matcher.quoteReplacement(p.getName()))
                    .replaceAll("%message", Matcher.quoteReplacement(msg));
            Utils.notifyOps(message);
            ChatLogger.writeToAdFile(p, msg);
        } else {
            uuidErrorMap.compute(p.getUniqueId(), (uuid, currentError) -> {
                double newError = (currentError == null ? 0 : currentError) - Config.ADS_REDUCE_THRESHOLD.getDouble();
                return Math.max(0, newError);
            });
        }
        return canceled;
    }
}