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

package net.elytrium.limboauth.socialaddon.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import net.elytrium.limboauth.socialaddon.Addon;
import net.elytrium.limboauth.socialaddon.Settings;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.dao.Dao;
import net.elytrium.limboauth.thirdparty.com.j256.ormlite.stmt.UpdateBuilder;

public class ValidateLinkCommand implements SimpleCommand {

  private final Addon addon;
  private final Dao<SocialPlayer, String> socialDao;

  public ValidateLinkCommand(Addon addon, Dao<SocialPlayer, String> socialDao) {
    this.addon = addon;
    this.socialDao = socialDao;
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
              if (this.socialDao.queryForId(username) == null) {
                this.socialDao.create(new SocialPlayer(username));
              } else if (!Settings.IMP.MAIN.ALLOW_ACCOUNT_RELINK) {
                this.addon.getSocialManager().broadcastMessage(tempAccount.getDbField(), tempAccount.getId(), Settings.IMP.MAIN.STRINGS.LINK_ALREADY);
                return;
              }

              UpdateBuilder<SocialPlayer, String> updateBuilder = this.socialDao.updateBuilder();
              updateBuilder.where().eq(SocialPlayer.LOWERCASE_NICKNAME_FIELD, username);
              updateBuilder.updateColumnValue(tempAccount.getDbField(), tempAccount.getId());
              updateBuilder.update();

              Settings.IMP.MAIN.AFTER_LINKAGE_COMMANDS.forEach(command ->
                  this.addon.getServer().getCommandManager().executeAsync(p -> Tristate.TRUE,
                      command.replace("{NICKNAME}", player.getUsername()).replace("{UUID}", player.getUniqueId().toString())));

              this.addon.getSocialManager().registerHook(tempAccount.getDbField(), tempAccount.getId());

              this.addon.getSocialManager()
                  .broadcastMessage(tempAccount.getDbField(), tempAccount.getId(), Settings.IMP.MAIN.STRINGS.LINK_SUCCESS, this.addon.getKeyboard());
            } else {
              source.sendMessage(Addon.getSerializer()
                  .deserialize(Settings.IMP.MAIN.STRINGS.LINK_WRONG_CODE.replace("{NICKNAME}", player.getUsername())));
            }

            this.addon.removeCode(username);
          } else {
            this.sendUsage(player);
          }
        } catch (NumberFormatException ignored) {
          this.sendUsage(player);
        } catch (SQLException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  private void sendUsage(Player player) {
    player.sendMessage(Addon.getSerializer()
        .deserialize(Settings.IMP.MAIN.STRINGS.LINK_CMD_USAGE.replace("{NICKNAME}", player.getUsername())));
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
