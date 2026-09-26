/**
 * Выбранный пункт полосы: вкладка-маршрут (`aria-current="page"`, ссылка TanStack Router) или
 * вкладка экрана (`aria-selected="true"`, Base UI Tabs).
 */
const SELECTED = '[aria-current="page"], [aria-selected="true"]'

/** Запас у края полосы — внутренний отступ контейнера пилюль (p-1): видно его скругление. */
const EDGE_GAP = 4

/** Ввод, которым пользователь сам берётся за полосу: палец, мышь, колесо, клавиши. */
const USER_INPUT = ['pointerdown', 'touchstart', 'wheel', 'keydown'] as const

/**
 * Показать выбранный пункт целиком, прокручивая только по горизонтали внутри полосы (страница по
 * вертикали не дёргается).
 *
 * - `nearest` — от текущего места ровно настолько, насколько нужно (как `scrollIntoView({ inline:
 *   'nearest' })`): после смены выбранной полоса не прыгает, если та и так видна;
 * - `start` — от самого начала полосы: 0, если выбранная помещается в первый экран, иначе она у
 *   правого края. Место зависит только от раскладки, а не от того, где полоса была до загрузки
 *   шрифта, — так её видит тот, кто полосу ещё не трогал (и скриншот-тест).
 */
function revealSelected(strip: HTMLElement, from: 'nearest' | 'start') {
  const selected = strip.querySelector<HTMLElement>(SELECTED)
  if (!selected) return
  const outer = strip.getBoundingClientRect()
  const inner = selected.getBoundingClientRect()
  if (from === 'start') {
    const right = inner.right - outer.left + strip.scrollLeft + EDGE_GAP
    strip.scrollLeft = Math.max(0, right - outer.width)
  } else if (inner.left - EDGE_GAP < outer.left) {
    strip.scrollLeft -= outer.left - inner.left + EDGE_GAP
  } else if (inner.right + EDGE_GAP > outer.right) {
    strip.scrollLeft += inner.right - outer.right + EDGE_GAP
  }
}

/**
 * Ref-колбэк для полосы вкладок, прокручиваемой вбок (телефон): выбранная вкладка подъезжает в
 * видимую область — когда полоса появилась, когда сменилась выбранная (или набор вкладок) и когда
 * поменялась ширина пилюль (догрузилось начертание шрифта, сменился язык).
 *
 * Последнее — только пока пользователь не взялся за полосу сам: после касания, колеса, клавиш или
 * любой прокрутки не нами полоса сама больше не двигается (иначе поздний шрифт отбросил бы её из-под
 * пальца назад, к выбранной) — до следующей смены выбранной вкладки. Пока полосу не трогали, её
 * место считается от начала (`start`), после — от текущего (`nearest`).
 *
 * Функция модульная, а не хук: ссылка на неё постоянна, React вызывает её один раз при появлении
 * узла и возвращённую очистку — при его удалении (React 19).
 *
 * `onSelect` — ещё один сигнал «выбранная сменилась», если прокрутку полосы после смены кто-то
 * переставляет сам: получает `select` (показать выбранную и снова следить за шириной) и возвращает
 * отписку. Вкладкам-маршрутам это `router.subscribe('onRendered', …)`: восстановление прокрутки
 * роутера после перехода возвращает полосе позицию со старого адреса — доводим после него.
 */
export function keepSelectedInView(
  strip: HTMLElement | null,
  onSelect?: (select: () => void) => () => void,
): (() => void) | undefined {
  if (!strip) return undefined

  /** Пользователь брался за эту полосу (касание, колесо, клавиши, своя прокрутка). */
  let touched = false
  /** Доводить выбранную при смене ширины: да — до прокрутки пользователем, снова — после смены выбранной. */
  let following = true
  /** Где полосу оставили мы: прокрутка в другое место — не наша. */
  let ours = strip.scrollLeft

  const reveal = () => {
    revealSelected(strip, touched ? 'nearest' : 'start')
    ours = strip.scrollLeft
  }
  const takeOver = () => {
    touched = true
    following = false
  }
  const onScroll = () => {
    if (Math.abs(strip.scrollLeft - ours) >= 1) takeOver()
  }

  // Ширина пилюль: полоса-обёртка (вкладки-маршруты) или сами вкладки (Base UI) — дети полосы.
  const sizes = new ResizeObserver(() => {
    if (following) reveal()
  })
  const observeItems = () => {
    for (const child of Array.from(strip.children)) sizes.observe(child)
  }
  // Выбранная сменилась (нажали вкладку, перешли по ссылке) — показать её, даже если полосу перед
  // этим прокрутили руками.
  const select = () => {
    following = true
    reveal()
  }
  // Пришёл другой набор вкладок (сменился раздел) — это как новая полоса: её ещё не трогали.
  const isNewSet = (record: MutationRecord) =>
    record.type === 'childList' && (record.target === strip || record.target.parentNode === strip)
  const selection = new MutationObserver((records) => {
    const newSet = records.some(isNewSet)
    if (newSet) {
      touched = false
      observeItems()
    }
    if (newSet || records.some((record) => record.type === 'attributes')) select()
  })
  const unsubscribe = onSelect?.(select)

  reveal()
  observeItems()
  selection.observe(strip, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ['aria-current', 'aria-selected'],
  })
  strip.addEventListener('scroll', onScroll, { passive: true })
  for (const type of USER_INPUT) strip.addEventListener(type, takeOver, { passive: true })

  return () => {
    unsubscribe?.()
    sizes.disconnect()
    selection.disconnect()
    strip.removeEventListener('scroll', onScroll)
    for (const type of USER_INPUT) strip.removeEventListener(type, takeOver)
  }
}
