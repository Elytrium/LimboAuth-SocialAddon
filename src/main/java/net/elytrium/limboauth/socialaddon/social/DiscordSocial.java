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

import java.util.List;
import java.util.stream.Collectors;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import org.jetbrains.annotations.NotNull;

public class DiscordSocial extends AbstractSocial {

  private JDA jda;

  public DiscordSocial(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
    super(onMessageReceived, onButtonClicked);
  }

  public void start() throws SocialInitializationException {
    try {
      this.jda = JDABuilder
          .create(Settings.IMP.MAIN.DISCORD.TOKEN, GatewayIntent.DIRECT_MESSAGES)
          .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
          .build();

      this.jda.addEventListener(new Listener(this::proceedMessage, this::proceedButton));
    } catch (LoginException e) {
      throw new SocialInitializationException(e);
    }
  }

  @Override
  public boolean isEnabled() {
    return Settings.IMP.MAIN.DISCORD.ENABLED;
  }

  @Override
  public void stop() {
    if (this.jda != null) {
      this.jda.shutdown();
    }
  }

  @Override
  public String getDbField() {
    return SocialPlayer.DISCORD_DB_FIELD;
  }

  @Override
  public void onPlayerAdded(Long id) {
    this.parseCommands(id, Settings.IMP.MAIN.DISCORD.ON_PLAYER_ADDED);
  }

  @Override
  public void onPlayerRemoved(SocialPlayer player) {
    this.parseCommands(player.getDiscordID(), Settings.IMP.MAIN.DISCORD.ON_PLAYER_REMOVED);
  }

  @SuppressWarnings("ConstantConditions")
  private void parseCommands(Long id, List<String> commands) {
    for (String argsString : commands) {
      String[] args = argsString.split(" ");
      String command = args[0];
      switch (command) {
        case "addrole": {
          long roleId = Long.parseLong(args[1]);
          Role role = this.jda.getRoleById(roleId);
          role.getGuild().addRoleToMember(id, role).queue();
          break;
        }
        case "remrole": {
          long roleId = Long.parseLong(args[1]);
          Role role = this.jda.getRoleById(roleId);
          role.getGuild().removeRoleFromMember(id, role).queue();
          break;
        }
        default: {
          break;
        }
      }
    }
  }

  @Override
  public void sendMessage(Long id, String content, List<List<ButtonItem>> buttons) {
    User user = this.jda.retrieveUserById(id).complete();

    if (user == null) {
      return;
    }

    List<ActionRow> actionRowList = buttons.stream().map(row ->
        ActionRow.of(row.stream().map(e -> {
          ButtonStyle style;

          switch (e.getColor()) {
            case RED:
              style = ButtonStyle.DANGER;
              break;
            case GREEN:
              style = ButtonStyle.SUCCESS;
              break;
            case LINK:
              style = ButtonStyle.LINK;
              break;
            case PRIMARY:
              style = ButtonStyle.PRIMARY;
              break;
            case SECONDARY:
            default:
              style = ButtonStyle.SECONDARY;
              break;
          }

          return Button.of(style, e.getId(), e.getValue());
        }).collect(Collectors.toList()))
    ).collect(Collectors.toList());

    user.openPrivateChannel()
        .submit()
        .thenAccept(privateChannel -> privateChannel
            .sendMessage(content)
            .setActionRows(actionRowList)
            .submit()
            .exceptionally(e -> {
              if (Settings.IMP.MAIN.DEBUG) {
                e.printStackTrace();
              }
              return null;
            }))
        .exceptionally(e -> {
          if (Settings.IMP.MAIN.DEBUG) {
            e.printStackTrace();
          }
          return null;
        });
  }

  @Override
  public void sendMessage(SocialPlayer player, String content, List<List<ButtonItem>> buttons) {
    this.sendMessage(player.getDiscordID(), content, buttons);
  }

  @Override
  public boolean canSend(SocialPlayer player) {
    return player.getDiscordID() != null;
  }

  private static class Listener extends ListenerAdapter {

    private final SocialMessageListener onMessageReceived;
    private final SocialButtonListener onButtonClicked;

    Listener(SocialMessageListener onMessageReceived, SocialButtonListener onButtonClicked) {
      this.onMessageReceived = onMessageReceived;
      this.onButtonClicked = onButtonClicked;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
      this.onMessageReceived.accept(SocialPlayer.DISCORD_DB_FIELD, event.getAuthor().getIdLong(), event.getMessage().getContentRaw());
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
      event.deferEdit().queue();
      this.onButtonClicked.accept(SocialPlayer.DISCORD_DB_FIELD, event.getUser().getIdLong(), event.getButton().getId());
    }

  }

}
