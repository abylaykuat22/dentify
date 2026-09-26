import { Link } from '@tanstack/react-router'
import { ArrowLeft, Building2, KeyRound, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { TKey } from '@/shared/i18n/keys'
import { buttonVariants } from '@/shared/ui/button'

import { AuthLayout } from './AuthLayout'

const STUBS = {
  // TODO(T-IAM-17): сброс пароля по ссылке (макет Parol).
  forgot: { icon: KeyRound, titleKey: 'auth:forgot.title', textKey: 'auth:forgot.text' },
  // TODO(T-CLN-05): мастер регистрации клиники (макеты Registraciya-1, Registraciya-2).
  register: { icon: Building2, titleKey: 'auth:register.title', textKey: 'auth:register.text' },
} as const satisfies Record<string, { icon: LucideIcon; titleKey: TKey; textKey: TKey }>

/**
 * Заглушки экранов без входа, на которые ведут ссылки экрана Vhod: «Забыли пароль?» и
 * «Зарегистрировать». Та же раскладка, что у входа (карточка как у макета Parol «Проверьте почту»), и
 * одна жёлтая «Вернуться ко входу».
 */
export function AuthStubPage({ kind }: { kind: keyof typeof STUBS }) {
  const { t } = useTranslation()
  const { icon: Icon, titleKey, textKey } = STUBS[kind]
  return (
    <AuthLayout>
      <section
        aria-labelledby="auth-stub-title"
        className="flex w-full max-w-110 flex-col items-center gap-4 rounded-window bg-card p-6 text-center md:p-10"
      >
        <span className="flex size-21 items-center justify-center rounded-full bg-fog text-lagoon">
          <Icon className="size-8" strokeWidth={1.8} aria-hidden="true" />
        </span>
        <h1 id="auth-stub-title" className="font-heading text-h1 font-semibold text-ink">
          {t(titleKey)}
        </h1>
        <p className="text-body font-semibold text-graphite">
          {t('common:stub.withSoon', { text: t(textKey), soon: t('common:stub.soon') })}
        </p>
        <Link
          to="/login"
          data-slot="button"
          data-variant="default"
          className={buttonVariants({ className: 'mt-2 h-13 w-full text-body-lg' })}
        >
          <ArrowLeft aria-hidden="true" />
          {t('auth:backToLogin')}
        </Link>
      </section>
    </AuthLayout>
  )
}
