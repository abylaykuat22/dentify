import { Package } from 'lucide-react'

import { StubScreen } from '@/shared/ui/StubScreen'

/**
 * Заглушка раздела «Склад» до его экранов (задачи T-INV-*): пустое состояние по макету
 * Sostoyaniya, без бизнес-логики. `soon` — вкладка, экран которой ещё не сделан.
 */
export function InventoryStubPage({ soon = false }: { soon?: boolean }) {
  return <StubScreen icon={Package} titleKey="inventory:stub.title" textKey="inventory:stub.text" soon={soon} />
}
