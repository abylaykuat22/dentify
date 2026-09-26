import { useTranslation } from 'react-i18next'

import { BASE_LANGUAGE, changeUiLanguage, type UiLanguage, uiLanguages } from '@/shared/i18n'
import { cn } from '@/shared/lib/utils'

import { ToggleGroup, ToggleGroupItem } from './toggle-group'

/** Можно ли сменить язык: флаг i18n.kk включён и словарей больше одного (dentify R003). */
export function canSwitchLanguage(): boolean {
  return uiLanguages().length > 1
}

type LanguageSwitchProps = {
  /** `card` — контейнер «туман» на белой карточке («Ещё», профиль), `page` — белый на «тумане» (вход). */
  surface?: 'card' | 'page'
  /** `short` — «Рус | Қаз» (вход, «Ещё»: полное название — в подсказке), `full` — «Русский | Қазақша» (профиль). */
  labels?: 'short' | 'full'
  className?: string
} & (
  | { /** Подпись группы — id видимого текста рядом («Язык»). */ 'aria-labelledby': string; 'aria-label'?: never }
  | { /** Подписи рядом нет (вход) — имя группы. */ 'aria-label': string; 'aria-labelledby'?: never }
)

/**
 * Переключатель языка интерфейса (макеты Vhod, Profil, Telefon-Eshche): сегменты 44 px, у каждого
 * `lang`. dentify R003: **скрыт флагом `i18n.kk`** — при выключенном флаге (или без словаря второго
 * языка) не рисуется совсем. Языки и смена — `@/shared/i18n` (`uiLanguages`, `changeUiLanguage`):
 * словарь догружается, `<html lang>` и даты меняются, компоненты перерисовываются сами.
 * TODO(IAM-31): сохранять выбор в аккаунте (язык из /me), а не только на устройстве.
 */
export function LanguageSwitch({ surface = 'card', labels = 'short', className, ...props }: LanguageSwitchProps) {
  const { t, i18n } = useTranslation()
  const languages = uiLanguages()
  if (languages.length < 2) return null

  const current = languages.find((language) => language === i18n.resolvedLanguage) ?? BASE_LANGUAGE
  return (
    <ToggleGroup<UiLanguage>
      variant="segment"
      surface={surface}
      value={[current]}
      onValueChange={(next) => {
        const picked = next[0]
        if (picked !== undefined && picked !== current) void changeUiLanguage(picked)
      }}
      aria-label={props['aria-label']}
      aria-labelledby={props['aria-labelledby']}
      className={className}
      data-language-switch
    >
      {languages.map((language) => (
        <ToggleGroupItem<UiLanguage>
          key={language}
          value={language}
          lang={language}
          title={labels === 'short' ? t(`common:languages.${language}.full`) : undefined}
          // На белом контейнере (вход) выбранный — «чернила», как выбранная вкладка (design-directions).
          className={cn('h-11 text-body', surface === 'page' && 'aria-pressed:bg-ink aria-pressed:text-white aria-pressed:shadow-none')}
        >
          {t(`common:languages.${language}.${labels}`)}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}
