import { ShieldCheck } from 'lucide-react'

import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка раздела «Доступы» до его экранов (задачи T-IAM-*): пустое состояние по макету
 * Sostoyaniya, без бизнес-логики. `soon` — вкладка, экран которой ещё не сделан.
 */
export function AccessStubPage({ soon = false }: { soon?: boolean }) {
  return <StubScreen icon={ShieldCheck} titleKey="access:stub.title" textKey="access:stub.text" soon={soon} />
}
