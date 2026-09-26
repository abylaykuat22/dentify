/**
 * Выполнить после первого экрана: когда догрузились шрифты (текст первого экрана ждёт их), кадр с
 * ними отрисован и браузер свободен. Так грузится то, чего на первом экране не видно, — место
 * уведомлений, боковая панель: их запросы не встают в цепочку до первого экрана и не отнимают у него
 * канал (PLT-22, LCP). Возвращает отмену — для очистки эффекта.
 */
export function whenIdle(callback: () => void, timeout = 2000): () => void {
  let cancel = () => {}
  let cancelled = false
  const schedule = () => {
    if (cancelled) return
    if (typeof window.requestIdleCallback === 'function') {
      const id = window.requestIdleCallback(callback, { timeout })
      cancel = () => window.cancelIdleCallback(id)
    } else {
      const id = window.setTimeout(callback, 200)
      cancel = () => window.clearTimeout(id)
    }
  }
  // Шрифты догрузились → ждём кадр, в котором их текст отрисован (два requestAnimationFrame), и уже
  // потом — простой. document.fonts есть не везде (jsdom в тестах) — тогда сразу.
  const afterPaint = () => {
    if (cancelled) return
    if (typeof window.requestAnimationFrame !== 'function') return schedule()
    window.requestAnimationFrame(() => window.requestAnimationFrame(schedule))
  }
  const fontsReady: Promise<unknown> = typeof document !== 'undefined' && document.fonts ? document.fonts.ready : Promise.resolve()
  void fontsReady.then(afterPaint, afterPaint)
  return () => {
    cancelled = true
    cancel()
  }
}
