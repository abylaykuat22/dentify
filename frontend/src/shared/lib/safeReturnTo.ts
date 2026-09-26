// eslint-disable-next-line no-control-regex -- управляющие символы и есть то, что отсекаем
const FORBIDDEN_CHARS = /[\\\u0000-\u001f\u007f]/

/**
 * Адрес возврата после входа безопасен: абсолютный путь этого же приложения. Отсекаем
 * `//evil.example`, `/\evil.example` (URL-парсер браузера читает «\» как «/» и уводит на
 * чужой хост), схемы (`https:`, `javascript:`) и управляющие символы; итог сверяем с
 * origin страницы — это и есть критерий, остальное — пояс к подтяжкам.
 */
export function isSameOriginPath(value: string, origin: string = window.location.origin): boolean {
  if (!value.startsWith('/') || value.startsWith('//')) return false
  if (FORBIDDEN_CHARS.test(value)) return false
  try {
    return new URL(value, origin).origin === origin
  } catch {
    return false
  }
}
