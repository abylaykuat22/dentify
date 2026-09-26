import io, json, os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(ROOT, 'mockups')

PAGES = [
    ('raspisanie', 'Расписание', [
        ('Raspisanie-Den', 'День'), ('Raspisanie-Nedelya', 'Неделя врача'),
        ('Zapis-1', 'Новая запись · 1 Пациент'), ('Zapis', 'Новая запись · 2 Врач и время'),
        ('Zapis-3', 'Новая запись · 3 Услуги'), ('Perenos-Otmena', 'Перенос и отмена'),
        ('Arhiv', 'Архив записей')]),
    ('pacienty', 'Пациенты', [
        ('Pacienty', 'Список'), ('Pacient-Novyj', 'Новый пациент'),
        ('Pacient-Osnovnoe', 'Карточка · Основное'), ('Pacient', 'Карточка · Медкарта'),
        ('Pacient-Vizity', 'Карточка · Визиты'), ('Pacient-Fajly', 'Карточка · Файлы'),
        ('Priem', 'Запись приёма')]),
    ('vrachi', 'Врачи', [
        ('Vrachi', 'Сотрудники'), ('Vrachi-Grafiki', 'Графики'),
        ('Vrach-Profil', 'Врач · Профиль'), ('Vrach-Grafik', 'Врач · График'),
        ('Vrach-Zapisi', 'Врач · Записи'), ('Vrach-Dostup', 'Врач · Доступ'),
        ('Vrach-Uvolnenie', 'Увольнение')]),
    ('sklad', 'Склад', [
        ('Sklad-Ostatki', 'Остатки'), ('Sklad-Nomenklatura', 'Номенклатура'),
        ('Sklad-Dokumenty', 'Документы'), ('Sklad-Prihod', 'Приход'),
        ('Sklad-Inventarizaciya', 'Инвентаризация'), ('Sklad-Postavshchiki', 'Поставщики и склады')]),
    ('klinika', 'Клиника', [
        ('Klinika-Profil', 'Профиль'), ('Klinika-Filialy', 'Филиалы'), ('Klinika-Kresla', 'Кресла'),
        ('Klinika-Prajs', 'Прайс'), ('Klinika-Spravochniki', 'Справочники'),
        ('Klinika-Nastrojki', 'Настройки')]),
    ('dostupy', 'Доступы', [
        ('Dostupy-Sotrudniki', 'Сотрудники'), ('Dostupy-Priglashenie', 'Приглашение'),
        ('Dostupy-Roli', 'Роли и права'), ('Dostupy-Zhurnal', 'Журнал действий'),
        ('Dostupy-Vhody', 'Входы и сессии')]),
    ('obshchee', 'Вход и общее', [
        ('Vhod', 'Вход'), ('Parol', 'Восстановление пароля'), ('Priglashenie', 'Приглашение сотрудника'),
        ('VyborKliniki', 'Выбор клиники'), ('Registraciya-1', 'Регистрация · 1 Клиника'),
        ('Registraciya-2', 'Регистрация · 2 Филиал и кресла'), ('Profil', 'Мой профиль'),
        ('Sostoyaniya', 'Пустые и служебные состояния'), ('Poisk', 'Поиск Ctrl K')]),
    ('telefon', 'Телефон', [
        ('Telefon', 'Расписание'), ('Telefon-Pacient', 'Карточка пациента'),
        ('Telefon-Zapis', 'Новая запись'), ('Telefon-Eshche', 'Меню «Ещё»')]),
    ('sistema', 'Дизайн-система', [('Sistema', 'Дизайн-система «Кресло»')]),
    ('napravleniya', 'Направления', [
        ('Main', 'A · «Эмаль»'), ('Karta', 'B · «Карта»'), ('Kreslo', 'C · «Кресло» — выбрано')]),
]

PER_ROW = 3
GAP_X, GAP_Y = 80, 120


def size_of(path):
    s = io.open(path, encoding='utf-8').read()
    m = re.search(r'"\$preview"\s*:\s*\{\s*"width"\s*:\s*(\d+)\s*,\s*"height"\s*:\s*(\d+)', s)
    if not m:
        raise SystemExit('no $preview in ' + path)
    return int(m.group(1)), int(m.group(2))


def main():
    old = json.load(io.open(os.path.join(PROJ, 'canvas.json'), encoding='utf-8'))
    boards, order, notes, missing = {}, [], {}, []
    for pid, pname, items in PAGES:
        x = y = 0
        row_h = 0
        col = 0
        present = [(f, t) for f, t in items if os.path.exists(os.path.join(PROJ, f + '.dc.html'))]
        missing += [f for f, t in items if (f, t) not in present]
        row_w = 0
        for f, title in present:
            w, h = size_of(os.path.join(PROJ, f + '.dc.html'))
            if col == PER_ROW:
                x, y, col = 0, y + row_h + GAP_Y, 0
                row_h = 0
            key = f + '.dc.html'
            boards[key] = {'x': x, 'y': y, 'w': w, 'h': h, 'title': title, 'page': pid}
            order.append(key)
            x += w + GAP_X
            row_w = max(row_w, x - GAP_X)
            row_h = max(row_h, h)
            col += 1
        if len(present) > 1:
            notes['t_' + pid] = {'kind': 'title1', 'x': 0, 'y': -300, 'text': pname,
                                 'maxW': max(row_w, 1440), 'w': 240, 'page': pid}
    canvas = {
        'v': 3,
        'attachments': old.get('attachments', {}),
        'boards': boards,
        'createdOnFiles': old['createdOnFiles'],
        'designSystems': old.get('designSystems', []),
        'launch': {'view': 'canvas', 'page': 'raspisanie'},
        'notes': notes,
        'order': order,
        'pages': [{'id': pid, 'name': pname} for pid, pname, _ in PAGES],
        'title': 'Dentify — экраны MVP',
    }
    io.open(os.path.join(PROJ, 'canvas.json'), 'w', encoding='utf-8', newline='\n').write(
        json.dumps(canvas, ensure_ascii=False, indent=1))
    extra = sorted(f[:-8] for f in os.listdir(PROJ) if f.endswith('.dc.html') and f not in boards)
    print('boards:', len(boards), '| missing:', missing, '| not placed:', extra)


if __name__ == '__main__':
    main()
