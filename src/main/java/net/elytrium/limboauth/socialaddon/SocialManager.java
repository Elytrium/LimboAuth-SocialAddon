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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.SocialInitializationException;
import net.elytrium.limboauth.socialaddon.social.SocialMessageListener;

public class SocialManager {

  private final LinkedList<AbstractSocial> socialList;
  private final LinkedList<SocialMessageListener> messageEvents = new LinkedList<>();
  private final HashMap<String, BiConsumer<String, Long>> buttonEvents = new HashMap<>();

  public SocialManager(AbstractSocial.Constructor... socialList) {
    this.socialList = new LinkedList<>();
    for (AbstractSocial.Constructor function : socialList) {
      try {
        AbstractSocial social = function.newInstance(this::onMessageReceived, this::onButtonClicked);
        if (social.isEnabled()) {
          this.socialList.add(social);
        }
      } catch (SocialInitializationException e) {
        e.printStackTrace();
      }
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

  public void removeButtonEvent(String id) {
    this.buttonEvents.remove(id);
  }

  public void start() {
    for (AbstractSocial social : this.socialList) {
      try {
        social.start();
      } catch (SocialInitializationException e) {
        e.printStackTrace();
      }
    }
  }

  public void stop() {
    this.socialList.forEach(AbstractSocial::stop);
  }

  public void unregisterHook(SocialPlayer player) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .forEach(e -> e.onPlayerRemoved(player));
  }

  public void unregisterHook(String dbField, SocialPlayer player) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .filter(e -> e.getDbField().equals(dbField))
        .forEach(e -> e.onPlayerRemoved(player));
  }

  public void registerHook(String dbField, Long id) {
    this.socialList.stream()
        .filter(e -> e.getDbField().equals(dbField))
        .forEach(e -> e.onPlayerAdded(id));
  }

  public void broadcastMessage(SocialPlayer player, String message, List<List<AbstractSocial.ButtonItem>> item) {
    this.broadcastMessage(player, message, item, AbstractSocial.ButtonVisibility.DEFAULT);
  }

  public void broadcastMessage(SocialPlayer player, String message,
                               List<List<AbstractSocial.ButtonItem>> item, AbstractSocial.ButtonVisibility visibility) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .forEach(e -> e.sendMessage(player, message, item, visibility));
  }

  public void broadcastMessage(SocialPlayer player, String message) {
    this.socialList.stream()
        .filter(e -> e.canSend(player))
        .forEach(e -> e.sendMessage(player, message));
  }

  public void broadcastMessage(String dbField, Long id, String message, List<List<AbstractSocial.ButtonItem>> item) {
    this.broadcastMessage(dbField, id, message, item, AbstractSocial.ButtonVisibility.DEFAULT);
  }


  public void broadcastMessage(String dbField, Long id, String message,
                               List<List<AbstractSocial.ButtonItem>> item, AbstractSocial.ButtonVisibility visibility) {
    this.socialList.stream()
        .filter(e -> e.getDbField().equals(dbField))
        .forEach(e -> e.sendMessage(id, message, item, visibility));
  }

  public void broadcastMessage(String dbField, Long id, String message) {
    this.socialList.stream()
        .filter(e -> e.getDbField().equals(dbField))
        .forEach(e -> e.sendMessage(id, message));
  }
}
