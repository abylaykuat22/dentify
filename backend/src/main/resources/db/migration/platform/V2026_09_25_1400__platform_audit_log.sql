-- platform · журнал действий audit_log (T-PLT-13, PLT-09). Как пользоваться — docs/architecture.md, «Журнал действий».
--
-- Журнал только дописывается: приложение (dentify_app) читает и вставляет, UPDATE и DELETE у него отняты, а триггер
-- запрещает правки, удаление и TRUNCATE всем, включая владельца схемы. Хранится не меньше срока медданных (PLT-28):
-- старые партиции не удаляются, их архивирование — отдельная задача.
--
-- Отличия от таблицы арендатора по шаблону: служебных колонок updated_at/by и version нет — событие не меняется; время
-- события — at, автор — account_id. clinic_id, RLS (ENABLE + FORCE, стандартная политика) — как у всех таблиц клиники.
--
-- Объём: помесячные партиции по at (RANGE, границы — полночь UTC первого числа), индексы — у каждой партиции свои.
-- Запросы журнала всегда с clinic_id и обычно с периодом: лишние партиции отсекаются, внутри — индекс (clinic_id, at).

CREATE TABLE audit_log (
    id           uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id    uuid        NOT NULL,
    at           timestamptz NOT NULL DEFAULT now(),
    account_id   uuid,
    staff_id     uuid,
    ip           inet,
    entity_type  text        NOT NULL,
    module       text        GENERATED ALWAYS AS (split_part(entity_type, '.', 1)) STORED,
    entity_id    uuid,
    entity_label text,
    action       text        NOT NULL,
    diff         jsonb,
    args         jsonb,
    search_text  text        NOT NULL,
    -- в ключе партиционированной таблицы обязан быть ключ партиций
    CONSTRAINT pk_audit_log PRIMARY KEY (id, at),
    CONSTRAINT ck_audit_log_entity_type CHECK (entity_type ~ '^[a-z]+\.[a-z][a-z0-9-]*$' AND length(entity_type) <= 100),
    CONSTRAINT ck_audit_log_action CHECK (action ~ '^[a-z][a-z0-9-]*$' AND length(action) <= 50),
    CONSTRAINT ck_audit_log_entity_label CHECK (length(entity_label) <= 500),
    CONSTRAINT ck_audit_log_diff CHECK (jsonb_typeof(diff) = 'array'),
    CONSTRAINT ck_audit_log_args CHECK (jsonb_typeof(args) = 'array')
) PARTITION BY RANGE (at);

-- Лента клиники за период (экран журнала), история объекта, действия сотрудника.
CREATE INDEX ix_audit_log_clinic_at ON audit_log (clinic_id, at);
CREATE INDEX ix_audit_log_clinic_entity ON audit_log (clinic_id, entity_type, entity_id, at);
CREATE INDEX ix_audit_log_clinic_account ON audit_log (clinic_id, account_id, at);

COMMENT ON TABLE audit_log IS
    'Журнал действий клиник (PLT-09): только INSERT и SELECT, помесячные партиции audit_log_YYYY_MM.';
COMMENT ON COLUMN audit_log.diff IS
    'Разница было → стало: [{field, type, from, to}]; секреты — type secret без значений.';
COMMENT ON COLUMN audit_log.search_text IS
    'Имя объекта и русский текст события в нижнем регистре: поиск по журналу.';

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_log
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());

-- Приложению — только чтение и вставка (гранты по умолчанию дали бы и UPDATE, DELETE).
REVOKE UPDATE, DELETE ON audit_log FROM dentify_app;

-- Триггер запрета: правка и удаление строк, TRUNCATE — ошибка для любой роли, в том числе владельца схемы.
-- Строчный триггер PostgreSQL копирует в каждую партицию сам, TRUNCATE-триггер на партицию ставит
-- audit_log_create_partitions: TRUNCATE партиции напрямую триггер родителя не вызывает.
CREATE FUNCTION audit_log_forbid_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_forbid_change();
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_forbid_change();

-- Партиции заранее: прошлый месяц, текущий и months_ahead вперёд (месяцы — по UTC). Нет партиции на момент события —
-- вставка падает, и вместе с ней изменение, поэтому их создают с запасом: эта миграция, старт приложения и ежедневная
-- задача platform.audit.AuditPartitions. DEFAULT-партиции нет: она спрятала бы сбой задачи, а партицию на её диапазон
-- потом не создать без переноса строк.
--
-- SECURITY DEFINER: выполняется правами владельца схемы, поэтому dentify_app (только DML) может вызвать её, но не
-- может создать ничего другого. search_path закреплён — подмена объектов через него невозможна. Партиция создаётся
-- отдельно и присоединяется ATTACH PARTITION: он берёт на audit_log только SHARE UPDATE EXCLUSIVE и не блокирует
-- вставки, в отличие от CREATE TABLE … PARTITION OF (ACCESS EXCLUSIVE). lock_timeout — не ждать долгих транзакций:
-- не вышло — повторит следующий запуск, запас есть. Одновременные вызовы нескольких экземпляров идут по очереди
-- (advisory-lock транзакции), повторный вызов ничего не делает.
--
-- Партиция — как таблица арендатора: RLS ENABLE + FORCE и та же политика (через родителя действует политика родителя,
-- а к партиции напрямую — её собственная); прав у dentify_app на неё нет вовсе: всё только через audit_log.
CREATE FUNCTION audit_log_create_partitions(months_ahead integer) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
    SET TimeZone = 'UTC'
    SET lock_timeout = '5s'
AS $$
DECLARE
    first_month date := date_trunc('month', now())::date - interval '1 month';
    month_start date;
    partition_name text;
    created integer := 0;
BEGIN
    IF months_ahead IS NULL OR months_ahead NOT BETWEEN 1 AND 24 THEN
        RAISE EXCEPTION 'audit_log_create_partitions: months_ahead must be between 1 and 24, got %', months_ahead;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('dentify.audit_log_create_partitions', 0));
    FOR i IN 0 .. months_ahead + 1 LOOP
        month_start := first_month + make_interval(months => i);
        partition_name := 'audit_log_' || to_char(month_start, 'YYYY_MM');
        CONTINUE WHEN to_regclass('dentify.' || partition_name) IS NOT NULL;

        EXECUTE format('CREATE TABLE dentify.%I (LIKE dentify.audit_log INCLUDING ALL)', partition_name);
        EXECUTE format('ALTER TABLE dentify.%I ENABLE ROW LEVEL SECURITY, FORCE ROW LEVEL SECURITY', partition_name);
        EXECUTE format('CREATE POLICY tenant_isolation ON dentify.%I '
            'USING (clinic_id = dentify.current_clinic_id()) WITH CHECK (clinic_id = dentify.current_clinic_id())',
            partition_name);
        EXECUTE format('CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON dentify.%I '
            'FOR EACH STATEMENT EXECUTE FUNCTION dentify.audit_log_forbid_change()', partition_name);
        EXECUTE format('REVOKE ALL ON dentify.%I FROM dentify_app', partition_name);
        EXECUTE format('ALTER TABLE dentify.audit_log ATTACH PARTITION dentify.%I FOR VALUES FROM (%L) TO (%L)',
            partition_name, month_start::timestamptz, (month_start + interval '1 month')::timestamptz);
        created := created + 1;
    END LOOP;
    RETURN created;
END
$$;

COMMENT ON FUNCTION audit_log_create_partitions(integer) IS
    'Создаёт недостающие партиции audit_log: прошлый, текущий и months_ahead следующих месяцев (UTC). Возвращает число созданных.';

-- EXECUTE для dentify_app выдают гранты по умолчанию (deploy/postgres/init/01-roles.sh). PERFORM, а не SELECT: Flyway
-- печатал бы результат SELECT таблицей в лог.
DO $$
BEGIN
    PERFORM audit_log_create_partitions(3);
END
$$;
