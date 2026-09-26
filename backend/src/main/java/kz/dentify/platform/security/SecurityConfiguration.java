package kz.dentify.platform.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.bucket4j.TimeMeter;
import jakarta.servlet.DispatcherType;
import kz.dentify.platform.security.SecurityProperties.PasswordHashing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.session.autoconfigure.SessionTimeout;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Spring Security Dentify (PLT-04, PLT-05, PLT-06). Две цепочки:
 * <ul>
 * <li>{@link #managementSecurity} — actuator на внутреннем порту управления 8081: открыт без входа, наружу порт не
 * публикуется. Spring Boot вешает ту же {@code springSecurityFilterChain} и на сервер управления, поэтому без своей
 * цепочки health получил бы 401;</li>
 * <li>{@link #apiSecurity} — всё остальное, то есть API на 8080. Без входа открыты только {@code /api/v1/auth/**}
 * (вход, выход, CSRF-токен, сброс пароля), {@code /api/v1/public/**} (регистрация клиники, города, приглашение по
 * ссылке), пересылка ошибок {@code /error} и, где springdoc включён, спецификация OpenAPI; прочее — только с
 * сессией.</li>
 * </ul>
 * Сессия stateful: Spring Session в Valkey с индексом по аккаунту, cookie {@code DENTIFY_SESSION} HttpOnly, Secure,
 * SameSite=Lax. Вход и выход — {@link SessionAccountSignIn}, отзыв сессий — {@link SessionRevocationFilter}, срок сессии
 * от входа — {@link SessionLifetimeFilter}. Нет входа — 401 ProblemDetail без редиректа, нет права
 * или CSRF-токена — 403 ProblemDetail ({@link ProblemSecurityHandler}); форм входа, HTTP Basic, стандартного
 * {@code /logout} и сохранения запроса до входа нет. CSRF — встроенный режим Security 7 для SPA {@code csrf.spa()}:
 * cookie {@code XSRF-TOKEN} читается скриптом и возвращается заголовком {@code X-XSRF-TOKEN}. CORS выключен: SPA и
 * API на одном origin.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

	/** Порядок цепочки actuator: раньше всех, у неё свой {@code securityMatcher}. */
	static final int MANAGEMENT_ORDER = 0;

	/** Порядок цепочки API: последней, она без {@code securityMatcher} и берёт всё, что не взяли другие. */
	static final int API_ORDER = 100;

	static final String ERROR_PATH = "/error";

	/** Хранилище контекста безопасности — как у цепочек по умолчанию: атрибут запроса и HTTP-сессия. */
	static final SecurityContextRepository CONTEXT_REPOSITORY = new DelegatingSecurityContextRepository(
			new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

	@Bean
	@Order(MANAGEMENT_ORDER)
	SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
		return http.securityMatcher(EndpointRequest.toAnyEndpoint())
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()))
			.build();
	}

	@Bean
	@Order(API_ORDER)
	SecurityFilterChain apiSecurity(HttpSecurity http, Environment environment, SecurityProperties properties,
			CookieCsrfTokenRepository csrfTokens, ProblemSecurityHandler problems, RedisRateLimiter rateLimiter,
			SessionLifetime sessionLifetime, SessionRevocations sessionRevocations,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors,
			@Qualifier(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME) LocaleResolver localeResolver) throws Exception {
		List<String> publicPaths = publicPaths(environment);
		return http
			.authorizeHttpRequests((requests) -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
				.permitAll()
				.requestMatchers(publicPaths.toArray(String[]::new))
				.permitAll()
				.anyRequest()
				.authenticated())
			// /error только отдаёт ProblemDetail и ничего не меняет: прямой POST на него — 404, а не 403 CSRF
			.csrf((csrf) -> csrf.spa().csrfTokenRepository(csrfTokens).ignoringRequestMatchers(ERROR_PATH))
			.securityContext((context) -> context.securityContextRepository(CONTEXT_REPOSITORY))
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(problems)
				.accessDeniedHandler(problems))
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives(properties.getContentSecurityPolicy()))
				.httpStrictTransportSecurity((hsts) -> hsts.maxAgeInSeconds(properties.getHstsMaxAge().toSeconds())
					.includeSubDomains(true))
				.frameOptions((frames) -> frames.deny())
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.permissionsPolicyHeader((permissions) -> permissions.policy(properties.getPermissionsPolicy())))
			.cors(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.addFilterAfter(new LocaleContextFilter(localeResolver), SecurityContextHolderFilter.class)
			// после записи заголовков: 401 отозванной и истёкшей сессии уходит с CSP, HSTS и остальными; отзыв — раньше
			// срока от входа: отозванной сессии бездействие не пишется
			.addFilterAfter(new SessionRevocationFilter(sessionRevocations), HeaderWriterFilter.class)
			.addFilterAfter(new SessionLifetimeFilter(sessionLifetime, Clock.systemUTC()), SessionRevocationFilter.class)
			.addFilterBefore(new ApiRateLimitFilter(rateLimiter, errors), CsrfFilter.class)
			.build();
	}

	/** Вход, выход, CSRF-токен и сброс пароля: механика сессии. */
	static final String AUTH_PATHS = "/api/v1/auth/**";

	/**
	 * Открытые ресурсы API до входа: регистрация клиники, города для её формы, приглашение по ссылке. Как у входа — CSRF и
	 * общий лимит API по IP; данные клиники здесь — только по токену ссылки, и каждый эндпоинт записан в реестр теста
	 * изоляции отдельно, с причиной (docs/architecture.md, «Соглашения API и OpenAPI»).
	 */
	static final String PUBLIC_PATHS = "/api/v1/public/**";

	/**
	 * Пути без входа: вход, открытые ресурсы, пересылка ошибок и спецификация OpenAPI там, где springdoc её отдаёт (dev,
	 * test).
	 */
	static List<String> publicPaths(Environment environment) {
		List<String> paths = new ArrayList<>(List.of(AUTH_PATHS, PUBLIC_PATHS, ERROR_PATH));
		if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true)) {
			String docs = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
			paths.addAll(List.of(docs, docs + "/**"));
		}
		if (environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
			paths.addAll(List.of("/swagger-ui.html", "/swagger-ui/**"));
		}
		return List.copyOf(paths);
	}

	@Bean
	ProblemSecurityHandler problemSecurityHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
		return new ProblemSecurityHandler(errors);
	}

	/**
	 * CSRF-токен в cookie {@code XSRF-TOKEN}: не HttpOnly — его читает SPA; Secure и SameSite=Lax, как cookie сессии.
	 * Тот же экземпляр — у цепочки API и у входа: вход меняет токен.
	 */
	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer((cookie) -> cookie.secure(true).sameSite("Lax"));
		return repository;
	}

	/**
	 * Срок сессии: 12 часов бездействия без Запомнить ({@code server.servlet.session.timeout}), срок от входа — 30 дней
	 * с Запомнить ({@code dentify.security.remember-me}) и без него ({@code dentify.security.session-max-age}).
	 */
	@Bean
	SessionLifetime sessionLifetime(SessionTimeout sessionTimeout, SecurityProperties properties) {
		return new SessionLifetime(sessionTimeout.getTimeout(), properties.getRememberMe(),
				properties.getSessionMaxAge());
	}

	/**
	 * Отметки отзыва сессий в Valkey: завершённую сессию не оживит запрос, который шёл в момент завершения
	 * ({@link SessionRevocationFilter}).
	 */
	@Bean
	SessionRevocations sessionRevocations(StringRedisTemplate redis, SessionLifetime sessionLifetime) {
		return new SessionRevocations(redis, sessionLifetime, Clock.systemUTC());
	}

	/**
	 * Неполный hash сессии в Valkey — сессии нет ({@link SafeRedisSessionMapper}, рецепт Spring Session Configuring Redis
	 * Session Mapper). Его пишет конец запроса завершённой сессии, если ключ сессии уже истёк: без этого запрос с её
	 * cookie, список сессий и завершение всех отвечали бы 500.
	 */
	@Bean
	SessionRepositoryCustomizer<RedisIndexedSessionRepository> safeSessionMapper(
			SessionDataRedisProperties sessionProperties) {
		return (repository) -> repository.setRedisSessionMapper(
				new SafeRedisSessionMapper(repository.getSessionRedisOperations(), sessionProperties.getNamespace()));
	}

	/**
	 * Что делает вход с сессией, как стандартные фильтры входа Spring Security: новый id сессии и новый CSRF-токен. Токен
	 * загружается сразу, как в режиме {@code csrf.spa()}: ответ на вход несёт новую cookie {@code XSRF-TOKEN}.
	 */
	@Bean
	SessionAccountSignIn accountSignIn(CookieCsrfTokenRepository csrfTokens, SessionLifetime sessionLifetime,
			SessionRevocations sessionRevocations) {
		XorCsrfTokenRequestAttributeHandler eagerToken = new XorCsrfTokenRequestAttributeHandler();
		eagerToken.setCsrfRequestAttributeName(null);
		CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokens);
		csrf.setRequestHandler(eagerToken);
		SessionAuthenticationStrategy strategy = new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
		return new SessionAccountSignIn(strategy, CONTEXT_REPOSITORY, csrfTokens, sessionLifetime, sessionRevocations);
	}

	/**
	 * Cookie сессии {@code DENTIFY_SESSION}: свойства {@code server.servlet.session.cookie.*}, как у автоконфигурации
	 * Spring Session, плюс постоянный срок при Запомнить ({@link SessionCookieSerializer}).
	 */
	@Bean
	SessionCookieSerializer cookieSerializer(ServerProperties server, SecurityProperties properties,
			ObjectProvider<DefaultCookieSerializerCustomizer> customizers) {
		Cookie cookie = server.getServlet().getSession().getCookie();
		SessionCookieSerializer serializer = new SessionCookieSerializer(properties.getRememberMe());
		PropertyMapper map = PropertyMapper.get();
		map.from(cookie::getName).to(serializer::setCookieName);
		map.from(cookie::getDomain).to(serializer::setDomainName);
		map.from(cookie::getPath).to(serializer::setCookiePath);
		map.from(cookie::getHttpOnly).to(serializer::setUseHttpOnlyCookie);
		map.from(cookie::getSecure).to(serializer::setUseSecureCookie);
		map.from(cookie::getSameSite).to((sameSite) -> serializer.setSameSite(sameSite.attributeValue()));
		customizers.orderedStream().forEach((customizer) -> customizer.customize(serializer));
		return serializer;
	}

	@Bean
	RedisAccountSessions accountSessions(FindByIndexNameSessionRepository<? extends Session> sessions,
			SessionClients clients, SessionLifetime sessionLifetime, SessionRevocations sessionRevocations) {
		return new RedisAccountSessions(sessions, clients, sessionLifetime, sessionRevocations, Clock.systemUTC());
	}

	@Bean
	SessionClients sessionClients(GeoIpCities cities) {
		return new SessionClients(new UserAgents(), cities);
	}

	/** Локальная GeoIP-база: нет файла — город сессий не определяется, старт не падает. */
	@Bean
	GeoIpCities geoIpCities(SecurityProperties properties) {
		return GeoIpCities.open(properties.getGeoip().databasePath());
	}

	@Bean
	RedisRateLimiter rateLimiter(LettuceConnectionFactory connectionFactory, StringRedisTemplate redis,
			SecurityProperties properties) {
		return new RedisRateLimiter(connectionFactory, redis, properties.getRateLimit(),
				TimeMeter.SYSTEM_MILLISECONDS);
	}

	/**
	 * Заголовкам прокси Tomcat верит только от точного адреса прокси: иначе клиент из частной сети подменял бы свой IP в
	 * лимитах, журнале входов и журнале действий ({@link TrustedProxiesCheck}).
	 */
	@Bean
	TrustedProxiesCheck trustedProxiesCheck(ServerProperties server, ObjectProvider<TomcatServerProperties> tomcat) {
		TomcatServerProperties.Remoteip remoteIp = tomcat.getIfAvailable(TomcatServerProperties::new).getRemoteip();
		return new TrustedProxiesCheck(server, remoteIp.getInternalProxies(), remoteIp.getTrustedProxies());
	}

	@Bean
	SecurityCurrentAccountProvider currentAccountProvider() {
		return new SecurityCurrentAccountProvider();
	}

	@Bean
	SecurityCurrentPermissions currentPermissions() {
		return new SecurityCurrentPermissions();
	}

	@Bean
	CommonPasswordPolicy passwordPolicy() {
		return new CommonPasswordPolicy(CommonPasswordPolicy.loadCommonPasswords());
	}

	/**
	 * Хэш пароля — Argon2id с параметрами OWASP: память 19 МиБ, 2 прохода, 1 поток, соль 16 байт, хэш 32 байта. В БД —
	 * {@code {argon2}$argon2id$v=19$m=19456,t=2,p=1$…}. Хэши других алгоритмов из этого списка тоже проверяются, и
	 * {@code upgradeEncoding} для них — true: вход перехэширует пароль в Argon2id с текущими параметрами. Одновременно
	 * считается не больше {@code dentify.security.password-hashing.concurrency} хэшей ({@link BoundedPasswordEncoder}):
	 * иначе параллельные входы заняли бы память без предела.
	 */
	@Bean
	PasswordEncoder passwordEncoder(SecurityProperties properties) {
		Map<String, PasswordEncoder> encoders = new HashMap<>();
		encoders.put(PasswordEncoders.ARGON2, PasswordEncoders.argon2id());
		encoders.put("argon2@SpringSecurity_v5_8", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
		encoders.put("bcrypt", new BCryptPasswordEncoder());
		encoders.put("pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
		PasswordHashing hashing = properties.getPasswordHashing();
		return new BoundedPasswordEncoder(new DelegatingPasswordEncoder(PasswordEncoders.ARGON2, encoders),
				hashing.effectiveConcurrency(), hashing.getMaxWait());
	}

}
