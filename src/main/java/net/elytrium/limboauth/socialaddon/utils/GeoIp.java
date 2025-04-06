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

package net.elytrium.limboauth.socialaddon.utils;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.AbstractNamedRecord;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import net.elytrium.commons.config.Placeholders;
import net.elytrium.limboauth.socialaddon.Settings;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

public class GeoIp {

  private final DatabaseReader reader;
  private final boolean cityEnabled;

  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  public GeoIp(Path dataPath) {
    this.cityEnabled = Settings.IMP.MAIN.GEOIP.FORMAT.contains("{CITY}");

    try {
      Path path = dataPath.resolve(this.cityEnabled ? "city.mmdb" : "country.mmdb");
      if (!Files.exists(path) || (System.currentTimeMillis() - path.toFile().lastModified())
          > Settings.IMP.MAIN.GEOIP.UPDATE_INTERVAL) {
        String uri = Placeholders.replace(this.cityEnabled ? Settings.IMP.MAIN.GEOIP.MMDB_CITY_DOWNLOAD
                : Settings.IMP.MAIN.GEOIP.MMDB_COUNTRY_DOWNLOAD, Settings.IMP.MAIN.GEOIP.LICENSE_KEY);

        ByteArrayInputStream byteStream = new ByteArrayInputStream(IOUtils.toByteArray(new URL(uri).openStream()));
        try (GZIPInputStream gzip = new GZIPInputStream(byteStream);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzip)) {
          TarArchiveEntry entry;
          byte[] b = new byte[4096];
          while ((entry = tarInputStream.getNextTarEntry()) != null) {
            if (entry.getName().endsWith("mmdb")) {
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

      this.reader = new DatabaseReader.Builder(path.toFile()).withCache(new CHMCache(4096 * 4)).build();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getLocation(String ip) {
    try {
      InetAddress address = InetAddress.getByName(ip);
      String city = "";
      String country = "";
      String leastSpecificSubdivision = "";
      String mostSpecificSubdivision = "";
      if (this.cityEnabled) {
        CityResponse response = this.reader.city(address);
        city = getName(response.getCity());
        country = getName(response.getCountry());
        leastSpecificSubdivision = getName(response.getLeastSpecificSubdivision());
        mostSpecificSubdivision = getName(response.getMostSpecificSubdivision());
      } else {
        CountryResponse response = this.reader.country(address);
        country = getName(response.getCountry());
      }

      return Placeholders.replace(Settings.IMP.MAIN.GEOIP.FORMAT, city, country, leastSpecificSubdivision, mostSpecificSubdivision);
    } catch (IOException | GeoIp2Exception e) {
      e.printStackTrace(); // printStackTrace is necessary there
      return Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE;
    }
  }

  private static String getName(AbstractNamedRecord response) {
    return response.getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE);
  }
}
