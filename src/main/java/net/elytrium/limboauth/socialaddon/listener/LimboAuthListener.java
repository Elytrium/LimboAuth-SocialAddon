/*
 * Copyright (C) 2022 - 2025 Elytrium
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

package net.elytrium.limboauth.socialaddon.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.commons.config.Placeholders;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.event.AuthUnregisterEvent;
import net.elytrium.limboauth.event.PostAuthorizationEvent;
import net.elytrium.limboauth.event.PostRegisterEvent;
import net.elytrium.limboauth.event.PreAuthorizationEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.socialaddon.Addon;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.SocialManager;
import net.elytrium.limboauth.socialaddon.handler.PreLoginLimboSessionHandler;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.utils.GeoIp;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.Dao;
import net.kyori.adventure.text.Component;

public class LimboAuthListener {

  private static final String ASK_NO_BTN = "ask_no";
  private static final String ASK_YES_BTN = "ask_yes";

  private final Component blockedAccount = Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.BLOCK_KICK_MESSAGE);
  private final Component askedKick = Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_KICK_MESSAGE);
  private final Component askedValidate = Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_VALIDATE_GAME);
  private final Component linkAnnouncement;

  private final Addon addon;
  private final LimboAuth plugin;
  private final Dao<SocialPlayer, String> socialPlayerDao;
  private final SocialManager socialManager;

  private final List<List<AbstractSocial.ButtonItem>> yesNoButtons;
  private final List<List<AbstractSocial.ButtonItem>> keyboard;
  private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

  private final GeoIp geoIp;
  private final boolean auth2faWithoutPassword = Settings.IMP.MAIN.AUTH_2FA_WITHOUT_PASSWORD;

  public LimboAuthListener(Addon addon, LimboAuth plugin, Dao<SocialPlayer, String> socialPlayerDao, SocialManager socialManager,
                           List<List<AbstractSocial.ButtonItem>> keyboard, GeoIp geoIp) {
    this.addon = addon;
    this.plugin = plugin;
    this.socialPlayerDao = socialPlayerDao;
    this.socialManager = socialManager;
    this.keyboard = keyboard;
    this.geoIp = geoIp;
    if (Settings.IMP.MAIN.REVERSE_YES_NO_BUTTONS) {
      this.yesNoButtons = List.of(
          List.of(
              new AbstractSocial.ButtonItem(ASK_YES_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_YES, AbstractSocial.ButtonItem.Color.GREEN),
              new AbstractSocial.ButtonItem(ASK_NO_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_NO, AbstractSocial.ButtonItem.Color.RED)
          )
      );
    } else {
      this.yesNoButtons = List.of(
          List.of(
              new AbstractSocial.ButtonItem(ASK_NO_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_NO, AbstractSocial.ButtonItem.Color.RED),
              new AbstractSocial.ButtonItem(ASK_YES_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_YES, AbstractSocial.ButtonItem.Color.GREEN)
          )
      );
    }

    this.socialManager.registerKeyboard(this.yesNoButtons);
    this.socialManager.removeButtonEvent(ASK_NO_BTN);
    this.socialManager.removeButtonEvent(ASK_YES_BTN);

    this.socialManager.addButtonEvent(ASK_NO_BTN, (dbField, id) -> {
      SocialPlayer player = this.queryPlayer(dbField, id);

      if (player != null && this.sessions.containsKey(player.getLowercaseNickname())) {
        this.sessions.get(player.getLowercaseNickname()).getEvent().completeAndCancel(this.askedKick);
        this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_WARN, this.keyboard);
      }
    });

    this.socialManager.addButtonEvent(ASK_YES_BTN, (dbField, id) -> {
      SocialPlayer player = this.queryPlayer(dbField, id);

      if (player != null && this.sessions.containsKey(player.getLowercaseNickname())) {
        AuthSession authSession = this.sessions.get(player.getLowercaseNickname());
        if (this.auth2faWithoutPassword) {
          LimboPlayer limboPlayer = authSession.getPlayer();
          limboPlayer.disconnect();
          Player proxyPlayer = limboPlayer.getProxyPlayer();
          this.plugin.cacheAuthUser(proxyPlayer);
          this.plugin.updateLoginData(proxyPlayer);
        } else {
          authSession.getEvent().complete(TaskEvent.Result.NORMAL);
        }

        this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_THANKS, this.keyboard);
      }
    });

    if (Settings.IMP.MAIN.STRINGS.LINK_ANNOUNCEMENT == null || Settings.IMP.MAIN.STRINGS.LINK_ANNOUNCEMENT.isEmpty()) {
      this.linkAnnouncement = null;
    } else {
      this.linkAnnouncement = Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.LINK_ANNOUNCEMENT);
    }
  }

  @Subscribe
  public void onAuth(PreAuthorizationEvent event) {
    Player proxyPlayer = event.getPlayer();
    SocialPlayer player = this.queryPlayer(proxyPlayer);
    if (player != null && player.isBlocked()) {
      event.cancel(this.blockedAccount);
    }

    if (this.auth2faWithoutPassword) {
      if (player != null && player.isTotpEnabled()) {
        event.setResult(TaskEvent.Result.WAIT);
        this.plugin.getAuthServer().spawnPlayer(proxyPlayer, new PreLoginLimboSessionHandler(this, event, player));
      }
    }
  }

  @Subscribe
  public void onAuthCompleted(PostAuthorizationEvent event) {
    Player proxyPlayer = event.getPlayer().getProxyPlayer();
    if (!this.auth2faWithoutPassword) {
      SocialPlayer player = this.queryPlayer(proxyPlayer);

      if (player != null && player.isTotpEnabled()) {
        event.setResult(TaskEvent.Result.WAIT);
        this.authMainHook(player, event.getPlayer(), event);
      }
    }

    if (!this.playerExists(proxyPlayer) && this.linkAnnouncement != null) {
      proxyPlayer.sendMessage(this.linkAnnouncement);
    }
  }

  public void authMainHook(SocialPlayer player, LimboPlayer limboPlayer, TaskEvent event) {
    Player proxyPlayer = limboPlayer.getProxyPlayer();
    this.sessions.put(player.getLowercaseNickname(), new AuthSession(event, limboPlayer));

    String ip = proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
    this.socialManager.broadcastMessage(player, Placeholders.replace(Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_VALIDATE,
            ip, Optional.ofNullable(this.geoIp).map(nonNullGeo -> nonNullGeo.getLocation(ip)).orElse("")),
        this.yesNoButtons, AbstractSocial.ButtonVisibility.PREFER_INLINE);

    proxyPlayer.sendMessage(this.askedValidate);
  }

  @Subscribe
  public void onRegisterCompleted(PostRegisterEvent event) {
    if (this.linkAnnouncement != null) {
      event.getPlayer()
          .getProxyPlayer()
          .sendMessage(this.linkAnnouncement);
    }
  }

  @Subscribe
  public void onGameProfile(PlayerChooseInitialServerEvent event) {
    SocialPlayer player = this.queryPlayer(event.getPlayer());
    if (player != null && Settings.IMP.MAIN.ENABLE_NOTIFY && player.isNotifyEnabled()) {
      String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
      this.socialManager.broadcastMessage(player, Placeholders.replace(Settings.IMP.MAIN.STRINGS.NOTIFY_JOIN,
          ip,
          Optional.ofNullable(this.geoIp).map(nonNullGeo -> nonNullGeo.getLocation(ip)).orElse("")), this.keyboard);
    }
  }

  @Subscribe
  public void onPlayerLeave(DisconnectEvent event) {
    if (event.getPlayer().getCurrentServer().isEmpty()) {
      return;
    }

    SocialPlayer player = this.queryPlayer(event.getPlayer());
    if (player != null) {
      if (Settings.IMP.MAIN.ENABLE_NOTIFY && player.isNotifyEnabled()) {
        this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_LEAVE, this.keyboard);
      }

      this.sessions.remove(player.getLowercaseNickname());
    }
  }

  @Subscribe
  public void onUnregister(AuthUnregisterEvent event) {
    this.addon.unregisterPlayer(event.getNickname());
  }

  private boolean playerExists(Player player) {
    try {
      return this.socialPlayerDao.idExists(player.getUsername().toLowerCase(Locale.ROOT));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private SocialPlayer queryPlayer(Player player) {
    try {
      return this.socialPlayerDao.queryForId(player.getUsername().toLowerCase(Locale.ROOT));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private SocialPlayer queryPlayer(String dbField, Long id) {
    try {
      List<SocialPlayer> l = this.socialPlayerDao.queryForEq(dbField, id);

      if (l.size() == 0) {
        return null;
      }

      return l.get(0);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class AuthSession {
    private final TaskEvent event;
    private final LimboPlayer player;

    private AuthSession(TaskEvent event, LimboPlayer player) {
      this.event = event;
      this.player = player;
    }

    public TaskEvent getEvent() {
      return this.event;
    }

    public LimboPlayer getPlayer() {
      return this.player;
    }
  }
}
