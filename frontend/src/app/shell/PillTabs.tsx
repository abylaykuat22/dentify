import { Link, useRouter } from '@tanstack/react-router'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { keepSelectedInView } from '@/shared/lib/keepSelectedInView'
import { pillTabsListVariants, pillTabVariants } from '@/shared/ui/tabs-variants'

import type { SectionTab } from './sections'

/**
 * Вкладки-пилюли раздела (вкладки-маршруты): вид — общий с `Tabs` дизайн-системы
 * (`pillTabVariants`), выбранная — заливка ink + белый текст (design-directions).
 * Не влезают — прокручиваются по горизонтали без полосы прокрутки, а выбранная вкладка
 * подъезжает в видимую область (и при заходе по ссылке, и при переключении), пока пользователь
 * не прокрутил полосу сам (`keepSelectedInView`).
 */
export function PillTabs({ tabs }: { tabs: SectionTab[] }) {
  const { t } = useTranslation()
  const router = useRouter()
  // После перехода роутер восстанавливает прокрутку полосы (позицию со старого адреса) —
  // выбранную доводим уже после него, по его событию onRendered.
  const stripRef = useCallback(
    (nav: HTMLElement | null) => keepSelectedInView(nav, (select) => router.subscribe('onRendered', select)),
    [router],
  )

  if (tabs.length < 2) return null

  return (
    <nav
      ref={stripRef}
      aria-label={t('shell:tabs.label')}
      className="flex max-w-full overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    >
      <div className={pillTabsListVariants({ surface: 'page' })}>
        {tabs.map((tab) => (
          <Link
            key={tab.to}
            to={tab.to}
            activeOptions={{ exact: true, includeSearch: false }}
            className={pillTabVariants()}
          >
            {t(tab.labelKey)}
          </Link>
        ))}
      </div>
    </nav>
  )
}
