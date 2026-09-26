import { i18n } from '@/shared/i18n'
import type { TKey } from '@/shared/i18n/keys'

// Заголовки вкладки браузера собираются шаблонами словаря (common:titles.page, common:titles.tab),
// а не склейкой в коде: порядок и разделители — дело перевода (dentify R003).

/** «<Экран> — Dentify» по ключу словаря. */
export function pageTitle(key: TKey, options?: Record<string, unknown>): string {
  return pageTitleOf(i18n.t(key, options))
}

/** «<Экран> — Dentify» для уже готовой строки (имя пациента и т. п.). */
export function pageTitleOf(screen: string): string {
  return i18n.t('common:titles.page', { screen, app: i18n.t('common:app.name') })
}

/** «<Вкладка> · <Раздел> — Dentify». */
export function tabTitle(tabKey: TKey, sectionKey: TKey): string {
  return i18n.t('common:titles.tab', { tab: i18n.t(tabKey), section: i18n.t(sectionKey), app: i18n.t('common:app.name') })
}
