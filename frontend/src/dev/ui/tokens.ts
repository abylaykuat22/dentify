import type { DoctorColor } from '@/shared/ui/tokens'

/** Значение цвета-токена из CSS (`--color-<name>` на :root, `@theme static`), «#0F3D4C». */
export function readColorToken(name: string): string {
  if (typeof window === 'undefined') return ''
  return getComputedStyle(document.documentElement).getPropertyValue(`--color-${name}`).trim().toUpperCase()
}

/** Фон записи врача этого цвета (`--color-doctor-<цвет>`). Имя токена собирается здесь, не в JSX. */
export function readDoctorColorToken(color: DoctorColor): string {
  return readColorToken(`doctor-${color}`)
}
