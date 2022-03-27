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

import api.longpoll.bots.LongPollBot;
import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.events.messages.MessageNew;
import api.longpoll.bots.model.objects.additional.Keyboard;
import api.longpoll.bots.model.objects.additional.buttons.Button;
import api.longpoll.bots.model.objects.additional.buttons.TextButton;
import api.longpoll.bots.model.objects.basic.Message;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;

public class VKSocial extends AbstractSocial {
  private BotImpl bot;

  public VKSocial(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
    super(onMessageReceived, onButtonClicked);
  }

  @Override
  public boolean isEnabled() {
    return Settings.IMP.MAIN.VK.ENABLED;
  }

  @Override
  public void init() throws SocialInitializationException {
    if (this.bot != null) {
      this.bot.stopPolling();
    }

    this.bot = new BotImpl(Settings.IMP.MAIN.VK.TOKEN, this::proceedMessage, this::proceedButton);
    AtomicReference<VkApiException> ex = new AtomicReference<>();

    new Thread(() -> {
      try {
        this.bot.startPolling();
      } catch (VkApiException e) {
        ex.set(e);
      }
    }).start();

    if (ex.get() != null) {
      throw new SocialInitializationException(ex.get());
    }
  }

  @Override
  public String getDbField() {
    return SocialPlayer.VK_DB_FIELD;
  }

  @Override
  public void sendMessage(Long id, String content, List<List<ButtonItem>> buttons) {
    List<List<Button>> vkButtons = buttons.stream().map(row -> row.stream().map(button -> {
      Button.Color color;
      switch (button.getColor()) {
        case RED:
          color = Button.Color.NEGATIVE;
          break;
        case GREEN:
          color = Button.Color.POSITIVE;
          break;
        case SECONDARY:
        case LINK:
          color = Button.Color.SECONDARY;
          break;
        case PRIMARY:
        default:
          color = Button.Color.PRIMARY;
          break;
      }

      JsonObject payload = new JsonObject();
      payload.addProperty("button", button.getId());

      return (Button) new TextButton(color, new TextButton.Action(button.getValue(), payload));
    }).collect(Collectors.toList())).collect(Collectors.toList());

    this.bot.sendMessage(id.intValue(), content, vkButtons);
  }

  @Override
  public void sendMessage(SocialPlayer player, String content, List<List<ButtonItem>> buttons) {
    this.sendMessage(player.getVkID(), content, buttons);
  }

  @Override
  public boolean canSend(SocialPlayer player) {
    return player.getVkID() != null;
  }

  private static class BotImpl extends LongPollBot {

    private final String token;
    private final SocialMessageListener onMessageReceived;
    private final SocialButtonListener onButtonClicked;

    BotImpl(String token, SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
      this.token = token;
      this.onMessageReceived = onMessageReceived;
      this.onButtonClicked = onButtonClicked;
    }

    @Override
    public String getAccessToken() {
      return this.token;
    }

    public void sendMessage(Integer id, String content, List<List<Button>> buttons) {
      this.vk.messages.send().setChatId(id).setMessage(content).setKeyboard(new Keyboard(buttons));
    }

    @Override
    public void onMessageNew(MessageNew messageNew) {
      Message message = messageNew.getMessage();

      if (message == null) {
        return;
      }

      if (message.getPayload() != null && message.getPayload().isJsonObject()) {
        JsonObject object = message.getPayload().getAsJsonObject();
        if (object.has("button")) {
          this.onButtonClicked.accept(SocialPlayer.VK_DB_FIELD, Long.valueOf(message.getPeerId()), object.get("button").getAsString());
        }
      } else if (message.hasText()) {
        this.onMessageReceived.accept(SocialPlayer.VK_DB_FIELD, Long.valueOf(message.getPeerId()), message.getText());
      }
    }
  }
}
