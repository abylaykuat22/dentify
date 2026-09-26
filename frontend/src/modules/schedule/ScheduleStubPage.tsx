import { CalendarDays } from 'lucide-react'

import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка раздела «Расписание» до его экранов (задачи T-SCH-*): пустое состояние по макету
 * Sostoyaniya, без бизнес-логики. `soon` — вкладка, экран которой ещё не сделан.
 */
export function ScheduleStubPage({ soon = false }: { soon?: boolean }) {
  return <StubScreen icon={CalendarDays} titleKey="schedule:stub.title" textKey="schedule:stub.text" soon={soon} />
}
