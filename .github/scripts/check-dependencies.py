"""Query OSV for resolved public Maven coordinates; never update dependencies."""

from datetime import date, datetime, timezone
import json
import os
from pathlib import Path
import re
import urllib.request


def load_exceptions(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate exception JSON key')
            result[key] = value
        return result

    document = json.loads(path.read_text(), object_pairs_hook=unique)
    if not isinstance(document, dict) or set(document) != {'exceptions'} or not isinstance(document['exceptions'], list):
        raise ValueError('Expected an exceptions list')
    exceptions = {}
    today = datetime.now(timezone.utc).date()
    for entry in document['exceptions']:
        if not isinstance(entry, dict) or set(entry) != {'id', 'coordinate', 'reason', 'expires'}:
            raise ValueError('Exception requires id, coordinate, reason and expires')
        if any(not isinstance(value, str) or not value.strip() or value != value.strip()
               for value in entry.values()):
            raise ValueError('Exception fields must be nonempty trimmed strings')
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', entry['id']):
            raise ValueError('Invalid advisory ID')
        if not re.fullmatch(r'[^:\s]+:[^:\s]+:[^:\s]+', entry['coordinate']):
            raise ValueError('Exception must identify one exact group:artifact:version')
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', entry['expires']) or date.fromisoformat(entry['expires']) <= today:
            raise ValueError('Exception expiry must be a future UTC date')
        key = (entry['coordinate'], entry['id'])
        if key in exceptions:
            raise ValueError('Duplicate advisory/coordinate exception')
        exceptions[key] = entry
    return exceptions


def scan(packages, request):
    findings = {}
    for start in range(0, len(packages), 100):
        batch = packages[start:start + 100]
        pending = [(item, {'package': {'ecosystem': 'Maven', 'name': item['name']}, 'version': item['version']}) for item in batch]
        seen = set()
        while pending:
            results = request({'queries': [query for item, query in pending]})['results']
            if len(results) != len(pending):
                raise ValueError('Incomplete OSV batch response')
            following = []
            for (item, query), result in zip(pending, results):
                if 'error' in result:
                    raise ValueError('OSV query failed')
                key = item['name'] + ':' + item['version']
                for vulnerability in result.get('vulns', []):
                    findings.setdefault(key, set()).add(vulnerability['id'])
                if token := result.get('next_page_token'):
                    marker = (key, token)
                    if marker in seen:
                        raise ValueError('OSV repeated a pagination token')
                    seen.add(marker)
                    following.append((item, dict(query, page_token=token)))
            pending = following
    return {key: sorted(ids) for key, ids in sorted(findings.items())}


def main():
    directory = Path('ci-output/dependencies')
    lines = ['## Dependency vulnerability review', '']
    try:
        exceptions = load_exceptions(Path('.github/osv-exceptions.json'))
        packages = json.loads((directory / 'inventory.json').read_text())['packages']
        if not packages:
            raise ValueError('Empty inventory')

        def request(payload):
            req = urllib.request.Request('https://api.osv.dev/v1/querybatch',
                                         data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(req, timeout=60) as response:
                return json.load(response)

        findings = scan(packages, request)
        (directory / 'findings.json').write_text(json.dumps(findings, indent=2) + '\n')
        suppressed = []
        active = {}
        for coordinate, ids in findings.items():
            for advisory in ids:
                if entry := exceptions.get((coordinate, advisory)):
                    suppressed.append(entry)
                else:
                    active.setdefault(coordinate, []).append(advisory)
        (directory / 'triage.json').write_text(json.dumps({
            'active': active, 'suppressed': suppressed,
        }, indent=2) + '\n')
        lines += [f'Resolved Maven coordinates checked: {len(packages)}', f'Coordinates with advisories: {len(findings)}', '']
        for coordinate, ids in findings.items():
            lines.append(f'- `{coordinate}`: ' + ', '.join(f'[{value}](https://osv.dev/vulnerability/{value})' for value in ids))
        lines += ['', f'Unexcepted coordinates: {len(active)}; excepted advisory matches: {len(suppressed)}']
        for entry in suppressed:
            # Escape repository-authored prose before putting it into Markdown.
            reason = entry['reason'].replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            reason = reason.replace('`', '&#96;').replace('\n', ' ').replace('\r', ' ')
            lines.append(f"- Excepted `{entry['id']}` for `{entry['coordinate']}` until {entry['expires']} UTC: {reason}")
        lines += ['', 'Unexcepted advisories fail this check; exceptions require a reason and a future expiry. Dependency pins are unchanged.',
                  'No advisory match is not proof of safety. Embedded native component versions, non-Maven build tooling and unreported vulnerabilities remain outside this coordinate scan.']
        if active:
            raise SystemExit(1)
    except Exception:
        lines.append('Review incomplete: inventory/resolution/OSV access failed; this is not a clean scan.')
        raise
    finally:
        directory.mkdir(parents=True, exist_ok=True)
        text = '\n'.join(lines) + '\n'
        (directory / 'results.md').write_text(text)
        if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
            with Path(summary).open('a') as stream:
                stream.write(text)


if __name__ == '__main__':
    main()
