-- iam · одноразовые токены ссылок: сброс пароля и приглашение (T-PLT-04, PLT-05).
-- Токен из ссылки — 256 бит случайности, в таблице только его SHA-256 (token_hash): утечка базы не даёт рабочих
-- ссылок. Токен гаснет после использования (used_at), по сроку (expires_at) и при смене пароля (revoked_at).
--
-- Глобальная таблица, без clinic_id и RLS: ссылку открывают до входа, когда клиники в контексте ещё нет. Клиника
-- приглашения хранится в самом приглашении (subject_id → приглашение, T-IAM), не здесь. Реестр глобальных таблиц —
-- RowLevelSecurityTests.GLOBAL_TABLES.
--
-- subject_id — на что выдан токен: аккаунт (сброс пароля) или приглашение. Внешнего ключа нет: таблицы аккаунтов и
-- приглашений появятся в T-IAM, а тип цели зависит от purpose.
CREATE TABLE one_time_token (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    purpose     text        NOT NULL,
    subject_id  uuid        NOT NULL,
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_one_time_token PRIMARY KEY (id),
    CONSTRAINT ux_one_time_token_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_one_time_token_purpose CHECK (purpose IN ('PASSWORD_RESET', 'INVITATION')),
    CONSTRAINT ck_one_time_token_hash_length CHECK (octet_length(token_hash) = 32)
);

-- Погасить действующие токены цели при смене пароля.
CREATE INDEX ix_one_time_token_subject_active ON one_time_token (subject_id, purpose)
    WHERE used_at IS NULL AND revoked_at IS NULL;

COMMENT ON TABLE one_time_token IS
    'Одноразовые токены ссылок сброса пароля и приглашения: только SHA-256 токена, срок, использование, отзыв.';
