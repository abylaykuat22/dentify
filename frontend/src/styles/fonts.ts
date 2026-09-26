// Шрифты «Кресла» — self-hosted из npm (@fontsource), без обращений к сторонним доменам
// (docs/data-residency.md: «нет Google Fonts»). Файл веса содержит все подмножества с
// unicode-range, браузер скачивает только нужные: latin, cyrillic и cyrillic-ext — в нём
// казахские ә ғ қ ң ө ұ ү һ і. Лицензия OFL, @font-face с font-display: swap. Что казахские
// буквы рисуются этими шрифтами, а не запасными, проверяет e2e/dev-ui.spec.ts (замер глифов).
import '@fontsource/nunito-sans/500.css'
import '@fontsource/nunito-sans/600.css'
import '@fontsource/nunito-sans/700.css'
import '@fontsource/nunito-sans/800.css'
import '@fontsource/rubik/500.css'
import '@fontsource/rubik/600.css'
import '@fontsource/rubik/700.css'
