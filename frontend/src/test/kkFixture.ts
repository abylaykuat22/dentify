import { i18n, NAMESPACES } from '@/shared/i18n'

/**
 * Фикстурный казахский словарь для тестов «казахский = словарь + флаг, без правок компонентов»
 * (PLT-11, dentify R003). Это не перевод (настоящий — PLT-34, с носителем языка): несколько строк,
 * по которым видно, что сменился язык; остальные ключи падают на русский.
 */
export const KK_FIXTURE = {
  common: {
    languages: { label: 'Интерфейс тілі' },
    // В казахском числительное не меняет форму существительного: «1 жас», «41 жас».
    age: '{age, plural, one {# жас} other {# жас}}',
    dates: { dayLabel: 'd MMMM, EEEE' },
    titles: { page: '{screen} — {app}' },
  },
  shell: {
    nav: { schedule: 'Кесте', patients: 'Пациенттер', more: 'Тағы', profile: 'Менің профилім' },
    more: { language: 'Тіл', logout: 'Шығу' },
    actions: { newAppointment: 'Жаңа жазба' },
  },
  auth: { login: { title: 'Кіру', submit: 'Кіру' } },
  errors: {
    ui: { tooManyWait: 'Әрекет тым көп. {unit, select, minute {{value} минуттан} other {{value} секундтан}} кейін қайталаңыз' },
    validation: { 'not-blank': 'Өрісті толтырыңыз' },
  },
} as const

/** Подложить фикстурный словарь kk (пространства без строк — пустые: язык не ходит за файлами). */
export function addKazakhFixture(): void {
  for (const namespace of NAMESPACES) {
    const bundle = (KK_FIXTURE as Record<string, object>)[namespace] ?? {}
    i18n.addResourceBundle('kk', namespace, bundle, true, true)
  }
}

/** Вернуть русский и убрать фикстуру и выбор языка — между тестами язык не протекает. */
export async function removeKazakhFixture(): Promise<void> {
  await i18n.changeLanguage('ru')
  // Только данные kk: removeResourceBundle убрал бы и само пространство из options.ns i18next.
  delete i18n.store.data.kk
  try {
    localStorage.removeItem('dentify-lang')
  } catch {
    // нет хранилища — нечего чистить
  }
}
