import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useRouter } from '@tanstack/react-router'
import { TimerOff } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { fieldErrorList, useApiErrorForm } from '@/shared/api/forms'
import { useLogin } from '@/shared/api/generated/endpoints/auth/auth'
import type { LoginRequest } from '@/shared/api/generated/model'
import { ME_QUERY_KEY, toCurrentUser } from '@/shared/api/me'
import { cn } from '@/shared/lib/utils'
import { Alert, AlertDescription, AlertTitle } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Checkbox } from '@/shared/ui/checkbox'
import { Field, FieldError, FieldLabel } from '@/shared/ui/field'
import { FormAlert } from '@/shared/ui/form-alert'
import { LoginField } from '@/shared/ui/login-field'
import { PasswordField } from '@/shared/ui/password-field'

import { AuthLayout } from './AuthLayout'

const route = getRouteApi('/login')

/** Поля формы: ошибка сервера по ним встаёт под поле; «Запомнить» — тоже поле (ошибка — под галочкой). */
const SHOWN_FIELDS = ['login', 'password', 'rememberMe'] as const

/** Ссылка экрана входа (макет Vhod): «лагуна», подчёркнута, цель касания 44 px. */
const linkClass =
  'inline-flex min-h-11 items-center px-2 font-bold text-lagoon underline decoration-2 underline-offset-3 hover:text-lagoon-deep'

/**
 * Экран входа по макету Vhod (IAM-02, IAM-03, IAM-34). Логин — телефон или e-mail (`LoginField`),
 * пароль с «глазом» (`PasswordField`), «Запомнить это устройство» (по умолчанию включено), одна
 * жёлтая «Войти», «Забыли пароль?» и «Зарегистрировать».
 *
 * Что верно, решает сервер (R002): форма ничего не проверяет сама и шлёт как есть. POST
 * /api/v1/auth/login с CSRF-токеном (мутатор сам берёт cookie или запрашивает GET /auth/csrf); ответ —
 * тот же MeResponse, что у /api/v1/me: он кладётся в кэш, и кабинет не перезапрашивает /me. Клиника
 * выбрана (одна клиника с одним филиалом) — сразу на `returnTo` или в первый раздел; нет — выбор
 * клиники. Ошибки: полей (400 `errors[]`) — под полями; неверный логин или пароль (401), доступ
 * отключён (403), лимит попыток (429, «через 15 минут» из Retry-After), сбой, нет сети — плашкой над
 * кнопкой; тексты — по коду ProblemDetail из словаря (`errors:*`).
 */
export function LoginPage() {
  const { t } = useTranslation()
  const { returnTo, reason } = route.useSearch()
  const router = useRouter()
  const queryClient = useQueryClient()
  const login = useLogin()
  const form = useForm<LoginRequest>({ defaultValues: { login: '', password: '', rememberMe: true } })
  // Тексты ошибок сервера — заново на новом языке при смене «Рус | Қаз» (PLT-11).
  const applyApiError = useApiErrorForm(form)

  // Экран входа — начало новой сессии: данные прошлой (выход, 401 посреди работы) из кэша удаляются.
  // Здесь, а не при уходе из кабинета: к этому моменту кабинет уже размонтирован.
  useEffect(() => {
    queryClient.clear()
  }, [queryClient])

  const onSubmit = form.handleSubmit(async (data) => {
    try {
      const me = toCurrentUser(await login.mutateAsync({ data }))
      queryClient.setQueryData(ME_QUERY_KEY, me)
      if (me.clinic) {
        router.history.replace(returnTo ?? '/')
      } else {
        // Несколько клиник или филиалов — сначала выбор, потом туда же, куда шли.
        await router.navigate({ to: '/select-clinic', search: { returnTo }, replace: true })
      }
    } catch (error) {
      applyApiError(error, { fields: SHOWN_FIELDS })
    }
  })

  return (
    <AuthLayout>
      <section
        aria-labelledby="login-title"
        className="flex w-full max-w-110 flex-col gap-6 rounded-window bg-card p-6 md:p-10"
      >
        <h1 id="login-title" className="font-heading text-h1 font-semibold text-ink">
          {t('auth:login.title')}
        </h1>

        <form onSubmit={(event) => void onSubmit(event)} noValidate className="flex flex-col gap-4">
          {reason === 'expired' ? (
            <Alert variant="warning" role="status" data-slot="session-expired">
              <TimerOff aria-hidden="true" />
              <span>
                <AlertTitle>{t('auth:login.sessionExpired.title')}</AlertTitle>{' '}
                <AlertDescription>{t('auth:login.sessionExpired.text')}</AlertDescription>
              </span>
            </Alert>
          ) : null}

          <Controller
            name="login"
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <FieldLabel htmlFor="login-login">{t('auth:login.login')}</FieldLabel>
                <LoginField
                  {...field}
                  id="login-login"
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
                <PasswordField
                  {...field}
                  id="login-password"
                  mode="current"
                  aria-invalid={fieldState.invalid}
                  aria-describedby={fieldState.invalid ? 'login-password-error' : undefined}
                />
                <FieldError id="login-password-error" errors={fieldErrorList(fieldState.error)} />
              </Field>
            )}
          />
          <Controller
            name="rememberMe"
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <label className="flex min-h-11 w-fit cursor-pointer items-center gap-3 text-body font-bold text-ink">
                  <Checkbox
                    ref={field.ref}
                    name={field.name}
                    checked={field.value}
                    onCheckedChange={(checked) => field.onChange(checked)}
                    onBlur={field.onBlur}
                    aria-invalid={fieldState.invalid}
                    aria-describedby={fieldState.invalid ? 'login-remember-error' : undefined}
                  />
                  {t('auth:login.remember')}
                </label>
                <FieldError id="login-remember-error" errors={fieldErrorList(fieldState.error)} />
              </Field>
            )}
          />

          <FormAlert errors={form.formState.errors.root} />

          {/* Пока идёт запрос, кнопка недоступна, но фокус не теряет (aria-disabled вместо disabled):
              иначе он падал бы на <body> (WCAG 2.4.3). Повторную отправку, в том числе Enter в поле,
              гасит Base UI. После ошибки фокус — на первом ошибочном поле или на плашке ошибки. */}
          <Button
            type="submit"
            disabled={form.formState.isSubmitting}
            focusableWhenDisabled
            className="h-13 text-body-lg"
          >
            {t('auth:login.submit')}
          </Button>
        </form>

        {/* TODO(T-IAM-17): экраны сброса пароля; пока — заглушка. */}
        <Link to="/forgot-password" className={cn(linkClass, '-mt-2.5 self-center text-body-sm')}>
          {t('auth:login.forgot')}
        </Link>
      </section>

      <p className="mt-3 flex flex-wrap items-center justify-center gap-x-1 text-center text-body-sm font-semibold text-graphite">
        {t('auth:login.noClinic', { app: t('common:app.name') })}
        {/* TODO(T-CLN-05): мастер регистрации клиники; пока — заглушка. */}
        <Link to="/register" className={cn(linkClass, 'px-1 font-extrabold')}>
          {t('auth:login.register')}
        </Link>
      </p>

      {import.meta.env.VITE_API_MOCK === 'true' ? (
        <p data-mock-hint className="mt-4 max-w-110 text-center text-caption text-graphite">
          {t('auth:login.mockHint')}
        </p>
      ) : null}
    </AuthLayout>
  )
}
