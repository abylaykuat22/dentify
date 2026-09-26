import { UserRound } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { useCurrentUser } from '@/shared/api/useCurrentUser'
import { FieldTitle } from '@/shared/ui/field'
import { canSwitchLanguage, LanguageSwitch } from '@/shared/ui/language-switch'
import { StateScreen } from '@/shared/ui/StateScreen'

/**
 * Заглушка «Мой профиль»: карточка пользователя из /api/v1/me, язык интерфейса (макет Profil;
 * скрыт флагом i18n.kk, dentify R003) и пустое состояние.
 */
export function ProfilePage() {
  const { t } = useTranslation()
  const me = useCurrentUser()
  const languageLabel = useId()
  // Роли в /me пока нет (TODO в shared/api/me.ts) — тогда только клиника.
  const roleLine = me.role
    ? t('common:user.roleAtClinic', { role: me.role.name, clinic: me.clinic?.name ?? '' })
    : (me.clinic?.name ?? '')

  return (
    <div className="flex flex-col gap-4">
      <section className="flex items-center gap-4 rounded-card bg-card p-5">
        <span
          role="img"
          aria-label={t('common:user.avatar', { name: me.fullName })}
          className="flex size-14 shrink-0 items-center justify-center rounded-full bg-lamp font-heading text-h4 font-bold text-ink"
        >
          {me.initials}
        </span>
        <div className="min-w-0">
          <p className="truncate font-heading text-h3 font-semibold text-ink">{me.fullName}</p>
          {roleLine ? <p className="truncate text-body font-semibold text-graphite">{roleLine}</p> : null}
        </div>
      </section>
      {canSwitchLanguage() ? (
        <section className="flex flex-col gap-2 rounded-card bg-card p-5" data-language-row>
          <FieldTitle id={languageLabel}>{t('common:languages.label')}</FieldTitle>
          <LanguageSwitch aria-labelledby={languageLabel} labels="full" />
        </section>
      ) : null}
      <StateScreen
        kind="empty"
        icon={UserRound}
        title={t('profile:stub.title')}
        text={t('profile:stub.text')}
      />
    </div>
  )
}
