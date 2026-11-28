"""Parse Android 6 owner and process evidence for the hosted emulator helpers."""

import re


def legacy_single_owner(listing):
    # Marshmallow has no `am get-current-user`. Its only registered, running user must be 0.
    return re.fullmatch(r'Users:[ \t]*\r?\n[ \t]*UserInfo\{0:[^}\r\n]+\}[ \t]+running\s*', listing.strip()) is not None


def legacy_process_ids(listing, name):
    rows = listing.splitlines()
    columns = rows[0].split() if rows else []
    if columns[:2] != ['USER', 'PID'] or columns[-1] not in ('NAME', 'CMD', 'COMMAND', 'ARGS'):
        raise ValueError('Unrecognized Android 6 process listing')
    matches = []
    for row in rows[1:]:
        # Marshmallow toolbox prints an unlabeled process-state column before NAME.
        fields = row.split()
        if len(fields) >= len(columns) - 1 and fields[-1].rsplit('/', 1)[-1] == name:
            pid = fields[1]
            if not pid.isdecimal() or int(pid) <= 0:
                raise ValueError('Invalid PID in Android 6 process listing')
            matches.append(pid)
    return matches
