/**
 * Быстрые фильтры списка пациентов — как чипы макета Pacienty. Сами фильтры (и счётчики у
 * чипов) считает сервер — R002; здесь только коды для URL.
 */
export const PATIENT_FILTERS = ['all', 'today', 'new', 'children', 'allergy', 'inactive'] as const

export type PatientFilter = (typeof PATIENT_FILTERS)[number]

export const PATIENTS_SEARCH_DEFAULTS = { filter: 'all', page: 1 } as const satisfies {
  filter: PatientFilter
  page: number
}
