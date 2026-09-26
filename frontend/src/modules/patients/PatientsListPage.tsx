import { getRouteApi, Link } from '@tanstack/react-router'
import { ChevronLeft, ChevronRight, UserPlus } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { buttonVariants } from '@/shared/ui/button'
import { chipVariants } from '@/shared/ui/chip'
import { StateScreen } from '@/shared/ui/StateScreen'

import { PATIENT_FILTERS } from './patientsSearch'

const route = getRouteApi('/_app/patients/')

// Кнопки страниц — на фоне «туман», поэтому белые с контуром (`soft` на «тумане» не видна).
const pageButton = buttonVariants({ variant: 'outline', size: 'icon-compact' })

/**
 * Заглушка списка пациентов: отражает состояние из URL (фильтр, страница) — данные и
 * фильтрация придут с сервера (dentify R001/R002), здесь только пустое состояние.
 */
export function PatientsListPage() {
  const { t } = useTranslation()
  const { filter, page } = route.useSearch()
  const atFirst = page <= 1

  return (
    <div className="flex flex-col gap-4" data-filter={filter} data-page={page}>
      <nav aria-label={t('patients:filters.label')} className="flex flex-wrap gap-2">
        {PATIENT_FILTERS.map((item) => (
          <Link
            key={item}
            to="/patients"
            search={{ filter: item, page: 1 }}
            // Выбранный фильтр — из URL. Без `exact` роутер счёл бы «Все» (параметры по умолчанию
            // из адреса убраны) частью любого адреса списка и тоже пометил бы его aria-current.
            activeOptions={{ exact: true }}
            aria-current={filter === item ? 'page' : undefined}
            data-filter-chip={item}
            className={chipVariants({ surface: 'page' })}
          >
            {t(`patients:filters.${item}`)}
          </Link>
        ))}
      </nav>

      <div className="flex items-center gap-2 text-body font-semibold text-graphite">
        <span className="mr-2" data-page-label>
          {t('patients:pages.current', { page })}
        </span>
        {/* На первой странице «назад» недоступна, но остаётся в порядке Tab (aria-disabled, без
            перехода): иначе фокус с неё, только что нажатой на второй странице, падал на <body>. */}
        <Link
          to="/patients"
          search={{ filter, page: Math.max(1, page - 1) }}
          aria-disabled={atFirst || undefined}
          onClick={(event) => {
            if (atFirst) event.preventDefault()
          }}
          aria-label={t('patients:pages.prev')}
          className={pageButton}
        >
          <ChevronLeft className="size-5" aria-hidden="true" />
        </Link>
        <Link
          to="/patients"
          search={{ filter, page: page + 1 }}
          aria-label={t('patients:pages.next')}
          className={pageButton}
        >
          <ChevronRight className="size-5" aria-hidden="true" />
        </Link>
      </div>

      <StateScreen
        kind="empty"
        icon={UserPlus}
        title={t('patients:stub.title')}
        text={t('patients:stub.text')}
      />
    </div>
  )
}
