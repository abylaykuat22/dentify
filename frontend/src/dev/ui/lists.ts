/**
 * Списки витрины — ключи словаря `devUi` по смыслу (`overlays.chairs.chair1`), а не массивы в JSON:
 * каждая строка — свой ключ и проходит ICU, как любая другая, а проверка словаря видит её через
 * шаблон `overlays.chairs.${id}`.
 */
export const DEMO_CHAIRS = ['chair1', 'chair2', 'chair3', 'chair4'] as const

export const DEMO_SPECIALTIES = [
  'therapy',
  'therapyKids',
  'aesthetic',
  'surgery',
  'orthodontics',
  'orthopedics',
  'hygiene',
  'periodontics',
  'implantology',
] as const
