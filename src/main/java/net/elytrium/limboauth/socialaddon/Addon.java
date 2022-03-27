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
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.event.AuthPluginReloadEvent;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.socialaddon.command.ValidateLinkCommand;
import net.elytrium.limboauth.socialaddon.listener.LimboAuthListener;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.DiscordSocial;
import net.elytrium.limboauth.socialaddon.social.TelegramSocial;
import net.elytrium.limboauth.socialaddon.social.VKSocial;
import net.elytrium.limboauth.socialaddon.utils.UpdatesChecker;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.Dao;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.DaoManager;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.stmt.UpdateBuilder;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.support.ConnectionSource;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.table.TableUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

@Plugin(
    id = "limboauth-social-addon",
    name = "LimboAuth Social Addon",
    version = "@version@",
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

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final LimboAuth plugin;

  private final SocialManager socialManager;
  private final HashMap<String, Integer> codeMap;
  private final HashMap<Long, String> requestedMap;
  private final HashMap<String, TempAccount> requestedReverseMap;

  private Dao<SocialPlayer, String> dao;

  public Addon(ProxyServer server, Logger logger, Metrics.Factory metricsFactory, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;

    this.plugin = (LimboAuth) this.server.getPluginManager().getPlugin("limboauth").flatMap(PluginContainer::getInstance).orElseThrow();
    this.socialManager = new SocialManager(DiscordSocial::new, TelegramSocial::new, VKSocial::new);
    this.codeMap = new HashMap<>();
    this.requestedMap = new HashMap<>();
    this.requestedReverseMap = new HashMap<>();

    metricsFactory.make(this, 14770);
  }

  @Subscribe(order = PostOrder.LAST)
  public void onProxyInitialization(ProxyInitializeEvent event) {
    Settings.IMP.reload(new File(this.dataDirectory.toFile().getAbsoluteFile(), "config.yml"));
    this.socialManager.init();

    List<List<AbstractSocial.ButtonItem>> keyboard = List.of(
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
      if (message.startsWith(Settings.IMP.MAIN.SOCIAL_LINK_CMD)) {
        try {
          if (this.dao.queryForEq(dbField, id).size() != 0) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
            return;
          }
        } catch (SQLException e) {
          e.printStackTrace();
          return;
        }

        String account = message.substring(Settings.IMP.MAIN.SOCIAL_LINK_CMD.length() + 1).toLowerCase(Locale.ROOT);
        if (account.length() > Settings.IMP.MAIN.MAX_NICKNAME_LENGTH) {
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
      } else if (message.startsWith(Settings.IMP.MAIN.FORCE_KEYBOARD_CMD)) {
        this.socialManager.broadcastMessage(id, Settings.IMP.MAIN.STRINGS.KEYBOARD_RESTORED, keyboard);
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

        if (proxyPlayer.isPresent()) {
          Player player1 = proxyPlayer.get();
          Optional<ServerConnection> connection = player1.getCurrentServer();

          if (connection.isPresent()) {
            server = connection.get().getServerInfo().getName();
          } else {
            server = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
          }

          ip = player1.getRemoteAddress().getAddress().getHostAddress();
        } else {
          server = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
          ip = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
        }

        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.INFO_MSG
            .replace("{NICKNAME}", player.getLowercaseNickname())
            .replace("{SERVER}", server)
            .replace("{IP}", ip)
            .replace("{NOTIFY_STATUS}", player.isNotifyEnabled() ? Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLED : Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLED)
            .replace("{BLOCK_STATUS}", player.isBlocked() ? Settings.IMP.MAIN.STRINGS.BLOCK_ENABLED : Settings.IMP.MAIN.STRINGS.BLOCK_DISABLED)
            .replace("{TOTP_STATUS}", player.isTotpEnabled() ? Settings.IMP.MAIN.STRINGS.TOTP_ENABLED : Settings.IMP.MAIN.STRINGS.TOTP_DISABLED)
        );
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
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNBLOCK_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
        } else {
          player.setBlocked(true);
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.BLOCK_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
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
              Settings.IMP.MAIN.STRINGS.TOTP_DISABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
        } else {
          player.setTotpEnabled(true);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.TOTP_ENABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
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
              Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
        } else {
          player.setNotifyEnabled(true);
          this.socialManager.broadcastMessage(dbField, id,
              Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLE_SUCCESS.replace("{NICKNAME}", player.getLowercaseNickname()));
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
        if (proxyPlayer.isPresent()) {
          proxyPlayer.get().disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(Settings.IMP.MAIN.STRINGS.KICK_GAME_MESSAGE));
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.KICK_SUCCESS);
        } else {
          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.KICK_IS_OFFLINE);
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
            Settings.IMP.MAIN.STRINGS.RESTORE_MSG.replace("{NICKNAME}", player.getLowercaseNickname()).replace("{PASSWORD}", newPassword));
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    this.socialManager.addButtonEvent(UNLINK_BTN, (dbField, id) -> {
      try {
        List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

        if (socialPlayerList.size() == 0) {
          return;
        }

        SocialPlayer player = socialPlayerList.get(0);
        this.dao.delete(player);

        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_SUCCESS);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });

    UpdatesChecker.checkForUpdates(this.logger);
  }

  @Subscribe
  public void onAuthReload(AuthPluginReloadEvent event) throws SQLException {
    this.server.getEventManager().unregisterListeners(this);

    ConnectionSource source = this.plugin.getConnectionSource();
    TableUtils.createTableIfNotExists(source, SocialPlayer.class);
    this.dao = DaoManager.createDao(source, SocialPlayer.class);

    this.plugin.migrateDb(this.dao);

    this.server.getEventManager().register(this, new LimboAuthListener(this.dao, this.socialManager));

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
}