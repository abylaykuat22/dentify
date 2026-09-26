-- iam · журнал входов (T-IAM-02; IAM-02, IAM-03, PLT-05): вход, выход, неверный пароль, блокировка, отказ в доступе.
-- Каждая попытка входа — строка с временем, аккаунтом, IP и User-Agent. Экран «Входы и сессии» (IAM-29) и выгрузка —
-- поверх этой таблицы. Соглашения — docs/architecture.md, «Вход, выход и /api/v1/me».
--
-- Глобальная таблица, без clinic_id и RLS (RowLevelSecurityTests.GLOBAL_TABLES):
--   * попытка входа случается до выбора клиники, и у большинства событий клиники нет вовсе: неверный пароль,
--     блокировка, вход аккаунта с членствами в нескольких клиниках;
--   * аккаунт один на все клиники (IAM-01), и журнал клиники (IAM-29) — это события её людей: строки аккаунтов, у которых
--     есть членство в клинике (читается в контексте клиники через membership под RLS);
--   * неизвестный логин — строка без аккаунта (account_id NULL): она не видна ни одной клинике, но IP и User-Agent нужны
--     для разбора подбора. Сам введённый логин не хранится: это телефон или e-mail, возможно чужой.
-- Приложение только дописывает журнал: UPDATE, DELETE и TRUNCATE у dentify_app отняты. Хранение — не меньше года
-- (IAM-29); удаления старых строк пока нет.
CREATE TABLE auth_event (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    at          timestamptz NOT NULL DEFAULT now(),
    account_id  uuid,
    kind        text        NOT NULL,
    ip          inet,
    user_agent  text,
    CONSTRAINT pk_auth_event PRIMARY KEY (id),
    CONSTRAINT fk_auth_event_account FOREIGN KEY (account_id) REFERENCES account (id),
    -- Виды событий этой задачи; смена и сброс пароля, принятие приглашения, завершение сеанса администратором
    -- добавляют свои задачи (T-IAM-13, T-IAM-16, T-IAM-24) новой миграцией.
    CONSTRAINT ck_auth_event_kind CHECK (kind IN ('SIGN_IN', 'SIGN_OUT', 'WRONG_PASSWORD', 'LOCKED_OUT',
                                                  'ACCESS_DISABLED')),
    CONSTRAINT ck_auth_event_user_agent_length CHECK (length(user_agent) BETWEEN 1 AND 512)
);

-- Журнал людей клиники за период и события одного сотрудника (IAM-29).
CREATE INDEX ix_auth_event_account_id_at ON auth_event (account_id, at);

REVOKE UPDATE, DELETE, TRUNCATE ON auth_event FROM dentify_app;

COMMENT ON TABLE auth_event IS
    'Журнал входов: вход, выход, неверный пароль, блокировка, отказ в доступе; IP и User-Agent. Только дописывается.';
