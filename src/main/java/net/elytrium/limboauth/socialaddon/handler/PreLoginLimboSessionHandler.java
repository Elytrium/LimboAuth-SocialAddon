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

package net.elytrium.limboauth.socialaddon.handler;

import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.socialaddon.listener.LimboAuthListener;
import net.elytrium.limboauth.socialaddon.model.SocialPlayer;

public class PreLoginLimboSessionHandler implements LimboSessionHandler {

  private final LimboAuthListener listener;
  private final TaskEvent event;
  private final SocialPlayer player;

  public PreLoginLimboSessionHandler(LimboAuthListener listener, TaskEvent event, SocialPlayer player) {
    this.listener = listener;
    this.event = event;
    this.player = player;
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer limboPlayer) {
    if (Settings.IMP.MAIN.DISABLE_FALLING) {
      limboPlayer.disableFalling();
    } else {
      limboPlayer.enableFalling();
    }

    this.listener.authMainHook(this.player, limboPlayer, this.event);
  }
}
