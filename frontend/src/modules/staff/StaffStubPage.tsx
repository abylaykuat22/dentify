import { Stethoscope } from 'lucide-react'

import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка раздела «Врачи» до его экранов (задачи T-STF-*): пустое состояние по макету
 * Sostoyaniya, без бизнес-логики. `soon` — вкладка, экран которой ещё не сделан.
 */
export function StaffStubPage({ soon = false }: { soon?: boolean }) {
  return <StubScreen icon={Stethoscope} titleKey="staff:stub.title" textKey="staff:stub.text" soon={soon} />
}
