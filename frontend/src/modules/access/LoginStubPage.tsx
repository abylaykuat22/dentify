import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, useRouter } from '@tanstack/react-router'
import { ShieldCheck, TimerOff } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { fieldErrorList, useApiErrorForm } from '@/shared/api/forms'
import { useLogin } from '@/shared/api/generated/endpoints/auth/auth'
import type { LoginRequest } from '@/shared/api/generated/model'
import { ME_QUERY_KEY, toCurrentUser } from '@/shared/api/me'
import { Alert, AlertDescription, AlertTitle } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Field, FieldError, FieldLabel } from '@/shared/ui/field'
import { FormAlert } from '@/shared/ui/form-alert'
import { Input } from '@/shared/ui/input'
import { canSwitchLanguage, LanguageSwitch } from '@/shared/ui/language-switch'

const route = getRouteApi('/login')

/** Поля, которые форма показывает; галочки «Запомнить» пока нет (T-IAM-03) — её ошибка станет общей. */
const SHOWN_FIELDS = ['login', 'password'] as const

/**
 * Временный экран входа (настоящий — T-IAM-03): логин + пароль → POST /api/v1/auth/login,
 * затем возврат на returnTo. Что верно, а что нет — решает сервер (R002): форма ничего не
 * проверяет сама, ошибки полей (400 `errors[]`) встают под поля, остальное (неверный пароль,
 * лимит попыток, сбой) — плашкой над кнопкой. Ответ входа — тот же MeResponse, что у
 * /api/v1/me: кладём его в кэш, и каркас не перезапрашивает /me. Клиника не выбрана
 * (context = null) — каркас сам уведёт на выбор клиники.
 */
export function LoginStubPage() {
  const { t } = useTranslation()
  const { returnTo, reason } = route.useSearch()
  const router = useRouter()
  const queryClient = useQueryClient()
  const login = useLogin()
  const form = useForm<LoginRequest>({
    // TODO(T-IAM-03): галочка «Запомнить» на экране Vhod; по спеке LoginRequest она включена
    // по умолчанию — заглушка всегда шлёт true.
    defaultValues: { login: '', password: '', rememberMe: true },
  })
  // Тексты ошибок сервера — заново на новом языке при смене «Рус | Қаз» (PLT-11).
  const applyApiError = useApiErrorForm(form)

  // Экран входа — начало новой сессии: данные прошлой (в том числе после 401 в каркасе) из кэша
  // удаляются. Здесь, а не при уходе из каркаса: к этому моменту каркас уже размонтирован.
  useEffect(() => {
    queryClient.clear()
  }, [queryClient])

  const onSubmit = form.handleSubmit(async (data) => {
    try {
      const me = await login.mutateAsync({ data })
      queryClient.setQueryData(ME_QUERY_KEY, toCurrentUser(me))
      router.history.replace(returnTo ?? '/')
    } catch (error) {
      applyApiError(error, { fields: SHOWN_FIELDS })
    }
  })

  return (
    // Левая панель по макету Vhod — плавающая карточка (отступ 16, скругление 24 со всех сторон,
    // карточка 640 px на широком экране); уже 1280 px — пропорцией, чтобы форме хватило места.
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] xl:grid-cols-[42rem_minmax(0,1fr)]">
      <aside className="m-4 hidden flex-col justify-between rounded-card bg-lagoon px-12 pt-11 pb-9 text-white lg:flex">
        <div>
          <p className="font-heading text-logo-lg font-bold">
            {t('common:app.logo')}
            <span className="text-lamp">.</span>
          </p>
          <h2 className="mt-6 max-w-md font-heading text-h1 font-medium">{t('auth:login.tagline')}</h2>
        </div>
        <p className="flex items-center gap-2 text-body-sm font-bold text-lagoon-muted">
          <ShieldCheck className="size-4" aria-hidden="true" />
          {t('auth:login.dataResidency')}
        </p>
      </aside>

      <main className="flex flex-col p-4">
        {/* «Рус | Қаз» в углу, как на макете Vhod (28 px сверху, 32 справа) — только при флаге i18n.kk
            и словаре kk (R003). */}
        {canSwitchLanguage() ? (
          <div className="flex justify-end md:mt-3 md:mr-4">
            <LanguageSwitch surface="page" aria-label={t('common:languages.label')} />
          </div>
        ) : null}
        {/* На телефоне и планшете левой панели нет — логотип над формой, как в шапке макета Vhod. */}
        <p className="mt-4 text-center font-heading text-logo font-bold text-lagoon lg:hidden">
          {t('common:app.logo')}
          <span className="text-lamp-deep">.</span>
        </p>
        <div className="flex flex-1 items-center justify-center">
          <form
            onSubmit={(event) => void onSubmit(event)}
            noValidate
            aria-labelledby="login-title"
            className="flex w-full max-w-110 flex-col gap-4 rounded-window bg-card p-6 md:p-10"
          >
            <h1 id="login-title" className="font-heading text-h1 font-semibold text-ink">
              {t('auth:login.title')}
            </h1>
            {import.meta.env.VITE_API_MOCK === 'true' ? (
              <p className="text-body-sm text-graphite">{t('auth:login.stub')}</p>
            ) : null}

            {reason === 'expired' ? (
              <Alert variant="warning" role="status" data-slot="session-expired">
                <TimerOff aria-hidden="true" />
                <span>
                  <AlertTitle>{t('auth:login.sessionExpired.title')}</AlertTitle>{' '}
                  <AlertDescription>{t('auth:login.sessionExpired.text')}</AlertDescription>
                </span>
              </Alert>
            ) : null}

            {/* Поля — пилюли дизайн-системы (48 px, контур фокуса 3 px «лагуна»), как на макете Vhod;
                ошибка сервера — красный контур и текст под полем (shadcn Field + Controller). */}
            <Controller
              name="login"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="login-login">{t('auth:login.login')}</FieldLabel>
                  <Input
                    {...field}
                    id="login-login"
                    type="text"
                    autoComplete="username"
                    aria-invalid={fieldState.invalid}
                    aria-describedby={fieldState.invalid ? 'login-login-error' : undefined}
                  />
                  <FieldError id="login-login-error" errors={fieldErrorList(fieldState.error)} />
                </Field>
              )}
            />
            <Controller
              name="password"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="login-password">{t('auth:login.password')}</FieldLabel>
                  <Input
                    {...field}
                    id="login-password"
                    type="password"
                    autoComplete="current-password"
                    aria-invalid={fieldState.invalid}
                    aria-describedby={fieldState.invalid ? 'login-password-error' : undefined}
                  />
                  <FieldError id="login-password-error" errors={fieldErrorList(fieldState.error)} />
                </Field>
              )}
            />

            <FormAlert errors={form.formState.errors.root} />

            {/* Пока идёт запрос, кнопка недоступна, но фокус не теряет (aria-disabled вместо disabled):
                иначе он падал бы на <body>, и клавиатура начинала бы с начала страницы (WCAG 2.4.3).
                Повторную отправку, в том числе Enter в поле, гасит Base UI. После ошибки фокус —
                на первом ошибочном поле или на плашке ошибки (FormAlert). */}
            <Button type="submit" disabled={form.formState.isSubmitting} focusableWhenDisabled>
              {t('auth:login.submit')}
            </Button>

            {import.meta.env.VITE_API_MOCK === 'true' ? (
              <p className="text-caption text-graphite">{t('auth:login.mockHint')}</p>
            ) : null}
          </form>
        </div>
      </main>
    </div>
  )
}
