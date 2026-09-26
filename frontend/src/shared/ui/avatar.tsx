import { Avatar as AvatarPrimitive } from '@base-ui/react/avatar'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/shared/lib/utils'

import { PALETTE_CLASSES, type PaletteColor } from './tokens'

/**
 * Аватар «Кресла» (shadcn Avatar на Base UI): фото или инициалы (Rubik 600) в цвете палитры.
 * - `solid` — врач: инициалы белым на насыщенном цвете врача (макет Raspisanie-Den); у
 *   не-врачей — нейтральный серый. Инициалы — обычный текст: контраст ≥ 4.5:1 у всех шести
 *   цветов (src/styles/contrast.test.ts, axe в e2e).
 * - `soft` — пациент: текст цвета на мягком фоне (макет Pacienty).
 * Инициалы присылает API (R002), фронт их не вычисляет. Рядом с аватаром всегда стоит
 * имя, поэтому по умолчанию он скрыт от чтения с экрана; одиночному аватару — `label`.
 */
const avatarVariants = cva(
  'relative flex shrink-0 overflow-hidden rounded-full font-heading font-semibold select-none',
  {
    variants: {
      size: {
        sm: 'size-8 text-caption',
        default: 'size-10 text-body-sm',
        lg: 'size-16 text-avatar-lg',
      },
    },
    defaultVariants: { size: 'default' },
  },
)

interface AvatarProps extends VariantProps<typeof avatarVariants> {
  /** Инициалы из API («СА»). */
  initials: string
  src?: string
  color?: PaletteColor
  tone?: 'solid' | 'soft'
  /** Доступное имя, если рядом нет подписи с именем. */
  label?: string
  className?: string
}

function Avatar({ initials, src, color = 'neutral', tone = 'solid', size, label, className }: AvatarProps) {
  const palette = PALETTE_CLASSES[color]
  return (
    <AvatarPrimitive.Root
      data-slot="avatar"
      data-color={color}
      data-tone={tone}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={cn(avatarVariants({ size }), className)}
    >
      {src ? (
        <AvatarPrimitive.Image data-slot="avatar-image" src={src} alt="" className="size-full object-cover" />
      ) : null}
      <AvatarPrimitive.Fallback
        data-slot="avatar-fallback"
        className={cn(
          'flex size-full items-center justify-center rounded-full',
          tone === 'solid' ? [palette.strong, 'text-white'] : [palette.bg, palette.fg],
        )}
      >
        {initials}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  )
}

export { Avatar, type AvatarProps }
