package kz.dentify.platform.api;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.jspecify.annotations.Nullable;

/**
 * IP клиента: разбор записи из {@code request.getRemoteAddr()} без DNS и маска для показа. Наружу IP — только маской:
 * список сессий (PLT-04), журнал действий (PLT-09), журнал входов (IAM-29). Полный адрес хранится в сессии в Valkey,
 * в {@code audit_log} и {@code auth_event}, в интерфейс не уходит.
 */
public final class IpAddresses {

	static final String MASK = "•";

	private IpAddresses() {
	}

	/**
	 * IP маской: у IPv4 видны первые два октета — 95.56.•.•, у IPv6 первые две группы — 2a02:8109:•:•. IPv4 внутри
	 * IPv6 показывается как IPv4. Не адрес — null.
	 */
	public static @Nullable String mask(String ip) {
		InetAddress address = parse(ip);
		if (address == null) {
			return null;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			return Byte.toUnsignedInt(bytes[0]) + "." + Byte.toUnsignedInt(bytes[1]) + "." + MASK + "." + MASK;
		}
		if (address instanceof Inet6Address) {
			return Integer.toHexString(((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff)) + ":"
					+ Integer.toHexString(((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff)) + ":" + MASK + ":" + MASK;
		}
		return null;
	}

	/** Адрес из текстовой записи IP без обращения к DNS или null, если это не IP. */
	public static @Nullable InetAddress parse(@Nullable String ip) {
		if (ip == null) {
			return null;
		}
		try {
			return InetAddress.ofLiteral(ip.strip());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
