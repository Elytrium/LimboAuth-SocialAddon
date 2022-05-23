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

import com.google.gson.JsonObject;
import com.vk.api.sdk.actions.LongPoll;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.callback.longpoll.responses.GetLongPollEventsResponse;
import com.vk.api.sdk.objects.groups.responses.GetLongPollServerResponse;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButton;
import com.vk.api.sdk.objects.messages.KeyboardButtonAction;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.TemplateActionTypeNames;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;

public class VKSocial extends AbstractSocial {
  private VkApiClient vk;
  private GroupActor actor;
  private boolean polling;

  public VKSocial(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
    super(onMessageReceived, onButtonClicked);
  }

  @Override
  public boolean isEnabled() {
    return Settings.IMP.MAIN.VK.ENABLED;
  }

  @Override
  public void init() {
    this.stop();

    TransportClient transportClient = new HttpTransportClient();
    GroupActor tempActor = new GroupActor(0, Settings.IMP.MAIN.VK.TOKEN);

    try {
      this.vk = new VkApiClient(transportClient);
      int groupId = this.vk.groups().getByIdObjectLegacy(tempActor).groupIds(Collections.emptyList()).execute().get(0).getId();

      this.actor = new GroupActor(groupId, Settings.IMP.MAIN.VK.TOKEN);
      this.vk.groups().setLongPollSettings(this.actor, groupId).enabled(true)
          .messageEvent(true)
          .messageNew(true)
          .execute();

      this.polling = true;
      new Thread(() -> {
        while (this.polling) {
          try {
            this.startPolling();
          } catch (LongPollServerKeyExpiredException ignored) {
            // ignored
          } catch (ClientException | ApiException e) {
            e.printStackTrace();
          }
        }
      }).start();
    } catch (ApiException | ClientException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    this.polling = false;
  }

  public void startPolling() throws ClientException, ApiException {
    GetLongPollServerResponse serverInfo = this.vk.groups().getLongPollServer(this.actor, this.actor.getGroupId()).execute();
    LongPoll longPoll = new LongPoll(this.vk);

    String server = serverInfo.getServer();
    String key = serverInfo.getKey();
    String ts = serverInfo.getTs();
    while (this.polling) {
      GetLongPollEventsResponse longPollResponse = longPoll.getEvents(server, key, ts).waitTime(25).execute();
      ts = longPollResponse.getTs();

      longPollResponse.getUpdates().forEach(e -> {
        if (e.has("type") && e.has("object")) {
          String type = e.get("type").getAsString();
          JsonObject object = e.get("object").getAsJsonObject();

          if (object != null) {
            switch (type) {
              case "message_new": {
                this.onMessageNew(object);
                break;
              }
              case "message_event": {
                this.onMessageEvent(object);
                break;
              }
              default: {
                // ignored
                break;
              }
            }
          }
        }
      });
    }
  }

  @Override
  public String getDbField() {
    return SocialPlayer.VK_DB_FIELD;
  }

  @Override
  public void onPlayerAdded(Long id) {

  }

  @Override
  public void onPlayerRemoved(SocialPlayer player) {

  }

  @Override
  public void sendMessage(Long id, String content, List<List<ButtonItem>> buttons) {
    List<List<KeyboardButton>> vkButtons = buttons.stream().map(row -> row.stream().map(button -> {
      KeyboardButtonColor color;
      switch (button.getColor()) {
        case RED:
          color = KeyboardButtonColor.NEGATIVE;
          break;
        case GREEN:
          color = KeyboardButtonColor.POSITIVE;
          break;
        case SECONDARY:
        case LINK:
          color = KeyboardButtonColor.DEFAULT;
          break;
        case PRIMARY:
        default:
          color = KeyboardButtonColor.PRIMARY;
          break;
      }

      JsonObject payload = new JsonObject();
      payload.addProperty("button", button.getId());

      return new KeyboardButton()
          .setColor(color)
          .setAction(
              new KeyboardButtonAction()
                  .setType(TemplateActionTypeNames.CALLBACK)
                  .setLabel(button.getValue())
                  .setPayload(payload.toString()));
    }).collect(Collectors.toList())).collect(Collectors.toList());

    try {
      Keyboard keyboard = new Keyboard()
          .setButtons(vkButtons)
          .setInline(false)
          .setOneTime(false);

      this.vk.messages()
          .send(this.actor)
          .userId(id.intValue())
          .message(content)
          .keyboard(keyboard)
          .randomId(ThreadLocalRandom.current().nextInt())
          .execute();
    } catch (ClientException | ApiException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void sendMessage(SocialPlayer player, String content, List<List<ButtonItem>> buttons) {
    this.sendMessage(player.getVkID(), content, buttons);
  }

  @Override
  public boolean canSend(SocialPlayer player) {
    return player.getVkID() != null;
  }

  public void onMessageNew(JsonObject messageNew) {
    if (messageNew.has("message")) {
      JsonObject message = messageNew.get("message").getAsJsonObject();

      if (message.has("text")) {
        this.proceedMessage(SocialPlayer.VK_DB_FIELD, message.get("from_id").getAsLong(), message.get("text").getAsString());
      }
    }
  }

  public void onMessageEvent(JsonObject messageEvent) {
    if (messageEvent.has("payload")) {
      JsonObject payload = messageEvent.get("payload").getAsJsonObject();
      String eventId = messageEvent.get("event_id").getAsString();
      int userId = messageEvent.get("user_id").getAsInt();
      int peerId = messageEvent.get("peer_id").getAsInt();

      try {
        this.vk.messages()
            .sendMessageEventAnswer(this.actor, eventId, userId, peerId)
            .execute();
      } catch (ClientException | ApiException e) {
        e.printStackTrace();
      }

      if (payload.has("button")) {
        this.proceedButton(SocialPlayer.VK_DB_FIELD, (long) userId, payload.get("button").getAsString());
      }
    }
  }
}
