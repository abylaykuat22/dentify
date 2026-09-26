import type { ParseKeys } from 'i18next'

import type { Namespace } from './resources'

/**
 * Ключ словаря в конфигах (разделы, вкладки, заголовки экранов): всегда полный —
 * `пространство:путь` (`shell:nav.schedule`), чтобы его можно было отдать любому `t`.
 */
export type TKey = Extract<ParseKeys, `${Namespace}:${string}`>
