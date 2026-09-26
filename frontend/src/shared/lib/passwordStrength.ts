import type { ZxcvbnFactory } from '@zxcvbn-ts/core'

/**
 * Надёжность нового пароля (IAM-05): подсказка по мере ввода, а не проверка. Жёсткие правила (8–128
 * символов, не частый пароль, не телефон и не e-mail) проверяет сервер (`PasswordPolicy`, R002);
 * фронт показывает индикатор zxcvbn-ts в 4 уровня и чек-лист рекомендаций.
 *
 * Словари zxcvbn-ts — общий, английский и русский (английский — по рекомендации zxcvbn-ts: пароль
 * латиницей из английских слов без него выглядит надёжнее, чем есть) — мегабайты: грузятся лениво,
 * при первом вводе в поле нового пароля, отдельными чанками; в начальную загрузку и на экран входа
 * не попадают.
 */

/** Уровень индикатора: 0 — не оценён (пусто или словари ещё грузятся), 1–4 — слабый … надёжный. */
export type StrengthLevel = 0 | 1 | 2 | 3 | 4

let scorer: Promise<ZxcvbnFactory> | null = null

/** Оценщик zxcvbn-ts со словарями: один на приложение, словари грузятся один раз. */
export function loadPasswordScorer(): Promise<ZxcvbnFactory> {
  scorer ??= Promise.all([
    import('@zxcvbn-ts/core'),
    import('@zxcvbn-ts/language-common'),
    import('@zxcvbn-ts/language-en'),
    import('@zxcvbn-ts/language-ru'),
  ]).then(
    ([core, common, en, ru]) =>
      new core.ZxcvbnFactory({
        translations: ru.translations,
        graphs: common.adjacencyGraphs,
        // Ключи словарей с суффиксом языка (`wikipedia-en`, `wikipedia-ru`) — друг друга не затирают.
        dictionary: { ...common.dictionary, ...en.dictionary, ...ru.dictionary },
      }),
  )
  // Не загрузилось (сеть) — следующая попытка загрузит заново.
  scorer.catch(() => {
    scorer = null
  })
  return scorer
}

/**
 * Оценка zxcvbn (0–4) → уровень индикатора (1–4): 0 и 1 — «слабый». `userInputs` — телефон, e-mail,
 * имя: пароль из них zxcvbn считает слабым.
 */
export async function passwordLevel(password: string, userInputs: readonly string[] = []): Promise<StrengthLevel> {
  if (!password) return 0
  const zxcvbn = await loadPasswordScorer()
  const { score } = zxcvbn.check(password, [...userInputs])
  return score <= 1 ? 1 : score
}

/** Чек-лист под полем (IAM-05): «8+ символов», «есть цифра», «есть заглавная» — рекомендации. */
export const PASSWORD_RULES = ['length', 'digit', 'upper'] as const
export type PasswordRule = (typeof PASSWORD_RULES)[number]

/** Минимальная длина — как у сервера (`PasswordPolicy`: 8–128 символов Unicode). */
const MIN_LENGTH = 8

export function passwordRules(password: string): Record<PasswordRule, boolean> {
  return {
    length: [...password].length >= MIN_LENGTH,
    digit: /\p{Nd}/u.test(password),
    upper: /\p{Lu}/u.test(password),
  }
}

/**
 * «Повторите пароль» не совпал с паролем (IAM-05: проверка до отправки, повтор серверу не уходит):
 * форма с полем `PasswordField mode="repeat"` не отправляется, пока это `true`. Пустой повтор — тоже
 * несовпадение (если пароль не пуст).
 */
export function passwordRepeatMismatch(password: string, repeat: string): boolean {
  return password !== repeat
}
