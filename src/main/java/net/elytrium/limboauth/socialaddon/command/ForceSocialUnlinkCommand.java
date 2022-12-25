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
import java.util.List;
import net.elytrium.limboauth.socialaddon.Addon;
import net.elytrium.limboauth.socialaddon.Settings;

public class ForceSocialUnlinkCommand implements SimpleCommand {

  private final Addon addon;

  public ForceSocialUnlinkCommand(Addon addon) {
    this.addon = addon;
  }

  @Override
  public void execute(Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      source.sendMessage(Addon.getSerializer()
          .deserialize(Settings.IMP.MAIN.STRINGS.FORCE_UNLINK_CMD_USAGE));
    } else {
      this.addon.unregisterPlayer(args[0]);
    }
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    return SimpleCommand.super.suggest(invocation);
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return invocation.source().getPermissionValue("limboauth.admin.socialaddon.forceunlink") == Tristate.TRUE;
  }
}
