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

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.elytrium.commons.config.Placeholders;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.commons.utils.updates.UpdatesChecker;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.socialaddon.command.ForceSocialUnlinkCommand;
import net.elytrium.limboauth.socialaddon.command.ValidateLinkCommand;
import net.elytrium.limboauth.socialaddon.listener.LimboAuthListener;
import net.elytrium.limboauth.socialaddon.listener.ReloadListener;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.DiscordSocial;
import net.elytrium.limboauth.socialaddon.social.TelegramSocial;
import net.elytrium.limboauth.socialaddon.social.VKSocial;
import net.elytrium.limboauth.socialaddon.utils.GeoIp;
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
    url = "https://elytrium.net/",
    authors = {
        "Elytrium (https://elytrium.net/)",
    },
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
  private static final String PLUGIN_MINIMUM_VERSION = "1.1.0";

  private static Serializer SERIALIZER;

  private final ProxyServer server;
  private final Logger logger;
  private final Metrics.Factory metricsFactory;
  private final Path dataDirectory;
  private final LimboAuth plugin;

  private final Map<String, Integer> codeMap;
  private final Map<String, TempAccount> requestedReverseMap;
  private final Map<String, CachedRegisteredUser> cachedAccountRegistrations = new ConcurrentHashMap<>();

  private Dao<SocialPlayer, String> dao;
  private Pattern nicknamePattern;

  private SocialManager socialManager;
  private List<List<AbstractSocial.ButtonItem>> keyboard;
  private GeoIp geoIp;
  private ScheduledTask purgeCacheTask;

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

    Optional<PluginContainer> container = this.server.getPluginManager().getPlugin("limboauth");
    String version = container.map(PluginContainer::getDescription).flatMap(PluginDescription::getVersion).orElseThrow();

    if (!UpdatesChecker.checkVersion(PLUGIN_MINIMUM_VERSION, version)) {
      throw new IllegalStateException("Incorrect version of LimboAuth plugin, the addon requires version " + PLUGIN_MINIMUM_VERSION + " or newer");
    }

    this.plugin = (LimboAuth) container.flatMap(PluginContainer::getInstance).orElseThrow();
    this.codeMap = new ConcurrentHashMap<>();
    this.requestedReverseMap = new ConcurrentHashMap<>();
  }

  @Subscribe(order = PostOrder.NORMAL)
  public void onProxyInitialization(ProxyInitializeEvent event) throws SQLException {
    this.onReload();
    this.metricsFactory.make(this, 14770);
    if (!UpdatesChecker.checkVersionByURL("https://raw.githubusercontent.com/Elytrium/LimboAuth-SocialAddon/master/VERSION", Settings.IMP.VERSION)) {
      this.logger.error("****************************************");
      this.logger.warn("The new LimboAuth update was found, please update.");
      this.logger.error("https://github.com/Elytrium/LimboAuth-SocialAddon/releases/");
      this.logger.error("****************************************");
    }
  }

  @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "LEGACY_AMPERSAND can't be null in velocity.")
  private void load() {
    Settings.IMP.reload(new File(this.dataDirectory.toFile().getAbsoluteFile(), "config.yml"), Settings.IMP.PREFIX);

    ComponentSerializer<Component, Component, String> serializer = Settings.IMP.SERIALIZER.getSerializer();
    if (serializer == null) {
      this.logger.warn("The specified serializer could not be founded, using default. (LEGACY_AMPERSAND)");
      setSerializer(new Serializer(Objects.requireNonNull(Serializers.LEGACY_AMPERSAND.getSerializer())));
    } else {
      setSerializer(new Serializer(serializer));
    }

    this.geoIp = Settings.IMP.MAIN.GEOIP.ENABLED ? new GeoIp(this.dataDirectory) : null;

    if (this.socialManager != null) {
      this.socialManager.stop();
    }

    this.socialManager = new SocialManager(DiscordSocial::new, TelegramSocial::new, VKSocial::new);
    this.socialManager.start();

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
      String lowercaseMessage = message.toLowerCase(Locale.ROOT);
      if (Settings.IMP.MAIN.START_MESSAGES.contains(lowercaseMessage)) {
        this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.START_REPLY);
        return;
      }

      for (String socialRegisterCmd : Settings.IMP.MAIN.SOCIAL_REGISTER_CMDS) {
        if (lowercaseMessage.startsWith(socialRegisterCmd)) {
          int desiredLength = socialRegisterCmd.length() + 1;

          if (message.length() <= desiredLength) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SOCIAL_REGISTER_CMD_USAGE);
            return;
          }

          String userIndex = dbField + id;
          CachedRegisteredUser cachedRegisteredUser = this.cachedAccountRegistrations.get(userIndex);
          if (cachedRegisteredUser == null) {
            this.cachedAccountRegistrations.put(userIndex, cachedRegisteredUser = new CachedRegisteredUser());
          }

          if (cachedRegisteredUser.getRegistrationAmount() >= Settings.IMP.MAIN.MAX_REGISTRATION_COUNT_PER_TIME) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.REGISTER_LIMIT);
            return;
          }

          cachedRegisteredUser.incrementRegistrationAmount();

          if (this.dao.queryForEq(dbField, id).size() != 0) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
            return;
          }

          String account = message.substring(desiredLength);
          if (!this.nicknamePattern.matcher(account).matches()) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.REGISTER_INCORRECT_NICKNAME);
            return;
          }

          String lowercaseNickname = account.toLowerCase(Locale.ROOT);
          if (this.plugin.getPlayerDao().idExists(lowercaseNickname)) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.REGISTER_TAKEN_NICKNAME);
            return;
          }

          if (!Settings.IMP.MAIN.ALLOW_PREMIUM_NAMES_REGISTRATION && this.plugin.isPremium(lowercaseNickname)) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.REGISTER_PREMIUM_NICKNAME);
            return;
          }

          String newPassword = Long.toHexString(Double.doubleToLongBits(Math.random()));

          RegisteredPlayer player = new RegisteredPlayer(account, "", "").setPassword(newPassword);
          this.plugin.getPlayerDao().create(player);

          this.linkSocial(lowercaseNickname, dbField, id);
          this.socialManager.broadcastMessage(dbField, id,
              Placeholders.replace(Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESS, newPassword));
        }
      }

      for (String socialLinkCmd : Settings.IMP.MAIN.SOCIAL_LINK_CMDS) {
        if (lowercaseMessage.startsWith(socialLinkCmd)) {
          int desiredLength = socialLinkCmd.length() + 1;

          if (message.length() <= desiredLength) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SOCIAL_CMD_USAGE);
            return;
          }

          String[] args = message.substring(desiredLength).split(" ");
          if (this.dao.queryForEq(dbField, id).size() != 0) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
            return;
          }

          String account = args[0].toLowerCase(Locale.ROOT);
          if (!this.nicknamePattern.matcher(account).matches()) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_UNKNOWN_ACCOUNT);
            return;
          }

          if (args.length == 1) {
            if (Settings.IMP.MAIN.DISABLE_LINK_WITHOUT_PASSWORD) {
              this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SOCIAL_CMD_USAGE);
              return;
            }

            int code = ThreadLocalRandom.current().nextInt(Settings.IMP.MAIN.CODE_LOWER_BOUND, Settings.IMP.MAIN.CODE_UPPER_BOUND);
            this.codeMap.put(account, code);
            this.requestedReverseMap.put(account, new TempAccount(dbField, id));
            this.socialManager.broadcastMessage(dbField, id, Placeholders.replace(Settings.IMP.MAIN.STRINGS.LINK_CODE, String.valueOf(code)));
          } else {
            if (Settings.IMP.MAIN.DISABLE_LINK_WITH_PASSWORD) {
              this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SOCIAL_CMD_USAGE);
              return;
            }

            RegisteredPlayer registeredPlayer = this.plugin.getPlayerDao().queryForId(account);
            if (AuthSessionHandler.checkPassword(args[1], registeredPlayer, this.plugin.getPlayerDao())) {
              this.linkSocial(account, dbField, id);
              this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_SUCCESS);
            } else {
              this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_WRONG_PASSWORD);
              return;
            }
          }

          return;
        }
      }

      for (String forceKeyboardCmd : Settings.IMP.MAIN.FORCE_KEYBOARD_CMDS) {
        if (lowercaseMessage.startsWith(forceKeyboardCmd)) {
          if (this.dao.queryBuilder().where().eq(dbField, id).countOf() == 0) {
            this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.START_REPLY);
            return;
          }

          this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.KEYBOARD_RESTORED, this.keyboard);
          return;
        }
      }
    });

    this.socialManager.addButtonEvent(INFO_BTN, (dbField, id) -> {
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
        location = Optional.ofNullable(this.geoIp).map(nonNullGeo -> nonNullGeo.getLocation(ip)).orElse("");
      } else {
        server = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
        ip = Settings.IMP.MAIN.STRINGS.STATUS_OFFLINE;
        location = "";
      }

      this.socialManager.broadcastMessage(dbField, id, Placeholders.replace(Settings.IMP.MAIN.STRINGS.INFO_MSG,
              player.getLowercaseNickname(),
              server,
              ip,
              location,
              player.isNotifyEnabled() ? Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLED : Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLED,
              player.isBlocked() ? Settings.IMP.MAIN.STRINGS.BLOCK_ENABLED : Settings.IMP.MAIN.STRINGS.BLOCK_DISABLED,
              player.isTotpEnabled() ? Settings.IMP.MAIN.STRINGS.TOTP_ENABLED : Settings.IMP.MAIN.STRINGS.TOTP_DISABLED),
          this.keyboard
      );
    });

    this.socialManager.addButtonEvent(BLOCK_BTN, (dbField, id) -> {
      List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

      if (socialPlayerList.size() == 0) {
        return;
      }

      SocialPlayer player = socialPlayerList.get(0);

      if (player.isBlocked()) {
        player.setBlocked(false);
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.UNBLOCK_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      } else {
        player.setBlocked(true);

        this.plugin.removePlayerFromCache(player.getLowercaseNickname());
        this.server
            .getPlayer(player.getLowercaseNickname())
            .ifPresent(e -> e.disconnect(Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.KICK_GAME_MESSAGE)));

        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.BLOCK_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      }

      this.dao.update(player);
    });

    this.socialManager.addButtonEvent(TOTP_BTN, (dbField, id) -> {
      List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

      if (socialPlayerList.size() == 0) {
        return;
      }

      SocialPlayer player = socialPlayerList.get(0);

      if (player.isTotpEnabled()) {
        player.setTotpEnabled(false);
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.TOTP_DISABLE_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      } else {
        player.setTotpEnabled(true);
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.TOTP_ENABLE_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      }

      this.dao.update(player);
    });

    this.socialManager.addButtonEvent(NOTIFY_BTN, (dbField, id) -> {
      List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

      if (socialPlayerList.size() == 0) {
        return;
      }

      SocialPlayer player = socialPlayerList.get(0);

      if (player.isNotifyEnabled()) {
        player.setNotifyEnabled(false);
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.NOTIFY_DISABLE_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      } else {
        player.setNotifyEnabled(true);
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.NOTIFY_ENABLE_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      }

      this.dao.update(player);
    });

    this.socialManager.addButtonEvent(KICK_BTN, (dbField, id) -> {
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
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.KICK_SUCCESS, player.getLowercaseNickname()), this.keyboard
        );
      } else {
        this.socialManager.broadcastMessage(dbField, id,
            Settings.IMP.MAIN.STRINGS.KICK_IS_OFFLINE.replace("{NICKNAME}", player.getLowercaseNickname()), this.keyboard
        );
      }

      this.dao.update(player);
    });

    this.socialManager.addButtonEvent(RESTORE_BTN, (dbField, id) -> {
      List<SocialPlayer> socialPlayerList = this.dao.queryForEq(dbField, id);

      if (socialPlayerList.size() == 0) {
        return;
      }

      SocialPlayer player = socialPlayerList.get(0);

      if (Settings.IMP.MAIN.PROHIBIT_PREMIUM_RESTORE
          && this.plugin.isPremiumInternal(player.getLowercaseNickname()).getState() != LimboAuth.PremiumState.CRACKED) {
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.RESTORE_MSG_PREMIUM, player.getLowercaseNickname()),
            this.keyboard
        );
        return;
      }

      Dao<RegisteredPlayer, String> playerDao = this.plugin.getPlayerDao();

      String newPassword = Long.toHexString(Double.doubleToLongBits(Math.random()));

      UpdateBuilder<RegisteredPlayer, String> updateBuilder = playerDao.updateBuilder();
      updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, player.getLowercaseNickname());
      updateBuilder.updateColumnValue(RegisteredPlayer.HASH_FIELD, RegisteredPlayer.genHash(newPassword));
      boolean updated = updateBuilder.update() != 0;

      if (updated) {
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.RESTORE_MSG, player.getLowercaseNickname(), newPassword),
            this.keyboard
        );
      } else {
        this.socialManager.broadcastMessage(dbField, id,
            Placeholders.replace(Settings.IMP.MAIN.STRINGS.RESTORE_MSG_PREMIUM, player.getLowercaseNickname()),
            this.keyboard
        );
      }
    });

    this.socialManager.addButtonEvent(UNLINK_BTN, (dbField, id) -> {
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

      SocialPlayer.DatabaseField.valueOf(dbField).setIdFor(player, null);
      boolean allUnlinked = Arrays.stream(SocialPlayer.DatabaseField.values())
          .noneMatch(v -> v.getIdFor(player) != null);

      if (Settings.IMP.MAIN.UNLINK_BTN_ALL || allUnlinked) {
        this.dao.delete(player);
        this.socialManager.unregisterHook(player);

        Settings.IMP.MAIN.AFTER_UNLINKAGE_COMMANDS.forEach(command ->
            this.server.getCommandManager().executeAsync(p -> Tristate.TRUE, command.replace("{NICKNAME}", player.getLowercaseNickname())));
      } else {
        UpdateBuilder<SocialPlayer, String> updateBuilder = this.dao.updateBuilder();
        updateBuilder.where().eq(SocialPlayer.LOWERCASE_NICKNAME_FIELD, player.getLowercaseNickname());
        updateBuilder.updateColumnValue(dbField, null);
        updateBuilder.update();

        this.socialManager.unregisterHook(dbField, player);
      }

      this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.UNLINK_SUCCESS);
      this.server.getPlayer(player.getLowercaseNickname()).ifPresent(p ->
          p.sendMessage(SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.UNLINK_SUCCESS_GAME)));
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

    this.server.getEventManager().register(this, new LimboAuthListener(this, this.plugin, this.dao, this.socialManager,
        this.keyboard, this.geoIp
    ));
    this.server.getEventManager().register(this, new ReloadListener(this));

    if (this.purgeCacheTask != null) {
      this.purgeCacheTask.cancel();
    }

    this.purgeCacheTask = this.server.getScheduler()
        .buildTask(this, () -> this.checkCache(this.cachedAccountRegistrations, Settings.IMP.MAIN.PURGE_REGISTRATION_CACHE_MILLIS))
        .delay(net.elytrium.limboauth.Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .repeat(net.elytrium.limboauth.Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .schedule();

    CommandManager commandManager = this.server.getCommandManager();
    commandManager.unregister(Settings.IMP.MAIN.LINKAGE_MAIN_CMD);
    commandManager.unregister(Settings.IMP.MAIN.FORCE_UNLINK_MAIN_CMD);

    commandManager.register(
        Settings.IMP.MAIN.LINKAGE_MAIN_CMD,
        new ValidateLinkCommand(this),
        Settings.IMP.MAIN.LINKAGE_ALIAS_CMD.toArray(new String[0])
    );
    commandManager.register(
        Settings.IMP.MAIN.FORCE_UNLINK_MAIN_CMD,
        new ForceSocialUnlinkCommand(this),
        Settings.IMP.MAIN.FORCE_UNLINK_ALIAS_CMD.toArray(new String[0])
    );
  }

  private void checkCache(Map<?, ? extends CachedUser> userMap, long time) {
    userMap.entrySet().stream()
        .filter(userEntry -> userEntry.getValue().getCheckTime() + time <= System.currentTimeMillis())
        .map(Map.Entry::getKey)
        .forEach(userMap::remove);
  }

  public void unregisterPlayer(String nickname) {
    try {
      SocialPlayer player = this.dao.queryForId(nickname.toLowerCase(Locale.ROOT));
      if (player != null) {
        this.socialManager.unregisterHook(player);
        this.dao.delete(player);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public void linkSocial(String lowercaseNickname, String dbField, Long id) throws SQLException {
    SocialPlayer socialPlayer = this.dao.queryForId(lowercaseNickname);
    if (socialPlayer == null) {
      Settings.IMP.MAIN.AFTER_LINKAGE_COMMANDS.forEach(command ->
          this.server.getCommandManager().executeAsync(p -> Tristate.TRUE, command.replace("{NICKNAME}", lowercaseNickname)));

      this.dao.create(new SocialPlayer(lowercaseNickname));
    } else if (!Settings.IMP.MAIN.ALLOW_ACCOUNT_RELINK && SocialPlayer.DatabaseField.valueOf(dbField).getIdFor(socialPlayer) != null) {
      this.socialManager.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
      return;
    }

    UpdateBuilder<SocialPlayer, String> updateBuilder = this.dao.updateBuilder();
    updateBuilder.where().eq(SocialPlayer.LOWERCASE_NICKNAME_FIELD, lowercaseNickname);
    updateBuilder.updateColumnValue(dbField, id);
    updateBuilder.update();
  }

  public Integer getCode(String nickname) {
    return this.codeMap.get(nickname);
  }

  public TempAccount getTempAccount(String nickname) {
    return this.requestedReverseMap.get(nickname);
  }

  public void removeCode(String nickname) {
    this.requestedReverseMap.remove(nickname);
    this.codeMap.remove(nickname);
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

  private static class CachedUser {

    private final long checkTime = System.currentTimeMillis();

    public long getCheckTime() {
      return this.checkTime;
    }
  }

  private static class CachedRegisteredUser extends CachedUser {

    private int registrationAmount;

    public int getRegistrationAmount() {
      return this.registrationAmount;
    }

    public void incrementRegistrationAmount() {
      this.registrationAmount++;
    }
  }

  private static void setSerializer(Serializer serializer) {
    SERIALIZER = serializer;
  }

  public static Serializer getSerializer() {
    return SERIALIZER;
  }
}
