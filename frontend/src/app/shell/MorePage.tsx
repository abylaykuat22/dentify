import { Link } from '@tanstack/react-router'
import { ChevronDown, ChevronRight, Globe, LogOut, MapPin } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { useCurrentUser } from '@/shared/api/useCurrentUser'
import { useSignOut } from '@/shared/api/useSignOut'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { canSwitchLanguage, LanguageSwitch } from '@/shared/ui/language-switch'
import { toast } from '@/shared/ui/toast'
import { PALETTE_CLASSES, type PaletteColor } from '@/shared/ui/tokens'

import { moreItems } from './mobileNav'

/** Кружок иконки пункта: цвет врача из палитры; нейтральный («Мой профиль») — «туман» и «лагуна». */
function tintClass(color: PaletteColor): string {
  if (color === 'neutral') return 'bg-fog text-lagoon'
  const palette = PALETTE_CLASSES[color]
  return cn(palette.bg, palette.fg)
}

/**
 * Меню «Ещё» по макету Telefon-Eshche: карточка пользователя, текущий филиал, разделы с
 * подписями (только разрешённые /api/v1/me), язык (скрыт флагом i18n.kk, R003) и «Выйти».
 * Все пункты — крупные карточки ≥ 72 px, цели касания ≥ 44 px.
 */
export function MorePage() {
  const { t } = useTranslation()
  const me = useCurrentUser()
  const { signOut, pending, failed } = useSignOut()
  const languageLabel = useId()
  // «Рус | Қаз» — только при включённом флаге i18n.kk и словаре kk (dentify R003); иначе строки нет.
  const languageRow = canSwitchLanguage()

  // Роли в /me пока нет (TODO в shared/api/me.ts) — тогда только клиника.
  const roleLine = me.role
    ? t('common:user.roleAtClinic', { role: me.role.name, clinic: me.clinic?.name ?? '' })
    : (me.clinic?.name ?? '')
  // Филиал не выбран (у клиники их несколько) — клиника, как в шапке.
  const place = me.branch?.name ?? me.clinic?.name ?? ''

  return (
    // На планшете и десктопе — колонкой, а не на всю ширину (там это меню открывают по адресу).
    <div className="flex flex-col pt-1.5 md:max-w-2xl">
      <header className="flex flex-col items-start gap-3.5">
        <div className="flex w-full min-w-0 items-center gap-3.5">
          <span
            aria-hidden="true"
            className="flex size-14 shrink-0 items-center justify-center rounded-full bg-lamp font-heading text-h3 font-semibold text-ink"
          >
            {me.initials}
          </span>
          <div className="flex min-w-0 flex-col gap-0.5">
            <h1 className="font-heading text-h2 font-semibold break-words text-ink">{me.fullName}</h1>
            {roleLine ? <p className="truncate text-body-sm font-semibold text-graphite">{roleLine}</p> : null}
          </div>
        </div>

        {/* TODO(T-IAM-08, T-PLT-17): список филиалов в портале и смена через API; пока — заглушка. */}
        <button
          type="button"
          aria-label={t('shell:more.branch.change', { branch: place })}
          onClick={() => toast.add({ title: t('shell:more.branch.soon') })}
          className="flex h-11 max-w-full items-center gap-2 rounded-pill bg-white pr-3.5 pl-3 text-body font-extrabold text-ink hover:bg-mist"
          data-branch-switch
        >
          <MapPin className="size-4.5 shrink-0 text-lagoon" strokeWidth={2.2} aria-hidden="true" />
          <span className="truncate">{place}</span>
          <ChevronDown className="size-4 shrink-0" strokeWidth={2.4} aria-hidden="true" />
        </button>
      </header>

      <nav aria-label={t('shell:more.sectionsLabel')} className="pt-5">
        <ul className="flex flex-col gap-2">
          {moreItems(me).map((item) => (
            <li key={item.id}>
              <Link
                to={item.to}
                data-more-item={item.id}
                className="flex min-h-18 items-center gap-3.5 rounded-card bg-white py-3 pr-4 pl-3 hover:bg-mist"
              >
                <span
                  aria-hidden="true"
                  className={cn('flex size-12 shrink-0 items-center justify-center rounded-full', tintClass(item.color))}
                >
                  <item.icon className="size-5.5" strokeWidth={2} />
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="font-heading text-h4 font-semibold text-ink">{t(item.labelKey)}</span>
                  <span className="text-label font-semibold text-graphite">{t(item.captionKey)}</span>
                </span>
                <ChevronRight className="size-4.5 shrink-0 text-graphite" strokeWidth={2.2} aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      </nav>

      {languageRow ? (
        <div className="mt-4 flex items-center gap-3.5 rounded-card bg-white p-3" data-language-row>
          <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-fog text-lagoon">
            <Globe className="size-5.5" strokeWidth={2} />
          </span>
          <span id={languageLabel} className="flex-1 font-heading text-h4 font-semibold text-ink">
            {t('shell:more.language')}
          </span>
          <LanguageSwitch aria-labelledby={languageLabel} />
        </div>
      ) : null}

      {/* Пока идёт выход, кнопка недоступна, но держит фокус (WCAG 2.4.3). */}
      <Button
        variant="danger"
        disabled={pending}
        focusableWhenDisabled
        onClick={() => void signOut()}
        className="mt-2.5 self-start has-[>svg:first-child]:pl-6"
      >
        <LogOut className="size-5" aria-hidden="true" />
        {t('shell:more.logout')}
      </Button>
      {failed ? (
        <p role="alert" className="mt-2 rounded-block bg-danger-bg px-4 py-3 text-body-sm font-semibold text-danger">
          {t('shell:more.logoutFailed')}
        </p>
      ) : null}
    </div>
  )
}
