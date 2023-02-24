/*
 * Copyright (C) 2022 - 2023 Elytrium
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
import net.dv8tion.jda.api.entities.Activity;
import net.elytrium.commons.config.YamlConfig;
import net.elytrium.commons.kyori.serialization.Serializers;

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
  public Serializers SERIALIZER = Serializers.LEGACY_AMPERSAND;
  public String PREFIX = "LimboAuth &6>>&f";

  @Create
  public MAIN MAIN;

  public static class MAIN {

    public List<String> SOCIAL_REGISTER_CMDS = List.of("!account register");
    public List<String> SOCIAL_LINK_CMDS = List.of("!account link");
    public List<String> FORCE_KEYBOARD_CMDS = List.of("!keyboard");

    public int CODE_LOWER_BOUND = 1000000;
    public int CODE_UPPER_BOUND = 10000000;

    public String LINKAGE_MAIN_CMD = "addsocial";
    public List<String> LINKAGE_ALIAS_CMD = List.of("addvk", "addtg", "addds");

    public String FORCE_UNLINK_MAIN_CMD = "forcesocialunlink";
    public List<String> FORCE_UNLINK_ALIAS_CMD = List.of("forceunlink");

    @Comment("Should we allow registration with premium usernames using social-register-cmds")
    public boolean ALLOW_PREMIUM_NAMES_REGISTRATION = false;

    public boolean ENABLE_NOTIFY = true;

    @Comment("Will the unlink button unregister all socials at once?")
    public boolean UNLINK_BTN_ALL = false;

    @Comment("Disable unlinking?")
    public boolean DISABLE_UNLINK = false;

    @Comment("Disable commands like !account link <username>")
    public boolean DISABLE_LINK_WITHOUT_PASSWORD = false;
    @Comment("Disable commands like !account link <username> <password>")
    public boolean DISABLE_LINK_WITH_PASSWORD = true;

    @Comment("Default buttons state")
    public boolean DEFAULT_BLOCKED = false;
    public boolean DEFAULT_TOTP_ENABLED = false;
    public boolean DEFAULT_NOTIFY_ENABLED = true;

    @Comment("Allow linking social to the player, who already has linked this type of social")
    public boolean ALLOW_ACCOUNT_RELINK = true;

    public List<String> AFTER_LINKAGE_COMMANDS = List.of("alert {NICKNAME} has linked a social account");
    public List<String> AFTER_UNLINKAGE_COMMANDS = List.of();
    public List<String> START_MESSAGES = List.of("/start", "Начать");
    public String START_REPLY = "Send '!account link <nickname>' to link your account";

    @Comment("Addon will print all exceptions if this parameter is set to true.")
    public boolean DEBUG = false;

    @Comment("Prohibit premium users from changing their password via the restore button.")
    public boolean PROHIBIT_PREMIUM_RESTORE = true;

    @Comment({
        "NO | YES - with the option disabled",
        "YES | NO - with the option enabled",
    })
    public boolean REVERSE_YES_NO_BUTTONS = false;

    @Comment({
        "false - players with social 2FA enabled should enter the password",
        "true - players with social 2FA enabled can login without the password"
    })
    public boolean AUTH_2FA_WITHOUT_PASSWORD = false;

    @Comment("How long in milliseconds the player should wait before registering new account")
    public long PURGE_REGISTRATION_CACHE_MILLIS = 86400000;

    @Comment("How many accounts can register the player per time (per purge-registration-cache-millis)")
    public int MAX_REGISTRATION_COUNT_PER_TIME = 3;

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
          "remrole <role id>",
          "",
          "Example: ",
          "on-player-added: ",
          " - addrole 12345678",
          "on-player-removed: ",
          " - remrole 12345678"
      })
      public List<String> ON_PLAYER_ADDED = List.of();
      public List<String> ON_PLAYER_REMOVED = List.of();

      public boolean ACTIVITY_ENABLED = true;
      @Comment("Available values: PLAYING, STREAMING, LISTENING, WATCHING, COMPETING")
      public Activity.ActivityType ACTIVITY_TYPE = Activity.ActivityType.PLAYING;
      @Comment("Activity URL. Supported only with activity-type: STREAMING")
      public String ACTIVITY_URL = null;
      public String ACTIVITY_NAME = "LimboAuth Social Addon";

      @Comment({
          "Which role ids a player must have on the Discord server to use the bot",
          "",
          "Example: ",
          "required-roles: ",
          " - 1234567890"
      })
      public List<Object> REQUIRED_ROLES = List.of();
      @Comment({
          "It's better to keep this option enabled if you have set required-roles config option",
          "Requires SERVER MEMBERS INTENT to be enabled in the bot settings on the Discord Developer Portal"
      })
      public boolean GUILD_MEMBER_CACHE_ENABLED = false;
      public String NO_ROLES_MESSAGE = "You don't have permission to use commands";
    }

    @Create
    public MAIN.TELEGRAM TELEGRAM;

    public static class TELEGRAM {
      public boolean ENABLED = false;
      public String TOKEN = "1234567890";
    }

    @Create
    public MAIN.GEOIP GEOIP;

    @Comment({
        "GeoIP is an offline database providing approximate IP address locations",
        "In the SocialAddon's case, the IP location is displayed in notifications and alerts"
    })
    public static class GEOIP {
      public boolean ENABLED = false;
      @Comment({
          "Available placeholders: {CITY}, {COUNTRY}, {LEAST_SPECIFIC_SUBDIVISION}, {MOST_SPECIFIC_SUBDIVISION}"
      })
      @Placeholders({"{CITY}", "{COUNTRY}", "{LEAST_SPECIFIC_SUBDIVISION}", "{MOST_SPECIFIC_SUBDIVISION}"})
      public String FORMAT = "{CITY}, {COUNTRY}";
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

      @Comment("It is not necessary to change {LICENSE_KEY}")
      @Placeholders({"{LICENSE_KEY}"})
      public String MMDB_CITY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key={LICENSE_KEY}&suffix=tar.gz";

      @Placeholders({"{LICENSE_KEY}"})
      public String MMDB_COUNTRY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country&license_key={LICENSE_KEY}&suffix=tar.gz";
    }

    @Create
    public MAIN.STRINGS STRINGS;

    public static class STRINGS {

      @Placeholders({"{NICKNAME}"})
      public String LINK_CMD_USAGE = "{PRFX} Send '!account link {NICKNAME}' to our Social Bot{NL} VK: vk.com/123{NL} DS: Bot#0000{NL} TG: @bot";
      @Placeholders({"{NICKNAME}"})
      public String LINK_WRONG_CODE = "{PRFX} Wrong code, run '!account link {NICKNAME}' again";
      public String LINK_SUCCESS_GAME = "{PRFX} Social was successfully linked";
      public String LINK_SUCCESS = "✅ Social was successfully linked{NL}Use '!keyboard' to show keyboard";
      public String LINK_ALREADY = "Account is already linked";
      public String LINK_SOCIAL_REGISTER_CMD_USAGE = "You didn't specify a nickname. Enter '!account register <nickname>'";
      public String LINK_SOCIAL_CMD_USAGE = "You didn't specify a nickname. Enter '!account link <nickname>'";
      public String LINK_UNKNOWN_ACCOUNT = "There is no account with this nickname";
      @Placeholders({"{CODE}"})
      public String LINK_CODE = "🔑 Enter '/addsocial {CODE}' in game to complete account linking";
      public String LINK_WRONG_PASSWORD = "Wrong password";
      public String REGISTER_INCORRECT_NICKNAME = "Nickname contains forbidden characters";
      public String REGISTER_TAKEN_NICKNAME = "This nickname is already taken";
      public String REGISTER_PREMIUM_NICKNAME = "This nickname belongs to a premium player";
      public String REGISTER_LIMIT = "You've tried to registered numerous times!";
      @Placeholders({"{PASSWORD}"})
      public String REGISTER_SUCCESS = "✅ Account was successfully registered{NL}Your password: {PASSWORD}{NL}Use '!keyboard' to show keyboard";

      public String FORCE_UNLINK_CMD_USAGE = "{PRFX} Usage: /forcesocialunregister <username>";

      public String NOTIFY_LEAVE = "➖ You've left the server";
      @Placeholders({"{IP}", "{LOCATION}"})
      public String NOTIFY_JOIN = "➕ You've joined the server {NL}🌐 IP: {IP} {LOCATION}{NL}You can block your account if that is not you";

      public String NOTIFY_ASK_KICK_MESSAGE = "{PRFX} You were kicked by the Social";
      @Placeholders({"{IP}", "{LOCATION}"})
      public String NOTIFY_ASK_VALIDATE = "❔ Someone tries to join the server.{NL}🌐 IP: {IP} {LOCATION}{NL}Is it you?";
      public String NOTIFY_ASK_VALIDATE_GAME = "{PRFX} You have 2FA enabled, check your social and validate your login!";
      public String NOTIFY_ASK_YES = "It's me";
      public String NOTIFY_ASK_NO = "It's not me";
      public String NOTIFY_THANKS = "Thanks for verifying your login";
      public String NOTIFY_WARN = "You can always change your password using the 'Restore' button";

      public String BLOCK_TOGGLE_BTN = "Toggle block";
      public String BLOCK_KICK_MESSAGE = "{PRFX} Your account was blocked by the Social";
      @Placeholders({"{NICKNAME}"})
      public String BLOCK_SUCCESS = "Account {NICKNAME} was successfully blocked";
      @Placeholders({"{NICKNAME}"})
      public String UNBLOCK_SUCCESS = "Account {NICKNAME} was successfully unblocked";

      @Placeholders({"{NICKNAME}"})
      public String TOTP_ENABLE_SUCCESS = "Account {NICKNAME} now uses 2FA";
      @Placeholders({"{NICKNAME}"})
      public String TOTP_DISABLE_SUCCESS = "Account {NICKNAME} doesn't use 2FA anymore";

      @Placeholders({"{NICKNAME}"})
      public String NOTIFY_ENABLE_SUCCESS = "Account {NICKNAME} now receives notifications";
      @Placeholders({"{NICKNAME}"})
      public String NOTIFY_DISABLE_SUCCESS = "Account {NICKNAME} doesn't receive notifications anymore";

      public String KICK_IS_OFFLINE = "Cannot kick player - player is offline";
      @Placeholders("{NICKNAME}")
      public String KICK_SUCCESS = "Player {NICKNAME} was successfully kicked";
      public String KICK_GAME_MESSAGE = "{PRFX} You were kicked by the Social";

      public String RESTORE_BTN = "Restore";
      @Placeholders({"{NICKNAME}", "{PASSWORD}"})
      public String RESTORE_MSG = "The new password for {NICKNAME} is: {PASSWORD}";
      @Placeholders({"{NICKNAME}"})
      public String RESTORE_MSG_PREMIUM = "We can't change your password, {NICKNAME}, perhaps you are a premium player.";

      public String INFO_BTN = "Info";
      @Placeholders({"{NICKNAME}", "{SERVER}", "{IP}", "{LOCATION}", "{NOTIFY_STATUS}", "{BLOCK_STATUS}", "{TOTP_STATUS}"})
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
      public String UNLINK_SUCCESS_GAME = "{PRFX} Unlink successful";
      public String UNLINK_BLOCK_CONFLICT = "You cannot unlink the social while your account is blocked. Unblock it first";
      public String UNLINK_2FA_CONFLICT = "You cannot unlink the social while 2FA is enabled. Disable it first";

      public String KEYBOARD_RESTORED = "Keyboard was restored";

      @Comment("This message will be sent to the players without social-link right after their login")
      public String LINK_ANNOUNCEMENT = "{PRFX} Hey! We recommend you to link a social network using the /addsocial command to secure your account";

      public String SOCIAL_EXCEPTION_CAUGHT = "An exception occurred while processing your request";
    }
  }
}
