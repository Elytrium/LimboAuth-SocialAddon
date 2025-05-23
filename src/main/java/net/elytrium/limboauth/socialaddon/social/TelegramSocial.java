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

package net.elytrium.limboauth.socialaddon.social;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.stream.Collectors;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramSocial extends AbstractSocial {

  private final TelegramBotsApi api;
  private TGBot bot;
  private BotSession botSession;

  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  public TelegramSocial(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) throws SocialInitializationException {
    super(onMessageReceived, onButtonClicked);

    try {
      this.api = new TelegramBotsApi(DefaultBotSession.class);
    } catch (TelegramApiException e) {
      throw new SocialInitializationException(e);
    }
  }

  @Override
  public boolean isEnabled() {
    return Settings.IMP.MAIN.TELEGRAM.ENABLED;
  }

  @Override
  public void start() throws SocialInitializationException {
    try {
      this.bot = new TGBot(Settings.IMP.MAIN.TELEGRAM.TOKEN, this::proceedMessage, this::proceedButton);
      this.botSession = this.api.registerBot(this.bot);
    } catch (TelegramApiException e) {
      throw new SocialInitializationException(e);
    }
  }

  @Override
  public void stop() {
    if (this.botSession != null) {
      this.botSession.stop();
    }
  }

  @Override
  public String getDbField() {
    return SocialPlayer.TELEGRAM_DB_FIELD;
  }

  @Override
  public void onPlayerAdded(Long id) {

  }

  @Override
  public void onPlayerRemoved(SocialPlayer player) {

  }

  @Override
  public void sendMessage(Long id, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility) {
    ReplyKeyboard keyboard;
    switch (visibility) {
      case PREFER_INLINE: {
        keyboard = new InlineKeyboardMarkup(buttons.stream().map(row -> row.stream().map(e -> {
          InlineKeyboardButton button = new InlineKeyboardButton();
          button.setText(e.getValue());
          button.setCallbackData(e.getId());

          return button;
        }).collect(Collectors.toList())).collect(Collectors.toList()));
        break;
      }
      default:
      case PREFER_KEYBOARD: {
        keyboard = new ReplyKeyboardMarkup(buttons.stream().map(row ->
            new KeyboardRow(row.stream().map(e ->
                new KeyboardButton(e.getValue())).collect(Collectors.toList())))
            .collect(Collectors.toList()));
        break;
      }
    }

    this.bot.sendMessage(id, content, keyboard);
  }

  @Override
  public void sendMessage(SocialPlayer player, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility) {
    this.sendMessage(player.getTelegramID(), content, buttons, visibility);
  }

  @Override
  public boolean canSend(SocialPlayer player) {
    return player.getTelegramID() != null;
  }

  private static final class TGBot extends TelegramLongPollingBot {

    private final String token;
    private final SocialMessageListener onMessageReceived;
    private final SocialButtonListener onButtonClicked;

    private TGBot(String token, SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
      this.token = token;
      this.onMessageReceived = onMessageReceived;
      this.onButtonClicked = onButtonClicked;
    }

    @Override
    public String getBotUsername() {
      return "LimboAuth Social Addon";
    }

    @Override
    public String getBotToken() {
      return this.token;
    }

    public void sendMessage(Long id, String content, ReplyKeyboard keyboard) {
      SendMessage sendMessage = new SendMessage();
      sendMessage.setChatId(String.valueOf(id));
      sendMessage.setText(content);
      sendMessage.setReplyMarkup(keyboard);

      try {
        this.sendApiMethod(sendMessage);
      } catch (TelegramApiRequestException e) {
        if (Settings.IMP.MAIN.DEBUG) {
          e.printStackTrace(); // printStackTrace is necessary there
        }
      } catch (TelegramApiException e) {
        e.printStackTrace(); // printStackTrace is necessary there
      }
    }

    @Override
    public void onUpdateReceived(Update update) {
      if (update.hasMessage()) {
        Message message = update.getMessage();
        if (message != null && message.hasText()) {
          this.onMessageReceived.accept(SocialPlayer.TELEGRAM_DB_FIELD, message.getChatId(), message.getText());
        }
      }

      if (update.hasCallbackQuery()) {
        CallbackQuery query = update.getCallbackQuery();
        if (query != null) {
          this.onButtonClicked.accept(SocialPlayer.TELEGRAM_DB_FIELD, query.getFrom().getId(), query.getData());
        }
      }
    }
  }
}
