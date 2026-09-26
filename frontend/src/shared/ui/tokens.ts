/**
 * Перечни токенов «Кресла» для компонентов и витрины. Значения цветов живут только в CSS
 * (`@theme` в src/styles/index.css); здесь — имена и готовые классы Tailwind (строки целиком,
 * чтобы сканер Tailwind их увидел).
 */

/** 6 цветов врачей (design-directions): бирюза, коралл, сирень, небо, песок, роза. Цвет врачу
 *  назначает и хранит сервер (STF), фронт только отображает. */
export const DOCTOR_COLORS = ['teal', 'coral', 'lilac', 'sky', 'sand', 'rose'] as const
export type DoctorColor = (typeof DOCTOR_COLORS)[number]

/** Цвет в палитре «Кресла»: цвет врача или нейтральный серый (не-врачи, метки без цвета). */
export type PaletteColor = DoctorColor | 'neutral'

interface PaletteClasses {
  /** Фон записи, мягкий аватар, метка. */
  bg: string
  /** Текст на `bg`. */
  fg: string
  /** Насыщенный: фон аватара врача, точка. */
  strong: string
  /** Контур насыщенным (состояния зуба, выбранная карточка). */
  line: string
}

export const PALETTE_CLASSES: Record<PaletteColor, PaletteClasses> = {
  teal: {
    bg: 'bg-doctor-teal',
    fg: 'text-doctor-teal-fg',
    strong: 'bg-doctor-teal-strong',
    line: 'border-doctor-teal-strong',
  },
  coral: {
    bg: 'bg-doctor-coral',
    fg: 'text-doctor-coral-fg',
    strong: 'bg-doctor-coral-strong',
    line: 'border-doctor-coral-strong',
  },
  lilac: {
    bg: 'bg-doctor-lilac',
    fg: 'text-doctor-lilac-fg',
    strong: 'bg-doctor-lilac-strong',
    line: 'border-doctor-lilac-strong',
  },
  sky: {
    bg: 'bg-doctor-sky',
    fg: 'text-doctor-sky-fg',
    strong: 'bg-doctor-sky-strong',
    line: 'border-doctor-sky-strong',
  },
  sand: {
    bg: 'bg-doctor-sand',
    fg: 'text-doctor-sand-fg',
    strong: 'bg-doctor-sand-strong',
    line: 'border-doctor-sand-strong',
  },
  rose: {
    bg: 'bg-doctor-rose',
    fg: 'text-doctor-rose-fg',
    strong: 'bg-doctor-rose-strong',
    line: 'border-doctor-rose-strong',
  },
  neutral: {
    bg: 'bg-fog',
    fg: 'text-slate',
    strong: 'bg-graphite',
    line: 'border-graphite',
  },
}

/** Статусы записи (design-directions: точка + слово). Коды API сопоставляются с ними в модуле
 *  расписания; переходы между статусами разрешает сервер (R002). */
export const APPOINTMENT_STATUSES = ['planned', 'arrived', 'inChair', 'done', 'noShow', 'cancelled'] as const
export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number]

/** Состояния зуба на формуле: заливка цветом врача + контур насыщенным; удалён — пунктир. */
export const TOOTH_STATES = ['caries', 'filling', 'canals', 'crown', 'implant', 'removed'] as const
export type ToothState = (typeof TOOTH_STATES)[number]

export const TOOTH_STATE_CLASSES: Record<ToothState, string> = {
  caries: 'bg-tooth-caries border-tooth-caries-line',
  filling: 'bg-tooth-filling border-tooth-filling-line',
  canals: 'bg-tooth-canals border-tooth-canals-line',
  crown: 'bg-tooth-crown border-tooth-crown-line',
  implant: 'bg-tooth-implant border-tooth-implant-line',
  removed: 'border-dashed border-tooth-removed-line',
}
