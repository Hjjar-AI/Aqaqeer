"""Lightweight tracked-source integrity and redacted credential-pattern checks."""

import argparse
import ast
import json
import os
from pathlib import Path
import re
import subprocess
import tomllib
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


SECRET_PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{50,255})\b'),
    'telegram-token': re.compile(r'\b[0-9]{6,16}:[A-Za-z0-9_-]{30,}\b'),
    'aws-access-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'openai-key': re.compile(r'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{40,}\b'),
    'google-api-key': re.compile(r'\bAIza[A-Za-z0-9_-]{35}\b'),
    'slack-token': re.compile(r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b|\bxapp-[A-Za-z0-9-]{20,}\b'),
}


def markdown_body(text):
    return re.sub(r'(?ms)^(```|~~~).*?^\1[^\n]*$', '', text)


def anchors(text):
    counts = {}
    values = set(re.findall(r'<a\s+(?:name|id)=["\']([^"\']+)', text))
    for heading in re.findall(r'^#{1,6}\s+(.+?)(?:\s+#+)?$', markdown_body(text), re.M):
        slug = re.sub(r'[^\w\- ]', '', heading.lower().replace('`', '')).replace(' ', '-')
        count = counts.get(slug, 0)
        values.add(slug + (f'-{count}' if count else ''))
        counts[slug] = count + 1
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-only', action='store_true', help='Exclude tests/inventories when test inspection is not authorized')
    args = parser.parse_args()
    root = Path.cwd()
    tracked = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], capture_output=True, check=True).stdout.decode().split('\0')
    problems = []
    links = 0
    files = 0
    for name in sorted(set(filter(None, tracked))):
        if args.source_only and (re.search(r"(^|/)(test|tests|androidTest|sharedTest)(/|$)", name) or name == "docs/test-api-inventory.json"):
            continue
        path = root / name
        # Never inspect private machine state, generated schemas or migration files.
        if name.startswith(('local/', 'ci-output/')):
            problems.append(f'{name}: private/generated directory must not be tracked')
            continue
        if re.search(r'migrat|(^|/)schemas/', name, re.I) or not path.is_file():
            continue
        if path.suffix.lower() in ('.jks', '.keystore', '.p12', '.pfx') or path.name in ('.env', 'keystore.properties', 'telegram-test-config.local.json', 'id_rsa', 'id_ed25519'):
            problems.append(f'{name}: possible private signing/credential file (contents not read)')
            continue
        raw = path.read_bytes()
        if b'\0' in raw:
            continue
        try:
            text = raw.decode('utf-8')
        except UnicodeDecodeError:
            continue
        files += 1
        for rule, pattern in SECRET_PATTERNS.items():
            for match in pattern.finditer(text):
                # Only documented, explicitly fake fixture values may be exempted here.
                # Do not print matched content, source lines, or credential hashes.
                line = text.count('\n', 0, match.start()) + 1
                problems.append(f'{name}:{line}: possible {rule} (value redacted)')
        try:
            if path.suffix == '.json': json.loads(text)
            elif path.suffix == '.toml': tomllib.loads(text)
            elif path.suffix == '.xml': ET.fromstring(text)
            elif path.suffix == '.py': ast.parse(text, filename=name)
        except (ValueError, ET.ParseError, SyntaxError):
            problems.append(f'{name}: malformed {path.suffix} source/configuration')
        if path.suffix != '.md':
            continue
        body = markdown_body(text)
        destinations = re.findall(r'\]\((<[^>]+>|[^)]+)\)', body)
        destinations += re.findall(r'^\s*\[[^\]]+\]:\s*(\S+)', body, re.M)
        for destination in destinations:
            destination = destination.strip().strip('<>')
            destination = re.split(r'\s+["\']', destination, maxsplit=1)[0]
            url = urlsplit(destination)
            if url.scheme or url.netloc:
                continue
            relative = unquote(url.path)
            target = (root / relative.lstrip('/') if relative.startswith('/') else path.parent / relative).resolve()
            if not target.is_relative_to(root):
                problems.append(f'{name}: local link escapes repository')
                continue
            if target.is_relative_to(root / 'local') or target.is_relative_to(root / 'ci-output'):
                continue  # Historical ignored evidence may be absent in fresh checkouts.
            links += 1
            if not target.exists():
                problems.append(f'{name}: missing local link {relative}')
            elif url.fragment and target.suffix == '.md' and unquote(url.fragment) not in anchors(target.read_text()):
                problems.append(f'{name}: missing local anchor {relative}#{url.fragment}')
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
    override = re.findall(r'kotlin-gradle-plugin:([^"\s]+)', (root / 'build.gradle.kts').read_text())
    if override != [catalog['versions']['kotlin']]:
        problems.append('Root Kotlin override must match the version catalog')
    for wrapper in ('gradlew', 'gradlew.bat'):
        if 'build_version.py' not in (root / wrapper).read_text():
            problems.append(f'{wrapper}: missing version-reservation integration')
    lines = ['## Repository integrity', '', f'Text files inspected: {files}; local links checked: {links}',
             f'Problems: {len(problems)}', '', *[f'- {problem}' for problem in problems], '',
             'Credential checks cover selected token/private-key formats only, not arbitrary passwords or all secret types.',
             'Migration/schema contents and ignored machine evidence are excluded. Workflow YAML is validated separately by actionlint.']
    report = '\n'.join(lines) + '\n'
    print(report)
    if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(summary).open('a') as stream:
            stream.write(report)
    if problems:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
