import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Двуязычное значение справочника (PLT-11): `LocalizedText { ru, kk }` бэкенда — `ru` обязателен,
 * `kk` может быть `null`. TODO: заменить сгенерированным типом, когда схема появится в
 * `api/openapi.json` (`pnpm api:generate`).
 */
export interface LocalizedText {
  ru: string
  kk?: string | null
}

/** Название на языке интерфейса; перевода нет (пусто или `null`) — русское. */
export function pickLocalized(text: LocalizedText, language: string): string {
  const value = language === 'kk' ? text.kk : null
  return value?.trim() ? value : text.ru
}

/** `pickLocalized` для текущего языка интерфейса: компонент перерисуется при смене языка. */
export function useLocalized(): (text: LocalizedText) => string {
  const { i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  return useCallback((text: LocalizedText) => pickLocalized(text, language), [language])
}
