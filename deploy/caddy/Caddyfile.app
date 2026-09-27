# Caddy сервиса web — всё приложение в контейнерах (docker compose up, T-PLT-15, PLT-26). Схема прода: один origin,
# /api → бэкенд (сервис app), остальное — production-сборка SPA (образ frontend/Dockerfile кладёт dist в /srv).
# Снаружи — http://localhost:8088 (порт — DENTIFY_WEB_PORT в deploy/.env). Без TLS: браузер принимает Secure-cookie
# сессии и на http://localhost (безопасный контекст), но не на http://127.0.0.1 — открывать по localhost.
# HTTPS как в проде (HSTS, HTTP/2) — профиль proxy (Caddyfile.dev) и прод-Caddyfile (T-PLT-25).
{
	auto_https off
}

:8080 {
	encode zstd gzip

	# Actuator слушает только внутренний порт 8081 и сюда не проксируется; явный 404, иначе /actuator/* получил бы
	# index.html со статусом 200. Обязателен и в прод-Caddyfile (T-PLT-25, PLT-10).
	handle /actuator* {
		respond 404
	}

	handle /api/* {
		reverse_proxy app:8080
	}

	# Файлы сборки с хэшем в имени — навсегда (только существующие: 404 браузер кэшировать не должен); нет файла — 404,
	# а не index.html (иначе браузер получил бы HTML вместо JS).
	handle /assets/* {
		root * /srv
		@built file
		header @built Cache-Control "public, max-age=31536000, immutable"
		file_server
	}

	# Остальное — SPA: существующий файл (favicon) или index.html для любого адреса приложения (/login, /schedule …).
	# Без хэша в имени — no-cache (браузер сверяет с сервером): после пересборки сразу берутся новые чанки.
	handle {
		root * /srv
		header Cache-Control "no-cache"
		try_files {path} /index.html
		file_server
	}
}
