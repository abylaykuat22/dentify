import io, os, re, sys
from html.parser import HTMLParser

PROJ = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mockups')
VOID = {'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr'}
SVG_SELF = {'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'ellipse', 'stop', 'use'}
BAD_FONTS = ['Inter', 'Roboto', 'Arial', 'Manrope', 'Unbounded', 'Onest', 'Geologica', 'IBM Plex', 'Golos']
LEGACY = {'Main', 'Karta'}  # direction samples keep their own fonts


class Checker(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack, self.errors = [], []

    def handle_starttag(self, tag, attrs):
        if tag not in VOID:
            self.stack.append(tag)

    def handle_startendtag(self, tag, attrs):
        if tag not in VOID and tag not in SVG_SELF:
            self.errors.append('self-closed <%s/>' % tag)

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if self.stack and self.stack[-1] == tag:
            self.stack.pop()
        elif tag in self.stack:
            while self.stack and self.stack[-1] != tag:
                self.errors.append('unclosed <%s> before </%s>' % (self.stack.pop(), tag))
            self.stack.pop()
        else:
            self.errors.append('stray </%s>' % tag)


def check(name):
    s = io.open(os.path.join(PROJ, name), encoding='utf-8').read()
    base = name[:-8]
    errs = []
    if '<script src="./support.js"></script>' not in s:
        errs.append('support.js line missing/changed')
    if 'data-dc-script' not in s or 'extends DCLogic' not in s:
        errs.append('dc-script block missing')
    m = re.search(r'"\$preview"\s*:\s*\{\s*"width"\s*:\s*(\d+)\s*,\s*"height"\s*:\s*(\d+)', s)
    if not m:
        errs.append('no $preview')
    else:
        w, h = m.group(1), m.group(2)
        body = s.split('</helmet>', 1)[-1]
        rm = re.search(r'<(div|main|section)[^>]*style="([^"]*)"', body)
        if not rm or ('width:%spx' % w) not in rm.group(2).replace(' ', '') or ('height:%spx' % h) not in rm.group(2).replace(' ', ''):
            errs.append('root size != $preview %sx%s' % (w, h))
    if '{{' in s:
        errs.append('template holes {{}}')
    if re.search(r'innerHTML|appendChild|document\.create', s):
        errs.append('script-built DOM')
    if base not in LEGACY:
        for f in BAD_FONTS:
            if re.search(r'font-family[^;"]*' + re.escape(f), s) or ('family=' + f.replace(' ', '+')) in s:
                errs.append('forbidden font ' + f)
    yellow = len([t for t in re.findall(r'<(?:button|a)\b[^>]*>', s)
                  if 'background:#FFCB47' in t.replace(' ', '') and 'aria-current' not in t])
    c = Checker()
    c.feed(s)
    errs += c.errors[:5]
    if c.stack:
        errs.append('unclosed at end: ' + ','.join(c.stack[-4:]))
    return errs, yellow


def main():
    bad = 0
    for name in sorted(f for f in os.listdir(PROJ) if f.endswith('.dc.html')):
        errs, yellow = check(name)
        flag = '' if yellow <= 1 else '  yellow-buttons=%d' % yellow
        if errs or flag:
            bad += 1
            print(name + flag + ('  ' + ' | '.join(errs) if errs else ''))
    print('files with notes:', bad)


if __name__ == '__main__':
    main()
