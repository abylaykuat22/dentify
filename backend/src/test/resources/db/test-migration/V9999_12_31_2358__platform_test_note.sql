-- Только для тестов (spring.flyway.locations в application-test.yml): таблица арендатора для проверок изоляции
-- клиник (T-PLT-03) — TenantIsolationTests, RowLevelSecurityTests, VersionConflictTests. В dev и prod её нет.
-- Создана строго по шаблону docs/architecture.md, «Таблица арендатора»: так тесты проверяют и сам шаблон.
CREATE TABLE test_note (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id   uuid        NOT NULL,
    branch_id   uuid,
    title       text        NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    archived_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_test_note PRIMARY KEY (id),
    CONSTRAINT ux_test_note_clinic_id UNIQUE (clinic_id, id)
);
CREATE INDEX ix_test_note_clinic_created ON test_note (clinic_id, created_at);

ALTER TABLE test_note ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_note FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON test_note
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());

-- Миграция с данными при FORCE RLS по рецепту docs/architecture.md: FORCE снимается и возвращается в той же
-- миграции. Тест каталога (RowLevelSecurityTests) проверяет, что после всех миграций FORCE снова стоит.
ALTER TABLE test_note NO FORCE ROW LEVEL SECURITY;
UPDATE test_note SET title = btrim(title) WHERE title <> btrim(title);
ALTER TABLE test_note FORCE ROW LEVEL SECURITY;
