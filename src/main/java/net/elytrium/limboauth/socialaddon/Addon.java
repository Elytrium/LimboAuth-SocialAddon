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

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.elytrium.java.commons.mc.serialization.Serializer;
import net.elytrium.java.commons.mc.serialization.Serializers;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.socialaddon.command.ValidateLinkCommand;
import net.elytrium.limboauth.socialaddon.listener.LimboAuthListener;
import net.elytrium.limboauth.socialaddon.listener.ReloadListener;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.DiscordSocial;
import net.elytrium.limboauth.socialaddon.social.TelegramSocial;
import net.elytrium.limboauth.socialaddon.social.VKSocial;
import net.elytrium.limboauth.socialaddon.utils.GeoIp;
import net.elytrium.limboauth.socialaddon.utils.UpdatesChecker;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.Dao;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.DaoManager;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.stmt.UpdateBuilder;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.support.ConnectionSource;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.table.TableUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

@Plugin(
    id = "limboauth-social-addon",
    name = "LimboAuth Social Addon",
    version = BuildConstants.ADDON_VERSION,
    authors = {"hevav", "mdxd44"},
    dependencies = {
        @Dependency(id = "limboauth")
    }
)
public class Addon {

  private static final String INFO_BTN = "info";
  private static final String BLOCK_BTN = "block";
  private static final String TOTP_BTN = "2fa";
  private static final String NOTIFY_BTN = "notify";
  private static final String KICK_BTN = "kick";
  private static final String RESTORE_BTN = "restore";
  private static final String UNLINK_BTN = "unlink";

  private static Serializer SERIALIZER;

  private final ProxyServer server;
  private final Logger logger;
  private final Metrics.Factory metricsFactory;
  private final Path dataDirectory;
  private final LimboAuth plugin;

  private final SocialManager socialManager;
  private final HashMap<String, Integer> codeMap;
  private final HashMap<Long, String> requestedMap;
  private final HashMap<String, TempAccount> requestedReverseMap;

  private Dao<SocialPlayer, String> dao;
  private Pattern nicknamePattern;

  private List<List<AbstractSocial.ButtonItem>> keyboard;
  private GeoIp geoIp;

  static {
    Objects.requireNonNull(org.apache.commons.logging.impl.LogFactoryImpl.class);
    Objects.requireNonNull(org.apache.commons.logging.impl.Log4JLogger.class);
    Objects.requireNonNull(org.apache.commons.logging.impl.Jdk14Logger.class);
    Objects.requireNonNull(org.apache.commons.logging.impl.Jdk13LumberjackLogger.class);
    Objects.requireNonNull(org.apache.commons.logging.impl.SimpleLog.class);
  }

  @Inject
  public Addon(ProxyServer server, Logger logger, Metrics.Factory metricsFactory, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.metricsFactory = metricsFactory;
    this.dataDirectory = dataDirectory;

    this.plugin = (LimboAuth) this.server.getPluginManager().getPlugin("limboauth").flatMap(PluginContainer::getInstance).orElseThrow();
    this.socialManager = new SocialManager(DiscordSocial::new, TelegramSocial::new, VKSocial::new);
    this.codeMap = new HashMap<>();
    this.requestedMap = new HashMap<>();
    this.requestedReverseMap = new HashMap<>();
  }

  @Subscribe(order = PostOrder.NORMAL)
  public void onProxyInitialization(ProxyInitializeEvent event) throws SQLException {
    this.onReload();
    this.metricsFactory.make(this, 14770);
    UpdatesChecker.checkForUpdates(this.logger);
  }

  @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "LEGACY_AMPERSAND can't be null in velocity.")
  private void load() {
    Settings.IMP.reload(new File(this.dataDirectory.toFile().getAbsoluteFile(), "config.yml"), Settings.IMP.PREFIX);

    ComponentSerializer<Component, Component, String> serializer = Serializers.valueOf(Settings.IMP.SERIALIZER.toUpperCase(Locale.ROOT)).getSerializer();
    if (serializer == null) {
      this.logger.warn("The specified serializer could not be founded, using default. (LEGACY_AMPERSAND)");
      setSerializer(new Serializer(Objects.requireNonNull(Serializers.LEGACY_AMPERSAND.getSerializer())));
    } else {
      setSerializer(new Serializer(serializer));
    }

    this.geoIp = Settings.IMP.MAIN.GEOIP.ENABLED ? new GeoIp(this.dataDirectory) : null;

    this.socialManager.clear();
    this.socialManager.init();

    this.keyboard = List.of(
        List.of(
            new AbstractSocial.ButtonItem(INFO_BTN, Settings.IMP.MAIN.STRINGS.INFO_BTN, AbstractSocial.ButtonItem.Color.PRIMARY)
        ),
        List.of(
            new AbstractSocial.ButtonItem(BLOCK_BTN, Settings.IMP.MAIN.STRINGS.BLOCK_TOGGLE_BTN, AbstractSocial.ButtonItem.Color.SECONDARY),
            new AbstractSocial.ButtonItem(TOTP_BTN, Settings.IMP.MAIN.STRINGS.TOGGLE_2FA_BTN, AbstractSocial.ButtonItem.Color.SECONDARY)
        ),
        List.of(
            new AbstractSocial.ButtonItem(NOTIFY_BTN, Settings.IMP.MAIN.STRINGS.TOGGLE_NOTIFICATION_BTN, AbstractSocial.ButtonItem.Color.SECONDARY)
        ),
        List.of(
            new AbstractSocial.ButtonItem(KICK_BTN, Settings.IMP.MAIN.STRINGS.KICK_BTN, AbstractSocial.ButtonItem.Color.RED),
            new AbstractSocial.ButtonItem(RESTORE_BTN, Settings.IMP.MAIN.STRINGS.RESTORE_BTN, AbstractSocial.ButtonItem.Color.RED),
            new AbstractSocial.ButtonItem(UNLINK_BTN, Settings.IMP.MAIN.STRINGS.UNLINK_BTN, AbstractSocial.ButtonItem.Color.RED)
        )
    );

    this.socialManager.addMessageEvent((dbField, id, message) -> {
      if (Settings.IMP.MAIN.START_MESSAGES.contains(message)) {
        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.START_REPLY);
        return;
      }

      for (String socialLinkCmd : Settings.IMP.MAIN.SOCIAL_LINK_CMDS) {
        if (message.startsWith(socialLinkCmd)) {
          int desiredLength = socialLinkCmd.length() + 1;

          if (message.length() <= desiredLength) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SOCIAL_CMD_USAGE);
            return;
          }

          try {
            if (this.dao.queryForEq(dbField, id).size() != 0) {
              this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
              return;
            }
          } catch (SQLException e) {
            e.printStackTrace();
            return;
          }

          String account = message.substring(desiredLength).toLowerCase(Locale.ROOT);
          if (!this.nicknamePattern.matcher(account).matches()) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_UNKNOWN_ACCOUNT);
            return;
          }

          if (this.requestedMap.containsKey(id)) {
            this.codeMap.remove(this.requestedMap.get(id));
          } else {
            this.requestedMap.put(id, account);
            this.requestedReverseMap.put(account, new TempAccount(dbField, id));
          }

          int code = ThreadLocalRandom.current().nextInt(Settings.IMP.MAIN.CODE_LOWER_BOUND, Settings.IMP.MAIN.CODE_UPPER_BOUND);
          this.codeMap.put(account, code);
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_CODE.replace("{CODE}", String.valueOf(code)));

          return;
        }
      }

      for (String forceKeyboardCmd : Settings.IMP.MAIN.FORCE_KEYBOARD_CMDS) {
        if (message.startsWith(forceKeyboardCmd)) {
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.KEYBOARD_RESTORED, this.keyboard);
          return;
        }
      }
    });

    this.socialManager.addButtonEvent(INFO_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);
        Optional<Player> proxyPlayer = this.server.getPlayer(player.getLowercaseNickname());
        String server;
        String ip;
        String location;

        if (proxyPlayer.isPresent()) {
          Player player1 = proxyPlayer.get();
          Optional<ServerConnection> connection = player1.getCurrentServer();

          if (connection.isPresent()) {
            server = connection.get().getServerInfo().getName();
          } else {
            server = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
          }

          ip = player1.getRemoteAddress().getAddress().getHostAddress();
          location = Optional.ofNullable(this.geoIp)
              .map(nonNullGeo -> "(" + nonNullGeo.getLocation(ip) + ")").orElse("");
        } else {
          server = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
          ip = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
          location = "";
        }

        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.INFO_MSG
                .replace("{NICKNAME}", player.getLowercaseNickname())
                .replace("{SERVER}", server)
                .replace("{IP}", ip)
                .replace("{LOCATION}", location)
                .replace("{NOTIFY_STATUS}", player.isNotifyEnabled()
                    ? Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLED : Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLED)
                .replace("{BLOCK_STATUS}", player.isBlocked() ? Settings.IMP.MAIN.STRINGS.BLOCK_ENABLED : Settings.IMP.MAIN.STRINGS.BLOCK_DISABLED)
                .replace("{TOTP_STATUS}", player.isTotpEnabled() ? Settings.IMP.MAIN.STRINGS.TOTP_ENABLED : Settings.IMP.MAIN.STRINGS.TOTP_DISABLED),
            this.keyboard);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(BLOCK_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);

        if (player.isBlocked()) {
          player.setBlocked(false);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.UNBLOCK_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        } else {
          player.setBlocked(true);

          this.plugin.removePlayerFromCache(player.getLowercaseNickname());
          this.server
              .getPlayer(player.getLowercaseNickname())
              .ifPresent(e -> e.disconnect(Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.KICK_GAME_MESSAGE)));

          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.BLOCK_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        }

        this.dao.update(player);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(TOTP_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);

        if (player.isTotpEnabled()) {
          player.setTotpEnabled(false);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.TOTP_DISABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        } else {
          player.setTotpEnabled(true);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.TOTP_ENABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        }

        this.dao.update(player);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(NOTIFY_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);

        if (player.isNotifyEnabled()) {
          player.setNotifyEnabled(false);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        } else {
          player.setNotifyEnabled(true);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        }

        this.dao.update(player);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(KICK_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);
        Optional<Player> proxyPlayer = this.server.getPlayer(player.getLowercaseNickname());
        this.plugin.removePlayerFromCache(player.getLowercaseNickname());

        if (proxyPlayer.isPresent()) {
          proxyPlayer.get().disconnect(Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.KICK_GAME_MESSAGE));
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.KICK_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        } else {
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.KICK_IS_OFFLINE.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard);
        }

        this.dao.update(player);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(RESTORE_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);
        Dao<RegisteredPlayer, String> playerDao = this.plugin.getPlayerDao();

        String newPassword = Long.toHexString(Double.doubleToLongBits(Math.random()));

        UpdateBuilder<RegisteredPlayer, String> updateBuilder = playerDao.updateBuilder();
        updateBuilder.where().eq("LOWERCASENICKNAME", player.getLowercaseNickname());
        updateBuilder.updateColumnValue("HASH", AuthSessionHandler.genHash(newPassword));
        updateBuilder.update();

        this.socialManager.broadcastMessage(dbField, id,
            Settings.IMP.MAIN.STRINGS.RESTORE_MSG.replace("{NICKNAME}", player.getLowercaseNickname()).replace("{PASSWORD}", newPassword),
            this.keyboard);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(UNLINK_BTN, (dbField, id) -> {
      try {
        if (Settings.IMP.MAIN.DISABLE_UNLINK) {
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_DISABLED, this.keyboard);
          return;
        }
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);

        if (player.isBlocked()) {
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_BLOCK_CONFLICT, this.keyboard);
          return;
        }

        if (player.isTotpEnabled()) {
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_2FA_CONFLICT, this.keyboard);
          return;
        }

        if (Settings.IMP.MAIN.UNLINK_BTN_ALL) {
          this.dao.delete(player);
          this.socialManager.unregisterHook(player);
        } else {
          UpdateBuilder<SocialPlayer, String> updateBuilder = this.dao.updateBuilder();
          updateBuilder.where().eq(SocialPlayer.LOWERCASE_NICKNAME_FIELD, player.getLowercaseNickname());
          updateBuilder.updateColumnValue(dbField, null);
          updateBuilder.update();

          this.socialManager.unregisterHook(dbField, player);
        }

        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_SUCCESS);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });
  }

  public void onReload() throws SQLException {
    this.load();
    this.server.getEventManager().unregisterListeners(this);

    ConnectionSource source = this.plugin.getConnectionSource();
    TableUtils.createTableIfNotExists(source, SocialPlayer.class);
    this.dao = DaoManager.createDao(source, SocialPlayer.class);

    this.plugin.migrateDb(this.dao);

    this.nicknamePattern = Pattern.compile(net.elytrium.limboauth.Settings.IMP.MAIN.ALLOWED_NICKNAME_REGEX);

    this.server.getEventManager().register(this, new LimboAuthListener(this.dao, this.socialManager,
        this.keyboard, this.geoIp));
    this.server.getEventManager().register(this, new ReloadListener(() -> {
      try {
        this.onReload();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }));

    CommandManager commandManager = this.server.getCommandManager();
    commandManager.unregister(Settings.IMP.MAIN.LINKAGE_MAIN_CMD);
    commandManager.register(Settings.IMP.MAIN.LINKAGE_MAIN_CMD,
        new ValidateLinkCommand(this, this.dao),
        Settings.IMP.MAIN.LINKAGE_ALIAS_CMD.toArray(new String[0]));
  }

  public int getCode(String nickname) {
    return this.codeMap.get(nickname);
  }

  public TempAccount getTempAccount(String nickname) {
    return this.requestedReverseMap.get(nickname);
  }

  public void removeCode(String nickname) {
    this.requestedMap.remove(this.requestedReverseMap.get(nickname).getId());
    this.requestedReverseMap.remove(nickname);
    this.codeMap.remove(nickname);
  }

  public boolean hasCode(String nickname) {
    return this.codeMap.containsKey(nickname);
  }

  public SocialManager getSocialManager() {
    return this.socialManager;
  }

  public ProxyServer getServer() {
    return this.server;
  }

  public List<List<AbstractSocial.ButtonItem>> getKeyboard() {
    return this.keyboard;
  }

  public static class TempAccount {

    private final String dbField;
    private final long id;

    public TempAccount(String dbField, long id) {
      this.dbField = dbField;
      this.id = id;
    }

    public String getDbField() {
      return this.dbField;
    }

    public long getId() {
      return this.id;
    }

  }

  private static void setSerializer(Serializer serializer) {
    SERIALIZER = serializer;
  }

  public static Serializer getSerializer() {
    return SERIALIZER;
  }
}
