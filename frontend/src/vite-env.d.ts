/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Моки MSW вместо бэкенда: `vite --mode mock` или VITE_API_MOCK=true. */
  readonly VITE_API_MOCK: 'true' | 'false'
  /** Флаг `i18n.kk` (src/shared/lib/flags.ts): казахский интерфейс и переключатель «Рус | Қаз»; по умолчанию выключен. */
  readonly VITE_I18N_KK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
