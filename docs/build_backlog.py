"""Генерирует requirements.md, backlog.md и kanban/tasks.json из docs/backlog.json.

Правится только backlog.json; md и tasks.json — производные, руками не трогать.
Запуск: python docs/build_backlog.py  (из apps/dentify)
"""
import json
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'docs' / 'backlog.json'
OUT_REQ = ROOT / 'docs' / 'requirements.md'
OUT_BL = ROOT / 'docs' / 'backlog.md'
OUT_KB = ROOT / 'kanban' / 'tasks.json'

ORDER = ['PLT', 'IAM', 'CLN', 'STF', 'PAT', 'SCH', 'MED', 'INV']
SHORT = {'PLT': 'Платформа', 'IAM': 'Доступы', 'CLN': 'Клиника', 'STF': 'Врачи',
         'PAT': 'Пациенты', 'SCH': 'Расписание', 'MED': 'Медкарта', 'INV': 'Склад'}
PHASES = [
    (0, 'Фундамент', 2, 'вход → пустой кабинет на телефоне и десктопе'),
    (1, 'Клиника и доступы', 3, 'владелец завёл клинику, прайс и админа'),
    (2, 'Врачи и пациенты', 2, 'справочники и люди заполнены'),
    (3, 'Расписание', 4, 'админ ведёт реальный день клиники'),
    (4, 'Формула и медзаписи', 2, 'врач ведёт приём в карте'),
    (5, 'Склад', 2, 'склад клиники вживую'),
    (6, 'Пилот', 2, 'клиника пользователя работает в Dentify'),
]
DAYS = {'S': 0.5, 'M': 1.5, 'L': 3}
TYPE_RU = {'backend': 'бэкенд', 'frontend': 'фронтенд', 'fullstack': 'бэкенд + фронт',
           'infra': 'инфраструктура', 'test': 'тесты', 'docs': 'документация'}


def load():
    data = json.loads(SRC.read_text(encoding='utf-8'))
    mods = {m['code']: m for m in data['modules']}
    unknown = [c for c in mods if c not in ORDER]
    return data, [mods[c] for c in ORDER if c in mods] + [mods[c] for c in unknown]


def check(mods):
    warn = []
    reqs, tasks = {}, {}
    for m in mods:
        for r in m['requirements']:
            if r['id'] in reqs:
                warn.append(f'дубль требования {r["id"]}')
            reqs[r['id']] = r
        for t in m['tasks']:
            if t['id'] in tasks:
                warn.append(f'дубль задачи {t["id"]}')
            tasks[t['id']] = (m['code'], t)
    for tid, (_, t) in tasks.items():
        for r in t['requirements']:
            if r not in reqs:
                warn.append(f'{tid}: нет требования {r}')
            elif reqs[r]['priority'] != 'MVP':
                warn.append(f'{tid}: ссылается на «Позже» {r}')
        for d in t['dependsOn']:
            if d not in tasks:
                warn.append(f'{tid}: нет задачи-зависимости {d}')
            elif tasks[d][1]['phase'] > t['phase']:
                warn.append(f'{tid} (ф{t["phase"]}) зависит от {d} из более поздней ф{tasks[d][1]["phase"]}')
    # циклы
    state = {}

    def dfs(n, path):
        state[n] = 1
        for d in tasks[n][1]['dependsOn']:
            if d not in tasks:
                continue
            if state.get(d) == 1:
                warn.append('цикл: ' + ' → '.join(path[path.index(d):] + [d]) if d in path else f'цикл через {d}')
            elif not state.get(d):
                dfs(d, path + [d])
        state[n] = 2

    for n in tasks:
        if not state.get(n):
            dfs(n, [n])
    covered = {r for _, t in tasks.values() for r in t['requirements']}
    for rid, r in reqs.items():
        if r['priority'] == 'MVP' and rid not in covered:
            warn.append(f'MVP-требование {rid} не покрыто задачей')
    return reqs, tasks, warn


def md_requirements(data, mods, tasks):
    by_req = defaultdict(list)
    for tid, (_, t) in tasks.items():
        for r in t['requirements']:
            by_req[r].append(tid)
    n_mvp = sum(1 for m in mods for r in m['requirements'] if r['priority'] == 'MVP')
    n_later = sum(1 for m in mods for r in m['requirements'] if r['priority'] != 'MVP')
    L = [
        '# Dentify — функциональные требования', '',
        f'> Версия {data.get("version", date.today().isoformat())}. Файл генерируется из '
        '[backlog.json](backlog.json) скриптом `python docs/build_backlog.py` — правьте JSON, не этот файл.',
        '> **MVP** — входит в первую версию (всё, что есть на макетах и в плане). **Позже** — после пилота.',
        f'> Всего: {n_mvp} требований MVP, {n_later} — позже. Задачи на их реализацию — [backlog.md](backlog.md).',
        '', '## Сводка', '',
        '| Модуль | Что покрывает | MVP | Позже | Задач |', '|---|---|---:|---:|---:|',
    ]
    for m in mods:
        mv = sum(1 for r in m['requirements'] if r['priority'] == 'MVP')
        L.append(f'| [{m["code"]}](#{m["code"].lower()}) | {m["name"]} | {mv} | '
                 f'{len(m["requirements"]) - mv} | {len(m["tasks"])} |')
    roles = data.get('roles')
    if roles:
        L += ['', '## Роли', '']
        L += [f'- **{r["name"]}** — {r["description"]}' for r in roles]
    for m in mods:
        L += ['', f'## {m["code"]}', '', f'**{m["name"]}.** {m.get("summary", "")}'.rstrip(), '']
        for prio in ('MVP', 'Позже'):
            rs = [r for r in m['requirements'] if (r['priority'] == 'MVP') == (prio == 'MVP')]
            if not rs:
                continue
            if prio == 'Позже':
                L += ['### Позже (после MVP)', '']
                L += [f'- **{r["id"]} {r["title"]}** — {r["description"]}' for r in rs]
                L.append('')
                continue
            for r in rs:
                L += [f'### {r["id"]} · {r["title"]}', '', r['description'], '']
                if r.get('acceptance'):
                    L.append('Приёмка:')
                    L += [f'- {a}' for a in r['acceptance']]
                    L.append('')
                meta = []
                if r.get('screens'):
                    meta.append('Экраны: ' + ', '.join(r['screens']))
                if r.get('roles'):
                    meta.append('Роли: ' + ', '.join(r['roles']))
                if by_req.get(r['id']):
                    meta.append('Задачи: ' + ', '.join(by_req[r['id']]))
                if meta:
                    L += ['<sub>' + ' · '.join(meta) + '</sub>', '']
    notes = data.get('criticNotes') or []
    if notes:
        dup = [n for n in notes if n.startswith('Дубли:')]
        rest = [n for n in notes if not n.startswith('Дубли:')]
        L += ['', '## Замечания ревью', '',
              'Сквозная проверка всех модулей: пробелы, порядок фаз, границы модулей. Порядок фаз и явные '
              'дубли уже исправлены в бэклоге; остальное — решить до начала соответствующего этапа.', '']
        L += [f'- {n}' for n in rest]
        if dup:
            L += ['', '### Разграничение пересекающихся требований', '']
            L += [f'- {n[len("Дубли: "):]}' for n in dup]
    return '\n'.join(L).rstrip() + '\n'


def md_backlog(data, mods, tasks):
    by_phase = defaultdict(list)
    for m in mods:
        for t in m['tasks']:
            by_phase[t['phase']].append((m['code'], t))
    total = sum(DAYS[t['estimate']] for _, t in tasks.values())
    L = [
        '# Dentify — бэклог задач', '',
        f'> Версия {data.get("version", date.today().isoformat())}. Генерируется из [backlog.json](backlog.json) '
        '(`python docs/build_backlog.py`). Живой статус задач — в канбане (ссылка в README).',
        '> Оценка: **S** — до ½ дня, **M** — 1–2 дня, **L** — ~3 дня. Сумма ниже считает S=0.5, M=1.5, L=3 рабочих дня.',
        '', '## Сводка по этапам', '',
        '| Этап | Задач | S / M / L | Оценка, дней | План, недель |', '|---|---:|---|---:|---:|',
    ]
    for n, name, weeks, _ in PHASES:
        ts = [t for _, t in by_phase[n]]
        cnt = {k: sum(1 for t in ts if t['estimate'] == k) for k in 'SML'}
        L.append(f'| {n} · {name} | {len(ts)} | {cnt["S"]} / {cnt["M"]} / {cnt["L"]} | '
                 f'{sum(DAYS[t["estimate"]] for t in ts):g} | {weeks} |')
    L.append(f'| **Итого** | **{len(tasks)}** | | **{total:g}** | **{sum(p[2] for p in PHASES)}** |')
    for n, name, weeks, demo in PHASES:
        items = by_phase[n]
        if not items:
            continue
        L += ['', f'## Этап {n} · {name}', '', f'Демо в конце: {demo}. План — {weeks} нед.', '']
        for code, t in items:
            L += [f'### {t["id"]} · {t["title"]}', '']
            meta = [f'`{t["estimate"]}`', TYPE_RU.get(t['type'], t['type']), SHORT.get(code, code)]
            if t['requirements']:
                meta.append('треб.: ' + ', '.join(t['requirements']))
            if t['dependsOn']:
                meta.append('после: ' + ', '.join(t['dependsOn']))
            L += [' · '.join(meta), '']
            L += [f'- [ ] {s}' for s in t['subtasks']]
            L.append('')
    return '\n'.join(L).rstrip() + '\n'


def kanban_json(data, mods, reqs, tasks):
    out = {
        'version': data.get('version', date.today().isoformat()),
        'phases': [{'n': n, 'name': nm, 'weeks': w, 'demo': d} for n, nm, w, d in PHASES],
        'modules': [{'code': m['code'], 'name': SHORT.get(m['code'], m['name'])} for m in mods],
        'requirements': {rid: {'t': r['title'], 'p': r['priority']} for rid, r in reqs.items()},
        'tasks': [],
    }
    for m in mods:
        for t in sorted(m['tasks'], key=lambda x: x['phase']):
            out['tasks'].append({
                'id': t['id'], 'title': t['title'], 'module': m['code'], 'phase': t['phase'],
                'type': t['type'], 'est': t['estimate'], 'req': t['requirements'], 'deps': t['dependsOn'],
                'sub': [{'k': f's{i + 1}', 't': s} for i, s in enumerate(t['subtasks'])],
            })
    return out


def main():
    data, mods = load()
    reqs, tasks, warn = check(mods)
    OUT_REQ.write_text(md_requirements(data, mods, tasks), encoding='utf-8')
    OUT_BL.write_text(md_backlog(data, mods, tasks), encoding='utf-8')
    OUT_KB.parent.mkdir(exist_ok=True)
    OUT_KB.write_text(json.dumps(kanban_json(data, mods, reqs, tasks), ensure_ascii=False, separators=(',', ':')),
                      encoding='utf-8')
    mvp = sum(1 for r in reqs.values() if r['priority'] == 'MVP')
    days = sum(DAYS[t['estimate']] for _, t in tasks.values())
    print(f'требований {len(reqs)} (MVP {mvp}), задач {len(tasks)}, '
          f'подзадач {sum(len(t["subtasks"]) for _, t in tasks.values())}, оценка {days:g} дн.')
    for w in warn:
        print('WARN', w)
    return 0


if __name__ == '__main__':
    sys.exit(main())
