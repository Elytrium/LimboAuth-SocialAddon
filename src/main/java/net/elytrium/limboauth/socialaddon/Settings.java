/*
 * Copyright (C) 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth.socialaddon;

import java.util.List;
import net.elytrium.java.commons.config.YamlConfig;

public class Settings extends YamlConfig {

  @Ignore
  public static final Settings IMP = new Settings();

  @Final
  public String VERSION = BuildConstants.ADDON_VERSION;

  @Comment({
      "Available serializers:",
      "LEGACY_AMPERSAND - \"&c&lExample &c&9Text\".",
      "LEGACY_SECTION - \"§c§lExample §c§9Text\".",
      "MINIMESSAGE - \"<bold><red>Example</red> <blue>Text</blue></bold>\". (https://webui.adventure.kyori.net/)",
      "GSON - \"[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},{\"text\":\" \",\"bold\":true},{\"text\":\"Text\",\"bold\":true,\"color\":\"blue\"}]\". (https://minecraft.tools/en/json_text.php/)",
      "GSON_COLOR_DOWNSAMPLING - Same as GSON, but uses downsampling."
  })
  public String SERIALIZER = "LEGACY_AMPERSAND";
  public String PREFIX = "LimboAuth &6>>&f";

  @Create
  public MAIN MAIN;

  public static class MAIN {

    public String SOCIAL_LINK_CMD = "!account link";
    public String FORCE_KEYBOARD_CMD = "!keyboard";

    public int CODE_LOWER_BOUND = 1000000;
    public int CODE_UPPER_BOUND = 10000000;

    public String LINKAGE_MAIN_CMD = "addsocial";
    public List<String> LINKAGE_ALIAS_CMD = List.of("addvk", "addtg", "addds");

    public boolean ENABLE_NOTIFY = true;

    @Comment("Will the unlink button unregister all socials at once?")
    public boolean UNLINK_BTN_ALL = false;

    @Comment("Disable unlinking?")
    public boolean DISABLE_UNLINK = false;

    @Comment("Default buttons state")
    public boolean DEFAULT_BLOCKED = false;
    public boolean DEFAULT_TOTP_ENABLED = false;
    public boolean DEFAULT_NOTIFY_ENABLED = true;

    @Comment("Allow linking social to the player, who already has linked this type of social")
    public boolean ALLOW_ACCOUNT_RELINK = true;

    public List<String> AFTER_LINKAGE_COMMANDS = List.of("alert {NICKNAME} ({UUID}) has linked a social account");

    @Create
    public MAIN.VK VK;

    public static class VK {
      public boolean ENABLED = false;
      public String TOKEN = "1234567890";
    }

    @Create
    public MAIN.DISCORD DISCORD;

    public static class DISCORD {
      public boolean ENABLED = false;
      public String TOKEN = "1234567890";

      @Comment({
          "Available: ",
          "addrole <role id>",
          "remrole <role id>"
      })
      public List<String> ON_PLAYER_ADDED = List.of();
      public List<String> ON_PLAYER_REMOVED = List.of();
    }

    @Create
    public MAIN.TELEGRAM TELEGRAM;

    public static class TELEGRAM {
      public boolean ENABLED = false;
      public String TOKEN = "1234567890";
    }

    @Create
    public MAIN.STRINGS STRINGS;

    @Comment({
        "GeoIP is an offline database providing approximate IP address locations",
        "In the SocialAddon's case, the IP location is displayed in notifications and alerts"
    })
    public static class GEOIP {
      public boolean ENABLED = false;
      @Comment({
          "Available: city, country",
          "City precision will involve both the country and the city displayed",
          "Country will involve only the country to be displayed"
      })
      public String PRECISION = "country";
      @Comment("ISO 639-1")
      public String LOCALE = "en";
      @Comment({
          "MaxMind license key",
          "Regenerate if triggers an error"
      })
      public String LICENSE_KEY = "P5g0fVdAQIq8yQau";
      @Comment({
          "The interval at which the database will be updated, in milliseconds",
          "Default value: 14 days"
      })
      public long UPDATE_INTERVAL = 1209600000L;
      public String DEFAULT_VALUE = "Unknown";
    }

    @Create
    public MAIN.GEOIP GEOIP;

    public static class STRINGS {

      public String LINK_CMD_USAGE = "{PRFX} Send '!account link {NICKNAME}' to our Social Bot{NL} VK: vk.com/123{NL} DS: Bot#0000{NL} TG: @bot";
      public String LINK_WRONG_CODE = "{PRFX} Wrong code, run '!account link {NICKNAME}' again";
      public String LINK_SUCCESS = "✅ Social was successfully linked{NL}Use '!keyboard' to show keyboard";
      public String LINK_ALREADY = "Account is already linked";
      public String LINK_SOCIAL_CMD_USAGE = "You didn't specify a nickname. Enter '!account link <nickname>'";
      public String LINK_UNKNOWN_ACCOUNT = "There is no account with this nickname";
      public String LINK_CODE = "🔑 Enter '/addsocial {CODE}' in game to complete account linking";

      public String NOTIFY_LEAVE = "➖ You've left the server";
      public String NOTIFY_JOIN = "➕ You've joined the server {NL}🌐 IP: {IP} {LOCATION}{NL}You can block your account if that is not you";

      public String NOTIFY_ASK_KICK_MESSAGE = "{PRFX} You were kicked by the Social";
      public String NOTIFY_ASK_VALIDATE = "❔ Someone tries to join the server.{NL}🌐 IP: {IP} {LOCATION}{NL}Is it you?";
      public String NOTIFY_ASK_VALIDATE_GAME = "{PRFX} You have 2FA enabled, check your social and validate your login!";
      public String NOTIFY_ASK_YES = "It's me";
      public String NOTIFY_ASK_NO = "It's not me";
      public String NOTIFY_THANKS = "Thanks for verifying your login";
      public String NOTIFY_WARN = "You can always change your password using the 'Restore' button";

      public String BLOCK_TOGGLE_BTN = "Toggle block";
      public String BLOCK_KICK_MESSAGE = "{PRFX} Your account was blocked by the Social";
      public String BLOCK_SUCCESS = "Account {NICKNAME} was successfully blocked";
      public String UNBLOCK_SUCCESS = "Account {NICKNAME} was successfully unblocked";

      public String TOTP_ENABLE_SUCCESS = "Account {NICKNAME} now uses 2FA";
      public String TOTP_DISABLE_SUCCESS = "Account {NICKNAME} doesn't use 2FA anymore";

      public String NOTIFY_ENABLE_SUCCESS = "Account {NICKNAME} now receives notifications";
      public String NOTIFY_DISABLE_SUCCESS = "Account {NICKNAME} doesn't receive notifications anymore";

      public String KICK_IS_OFFLINE = "Cannot kick player - player is offline";
      public String KICK_SUCCESS = "Player was successfully kicked";
      public String KICK_GAME_MESSAGE = "{PRFX} You were kicked by the Social";

      public String RESTORE_BTN = "Restore";
      public String RESTORE_MSG = "The new password for {NICKNAME} is: {PASSWORD}";

      public String INFO_BTN = "Info";
      public String INFO_MSG = "👤 IGN: {NICKNAME}{NL}🌍 Current status: {SERVER}{NL}🌐 IP: {IP} {LOCATION}{NL}⏰ Notifications: {NOTIFY_STATUS}{NL}❌ Blocked: {BLOCK_STATUS}{NL}🔑 2FA: {TOTP_STATUS}";
      public String STATUS_OFFLINE = "OFFLINE";
      public String NOTIFY_ENABLED = "Enabled";
      public String NOTIFY_DISABLED = "Disabled";
      public String BLOCK_ENABLED = "Yes";
      public String BLOCK_DISABLED = "No";
      public String TOTP_ENABLED = "Enabled";
      public String TOTP_DISABLED = "Disabled";

      public String KICK_BTN = "Kick";
      public String TOGGLE_NOTIFICATION_BTN = "Toggle notifications";
      public String TOGGLE_2FA_BTN = "Toggle 2FA";
      public String UNLINK_BTN = "Unlink social";
      public String UNLINK_DISABLED = "Unlinking disabled";
      public String UNLINK_SUCCESS = "Unlink successful";
      public String UNLINK_BLOCK_CONFLICT = "You cannot unlink the social while your account is blocked. Unblock it first";
      public String UNLINK_2FA_CONFLICT = "You cannot unlink the social while 2FA is enabled. Disable it first";

      public String KEYBOARD_RESTORED = "Keyboard was restored";

      @Comment("This message will be sent to the players without social-link right after their login")
      public String LINK_ANNOUNCEMENT = "{PRFX} Hey! We recommend you to link a social network using the /addsocial command to secure your account";

    }
  }
}
