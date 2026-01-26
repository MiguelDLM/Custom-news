import json
import requests
import re
import concurrent.futures
import os
import time
from urllib.parse import urljoin, urlparse

FEED_FILE = 'app/src/main/assets/suggested_feeds.json'
OUTPUT_FILE = 'app/src/main/assets/suggested_feeds_fixed.json'

# Headers to mimic a browser to avoid 403s
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8'
}

def is_valid_feed_content(content):
    content = content.strip()
    # Simple check for XML signature
    if content.startswith(b'<?xml') or b'<rss' in content or b'<feed' in content or b'<rdf:RDF' in content:
        return True
    return False

def find_feed_link(html_content, base_url):
    try:
        if isinstance(html_content, bytes):
            html_content = html_content.decode('utf-8', errors='ignore')
            
        # Regex to find <link rel="alternate" type="application/rss+xml" href="...">
        # This is less robust than BS4 but works for standard cases
        matches = re.findall(r'<link[^>]*rel=["\']alternate["\'][^>]*>', html_content, re.IGNORECASE)
        for match in matches:
            if 'application/rss+xml' in match or 'application/atom+xml' in match:
                href_match = re.search(r'href=["\']([^"\']+)["\']', match)
                if href_match:
                    return urljoin(base_url, href_match.group(1))
    except Exception:
        pass
    return None

def process_feed(feed):
    original_url = feed.get('url')
    if not original_url:
        return None

    try:
        response = None
        # Retry loop for 429/503
        for attempt in range(3):
            try:
                # First attempt: Just get the URL, following redirects
                response = requests.get(original_url, headers=HEADERS, timeout=30, allow_redirects=True)
                
                if response.status_code in [429, 503]:
                    time.sleep(2 * (attempt + 1))
                    continue
                break
            except requests.exceptions.RequestException:
                if attempt == 2:
                    # print(f"[-] Error: {original_url}")
                    return None
                time.sleep(1)
        
        if not response:
             return None

        final_url = response.url
        
        # If the status code is bad, mark as invalid immediately
        if response.status_code >= 400:
            print(f"[-] Dead ({response.status_code}): {original_url}")
            return None

        # Check content
        content = response.content
        if is_valid_feed_content(content):
            # It's a valid feed!
            if final_url != original_url:
                print(f"[+] Fixed Redirect: {original_url} -> {final_url}")
                feed['url'] = final_url
            else:
                pass # print(f"[.] Valid: {original_url}")
            return feed
        
        # If content is HTML, maybe the URL points to a webpage
        # Try to discover feed link in the HTML
        discovered_url = find_feed_link(content, final_url)
        if discovered_url:
            # Verify the discovered URL
            try:
                sub_response = requests.get(discovered_url, headers=HEADERS, timeout=15)
                if sub_response.status_code == 200 and is_valid_feed_content(sub_response.content):
                    print(f"[+] Discovered Feed: {original_url} -> {discovered_url}")
                    feed['url'] = discovered_url
                    return feed
            except:
                pass
        
        print(f"[-] Not a feed & no discovery: {original_url} (Final: {final_url})")
        return None

    except Exception as e:
        print(f"[-] Error {e}: {original_url}")
        return None

def main():
    if not os.path.exists(FEED_FILE):
        print("Input file not found.")
        return

    with open(FEED_FILE, 'r') as f:
        feeds = json.load(f)

    print(f"Processing {len(feeds)} feeds...")
    
    fixed_feeds = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        future_to_feed = {executor.submit(process_feed, feed): feed for feed in feeds}
        for future in concurrent.futures.as_completed(future_to_feed):
            result = future.result()
            if result:
                fixed_feeds.append(result)
            
    print(f"Finished. Retained {len(fixed_feeds)} valid feeds.")
    
    with open(OUTPUT_FILE, 'w') as f:
        json.dump(fixed_feeds, f, indent=2)

if __name__ == "__main__":
    main()
