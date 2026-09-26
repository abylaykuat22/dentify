import { ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { canSwitchLanguage, LanguageSwitch } from '@/shared/ui/language-switch'

import { AuthIllustration } from './AuthIllustration'

/**
 * Раскладка экранов без входа — вход, сброс пароля, приглашение, регистрация (макеты Vhod, Parol,
 * Priglashenie): слева с 1024 px — панель «О сервисе» (плавающая карточка «лагуна»: логотип, слоган,
 * картинка расписания, «Данные хранятся в Казахстане»), справа — форма по центру и «Рус | Қаз» в
 * углу (только при флаге i18n.kk и словаре kk, dentify R003). На телефоне и планшете (IAM-34) —
 * одна колонка без панели, логотип над формой.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  return (
    // Панель по макету — 640 px и отступ 16 со всех сторон (колонка 672 px); уже 1280 px — пропорцией,
    // чтобы форме хватило места. Высота панели — окно: длинная форма прокручивается, панель стоит; в
    // низком окне картинка расписания обрезается снизу, а не растягивает страницу.
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] xl:grid-cols-[42rem_minmax(0,1fr)]">
      <aside
        aria-label={t('auth:about.label')}
        className="sticky top-4 m-4 hidden h-[calc(100dvh-2rem)] flex-col gap-5.5 self-start overflow-hidden rounded-card bg-lagoon px-12 pt-11 pb-9 text-white lg:flex"
      >
        <p className="font-heading text-logo-lg font-bold">
          {t('common:app.logo')}
          <span className="text-lamp">.</span>
        </p>
        <p className="max-w-125 font-heading text-tagline font-medium">{t('auth:about.tagline')}</p>
        <div className="grow" />
        <AuthIllustration />
        <div className="grow" />
        <p className="flex items-center gap-2 text-body-sm font-bold text-lagoon-muted">
          <ShieldCheck className="size-4.5 shrink-0" aria-hidden="true" />
          {t('auth:about.dataResidency')}
        </p>
      </aside>

      <main className="relative flex min-w-0 flex-col items-center px-4 pt-6 pb-10 md:px-8 lg:justify-center lg:py-6">
        {/* «Рус | Қаз» в углу, как на макете Vhod (28 px сверху, 32 справа). */}
        {canSwitchLanguage() ? (
          <div className="flex w-full justify-end lg:absolute lg:top-7 lg:right-8 lg:w-auto">
            <LanguageSwitch surface="page" aria-label={t('common:languages.label')} />
          </div>
        ) : null}
        {/* Без панели «О сервисе» — логотип над формой. */}
        <p className="mt-2 mb-6 font-heading text-logo font-bold text-lagoon lg:hidden">
          {t('common:app.logo')}
          <span className="text-lamp-deep">.</span>
        </p>
        <div className="flex w-full flex-1 flex-col items-center justify-center lg:flex-none">{children}</div>
      </main>
    </div>
  )
}
