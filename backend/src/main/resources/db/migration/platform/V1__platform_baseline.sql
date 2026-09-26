-- V1 · platform · базовая миграция.
-- Выполняется ролью dentify_owner (владелец схемы dentify). Соглашения — docs/architecture.md, «Миграции».

-- PostgreSQL 18+: соглашения опираются на встроенную uuidv7() (id всех таблиц).
DO $$
BEGIN
    IF current_setting('server_version_num')::int < 180000 THEN
        RAISE EXCEPTION 'Dentify requires PostgreSQL 18+ (uuidv7), found %', current_setting('server_version');
    END IF;
END
$$;

-- Расширения живут в public. Локально и в тестах их уже создал deploy/postgres/init/01-roles.sh
-- (суперпользователем); в managed PostgreSQL их включает администратор. Здесь — фиксация требования.
-- btree_gist: EXCLUDE «приёмы в одном кресле не пересекаются»; pg_trgm: нечёткий поиск пациентов.
-- NOTICE «already exists, skipping» Flyway пишет в лог как WARN — глушим до конца транзакции миграции.
SET LOCAL client_min_messages = warning;
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

COMMENT ON SCHEMA dentify IS 'Dentify: таблицы модулей. DDL — только Flyway (dentify_owner), приложение — dentify_app (DML).';

-- История миграций — только для Flyway: гранты по умолчанию дали бы dentify_app DML и на неё.
REVOKE ALL ON TABLE "${flyway:defaultSchema}"."${flyway:table}" FROM dentify_app;
