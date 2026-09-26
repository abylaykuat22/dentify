/**
 * Поле «Телефон или e-mail» (IAM-02): как показать введённое и какую клавиатуру открыть. Только вид:
 * что это за логин, какой номер и есть ли такой аккаунт, решает сервер (R002, `LoginName` бэкенда:
 * есть «@» — e-mail, иначе телефон в любой записи). Серверу уходит то, что человек набрал (`raw`), —
 * без пробелов маски, но со своей «8», «+7» или без кода: `PhoneNumber.parse` понимает любую запись.
 *
 * Маска телефона — «+7 707 123 45 67»:
 * - первая «8» — междугородний префикс, на экране это «+7» («8 707…» ≡ «+7 707…»);
 * - «+7» можно набрать самому; «+» с другим кодом страны — без маски, как ввели;
 * - номер без кода («707 123 45 67») — «+7» стоит спереди;
 * - одиннадцатая цифра после «7» — значит, первая «7» была кодом страны («7 707 123 45 67»): номер
 *   перестраивается. Цифры человека не теряются и не добавляются — меняются только пробелы и префикс.
 *
 * Буква или «@» — e-mail (или просто текст): без маски. Если перед этим был телефон под маской
 * («87071234567@mail.ru» набирают цифрами), цифры возвращаются такими, какими их набрали.
 */

export type LoginKind = 'empty' | 'phone' | 'email'

/** Префикс маски: код Казахстана. */
const PREFIX = '+7'
/** Разметка номера после кода: 707 123 45 67. */
const GROUPS = [3, 3, 2, 2] as const
const NATIONAL_LENGTH = GROUPS.reduce((sum, size) => sum + size, 0)
const PHONE_LOOKING = /^\+?[\d\s()-]*$/

const onlyDigits = (value: string) => value.replace(/\D/g, '')

/** Что сейчас в поле: пусто, телефон (цифры и «+ ( ) -») или e-mail/текст (есть буква или «@»). */
export function loginKind(value: string): LoginKind {
  const trimmed = value.trim()
  if (!trimmed) return 'empty'
  return PHONE_LOOKING.test(trimmed) && /[\d+]/.test(trimmed) ? 'phone' : 'email'
}

/** Клавиатура на телефоне: цифровая для номера, с «@» — для e-mail и пустого поля. */
export function loginInputMode(value: string): 'tel' | 'email' {
  return loginKind(value) === 'phone' ? 'tel' : 'email'
}

/** Значение поля и его вид. */
export interface LoginState {
  /** Значение — то, что уйдёт серверу. */
  raw: string
  /** Что показано в поле. */
  display: string
  /**
   * Телефон под маской «+7»: что из набранного стоит за префиксом — «8», «+7», «7» (код страны) или
   * ничего (номер без кода). `null` — не под маской (e-mail, пусто, другой код страны).
   */
  trunk: string | null
}

export interface LoginEdit extends LoginState {
  /** Куда поставить курсор в показанном. */
  caret: number
}

function group(national: string): string {
  const parts: string[] = []
  let rest = national
  for (const size of GROUPS) {
    if (!rest) break
    parts.push(rest.slice(0, size))
    rest = rest.slice(size)
  }
  if (rest) parts.push(rest)
  return parts.join(' ')
}

function masked(trunk: string, national: string): LoginState {
  return { raw: trunk + national, display: `${PREFIX} ${group(national)}`, trunk }
}

const plain = (raw: string): LoginState => ({ raw, display: raw, trunk: null })

/** Вид значения с нуля: при открытии формы, сбросе и когда маски в поле ещё не было. */
export function loginState(value: string): LoginState {
  if (loginKind(value) !== 'phone') return plain(value)
  const trimmed = value.trim()
  const raw = (trimmed.startsWith('+') ? '+' : '') + onlyDigits(trimmed)
  if (raw.startsWith('+')) return raw.startsWith(PREFIX) ? masked(PREFIX, raw.slice(PREFIX.length)) : plain(raw)
  if (raw.startsWith('8')) return masked('8', raw.slice(1))
  // Одиннадцать цифр с «7» впереди — «7» была кодом страны, а не началом номера.
  if (raw.startsWith('7') && raw.length > NATIONAL_LENGTH) return masked('7', raw.slice(1))
  return masked('', raw)
}

/** Позиция в показанном номере после `count` цифр номера (после «+7 »). */
function caretAfterNational(display: string, count: number): number {
  const start = PREFIX.length + 1
  if (count <= 0) return Math.min(start, display.length)
  let seen = 0
  for (let index = start; index < display.length; index += 1) {
    if (/\d/.test(display.charAt(index))) seen += 1
    if (seen === count) return index + 1
  }
  return display.length
}

/** Сколько знаков значения (`+` и цифры) стоит в `text` — для курсора после разбора с нуля. */
const significant = (text: string) => (text.trimStart().startsWith('+') ? 1 : 0) + onlyDigits(text).length

function withCaretFromScratch(state: LoginState, before: number): LoginEdit {
  if (state.trunk === null) return { ...state, caret: Math.min(before, state.display.length) }
  return { ...state, caret: caretAfterNational(state.display, before - state.trunk.length) }
}

/**
 * Правка в поле: человек изменил показанное (`next`, курсор после правки — `caret`) — каким стало
 * значение и вид. По `prev` видно, что стоит за «+7» маски.
 */
export function applyLoginEdit(prev: LoginState, next: string, caret: number = next.length): LoginEdit {
  const underMask = prev.trunk !== null && next.startsWith(PREFIX)
  const kind = loginKind(next)

  if (!underMask || prev.trunk === null) {
    if (kind !== 'phone') return { ...plain(next), caret }
    return withCaretFromScratch(loginState(next), significant(next.slice(0, caret)))
  }

  const typed = next.slice(PREFIX.length)
  const typedBefore = typed.slice(0, Math.max(0, caret - PREFIX.length))

  if (kind === 'email') {
    // Под маской набрали букву или «@» — это был не телефон: цифры — такими, какими их набирали.
    const raw = prev.trunk + typed.replace(/\s/g, '')
    return { ...plain(raw), caret: prev.trunk.length + typedBefore.replace(/\s/g, '').length }
  }

  let trunk = prev.trunk
  let national = onlyDigits(typed)
  let before = onlyDigits(typedBefore).length
  if (!national && next.length < prev.display.length && !prev.raw.slice(trunk.length)) {
    // Номера за «+7» не было, стёрли и сам префикс — вместе с ним уходит то, что за ним стояло.
    const rest = loginState(trunk.slice(0, -1))
    return { ...rest, caret: rest.display.length }
  }
  // Стёрли все цифры номера без кода — поле пустое, а не «+7 » без значения.
  if (!trunk && !national) return { ...plain(''), caret: 0 }
  if (!trunk && national.length > NATIONAL_LENGTH && national.startsWith('7')) {
    // Одиннадцатая цифра: первая «7» была кодом страны.
    trunk = '7'
    national = national.slice(1)
    before = Math.max(0, before - 1)
  }
  const state = masked(trunk, national)
  return { ...state, caret: caretAfterNational(state.display, before) }
}
