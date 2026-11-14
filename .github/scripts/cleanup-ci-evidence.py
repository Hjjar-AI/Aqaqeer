"""Scheduled/manual completed-run cleanup, including run logs and attached artifacts."""

from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request


def timestamp(value):
    result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('Run timestamp must include a timezone')
    return result


def eligible_run(run, repository_id, cutoff, own_run_id, workflow_id=None):
    return (run['id'] != own_run_id and run['status'] == 'completed'
            and run['repository']['id'] == repository_id
            and (workflow_id is None or run['workflow_id'] == workflow_id)
            and timestamp(run['updated_at']) < cutoff)


def main():
    if os.environ.get('GITHUB_REF') != 'refs/heads/' + os.environ['CLEANUP_DEFAULT_BRANCH']:
        raise SystemExit('Cleanup may only run from the repository default branch.')
    event = os.environ['CLEANUP_EVENT']
    days = int(os.environ['CLEANUP_DAYS'])
    scope = os.environ['CLEANUP_SCOPE']
    dry_value = os.environ['CLEANUP_DRY_RUN']
    if (event not in ('schedule', 'workflow_dispatch') or not 0 <= days <= 365
            or scope not in ('emulator', 'all-workflows') or dry_value not in ('true', 'false')):
        raise SystemExit('Invalid cleanup inputs; days must be 0–365.')
    if event == 'schedule' and (days, scope, dry_value) != (7, 'all-workflows', 'false'):
        raise SystemExit('Scheduled cleanup must retain seven days and target all completed runs.')
    dry_run = dry_value == 'true'
    own_run_id = int(os.environ['GITHUB_RUN_ID'])
    repository = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise SystemExit('Invalid repository.')
    base = 'https://api.github.com/repos/' + repository
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    lines = [f'## Workflow run cleanup ({"preview" if dry_run else "delete"})', '',
             f'Scope: {scope}; cutoff: {cutoff.isoformat()}', '',
             'Deleting a run removes its attempts, logs, summaries and attached artifacts, including APK downloads.',
             'GitHub Releases/assets, repository files, caches and active runs are not deleted.', '']

    def api(path, method='GET', missing_ok=False):
        request = urllib.request.Request(base + path, method=method, headers={
            'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
        })
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise RuntimeError(f'GitHub {method} failed with HTTP {error.code}') from None

    def pages(path):
        # Do not filter the API by status/date: filtered searches have a 1,000-run cap.
        # Snapshot all pages before deletion so our deletions cannot shift pagination.
        items = {}
        page = 1
        while True:
            batch = api(f'{path}?per_page=100&page={page}')['workflow_runs']
            for run in batch:
                items[run['id']] = run
            if len(batch) < 100:
                return list(items.values())
            page += 1

    def confirm_absent(run):
        # GitHub can acknowledge DELETE before GET stops returning the run.
        # Poll reads only: never repeat a destructive request during confirmation.
        for delay in (0, 1, 2, 4, 8, 16):
            if delay:
                time.sleep(delay)
            current = api(f'/actions/runs/{run["id"]}', missing_ok=True)
            if current is None:
                return True
            # A changed attempt/status is not the run state we approved for deletion.
            if (current['id'] != run['id'] or current['repository']['id'] != repository_id
                    or current['workflow_id'] != run['workflow_id']
                    or current['run_attempt'] != run['run_attempt']
                    or current['status'] != 'completed'):
                return False
        return False

    errors = 0
    try:
        repo = api('')
        if repo['private'] or repo['full_name'].casefold() != repository.casefold():
            raise RuntimeError('Cleanup is restricted to the selected public repository.')
        repository_id = repo['id']
        workflow_id = None
        path = '/actions/runs'
        if scope == 'emulator':
            workflow = api('/actions/workflows/emulator.yml', missing_ok=True)
            if workflow is None:
                return
            workflow_id = workflow['id']
            path = f'/actions/workflows/{workflow_id}/runs'
        candidates = pages(path)
        for run in candidates:
            if not eligible_run(run, repository_id, cutoff, own_run_id, workflow_id):
                continue
            run_id = run['id']
            fresh = api(f'/actions/runs/{run_id}', missing_ok=True)
            if (fresh is None or fresh['id'] != run_id
                    or not eligible_run(fresh, repository_id, cutoff, own_run_id, workflow_id)
                    or fresh['workflow_id'] != run['workflow_id']
                    or fresh['run_attempt'] != run['run_attempt']
                    or fresh['updated_at'] != run['updated_at']):
                continue
            if dry_run:
                lines.append(f'- Would delete completed run {run_id} (attempt {fresh["run_attempt"]}).')
            else:
                api(f'/actions/runs/{run_id}', method='DELETE', missing_ok=True)
                if not confirm_absent(fresh):
                    errors += 1
                    lines.append(f'- Deletion requested for run {run_id}, but absence remains unconfirmed; no deletion pass claimed. Continuing with other runs.')
                    continue
                lines.append(f'- Deleted or confirmed absent run {run_id}.')
        lines.append(f'\nRuns listed before eligibility checks: {len(candidates)}')
    except (OSError, RuntimeError, ValueError, KeyError) as error:
        errors += 1
        lines.append(f'- Cleanup incomplete: {type(error).__name__}; inspect step failure.')
        raise
    finally:
        lines.append(f'\nCleanup errors: {errors}')
        text = '\n'.join(lines) + '\n'
        print(text)
        if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
            with Path(summary).open('a') as stream:
                stream.write(text)
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
