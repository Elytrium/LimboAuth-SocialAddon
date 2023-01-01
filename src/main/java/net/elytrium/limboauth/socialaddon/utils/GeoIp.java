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

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import net.elytrium.limboauth.socialaddon.Settings;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

public class GeoIp {

  private static final String GEOIP_CITY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download"
      + "?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz";
  private static final String GEOIP_COUNTRY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download"
      + "?edition_id=GeoLite2-Country&license_key=%s&suffix=tar.gz";
  private final Path dataPath;

  private DatabaseReader reader;

  public GeoIp(Path dataPath) {
    this.dataPath = dataPath;

    try {
      this.initialiseGeoIp();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initialiseGeoIp() throws Exception {
    Path path = this.dataPath.resolve("geo.mmdb");
    if (!Files.exists(path)) {
      this.downloadDatabase();
    }
    if ((System.currentTimeMillis() - path.toFile().lastModified()) > Settings.IMP.MAIN.GEOIP.UPDATE_INTERVAL) {
      this.downloadDatabase();
    }

    this.reader = new DatabaseReader.Builder(path.toFile()).withCache(new CHMCache(4096 * 4)).build();
  }

  private void downloadDatabase() throws Exception {
    String uri = String.format(Settings.IMP.MAIN.GEOIP.PRECISION.equalsIgnoreCase("city")
        ? GEOIP_CITY_DOWNLOAD : GEOIP_COUNTRY_DOWNLOAD, Settings.IMP.MAIN.GEOIP.LICENSE_KEY);

    ByteArrayInputStream byteStream = new ByteArrayInputStream(IOUtils.toByteArray(new URL(uri).openStream()));
    try (GZIPInputStream gzip = new GZIPInputStream(byteStream);
         TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzip)) {
      TarArchiveEntry entry;
      byte[] b = new byte[4096];
      while ((entry = tarInputStream.getNextTarEntry()) != null) {
        if (entry.getName().endsWith("mmdb")) {
          Path path = this.dataPath.resolve("geo.mmdb");
          Files.deleteIfExists(path);

          try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            int r;
            while ((r = tarInputStream.read(b)) != -1) {
              fos.write(b, 0, r);
            }
          }

        }
      }
    }
  }

  public String getLocation(String ip) {
    try {
      InetAddress address = InetAddress.getByName(ip);
      if (Settings.IMP.MAIN.GEOIP.PRECISION.equalsIgnoreCase("city")) {
        CityResponse response = this.reader.city(address);
        return response.getCity().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE)
            + ", " + response.getCountry().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE);
      } else {
        CountryResponse response = this.reader.country(address);
        return response.getCountry().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE);
      }
    } catch (IOException | GeoIp2Exception e) {
      e.printStackTrace();
      return Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE;
    }
  }

}
