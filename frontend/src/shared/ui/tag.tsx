import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

import { Badge } from './badge'
import { PALETTE_CLASSES, type PaletteColor } from './tokens'

/**
 * Метка в цвете палитры «Кресла» (метки пациентов: «Аллергия», «Имплант»; макет Pacienty).
 * Цвет метки задаёт справочник на сервере; компонент только рисует.
 */
function Tag({
  color = 'neutral',
  className,
  ...props
}: Omit<React.ComponentProps<typeof Badge>, 'variant' | 'color'> & { color?: PaletteColor }) {
  const palette = PALETTE_CLASSES[color]
  return <Badge size="compact" data-color={color} className={cn(palette.bg, palette.fg, className)} {...props} />
}

export { Tag }
