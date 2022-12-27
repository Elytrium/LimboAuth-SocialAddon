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

package net.elytrium.limboauth.socialaddon.social;

import java.util.Collections;
import java.util.List;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;

public abstract class AbstractSocial {

  private final SocialMessageListener onMessageReceived;
  private final SocialButtonListener onButtonClicked;

  protected AbstractSocial(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
    this.onMessageReceived = onMessageReceived;
    this.onButtonClicked = onButtonClicked;
  }

  protected void proceedMessage(String dbField, Long id, String message) {
    this.onMessageReceived.accept(dbField, id, message);
  }

  protected void proceedButton(String dbField, Long id, String message) {
    this.onButtonClicked.accept(dbField, id, message);
  }

  public abstract boolean isEnabled();

  public abstract void start() throws SocialInitializationException;

  public abstract void stop();

  public abstract String getDbField();

  public abstract void onPlayerAdded(Long id);

  public abstract void onPlayerRemoved(SocialPlayer player);

  public void sendMessage(Long id, String content) {
    this.sendMessage(id, content, Collections.emptyList());
  }

  public abstract void sendMessage(Long id, String content, List<List<ButtonItem>> buttons);

  public void sendMessage(SocialPlayer player, String content) {
    this.sendMessage(player, content, Collections.emptyList());
  }

  public abstract void sendMessage(SocialPlayer player, String content, List<List<ButtonItem>> buttons);

  public abstract boolean canSend(SocialPlayer player);

  public static class ButtonItem {

    private final String id;
    private final String value;
    private final Color color;

    public ButtonItem(String id, String value, Color color) {
      this.id = id;
      this.value = value;
      this.color = color;
    }

    public String getId() {
      return this.id;
    }

    public String getValue() {
      return this.value;
    }

    public Color getColor() {
      return this.color;
    }

    public enum Color {

      GREEN,
      RED,
      PRIMARY,
      SECONDARY,
      LINK
    }

  }

  public interface Constructor {
    AbstractSocial newInstance(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) throws SocialInitializationException;
  }
}
