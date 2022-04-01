package net.elytrium.limboauth.socialaddon.utils;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import net.elytrium.limboauth.socialaddon.Settings;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

public class GeoIp {

  private static final String GEOIP_CITY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download" +
      "?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz";
  private static final String GEOIP_COUNTRY_DOWNLOAD = "https://download.maxmind.com/app/geoip_download" +
      "?edition_id=GeoLite2-Country&license_key=%s&suffix=tar.gz";
  private final Path dataPath;

  private DatabaseReader reader;

  public GeoIp(Path dataPath) {
    this.dataPath = dataPath;

    try {
      initialiseGeoIp();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initialiseGeoIp() throws Exception {
    Path path = dataPath.resolve("geo.mmdb");
    if (!Files.exists(path))
      downloadDatabase();
    if ((System.currentTimeMillis() - path.toFile().lastModified()) > Settings.IMP.MAIN.GEOIP.UPDATE_INTERVAL)
      downloadDatabase();

    reader = new DatabaseReader.Builder(path.toFile()).withCache(new CHMCache(4096 * 4)).build();
  }

  private void downloadDatabase() throws Exception {
    String uri = String.format(Settings.IMP.MAIN.GEOIP.PRECISION.equalsIgnoreCase("city")
        ? GEOIP_CITY_DOWNLOAD : GEOIP_COUNTRY_DOWNLOAD, Settings.IMP.MAIN.GEOIP.LICENSE_KEY);
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    Files.copy(Paths.get(uri), byteOutputStream);

    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(byteOutputStream.toByteArray()));
         TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzip)) {
      TarArchiveEntry entry;
      while ((entry = tarInputStream.getNextTarEntry()) != null) {
        if (entry.getName().endsWith("mmdb")) {
          Files.copy(new FileInputStream(entry.getFile()), dataPath.resolve("geo.mmdb"),
              StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  public String getLocation(String ip) {
    try {
      InetAddress address = InetAddress.getByName(ip);
      if (Settings.IMP.MAIN.GEOIP.PRECISION.equalsIgnoreCase("city")) {
        CityResponse response = reader.city(address);
        return response.getCity().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE)
            + ", " + response.getCountry().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE);
      } else {
        CountryResponse response = reader.country(address);
        return response.getCountry().getNames().getOrDefault(Settings.IMP.MAIN.GEOIP.LOCALE, Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE);
      }
    } catch (IOException | GeoIp2Exception e) {
      e.printStackTrace();
      return Settings.IMP.MAIN.GEOIP.DEFAULT_VALUE;
    }
  }

}
