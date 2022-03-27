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

package net.elytrium.limboauth.socialaddon.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.elytrium.limboauth.event.PostAuthorizationEvent;
import net.elytrium.limboauth.event.PreAuthorizationEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.SocialManager;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.Dao;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class LimboAuthListener {

  private static final String ASK_NO_BTN = "ask_no";
  private static final String ASK_YES_BTN = "ask_yes";

  private final Component blockedAccount = LegacyComponentSerializer.legacyAmpersand().deserialize(Settings.IMP.MAIN.STRINGS.BLOCK_KICK_MESSAGE);
  private final Component askedKick = LegacyComponentSerializer.legacyAmpersand().deserialize(Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_KICK_MESSAGE);

  private final Dao<SocialPlayer, String> socialPlayerDao;
  private final SocialManager socialManager;

  private final List<List<AbstractSocial.ButtonItem>> yesNoButtons;
  private final HashMap<String, PostAuthorizationEvent> sessions = new HashMap<>();

  public LimboAuthListener(Dao<SocialPlayer, String> socialPlayerDao, SocialManager socialManager) {
    this.socialPlayerDao = socialPlayerDao;
    this.socialManager = socialManager;
    this.yesNoButtons = Collections.singletonList(Arrays.asList(
        new AbstractSocial.ButtonItem(ASK_NO_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_NO, AbstractSocial.ButtonItem.Color.RED),
        new AbstractSocial.ButtonItem(ASK_YES_BTN, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_YES, AbstractSocial.ButtonItem.Color.GREEN)
    ));

    this.socialManager.addButtonEvent(ASK_NO_BTN, (dbField, id) -> {
      SocialPlayer player = this.queryPlayer(dbField, id);

      if (player != null && this.sessions.containsKey(player.getLowercaseNickname())) {
        this.sessions.get(player.getLowercaseNickname()).completeAndCancel(this.askedKick);
      }
    });

    this.socialManager.addButtonEvent(ASK_YES_BTN, (dbField, id) -> {
      SocialPlayer player = this.queryPlayer(dbField, id);

      if (player != null && this.sessions.containsKey(player.getLowercaseNickname())) {
        this.sessions.get(player.getLowercaseNickname()).complete(TaskEvent.Result.NORMAL);
      }
    });
  }

  @Subscribe
  public void onAuthCompleted(PostAuthorizationEvent event) {
    Player proxyPlayer = event.getPlayer().getProxyPlayer();
    SocialPlayer player = this.queryPlayer(proxyPlayer);
    if (player != null && player.isTotpEnabled()) {
      event.setResult(TaskEvent.Result.WAIT);
      this.sessions.put(player.getLowercaseNickname(), event);

      String ip = proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
      this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_ASK_VALIDATE.replace("{IP}", ip), this.yesNoButtons);
    }
  }

  @Subscribe
  public void onGameProfile(PlayerChooseInitialServerEvent event) {
    SocialPlayer player = this.queryPlayer(event.getPlayer());
    if (player != null && Settings.IMP.MAIN.ENABLE_NOTIFY && player.isNotifyEnabled()) {
      String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
      this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_JOIN.replace("{IP}", ip));
    }
  }

  @Subscribe
  public void onPlayerLeave(DisconnectEvent event) {
    SocialPlayer player = this.queryPlayer(event.getPlayer());
    if (player != null) {
      if (Settings.IMP.MAIN.ENABLE_NOTIFY && player.isNotifyEnabled()) {
        this.socialManager.broadcastMessage(player, Settings.IMP.MAIN.STRINGS.NOTIFY_LEAVE);
      }

      this.sessions.remove(player.getLowercaseNickname());
    }
  }

  @Subscribe
  public void onAuth(PreAuthorizationEvent event) {
    SocialPlayer player = this.queryPlayer(event.getPlayer());
    if (player != null && player.isBlocked()) {
      event.cancel(this.blockedAccount);
    }
  }

  private SocialPlayer queryPlayer(Player player) {
    try {
      return this.socialPlayerDao.queryForId(player.getUsername().toLowerCase(Locale.ROOT));
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
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
      e.printStackTrace();
      return null;
    }
  }
}
