import { createFileRoute } from '@tanstack/react-router'

import { NewAppointmentStubPage } from '@/modules/schedule/NewAppointmentStubPage'
import { pageTitle } from '@/shared/lib/title'

/**
 * Новая запись: сюда ведут «+» нижней панели телефона и жёлтая кнопка шапки расписания.
 * Пока заглушка; мастер записи (пациент → врач и время → услуги) и параметры предзаполнения
 * `?patientId&doctorId&start&chairId` — T-SCH-16, вид телефона — T-SCH-26.
 */
export const Route = createFileRoute('/_app/schedule/new')({
  staticData: { titleKey: 'shell:actions.newAppointment', bare: true },
  head: () => ({ meta: [{ title: pageTitle('shell:actions.newAppointment') }] }),
  component: NewAppointmentStubPage,
})
