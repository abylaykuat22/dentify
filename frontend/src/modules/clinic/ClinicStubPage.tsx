import { Building2 } from 'lucide-react'

import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка раздела «Клиника» до его экранов (задачи T-CLN-*): пустое состояние по макету
 * Sostoyaniya, без бизнес-логики. `soon` — вкладка, экран которой ещё не сделан.
 */
export function ClinicStubPage({ soon = false }: { soon?: boolean }) {
  return <StubScreen icon={Building2} titleKey="clinic:stub.title" textKey="clinic:stub.text" soon={soon} />
}
