import { cva } from 'class-variance-authority'

/**
 * Вкладки-пилюли «Кресла» (shadcn Tabs на Base UI): пилюли в контейнере, выбранная —
 * заливка «чернила» + белый текст. Вкладки внутри экрана — `Tabs`; вкладки-маршруты раздела
 * (ссылки) — `PillTabs` каркаса на тех же `pillTabsListVariants` / `pillTabVariants`.
 *
 * `surface`: `card` — контейнер «туман» на белой карточке (макет Sistema), `page` — белый
 * контейнер на фоне «туман» (шапка раздела, макет Raspisanie-Den).
 * `size`: `default` — 40 px, 15 (шапка раздела), `compact` — 36 px, 13 (внутри карточки).
 *
 * Виды — отдельным модулем без Base UI: вкладки-ссылки каркаса на первом экране берут только их
 * (PLT-22), а `Tabs` на Base UI грузится с экранами, где он есть (tabs.tsx).
 */
export const pillTabsListVariants = cva('group/tabs-list inline-flex w-fit shrink-0 items-center gap-1 rounded-pill p-1', {
  variants: {
    surface: {
      card: 'bg-fog',
      page: 'bg-white',
    },
  },
  defaultVariants: { surface: 'card' },
})

export const pillTabVariants = cva(
  // Контур фокуса — с отступом 1 px, а не 2: полоса вкладок прокручивается (overflow), и контур 3 px
  // + 2 px выходил за внутренний отступ контейнера (4 px) и обрезался по краям.
  "relative inline-flex items-center justify-center gap-1.5 rounded-pill font-bold focus-visible:outline-offset-1 whitespace-nowrap text-graphite transition-colors select-none hover:text-ink disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50 data-active:bg-ink data-active:font-extrabold data-active:text-white aria-[current=page]:bg-ink aria-[current=page]:font-extrabold aria-[current=page]:text-white [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      size: {
        default: "h-10 px-4 text-body after:absolute after:inset-x-0 after:-inset-y-0.5 after:content-['']",
        compact: "h-9 px-3.5 text-label after:absolute after:inset-x-0 after:-inset-y-1 after:content-['']",
      },
    },
    defaultVariants: { size: 'default' },
  },
)
