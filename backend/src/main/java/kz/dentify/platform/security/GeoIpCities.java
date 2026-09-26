package kz.dentify.platform.security;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.maxmind.db.CHMCache;
import com.maxmind.db.Metadata;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

/**
 * Город по IP из локальной базы mmdb — без внешних вызовов (PLT-04). Читает GeoIP2 API MaxMind; формат совместим с
 * бесплатной DB-IP IP to City Lite (лицензия CC BY 4.0, атрибуция — IP Geolocation by DB-IP, https://db-ip.com) и с
 * GeoLite2 City.
 * <p>
 * Базы нет — город не определяется, это не ошибка: база не коммитится и может быть не скачана. Название города — на
 * языке запроса, если оно есть в базе, иначе русское, иначе английское.
 */
class GeoIpCities implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(GeoIpCities.class);

	private final @Nullable DatabaseReader reader;

	GeoIpCities(@Nullable DatabaseReader reader) {
		this.reader = reader;
	}

	/** База из файла; не задан, файла нет или он не читается — без базы. */
	static GeoIpCities open(@Nullable Path database) {
		if (database == null) {
			log.info("GeoIP-база не задана (dentify.security.geoip.database): город сессий не определяется");
			return new GeoIpCities(null);
		}
		if (!Files.isRegularFile(database)) {
			log.info("GeoIP-базы нет по пути {}: город сессий не определяется", database.toAbsolutePath());
			return new GeoIpCities(null);
		}
		try {
			DatabaseReader reader = new DatabaseReader.Builder(database.toFile()).withCache(new CHMCache()).build();
			Metadata metadata = reader.metadata();
			log.info("GeoIP-база {} от {}", metadata.databaseType(), metadata.buildTime());
			return new GeoIpCities(reader);
		}
		catch (IOException ex) {
			log.warn("GeoIP-база {} не читается, город сессий не определяется: {}", database.toAbsolutePath(),
					ex.getClass().getSimpleName());
			return new GeoIpCities(null);
		}
	}

	boolean available() {
		return this.reader != null;
	}

	/** Город IP или null: базы нет, адрес не IP, адрес не найден или без города. */
	@Nullable String city(String ip, Locale locale) {
		DatabaseReader current = this.reader;
		if (current == null) {
			return null;
		}
		try {
			Optional<CityResponse> response = current.tryCity(InetAddress.ofLiteral(ip.strip()));
			return response.map((city) -> name(city.city().names(), locale)).orElse(null);
		}
		catch (IllegalArgumentException | IOException | GeoIp2Exception ex) {
			log.debug("Город по IP не определён: {}", ex.getClass().getSimpleName());
			return null;
		}
	}

	private static @Nullable String name(@Nullable Map<String, String> names, Locale locale) {
		if (names == null || names.isEmpty()) {
			return null;
		}
		for (String language : new String[] { locale.getLanguage(), "ru", "en" }) {
			String name = names.get(language);
			if (StringUtils.hasText(name)) {
				return name;
			}
		}
		return null;
	}

	@Override
	public void destroy() throws IOException {
		if (this.reader != null) {
			this.reader.close();
		}
	}

}
