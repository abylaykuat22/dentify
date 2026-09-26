import { Checkbox as CheckboxPrimitive } from '@base-ui/react/checkbox'
import { CheckIcon } from 'lucide-react'

import { cn } from '@/shared/lib/utils'

/**
 * Галочка «Кресла» (shadcn Checkbox на Base UI; макет Vhod — «Запомнить это устройство»):
 * квадрат 24 px со скруглением 8, пустой — белый с контуром 2 px `line`, отмеченный — «чернила» с
 * галочкой «лампой». Фокус — общий контур 3 px «лагуна». Подпись — обёрткой `<label>` вокруг
 * галочки и текста: вся строка — цель касания (≥ 44 px задаёт экран, `min-h-11` у подписи).
 */
function Checkbox({ className, ...props }: CheckboxPrimitive.Root.Props) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(
        'peer relative flex size-6 shrink-0 items-center justify-center rounded-check border-2 border-line bg-white text-lamp transition-colors disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-danger data-checked:border-ink data-checked:bg-ink',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator data-slot="checkbox-indicator" className="grid place-content-center text-current">
        <CheckIcon className="size-4" strokeWidth={3.2} aria-hidden="true" />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

export { Checkbox }
