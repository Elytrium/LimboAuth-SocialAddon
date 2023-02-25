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

import java.util.function.Function;
import java.util.function.Supplier;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;

public enum PanelButton {
  INFO_BTN("info", () -> Settings.IMP.MAIN.STRINGS.INFO_BTN),
  BLOCK_BTN("block", () -> Settings.IMP.MAIN.STRINGS.BLOCK_TOGGLE_BTN),
  TOTP_BTN("2fa", () -> Settings.IMP.MAIN.STRINGS.TOGGLE_2FA_BTN),
  NOTIFY_BTN("notify", () -> Settings.IMP.MAIN.STRINGS.TOGGLE_NOTIFICATION_BTN),
  KICK_BTN("kick", () -> Settings.IMP.MAIN.STRINGS.KICK_BTN),
  RESTORE_BTN("restore", () -> Settings.IMP.MAIN.STRINGS.RESTORE_BTN),
  UNLINK_BTN("unlink", () -> Settings.IMP.MAIN.STRINGS.UNLINK_BTN);

  private final String id;
  private final Supplier<String> textGetter;

  PanelButton(String id, Supplier<String> textGetter) {
    this.id = id;
    this.textGetter = textGetter;
  }

  public String getId() {
    return this.id;
  }

  public String getText() {
    return this.textGetter.get();
  }

  enum Color {
    GREEN((player) -> AbstractSocial.ButtonItem.Color.GREEN),
    RED((player) -> AbstractSocial.ButtonItem.Color.RED),
    PRIMARY((player) -> AbstractSocial.ButtonItem.Color.PRIMARY),
    SECONDARY((player) -> AbstractSocial.ButtonItem.Color.SECONDARY),
    LINK((player) -> AbstractSocial.ButtonItem.Color.LINK),
    BLOCK_STATE((player) -> player.isBlocked() ? AbstractSocial.ButtonItem.Color.RED : AbstractSocial.ButtonItem.Color.SECONDARY),
    TOTP_STATE((player) -> player.isTotpEnabled() ? AbstractSocial.ButtonItem.Color.GREEN : AbstractSocial.ButtonItem.Color.RED),
    NOTIFY_STATE((player) -> player.isNotifyEnabled() ? AbstractSocial.ButtonItem.Color.GREEN : AbstractSocial.ButtonItem.Color.RED);

    private final Function<SocialPlayer, AbstractSocial.ButtonItem.Color> colorGetter;

    Color(Function<SocialPlayer, AbstractSocial.ButtonItem.Color> colorGetter) {
      this.colorGetter = colorGetter;
    }

    AbstractSocial.ButtonItem.Color getColor(SocialPlayer player) {
      return this.colorGetter.apply(player);
    }
  }
}
