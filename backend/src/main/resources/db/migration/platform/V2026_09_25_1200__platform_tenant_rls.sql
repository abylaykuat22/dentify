-- platform · тенант-контекст для RLS (T-PLT-03, PLT-02).
-- Клинику текущей транзакции приложение кладёт в настройку сессии app.clinic_id при каждой выдаче соединения из
-- пула (platform.persistence.ClinicConnectionDataSource). Политики RLS таблиц арендатора сравнивают clinic_id с
-- current_clinic_id(): шаблон — docs/architecture.md, «Таблица арендатора».
--
-- Функция в версионной миграции, а не в R__: на неё ссылаются политики таблиц из более поздних версионных миграций,
-- а повторяемые миграции Flyway выполняет после всех версионных — на пустой базе политика не нашла бы функцию.
--
-- LANGUAGE sql, STABLE, одно выражение без SECURITY DEFINER — планировщик встраивает (inline) её в запрос, как если бы
-- выражение было написано в политике: индекс по clinic_id используется, вызова функции на каждую строку нет.
-- nullif: после транзакционного set_config в сессии остаётся пустая строка, а не NULL. Без контекста — NULL, и
-- сравнение clinic_id = NULL не пропускает ни одной строки ни на чтение, ни на запись.
CREATE FUNCTION current_clinic_id() RETURNS uuid
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS $$
    SELECT nullif(current_setting('app.clinic_id', true), '')::uuid
$$;

COMMENT ON FUNCTION current_clinic_id() IS
    'Клиника текущего запроса (app.clinic_id) или NULL. Используется в политиках RLS таблиц арендатора.';

-- EXECUTE для dentify_app выдают гранты по умолчанию (deploy/postgres/init/01-roles.sh), у PUBLIC он снят там же.
