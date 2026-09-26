#!/usr/bin/env bash
# База dentify: роли под RLS, схема, расширения, гранты по умолчанию.
#
# Выполняется образом postgres один раз — при инициализации пустого тома (docker-entrypoint-initdb.d).
# Поменяли пароли в deploy/.env → пересоздать том: deploy/dev.ps1 reset-db -Force.
#
# Роли (решение для T-PLT-03, RLS):
#   postgres       суперпользователь контейнера; только админка и этот скрипт, приложение им не ходит.
#   dentify_owner  владелец схемы dentify и всех таблиц; им ходит Flyway (DDL). Не суперпользователь,
#                  NOBYPASSRLS: при FORCE ROW LEVEL SECURITY политики действуют и на владельца.
#                  Так же будет в managed PostgreSQL, где суперпользователя не дают.
#   dentify_app    роль приложения: только DML, таблицами не владеет, NOBYPASSRLS — RLS действует всегда.
set -Eeuo pipefail

: "${DENTIFY_OWNER_PASSWORD:?DENTIFY_OWNER_PASSWORD is not set (deploy/.env)}"
: "${DENTIFY_APP_PASSWORD:?DENTIFY_APP_PASSWORD is not set (deploy/.env)}"

# Пароли передаются psql-переменными (:'var' экранирует как литерал), а не подстановкой в текст SQL.
psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set owner_password="$DENTIFY_OWNER_PASSWORD" \
  --set app_password="$DENTIFY_APP_PASSWORD" <<'SQL'
CREATE ROLE dentify_owner
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD :'owner_password';

CREATE ROLE dentify_app
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT
  PASSWORD :'app_password';

-- К базе подключаются только свои роли; в служебные базы — никто, кроме суперпользователя.
REVOKE ALL ON DATABASE dentify FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres, template1 FROM PUBLIC;
GRANT CONNECT, TEMPORARY, CREATE ON DATABASE dentify TO dentify_owner;
GRANT CONNECT ON DATABASE dentify TO dentify_app;

-- Схема приложения. public остаётся под расширения, создавать в ней никому нельзя (как и в PG 15+).
CREATE SCHEMA dentify AUTHORIZATION dentify_owner;
REVOKE ALL ON SCHEMA dentify FROM PUBLIC;
GRANT USAGE ON SCHEMA dentify TO dentify_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER DATABASE dentify SET search_path = dentify, public;

-- btree_gist: EXCLUDE-ограничение «приёмы в одном кресле не пересекаются»;
-- pg_trgm: нечёткий поиск пациентов. uuidv7() в PG 18 встроена, расширение не нужно.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- Всё, что dentify_owner (Flyway) создаст в схеме dentify, сразу доступно приложению на DML.
-- TRUNCATE, REFERENCES, TRIGGER и DDL приложению не выдаются.
ALTER DEFAULT PRIVILEGES FOR ROLE dentify_owner IN SCHEMA dentify
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dentify_app;
ALTER DEFAULT PRIVILEGES FOR ROLE dentify_owner IN SCHEMA dentify
  GRANT USAGE, SELECT ON SEQUENCES TO dentify_app;
ALTER DEFAULT PRIVILEGES FOR ROLE dentify_owner
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE dentify_owner IN SCHEMA dentify
  GRANT EXECUTE ON FUNCTIONS TO dentify_app;
SQL

echo "dentify: roles dentify_owner/dentify_app, schema dentify, extensions btree_gist/pg_trgm are ready"
