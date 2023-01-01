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

package net.elytrium.limboauth.socialaddon.model;

import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.field.DatabaseField;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.table.DatabaseTable;

@SuppressWarnings("unused")
@DatabaseTable(tableName = "SOCIAL")
public class SocialPlayer {
  public static final String LOWERCASE_NICKNAME_FIELD = "LOWERCASENICKNAME";
  public static final String VK_DB_FIELD = "VK_ID";
  public static final String TELEGRAM_DB_FIELD = "TELEGRAM_ID";
  public static final String DISCORD_DB_FIELD = "DISCORD_ID";

  @DatabaseField(id = true, columnName = LOWERCASE_NICKNAME_FIELD)
  private String lowercaseNickname;

  @DatabaseField(columnName = VK_DB_FIELD)
  private Long vkID;

  @DatabaseField(columnName = TELEGRAM_DB_FIELD)
  private Long telegramID;

  @DatabaseField(columnName = DISCORD_DB_FIELD)
  private Long discordID;

  @DatabaseField(columnName = "BLOCKED")
  private Boolean blocked = Settings.IMP.MAIN.DEFAULT_BLOCKED;

  @DatabaseField(columnName = "TOTP_ENABLED")
  private Boolean totpEnabled = Settings.IMP.MAIN.DEFAULT_TOTP_ENABLED;

  @DatabaseField(columnName = "NOTIFY_ENABLED")
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
    return this.totpEnabled;
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
}
