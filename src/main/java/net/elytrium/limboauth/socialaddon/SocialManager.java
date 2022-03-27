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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.SocialButtonListener;
import net.elytrium.limboauth.socialaddon.social.SocialInitializationException;
import net.elytrium.limboauth.socialaddon.social.SocialMessageListener;

public class SocialManager {

  private final LinkedList<AbstractSocial> socialList;
  private final LinkedList<SocialMessageListener> messageEvents = new LinkedList<>();
  private final HashMap<String, BiConsumer<String, Long>> buttonEvents = new HashMap<>();

  @SafeVarargs
  public SocialManager(BiFunction<SocialMessageListener, SocialButtonListener, AbstractSocial>... socialList) {
    this.socialList = new LinkedList<>();
    for (BiFunction<SocialMessageListener, SocialButtonListener, AbstractSocial> function : socialList) {
      this.socialList.add(function.apply(this::onMessageReceived, this::onButtonClicked));
    }
  }

  private void onMessageReceived(String dbField, Long id, String message) {
    this.messageEvents.forEach(e -> e.accept(dbField, id, message));
  }

  private void onButtonClicked(String dbField, Long id, String buttonId) {
    if (this.buttonEvents.containsKey(buttonId)) {
      this.buttonEvents.get(buttonId).accept(dbField, id);
    }
  }

  public void addMessageEvent(SocialMessageListener event) {
    this.messageEvents.add(event);
  }

  public void addButtonEvent(String id, BiConsumer<String, Long> event) {
    this.buttonEvents.put(id, event);
  }

  public void init() {
    this.socialList.forEach(abstractSocial -> {
      try {
        abstractSocial.init();
      } catch (SocialInitializationException e) {
        e.printStackTrace();
      }
    });
  }

  public void broadcastMessage(SocialPlayer player, String message, List<List<AbstractSocial.ButtonItem>> item) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .forEach(e -> e.sendMessage(player, message, item));
  }

  public void broadcastMessage(SocialPlayer player, String message) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .forEach(e -> e.sendMessage(player, message));
  }

  public void broadcastMessage(Long id, String message, List<List<AbstractSocial.ButtonItem>> item) {
    this.socialList.forEach(e -> e.sendMessage(id, message, item));
  }

  public void broadcastMessage(String dbField, Long id, String message) {
    this.socialList.stream().filter(e -> e.getDbField().equals(dbField)).forEach(e -> e.sendMessage(id, message));
  }
}
