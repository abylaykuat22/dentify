import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, useRouter } from '@tanstack/react-router'
import { ArrowRight, Building2, LogOut, MapPin } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useSelectContext } from '@/shared/api/generated/endpoints/account/account'
import type { BranchRef, ClinicRef } from '@/shared/api/generated/model'
import { ME_QUERY_KEY, toCurrentUser } from '@/shared/api/me'
import { describeApiError } from '@/shared/api/problem'
import { useSignOut } from '@/shared/api/useSignOut'
import { Button } from '@/shared/ui/button'
import { StateScreen } from '@/shared/ui/StateScreen'

const route = getRouteApi('/select-clinic')

/** Куда можно войти: филиал клиники (или клиника целиком, если филиалов в ответе нет). */
interface Place {
  key: string
  clinic: ClinicRef
  branch: BranchRef | null
}

/**
 * Выбор клиники и филиала сессии — минимальный VyborKliniki (IAM-02, IAM-34; настоящий — T-IAM-08):
 * сюда ведут вход и кабинет, когда в /api/v1/me `context = null` (врач в нескольких клиниках, клиника
 * с несколькими филиалами). Карточка — филиал, в который пускает активное членство (`branches` из
 * /me), нажатие → POST /api/v1/me/context `{clinicId, branchId}` → обратно на `returnTo`. Куда
 * пускать, решает сервер (R002): фронт показывает то, что пришло в /me, и шлёт намерение; отказ
 * (доступ отключили только что) — текстом сервера. Здесь же «Выйти».
 * TODO(T-IAM-08): записи на сегодня, кресла, последний выбор («лампой»); готовый список мест для выбора
 * с сервера (или признак `selectable` у членства) — сейчас фронт сам оставляет активные членства и
 * раскладывает их по филиалам (минимальная версия; R002 — это правило выбора, ему место на сервере).
 */
export function SelectClinicPage() {
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
  const failure = select.isError
    ? describeApiError(select.error).message
    : signOut.failed
      ? t('auth:selectClinic.logoutFailed')
      : null

  const places: Place[] = me.memberships
    .filter((membership) => membership.status === 'ACTIVE')
    .flatMap((membership): Place[] =>
      membership.branches.length > 0
        ? membership.branches.map((branch) => ({ key: `${membership.id}:${branch.id}`, clinic: membership.clinic, branch }))
        : [{ key: membership.id, clinic: membership.clinic, branch: null }],
    )

  function choose(place: Place) {
    if (pending) return
    select.mutate({ data: { clinicId: place.clinic.id, branchId: place.branch?.id ?? null } })
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
          <h1 className="font-heading text-h1 font-semibold text-ink">
            <span className="block">{t('auth:selectClinic.greeting', { name: me.fullName })}</span>
            <span className="block text-graphite">{t('auth:selectClinic.heading')}</span>
          </h1>
        </div>

        {places.length > 0 ? (
          <ul aria-label={t('auth:selectClinic.listLabel')} className="grid w-full gap-4 md:grid-cols-2">
            {places.map((place) => (
              <li key={place.key}>
                {/* Пока идёт запрос — aria-disabled, а не disabled: нажатая карточка держит фокус
                    (WCAG 2.4.3), повторное нажатие гасит choose(). */}
                <button
                  type="button"
                  aria-disabled={pending || undefined}
                  onClick={() => choose(place)}
                  aria-label={
                    place.branch
                      ? t('auth:selectClinic.choose', {
                          branch: place.branch.name,
                          clinic: place.clinic.name,
                          city: place.branch.city,
                        })
                      : t('auth:selectClinic.chooseClinic', { clinic: place.clinic.name, city: place.clinic.city })
                  }
                  data-place={place.key}
                  className="group flex w-full flex-col gap-4 rounded-card border-2 border-transparent bg-card p-6 text-left transition-colors hover:border-line aria-disabled:opacity-60"
                >
                  <span className="flex w-full items-center justify-between">
                    <span className="flex size-14 items-center justify-center rounded-full bg-lagoon text-lamp">
                      {place.branch ? (
                        <MapPin className="size-6" aria-hidden="true" />
                      ) : (
                        <Building2 className="size-6" aria-hidden="true" />
                      )}
                    </span>
                    {/* Какой филиал выделить, фронт не решает (R002): стрелки нейтральные, «лампой»
                        загорается тот, на который навели или перешли с клавиатуры. */}
                    <span className="flex size-11 items-center justify-center rounded-full bg-fog text-ink group-hover:bg-lamp group-focus-visible:bg-lamp">
                      <ArrowRight className="size-5" aria-hidden="true" />
                    </span>
                  </span>
                  <span>
                    <span className="block font-heading text-h2 font-semibold text-ink">
                      {place.branch?.name ?? place.clinic.name}
                    </span>
                    <span className="mt-1 block text-body-sm font-semibold text-graphite">
                      {t('auth:selectClinic.place', {
                        clinic: place.clinic.name,
                        city: place.branch?.city ?? place.clinic.city,
                      })}
                    </span>
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
