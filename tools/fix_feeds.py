#!/usr/bin/env python3
"""
Attempt to auto-fix common IOPScience feed URLs and update suggested_feeds.json.
Rules implemented:
- Replace iopscience urls like http://iopscience.iop.org/<issn>/?rss=1 with https://iopscience.iop.org/journal/rss/<issn>

Run: python3 tools/fix_feeds.py
"""
import json
import re
from pathlib import Path

JSON = Path('app/src/main/assets/suggested_feeds.json')

def fix_iop(url):
    m = re.search(r'iopscience\.iop\.org/([0-9]{4}-[0-9]{4})', url)
    if m:
        issn = m.group(1)
        return f'https://iopscience.iop.org/journal/rss/{issn}'
    return None

def main():
    feeds = json.loads(JSON.read_text(encoding='utf-8'))
    changed = False
    for f in feeds:
        url = f.get('url','')
        if 'iopscience.iop.org' in url:
            new = fix_iop(url)
            if new and new != url:
                print(f"Fixing {f.get('title')}\n  {url}\n->{new}\n")
                f['url'] = new
                changed = True
    if changed:
        JSON.write_text(json.dumps(feeds, ensure_ascii=False, indent=2), encoding='utf-8')
        print('Updated suggested_feeds.json')
    else:
        print('No changes')

if __name__ == '__main__':
    main()
