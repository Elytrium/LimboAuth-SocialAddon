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

package net.elytrium.limboauth.socialaddon.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboauth.socialaddon.Settings;
import org.slf4j.Logger;

public class UpdatesChecker {

  public static void checkForUpdates(Logger logger) {
    try {
      URLConnection conn = new URL("https://raw.githubusercontent.com/Elytrium/LimboAuth-SocialAddon/master/VERSION").openConnection();
      int timeout = (int) TimeUnit.SECONDS.toMillis(5);
      conn.setConnectTimeout(timeout);
      conn.setReadTimeout(timeout);
      try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        String latestVersion = in.readLine();
        if (latestVersion == null) {
          logger.warn("Unable to check for updates.");
          return;
        }
        String latestVersion0 = getCleanVersion(latestVersion.trim());
        String currentVersion0 = getCleanVersion(Settings.IMP.VERSION);
        int latestVersionId = Integer.parseInt(latestVersion0.replace(".", "").replace("$", ""));
        int currentVersionId = Integer.parseInt(currentVersion0.replace(".", "").replace("$", ""));
        if (latestVersion0.endsWith("$")) {
          --latestVersionId;
        }
        if (currentVersion0.endsWith("$")) {
          --currentVersionId;
        }

        if (currentVersionId < latestVersionId) {
          logger.error("****************************************");
          logger.warn("The new LimboAuth update was found, please update.");
          logger.error("https://github.com/Elytrium/LimboAuth-SocialAddon/releases/");
          logger.error("****************************************");
        }
      }
    } catch (IOException e) {
      logger.warn("Unable to check for updates.", e);
    }
  }

  private static String getCleanVersion(String version) {
    int indexOf = version.indexOf("-");
    if (indexOf > 0) {
      return version.substring(0, indexOf) + "$"; // "$" - Indicates that the version is not release.
    } else {
      return version;
    }
  }
}
