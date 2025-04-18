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

package net.elytrium.limboauth.socialaddon.model;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.table.DatabaseTable;

@SuppressWarnings("unused")
@DatabaseTable(tableName = SocialPlayer.TABLE_NAME)
public class SocialPlayer {
  public static final String TABLE_NAME = "SOCIAL";
  public static final String LOWERCASE_NICKNAME_FIELD = "LOWERCASENICKNAME";
  public static final String VK_DB_FIELD = "VK_ID";
  public static final String TELEGRAM_DB_FIELD = "TELEGRAM_ID";
  public static final String DISCORD_DB_FIELD = "DISCORD_ID";
  public static final String BLOCKED_FIELD = "BLOCKED";
  public static final String TOTP_ENABLED_FIELD = "TOTP_ENABLED";
  public static final String NOTIFY_ENABLED_FIELD = "NOTIFY_ENABLED";

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(id = true, columnName = LOWERCASE_NICKNAME_FIELD)
  private String lowercaseNickname;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = VK_DB_FIELD)
  private Long vkID;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = TELEGRAM_DB_FIELD)
  private Long telegramID;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = DISCORD_DB_FIELD)
  private Long discordID;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = BLOCKED_FIELD)
  private Boolean blocked = Settings.IMP.MAIN.DEFAULT_BLOCKED;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = TOTP_ENABLED_FIELD)
  private Boolean totpEnabled = Settings.IMP.MAIN.DEFAULT_TOTP_ENABLED;

  @net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField(columnName = NOTIFY_ENABLED_FIELD)
  private Boolean notifyEnabled = Settings.IMP.MAIN.DEFAULT_NOTIFY_ENABLED;

  public SocialPlayer(String lowercaseNickname) {
    this.lowercaseNickname = lowercaseNickname;
  }

  public SocialPlayer() {

  }

  public String getLowercaseNickname() {
    return this.lowercaseNickname;
  }

  public Long getVkID() {
    return this.vkID;
  }

  public void setVkID(Long vkID) {
    this.vkID = vkID;
  }

  public Long getTelegramID() {
    return this.telegramID;
  }

  public void setTelegramID(Long telegramID) {
    this.telegramID = telegramID;
  }

  public Long getDiscordID() {
    return this.discordID;
  }

  public void setDiscordID(Long discordID) {
    this.discordID = discordID;
  }

  public Boolean isBlocked() {
    return this.blocked;
  }

  public void setBlocked(Boolean blocked) {
    this.blocked = blocked;
  }

  public Boolean isTotpEnabled() {
    return this.totpEnabled && (this.getDiscordID() != null || this.getVkID() != null || this.getTelegramID() != null);
  }

  public void setTotpEnabled(Boolean totpEnabled) {
    this.totpEnabled = totpEnabled;
  }

  public boolean isNotifyEnabled() {
    return this.notifyEnabled;
  }

  public void setNotifyEnabled(boolean notifyEnabled) {
    this.notifyEnabled = notifyEnabled;
  }

  public enum DatabaseField {
    VK_ID(SocialPlayer::getVkID, SocialPlayer::setVkID),
    TELEGRAM_ID(SocialPlayer::getTelegramID, SocialPlayer::setTelegramID),
    DISCORD_ID(SocialPlayer::getDiscordID, SocialPlayer::setDiscordID);

    private final Function<SocialPlayer, Long> idGetter;
    private final BiConsumer<SocialPlayer, Long> idSetter;

    DatabaseField(Function<SocialPlayer, Long> idGetter, BiConsumer<SocialPlayer, Long> idSetter) {
      this.idGetter = idGetter;
      this.idSetter = idSetter;
    }

    public Long getIdFor(SocialPlayer player) {
      return this.idGetter.apply(player);
    }

    public void setIdFor(SocialPlayer player, Long id) {
      this.idSetter.accept(player, id);
    }
  }
}
