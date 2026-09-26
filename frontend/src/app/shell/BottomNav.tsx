import { Link, useLinkProps, useLocation } from '@tanstack/react-router'
import { Ellipsis, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { type CurrentUser, hasPermission } from '@/shared/api/me'

import { BOTTOM_NAV, isMoreArea, MORE_PATH, NEW_APPOINTMENT_PATH } from './mobileNav'
import { type SectionId, sectionById, visibleSections } from './sections'

// Пункт панели: вся пятая часть ширины и 56 px в высоту — цель касания с запасом (≥ 44 px).
// Панель уже макета (366 px — это 390 − 2 × 12; телефоны 360–389) — подпись чуть плотнее, чтобы
// «Расписание» уместилось в свою ячейку и не слипалось с соседней.
const itemClass =
  'flex h-14 min-w-0 flex-col items-center justify-center gap-1 rounded-item text-nav font-bold text-lagoon-muted @max-[22.875rem]:tracking-tight data-[status=active]:font-extrabold data-[status=active]:text-lamp'

/**
 * Нижняя панель телефона (< 768 px) по макету Telefon: «Расписание · Пациенты · + · Склад ·
 * Ещё» на плавающей «лагуне». Пункты — только разделы, на которые есть право (/api/v1/me);
 * место скрытого пункта остаётся пустым, чтобы «+» и «Ещё» не прыгали между ролями. «+» —
 * навигация в мастер новой записи (жёлтая, но не кнопка области; «текущей страницей» не
 * бывает — на /schedule/new текущий пункт один, «Расписание»). «Ещё» подсвечен и на
 * разделах из меню «Ещё». Над полоской «домой» iOS панель приподнята на safe-area; пока
 * открыта экранная клавиатура — спрятана (`typing:`).
 */
export function BottomNav({ user }: { user: CurrentUser }) {
  const { t } = useTranslation()
  const pathname = useLocation({ select: (location) => location.pathname })
  const visible = new Set(visibleSections(user).map((section) => section.id))
  // TODO(allowedActions): «+» — по праву создавать запись из API, а не по праву видеть расписание.
  const canCreate = hasPermission(user, 'schedule.view')
  const moreActive = isMoreArea(pathname)

  const sectionItem = (id: SectionId) => {
    if (!visible.has(id)) return <span key={id} aria-hidden="true" />
    const section = sectionById(id)
    return (
      <Link key={id} to={section.to} activeOptions={{ includeSearch: false }} className={itemClass} data-bottom-item={id}>
        <section.icon className="size-5.5" strokeWidth={1.9} aria-hidden="true" />
        {t(section.labelKey)}
      </Link>
    )
  }

  return (
    <nav
      aria-label={t('shell:nav.mobileLabel')}
      data-bottom-nav
      className="surface-dark @container fixed right-[max(0.75rem,env(safe-area-inset-right))] bottom-[max(0.75rem,env(safe-area-inset-bottom))] left-[max(0.75rem,env(safe-area-inset-left))] z-20 h-17.5 rounded-card bg-lagoon shadow-dock typing:hidden md:hidden"
    >
      {/* Сетка — внутри панели: её ширину меряет контейнерный запрос (@max-…). Уже макета —
          «+» занимает ровно свой круг, а остальное делят подписи; отступы по краям тоньше. */}
      <div className="grid h-full grid-cols-5 items-center px-1.5 @max-[22.875rem]:grid-cols-[repeat(2,minmax(0,1fr))_auto_repeat(2,minmax(0,1fr))] @max-[22.875rem]:px-0.5">
        {BOTTOM_NAV.start.map(sectionItem)}
        {/* Место «+» держится и без права на запись: «Ещё» и «Склад» не съезжают. */}
        <span className="flex min-w-15 justify-center">
          {canCreate ? <NewAppointmentLink label={t('shell:actions.newAppointment')} /> : null}
        </span>
        {BOTTOM_NAV.end.map(sectionItem)}
        <Link
          to={MORE_PATH}
          // Разделы из меню «Ещё» (Врачи, Клиника, Доступы, профиль) тоже подсвечивают «Ещё».
          data-status={moreActive ? 'active' : undefined}
          aria-current={moreActive ? 'page' : undefined}
          className={itemClass}
          data-bottom-item="more"
        >
          <Ellipsis className="size-5.5" strokeWidth={2.4} aria-hidden="true" />
          {t('shell:nav.more')}
        </Link>
      </div>
    </nav>
  )
}

/**
 * «+» — переход в мастер новой записи. Это действие, а не пункт с состоянием: `aria-current`
 * ему не ставится (Link ставит его сам на точном совпадении, поэтому здесь `useLinkProps`), иначе
 * на /schedule/new диктор объявил бы две текущие страницы — «+» и «Расписание».
 */
function NewAppointmentLink({ label }: { label: string }) {
  const linkProps = useLinkProps({ to: NEW_APPOINTMENT_PATH })
  return (
    <a
      {...linkProps}
      aria-current={undefined}
      aria-label={label}
      data-bottom-item="create"
      className="flex size-15 -translate-y-4 items-center justify-center rounded-full border-5 border-fog bg-lamp text-ink shadow-lamp-glow"
    >
      <Plus className="size-6" strokeWidth={3} aria-hidden="true" />
    </a>
  )
}
