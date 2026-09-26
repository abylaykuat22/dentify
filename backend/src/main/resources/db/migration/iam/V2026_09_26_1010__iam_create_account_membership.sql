-- iam · аккаунты и членство в клиниках (T-IAM-01, IAM-01).
-- Аккаунт — один на человека для всех клиник: вход по телефону или e-mail и паролю. Членство — доступ аккаунта к одной
-- клинике: роль, филиалы (нет строк — все филиалы), статус, карточка сотрудника, последний вход.
-- Соглашения — docs/architecture.md, «Миграции» и «Аккаунты и членство».

-- Аккаунт — глобальная таблица, без clinic_id и RLS: аккаунт существует до выбора клиники и один на все клиники
-- (RowLevelSecurityTests.GLOBAL_TABLES). Значения приходят нормализованными (platform.api.PhoneNumber и EmailAddress):
-- телефон в E.164, e-mail без крайних пробелов и в нижнем регистре. Ограничения ниже — страховка: входные данные
-- проверяет сервис (iam.internal.Accounts), нарушение CHECK — дефект, а не ответ пользователю.
CREATE TABLE account (
    id            uuid        NOT NULL DEFAULT uuidv7(),
    phone         text,
    email         text,
    full_name     text        NOT NULL,
    password_hash text        NOT NULL,
    language      text        NOT NULL DEFAULT 'ru',
    photo_key     text,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT ck_account_contact CHECK (phone IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT ck_account_phone_e164 CHECK (phone ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT ck_account_email_length CHECK (length(email) BETWEEN 3 AND 254),
    CONSTRAINT ck_account_full_name_length CHECK (length(full_name) BETWEEN 1 AND 200),
    CONSTRAINT ck_account_password_hash CHECK (length(password_hash) BETWEEN 1 AND 500),
    CONSTRAINT ck_account_language CHECK (language IN ('ru', 'kk')),
    CONSTRAINT ck_account_photo_key_length CHECK (length(photo_key) BETWEEN 1 AND 500),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Телефон и e-mail уникальны во всём Dentify (IAM-01). NULL не мешает: аккаунтов без телефона много.
-- E-mail — без учёта регистра: индекс по lower(email), по нему же идёт поиск при входе.
CREATE UNIQUE INDEX ux_account_phone ON account (phone);
CREATE UNIQUE INDEX ux_account_email ON account (lower(email));

COMMENT ON TABLE account IS
    'Аккаунт: один на человека для всех клиник. Телефон E.164 и e-mail (lower) уникальны, хэш пароля — {argon2}.';

-- Членство — таблица арендатора по шаблону. Одно на аккаунт в клинике.
-- role_id: внешний ключ (clinic_id, role_id) → role и NOT NULL добавит T-IAM-06 вместе с таблицей ролей.
-- staff_id: карточка сотрудника модуля staff (0..1). Внешнего ключа нет: iam от staff не зависит (только от его
-- событий); частичный уникальный индекс и связь — T-IAM-26.
CREATE TABLE membership (
    id              uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id       uuid        NOT NULL,
    account_id      uuid        NOT NULL,
    role_id         uuid,
    staff_id        uuid,
    status          text        NOT NULL,
    last_sign_in_at timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_membership PRIMARY KEY (id),
    CONSTRAINT ux_membership_clinic_id UNIQUE (clinic_id, id),
    CONSTRAINT ux_membership_clinic_id_account_id UNIQUE (clinic_id, account_id),
    CONSTRAINT fk_membership_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT fk_membership_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT ck_membership_status CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED'))
);
-- Свои членства аккаунта во всех клиниках (политика own_memberships ниже).
CREATE INDEX ix_membership_account_id ON membership (account_id);

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON membership
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());

-- Исключение из «одна политика на таблицу» (RowLevelSecurityTests.READ_POLICIES, docs/architecture.md, «Аккаунты и
-- членство»): аккаунт видит свои членства во всех клиниках. Без этого вход не узнает, в какие клиники аккаунт может
-- войти (IAM-01: «входит одним паролем и видит обе клиники»), — клиника ещё не выбрана, а tenant_isolation без неё не
-- показывает ничего. Роли с BYPASSRLS и корневого арендатора нет (запрещено), а SECURITY DEFINER не помогает: FORCE
-- действует и на владельца таблицы.
--   * только SELECT: для UPDATE и DELETE строка должна пройти и политики SELECT, и политики UPDATE/DELETE (они
--     складываются через AND, документация PostgreSQL, CREATE POLICY), а у изменений есть только tenant_isolation —
--     изменить или удалить членство чужой клиники по-прежнему нельзя, вставить — тоже;
--   * только строки одного аккаунта: app.account_id ставит на время своей транзакции iam.internal.AccountMemberships
--     (set_config(…, true)) и снимает сразу после запроса. Без настройки current_account_id() — NULL, и политика не
--     пропускает ничего.
CREATE FUNCTION current_account_id() RETURNS uuid
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS $$
    SELECT nullif(current_setting('app.account_id', true), '')::uuid
$$;

COMMENT ON FUNCTION current_account_id() IS
    'Аккаунт, чьи членства во всех клиниках читает iam (app.account_id) или NULL. Только для политики own_memberships.';

CREATE POLICY own_memberships ON membership
    FOR SELECT
    USING (account_id = current_account_id());

COMMENT ON TABLE membership IS
    'Членство аккаунта в клинике: роль, филиалы, статус, сотрудник, последний вход. Свои во всех клиниках — own_memberships.';

-- Филиалы членства: нет строк — доступ ко всем филиалам клиники. Составные внешние ключи не дают связать членство с
-- филиалом другой клиники.
CREATE TABLE membership_branch (
    id            uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id     uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    branch_id     uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_membership_branch PRIMARY KEY (id),
    CONSTRAINT ux_membership_branch_membership_id_branch_id UNIQUE (clinic_id, membership_id, branch_id),
    CONSTRAINT fk_membership_branch_membership FOREIGN KEY (clinic_id, membership_id)
        REFERENCES membership (clinic_id, id),
    CONSTRAINT fk_membership_branch_branch FOREIGN KEY (clinic_id, branch_id) REFERENCES branch (clinic_id, id)
);
-- Кто имеет доступ к филиалу.
CREATE INDEX ix_membership_branch_clinic_id_branch_id ON membership_branch (clinic_id, branch_id);

ALTER TABLE membership_branch ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_branch FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON membership_branch
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());

COMMENT ON TABLE membership_branch IS 'Филиалы членства; нет строк — все филиалы клиники.';
