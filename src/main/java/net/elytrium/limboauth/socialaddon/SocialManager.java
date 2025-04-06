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

package net.elytrium.limboauth.socialaddon;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.socialaddon.social.AbstractSocial;
import net.elytrium.limboauth.socialaddon.social.SocialButtonListenerAdapter;
import net.elytrium.limboauth.socialaddon.social.SocialInitializationException;
import net.elytrium.limboauth.socialaddon.social.SocialMessageListenerAdapter;

public class SocialManager {

  private final LinkedList<AbstractSocial> socialList;
  private final LinkedList<SocialMessageListenerAdapter> messageEvents = new LinkedList<>();
  private final HashMap<String, SocialButtonListenerAdapter> buttonEvents = new HashMap<>();
  private final HashMap<String, String> buttonIdMap = new HashMap<>();

  public SocialManager(AbstractSocial.Constructor... socialList) {
    this.socialList = new LinkedList<>();
    for (AbstractSocial.Constructor function : socialList) {
      try {
        AbstractSocial social = function.newInstance(this::onMessageReceived, this::onButtonClicked);
        if (social.isEnabled()) {
          this.socialList.add(social);
        }
      } catch (SocialInitializationException e) {
        e.printStackTrace(); // printStackTrace is necessary there
      }
    }
  }

  private void onMessageReceived(String dbField, Long id, String message) {
    String buttonId = this.buttonIdMap.get(message);
    if (buttonId != null) {
      this.onButtonClicked(dbField, id, buttonId);
    }

    this.messageEvents.forEach(event -> {
      try {
        event.accept(dbField, id, message);
      } catch (Exception e) {
        this.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.SOCIAL_EXCEPTION_CAUGHT);
        if (Settings.IMP.MAIN.DEBUG) {
          e.printStackTrace(); // printStackTrace is necessary there
        }
      }
    });
  }

  private void onButtonClicked(String dbField, Long id, String buttonId) {
    SocialButtonListenerAdapter buttonListenerAdapter = this.buttonEvents.get(buttonId);
    if (buttonListenerAdapter != null) {
      try {
        buttonListenerAdapter.accept(dbField, id);
      } catch (Exception e) {
        this.broadcastMessage(dbField, id, Settings.IMP.MAIN.STRINGS.SOCIAL_EXCEPTION_CAUGHT);
        if (Settings.IMP.MAIN.DEBUG) {
          e.printStackTrace(); // printStackTrace is necessary there
        }
      }
    }
  }

  public void addMessageEvent(SocialMessageListenerAdapter event) {
    this.messageEvents.add(event);
  }

  public void addButtonEvent(String id, SocialButtonListenerAdapter event) {
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
        e.printStackTrace(); // printStackTrace is necessary there
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

  public void registerKeyboard(List<List<AbstractSocial.ButtonItem>> keyboard) {
    for (List<AbstractSocial.ButtonItem> items : keyboard) {
      for (AbstractSocial.ButtonItem item : items) {
        this.buttonIdMap.put(item.getValue(), item.getId());
      }
    }
  }

  public void registerButton(AbstractSocial.ButtonItem item) {
    this.buttonIdMap.put(item.getValue(), item.getId());
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
