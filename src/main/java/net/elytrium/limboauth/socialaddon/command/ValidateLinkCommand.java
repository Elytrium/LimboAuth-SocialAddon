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

package net.elytrium.limboauth.socialaddon.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import net.elytrium.commons.config.Placeholders;
import net.elytrium.limboauth.socialaddon.Addon;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;

public class ValidateLinkCommand implements SimpleCommand {

  private final Addon addon;

  public ValidateLinkCommand(Addon addon) {
    this.addon = addon;
  }

  @Override
  public void execute(Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (source instanceof Player) {
      Player player = (Player) source;

      if (args.length == 0) {
        this.sendUsage(player);
      } else {
        try {
          String username = player.getUsername().toLowerCase(Locale.ROOT);
          Integer validCode = this.addon.getCode(username);
          if (validCode != null) {
            if (validCode == Integer.parseInt(args[0])) {
              Addon.TempAccount tempAccount = this.addon.getTempAccount(username);
              SocialPlayer socialPlayer = this.addon.linkSocial(username, tempAccount.getDbField(), tempAccount.getId());
              if (socialPlayer == null) {
                player.sendMessage(Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.LINK_ALREADY_GAME));
                return;
              } else {
                this.addon.getSocialManager().registerHook(tempAccount.getDbField(), tempAccount.getId());
                this.addon.getSocialManager()
                    .broadcastMessage(tempAccount.getDbField(), tempAccount.getId(), Settings.IMP.MAIN.STRINGS.LINK_SUCCESS,
                        this.addon.getKeyboard(socialPlayer));
                player.sendMessage(Addon.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.LINK_SUCCESS_GAME));
              }
            } else {
              source.sendMessage(Addon.getSerializer()
                  .deserialize(Placeholders.replace(Settings.IMP.MAIN.STRINGS.LINK_WRONG_CODE, player.getUsername())));
            }

            this.addon.removeCode(username);
          } else {
            this.sendUsage(player);
          }
        } catch (NumberFormatException ignored) {
          this.sendUsage(player);
        } catch (SQLException ex) {
          throw new IllegalStateException(ex);
        }
      }
    }
  }

  private void sendUsage(Player player) {
    player.sendMessage(Addon.getSerializer()
        .deserialize(Placeholders.replace(Settings.IMP.MAIN.STRINGS.LINK_CMD_USAGE, player.getUsername())));
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    return SimpleCommand.super.suggest(invocation);
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return SimpleCommand.super.hasPermission(invocation);
  }
}
