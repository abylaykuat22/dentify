-- clinic · минимальные клиника и филиал (T-IAM-01): на них ссылаются членство в клинике и его филиалы (iam).
-- Реквизиты, пояс, настройки, адрес и часы филиала, справочник городов добавит T-CLN-01 своими миграциями; здесь только
-- то, без чего нет членства и dev-сида: название и город.
--
-- clinic — корень арендатора: своей колонки clinic_id у неё нет, её id и есть clinic_id всех данных клиники. Политика
-- RLS — та же изоляция, но по id: клиника видит только саму себя (RowLevelSecurityTests.TENANT_ROOT_TABLES). Список всех
-- клиник приложению недоступен — межклиничный реестр, если понадобится, заводится отдельно и с обоснованием.
-- Новую клинику регистрация вставляет в контексте её же id (id известен до вставки).
CREATE TABLE clinic (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    name        text        NOT NULL,
    city        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_clinic PRIMARY KEY (id),
    CONSTRAINT ck_clinic_name_length CHECK (length(name) BETWEEN 2 AND 120),
    CONSTRAINT ck_clinic_city_length CHECK (length(city) BETWEEN 1 AND 100)
);

ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic
    USING      (id = current_clinic_id())
    WITH CHECK (id = current_clinic_id());

COMMENT ON TABLE clinic IS
    'Клиника — корень арендатора: id = clinic_id её данных, RLS по id. Реквизиты и настройки — T-CLN-01.';

-- Филиал — таблица арендатора по шаблону docs/architecture.md, «Таблица арендатора».
CREATE TABLE branch (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id   uuid        NOT NULL,
    name        text        NOT NULL,
    city        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_branch PRIMARY KEY (id),
    CONSTRAINT ux_branch_clinic_id UNIQUE (clinic_id, id),
    CONSTRAINT fk_branch_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT ck_branch_name_length CHECK (length(name) BETWEEN 1 AND 100),
    CONSTRAINT ck_branch_city_length CHECK (length(city) BETWEEN 1 AND 100)
);

ALTER TABLE branch ENABLE ROW LEVEL SECURITY;
ALTER TABLE branch FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON branch
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());

COMMENT ON TABLE branch IS 'Филиал клиники. Адрес, телефон, часы работы, архив — T-CLN-01.';
