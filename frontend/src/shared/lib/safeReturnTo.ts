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

/** Экран входа: возврат на него после входа снова показал бы форму входа, а не кабинет. */
const LOGIN_PATH = '/login'

/**
 * Адрес возврата после входа и выбора клиники (`returnTo`): путь этого же приложения
 * (`isSameOriginPath`) и не сам вход — ссылка `/login?returnTo=%2Flogin` после входа открыла бы форму
 * входа снова. Регистр и «/» в конце роутер не различает, поэтому и здесь они не в счёт.
 */
export function isReturnPath(value: string, origin: string = window.location.origin): boolean {
  if (!isSameOriginPath(value, origin)) return false
  let path = new URL(value, origin).pathname
  try {
    path = decodeURIComponent(path)
  } catch {
    return false
  }
  return path.toLowerCase().replace(/\/+$/, '') !== LOGIN_PATH
}
