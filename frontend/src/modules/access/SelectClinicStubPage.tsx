import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, useRouter } from '@tanstack/react-router'
import { ArrowRight, Building2, LogOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useSelectContext } from '@/shared/api/generated/endpoints/account/account'
import { ME_QUERY_KEY, toCurrentUser } from '@/shared/api/me'
import { describeApiError } from '@/shared/api/problem'
import { useSignOut } from '@/shared/api/useSignOut'
import { Button } from '@/shared/ui/button'
import { StateScreen } from '@/shared/ui/StateScreen'

const route = getRouteApi('/select-clinic')

/**
 * Временный выбор клиники (макет VyborKliniki; настоящий — T-IAM-08): карточки активных членств
 * из /api/v1/me → POST /api/v1/me/context → обратно на returnTo. Какие клиники доступны и какой
 * филиал выбрать — решает сервер (R002): фронт показывает членства со статусом ACTIVE и шлёт
 * намерение, филиал не передаёт (единственный сервер выберет сам).
 */
export function SelectClinicStubPage() {
  const { t } = useTranslation()
  const { me } = route.useRouteContext()
  const { returnTo } = route.useSearch()
  const router = useRouter()
  const queryClient = useQueryClient()
  const select = useSelectContext({
    mutation: {
      onSuccess: (next) => {
        queryClient.setQueryData(ME_QUERY_KEY, toCurrentUser(next))
        router.history.replace(returnTo ?? '/')
      },
    },
  })
  const signOut = useSignOut()
  const pending = select.isPending || signOut.pending
  // Почему не вышло — словами сервера (доступ к клинике отключён, нет связи), а не общим «не удалось».
  const failure = select.isError ? describeApiError(select.error).message : signOut.failed ? t('auth:selectClinic.logoutFailed') : null

  const clinics = me.memberships.filter((membership) => membership.status === 'ACTIVE')

  function choose(clinicId: string) {
    if (pending) return
    select.mutate({ data: { clinicId, branchId: null } })
  }

  return (
    <div className="flex min-h-dvh flex-col px-4 py-6 md:px-10 md:py-8">
      <p className="font-heading text-logo font-bold text-lagoon">
        {t('common:app.logo')}
        <span className="text-lamp-deep">.</span>
      </p>

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-center justify-center gap-8 py-10">
        <div className="flex flex-col items-center gap-4 text-center">
          <span
            role="img"
            aria-label={t('common:user.avatar', { name: me.fullName })}
            className="flex size-16 items-center justify-center rounded-full bg-lamp font-heading text-h3 font-bold text-ink"
          >
            {me.initials}
          </span>
          <h1 className="font-heading text-h1 font-semibold text-ink">{t('auth:selectClinic.heading')}</h1>
          <p className="text-body-sm text-graphite">{t('auth:selectClinic.stub')}</p>
        </div>

        {clinics.length > 0 ? (
          <ul aria-label={t('auth:selectClinic.listLabel')} className="grid w-full gap-4 md:grid-cols-2">
            {clinics.map((membership) => (
              <li key={membership.id}>
                {/* Пока идёт запрос — aria-disabled, а не disabled: нажатая карточка держит фокус
                    (WCAG 2.4.3), повторное нажатие гасит choose(). */}
                <button
                  type="button"
                  aria-disabled={pending || undefined}
                  onClick={() => choose(membership.clinic.id)}
                  aria-label={t('auth:selectClinic.choose', {
                    clinic: membership.clinic.name,
                    city: membership.clinic.city,
                  })}
                  className="group flex w-full flex-col gap-4 rounded-card border-2 border-transparent bg-card p-6 text-left transition-colors hover:border-line aria-disabled:opacity-60"
                >
                  <span className="flex w-full items-center justify-between">
                    <span className="flex size-14 items-center justify-center rounded-full bg-lagoon text-lamp">
                      <Building2 className="size-6" aria-hidden="true" />
                    </span>
                    {/* Какую клинику выделить, фронт не решает (R002): стрелки нейтральные, «лампой»
                        загорается та, на которую навели или перешли с клавиатуры. Когда API отдаст
                        последнюю клинику (T-IAM-08), выделять её. */}
                    <span className="flex size-11 items-center justify-center rounded-full bg-fog text-ink group-hover:bg-lamp group-focus-visible:bg-lamp">
                      <ArrowRight className="size-5" aria-hidden="true" />
                    </span>
                  </span>
                  <span>
                    <span className="block font-heading text-h2 font-semibold text-ink">{membership.clinic.name}</span>
                    <span className="mt-1 block text-body-sm font-semibold text-graphite">{membership.clinic.city}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <StateScreen
            kind="empty"
            icon={Building2}
            title={t('auth:selectClinic.empty.title')}
            text={t('auth:selectClinic.empty.text')}
            className="w-full"
          />
        )}

        {failure ? (
          <p role="alert" className="rounded-block bg-danger-bg px-4 py-3 text-body-sm font-semibold text-danger">
            {failure}
          </p>
        ) : null}
      </main>

      <Button
        variant="ghost"
        disabled={pending}
        focusableWhenDisabled
        onClick={() => void signOut.signOut()}
        className="self-center text-slate"
      >
        <LogOut aria-hidden="true" />
        {t('auth:selectClinic.logout')}
      </Button>
    </div>
  )
}
