"""Export tracked PUBLIC fixtures for the existing independent JDK verifier."""

import argparse
import base64
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    vectors = json.loads((root / 'docs/crypto/v1-vectors.json').read_text())['vectors']
    lines = []
    for vector in vectors:
        envelope = vector['envelope']
        password = vector['publicTestPassword']
        lines.append('\t'.join([
            vector['domain'],
            base64.b64encode(password.encode('utf-8')).decode('ascii') if password is not None else '-',
            envelope.get('salt', '-'), vector['keyHex'], envelope['nonce'], vector['aadHex'],
            base64.b64encode(vector['plaintextUtf8'].encode('utf-8')).decode('ascii'),
            envelope['ct'], envelope['tag'],
        ]))
    args.destination.write_text('\n'.join(lines) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
