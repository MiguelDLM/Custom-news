#!/usr/bin/env python3
"""
Validate suggested_feeds.json entries:
- checks HTTP status
- checks Content-Type for XML
- parses XML and validates root is rss/feed/rdf and that it contains at least one item/entry

Usage: python3 tools/validate_suggested_feeds.py
"""
import json
import sys
import urllib.request
import urllib.error
import ssl
from xml.etree import ElementTree as ET
from urllib.parse import urljoin

TIMEOUT = 15
JSON_PATH = 'app/src/main/assets/suggested_feeds.json'

def fetch(url):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'NewsReaderFeedValidator/1.0'
    })
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=TIMEOUT, context=ctx) as resp:
        status = resp.getcode()
        ctype = resp.headers.get('Content-Type', '')
        data = resp.read()
        return status, ctype, data

def is_xml_content(ctype):
    if not ctype:
        return False
    c = ctype.lower()
    return 'xml' in c or 'rss' in c or 'application/rss+xml' in c

def validate_feed_url(url):
    try:
        status, ctype, data = fetch(url)
    except urllib.error.HTTPError as e:
        return False, f'HTTPError {e.code}'
    except Exception as e:
        return False, f'Fetch error: {e}'

    if status != 200:
        return False, f'HTTP {status}'

    if not is_xml_content(ctype):
        # still try to parse even if content-type is missing/wrong
        pass

    try:
        root = ET.fromstring(data)
    except Exception as e:
        return False, f'XML parse error: {e}'

    tag = root.tag.lower()
    # In case of namespaces, strip
    if '}' in tag:
        tag = tag.split('}', 1)[1]

    if tag not in ('rss', 'feed', 'rdf'):
        return False, f'Root tag not rss/feed/rdf: {tag}'

    # look for items/entries
    items = list(root.iter())
    has_item = any((strip_tag(n.tag) in ('item', 'entry')) for n in items)
    if not has_item:
        return False, 'No <item> or <entry> elements found'

    return True, 'OK'

def strip_tag(tag):
    if tag is None:
        return ''
    if '}' in tag:
        return tag.split('}', 1)[1].lower()
    return tag.lower()

def main():
    try:
        with open(JSON_PATH, 'r', encoding='utf-8') as f:
            feeds = json.load(f)
    except Exception as e:
        print(f'Failed to read {JSON_PATH}: {e}', file=sys.stderr)
        sys.exit(2)

    results = []
    invalid = []
    print('Validating', len(feeds), 'feeds...')
    for idx, feed in enumerate(feeds, 1):
        url = feed.get('url')
        title = feed.get('title')
        print(f'[{idx}/{len(feeds)}] {title} -> {url}')
        ok, msg = validate_feed_url(url)
        results.append({'title': title, 'url': url, 'ok': ok, 'msg': msg})
        if not ok:
            invalid.append({'title': title, 'url': url, 'msg': msg})
        # be polite
    print('\nSummary:')
    print('  Total:', len(feeds))
    print('  Valid:', len(feeds) - len(invalid))
    print('  Invalid:', len(invalid))
    if invalid:
        print('\nInvalid feeds:')
        for f in invalid:
            print(f" - {f['title']}: {f['url']} -> {f['msg']}")
        sys.exit(1)
    else:
        print('All suggested feeds look valid')

if __name__ == '__main__':
    main()
