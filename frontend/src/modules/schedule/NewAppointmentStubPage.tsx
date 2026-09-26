import { Link } from '@tanstack/react-router'
import { ArrowLeft, CalendarPlus } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { buttonVariants } from '@/shared/ui/button'
import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка мастера новой записи (T-SCH-16): пустое состояние и выход назад к расписанию — без него
 * уйти можно было только через меню. Настоящий мастер — окно или панель поверх сетки с «Отменой» на
 * первом шаге и «← Назад» дальше (R023, макеты Zapis-1 / Zapis).
 */
export function NewAppointmentStubPage() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-start gap-4">
      <Link to="/schedule" className={buttonVariants({ variant: 'ghost' })} data-back-to-schedule>
        <ArrowLeft aria-hidden="true" />
        {t('schedule:newStub.back')}
      </Link>
      <StubScreen
        icon={CalendarPlus}
        titleKey="schedule:newStub.title"
        textKey="schedule:newStub.text"
        soon
        className="self-stretch"
      />
    </div>
  )
}
