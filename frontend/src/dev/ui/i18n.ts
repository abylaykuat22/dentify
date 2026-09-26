import { i18n } from '@/shared/i18n'

import devUi from './ru.json'

/** Подключить словарь витрины (пространство `devUi`). Вызывается из самой витрины: модуль
 *  без побочных эффектов верхнего уровня, чтобы production-сборка вырезала его целиком. */
export function ensureDevUiI18n(): void {
  if (!i18n.hasResourceBundle('ru', 'devUi')) i18n.addResourceBundle('ru', 'devUi', devUi)
}
