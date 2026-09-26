-- Только для тестов (spring.flyway.locations в application-test.yml). Метка — самая поздняя: миграция применяется
-- последней на свежей тестовой базе и ни от чего не зависит. В dev и prod её нет.

-- JSON-агрегат LocalizedText в jsonb (LocalizedTextMappingTests).
CREATE TABLE test_localized_probe (
    id   uuid  NOT NULL,
    name jsonb NOT NULL,
    CONSTRAINT pk_test_localized_probe PRIMARY KEY (id)
);

-- Настоящие ошибки БД через JPA (ProblemDetailsTests, DataProbeController): уникальность, NOT NULL, CHECK,
-- конфликт версии и объект, которого нет.
CREATE TABLE test_constraint_probe (
    id      uuid    NOT NULL,
    code    text    NOT NULL,
    qty     integer NOT NULL DEFAULT 0,
    version bigint  NOT NULL DEFAULT 0,
    CONSTRAINT pk_test_constraint_probe PRIMARY KEY (id),
    CONSTRAINT ux_test_constraint_probe_code UNIQUE (code),
    CONSTRAINT ck_test_constraint_probe_qty CHECK (qty >= 0)
);
