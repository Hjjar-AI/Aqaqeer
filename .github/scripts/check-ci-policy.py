"""Enforce public CI safeguards and the single scheduled cleanup exception."""

from pathlib import Path
import re

import yaml

from ci_yaml import load_yaml


CACHE_READ_ONLY = '${{ github.ref_name != github.event.repository.default_branch }}'
PUBLIC_GUARD = '${{ github.event.repository.private == false }}'
CLEANUP_GUARD = "${{ github.event.repository.private == false && github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}"
PIN = re.compile(r'[\w.-]+/[\w.-]+(?:/[\w./-]+)?@[0-9a-f]{40}')


def check(path):
    root = Path.cwd().resolve()
    errors = []

    def problem(context, message):
        errors.append(context + ': ' + message)

    def steps_check(steps, context, stack=(), checkout=False):
        if not isinstance(steps, list) or not steps:
            problem(context, 'nonempty steps list required')
            return
        for index, step in enumerate(steps, 1):
            label = f'{context}/step {index}'
            if not isinstance(step, dict) or ('run' in step) == ('uses' in step):
                problem(label, 'exactly one of run/uses required')
                continue
            if 'run' in step:
                continue
            action = step['uses']
            inputs = step.get('with', {})
            if not isinstance(action, str) or not isinstance(inputs, dict):
                problem(label, 'invalid action/inputs')
                continue
            if action.startswith('./'):
                directory = (root / action).resolve()
                if not directory.is_relative_to(root / '.github/actions'):
                    problem(label, 'local action must stay inside .github/actions')
                    continue
                candidates = [directory / name for name in ('action.yml', 'action.yaml')
                              if (directory / name).is_file()]
                if len(candidates) != 1 or directory in stack:
                    problem(label, 'missing/ambiguous/recursive local action')
                    continue
                if not candidates[0].resolve().is_relative_to(directory):
                    problem(label, 'local action manifest escapes its directory')
                    continue
                if not checkout:
                    problem(label, 'local action requires a prior checkout')
                try:
                    local = load_yaml(candidates[0])
                    runs = local.get('runs', {})
                    if not isinstance(runs, dict) or runs.get('using') != 'composite':
                        problem(label, 'only local composite actions are allowed')
                    else:
                        steps_check(runs.get('steps'), str(candidates[0].relative_to(root)),
                                    (*stack, directory), checkout=True)
                except (OSError, ValueError, yaml.YAMLError) as error:
                    problem(label, f'invalid local action ({type(error).__name__})')
                continue
            if not PIN.fullmatch(action):
                problem(label, 'remote action must use a full commit pin')
            name = action.split('@', 1)[0].rstrip('/').casefold()
            if name == 'actions/checkout':
                checkout = True
                if inputs.get('persist-credentials') is not False:
                    problem(label, 'checkout must disable persisted credentials')
            elif name == 'actions/upload-artifact':
                if type(inputs.get('retention-days')) is not int or inputs['retention-days'] != 1:
                    problem(label, 'uploads need literal one-day retention')
            elif name == 'gradle/actions/setup-gradle':
                if inputs.get('cache-read-only') != CACHE_READ_ONLY:
                    problem(label, 'cache writes are allowed only on the default branch')
                if inputs.get('cache-provider') != 'basic':
                    problem(label, 'Gradle must use the basic GitHub cache provider')
                for key in ('cache-write-only', 'cache-disabled'):
                    if key in inputs and inputs[key] is not False:
                        problem(label, f'{key} must not override cache policy')

    try:
        workflow = load_yaml(path)
        triggers = workflow.get('on')
        expected_triggers = {'workflow_dispatch', 'schedule'} if path.name == 'cleanup-evidence.yml' else {'workflow_dispatch'}
        if not isinstance(triggers, dict) or set(triggers) != expected_triggers:
            problem(path.name, 'only cleanup may add its approved daily schedule to workflow_dispatch')
        if path.name == 'cleanup-evidence.yml' and isinstance(triggers, dict):
            if triggers.get('schedule') != [{'cron': '23 3 * * *'}]:
                problem(path.name, 'cleanup must use the approved daily 03:23 UTC schedule')
        if path.name in ('android.yml', 'publish-debug.yml') and isinstance(triggers, dict):
            dispatch = triggers.get('workflow_dispatch')
            inputs = dispatch.get('inputs', {}) if isinstance(dispatch, dict) else {}
            flavor = inputs.get('flavor', {})
            expected_inputs = {'flavor', 'publication'} if path.name == 'publish-debug.yml' else {'flavor'}
            if (set(inputs) != expected_inputs or not isinstance(flavor, dict)
                    or flavor.get('type') != 'choice' or flavor.get('required') is not True
                    or flavor.get('default') != 'standard' or flavor.get('options') != ['standard', 'demo', 'both']):
                problem(path.name, 'debug packaging must select standard, demo or both')
            if path.name == 'publish-debug.yml':
                publication = inputs.get('publication', {})
                if (not isinstance(publication, dict) or publication.get('type') != 'choice'
                        or publication.get('required') is not True or publication.get('default') != 'prerelease'
                        or publication.get('options') != ['release', 'prerelease', 'draft']):
                    problem(path.name, 'debug publication must select release, prerelease or draft')
        if workflow.get('permissions') != {'contents': 'read'}:
            problem(path.name, 'global permissions must be contents: read only')
        concurrency = workflow.get('concurrency')
        expected_group = 'cleanup-ci-evidence' if path.name == 'cleanup-evidence.yml' else '${{ github.workflow }}-${{ github.ref }}'
        if concurrency != {'group': expected_group, 'cancel-in-progress': False}:
            problem(path.name, 'non-cancelling workflow concurrency required')
        jobs = workflow.get('jobs')
        if not isinstance(jobs, dict) or not jobs:
            return errors + ['nonempty jobs mapping required']
        for name, job in jobs.items():
            if not isinstance(job, dict):
                problem(name, 'job must be a mapping')
                continue
            cleanup = path.name == 'cleanup-evidence.yml' and name == 'cleanup'
            expected_guard = CLEANUP_GUARD if cleanup or path.name == 'publish-release.yml' else PUBLIC_GUARD
            if job.get('if') != expected_guard:
                problem(name, 'exact public/default-branch guard required')
            if job.get('runs-on') != 'ubuntu-24.04':
                problem(name, 'only standard ubuntu-24.04 runners allowed')
            timeout = job.get('timeout-minutes')
            if type(timeout) is not int or not 1 <= timeout <= 60:
                problem(name, 'literal timeout must be 1–60 minutes')
            allowed = {'contents': 'read'}
            if cleanup:
                allowed['actions'] = 'write'
            elif path.name == 'publish-release.yml' and name == 'reserve':
                allowed = {'contents': 'write'}
            elif path.name in ('publish-debug.yml', 'publish-release.yml') and name == 'publish':
                allowed = {'contents': 'write', 'actions': 'read'}
            elif path.name == 'security.yml' and name == 'codeql':
                allowed['security-events'] = 'write'
            if 'permissions' in job and job['permissions'] != allowed:
                problem(name, 'unexpected job permissions')
            if (len(allowed) > 1 or path.name == 'publish-release.yml' and name == 'reserve') and job.get('permissions') != allowed:
                problem(name, 'required scoped job permissions missing')
            if path.name == 'publish-debug.yml' and name == 'publish' and job.get('needs') != 'build-debug':
                problem(name, 'publication must depend on all selected debug builds succeeding')
            if path.name == 'publish-release.yml':
                expected_dependencies = {'reserve': None, 'build': 'reserve', 'publish': 'build'}
                if name not in expected_dependencies or job.get('needs') != expected_dependencies.get(name):
                    problem(name, 'production jobs must reserve, build, then sign/publish in order')
                if name == 'publish' and job.get('environment') != 'release-signing':
                    problem(name, 'production signing must use the release-signing environment')
            if 'uses' in job:
                problem(name, 'reusable workflow jobs are forbidden')
            if 'concurrency' in job:
                problem(name, 'job concurrency must not override workflow serialization')
            steps_check(job.get('steps'), name)
            if path.name == 'android.yml' or (path.name == 'publish-debug.yml' and name == 'build-debug'):
                steps = job.get('steps', [])
                commands = [step for step in steps if isinstance(step, dict) and step.get('id') == 'build']
                expected = ('set -euo pipefail\n'
                            'python3 tools/build_debug.py "$CI_BUILD_FLAVOR" --no-export \\\n'
                            '  2>&1 | tee ci-output/build/gradle-build.log\n')
                if (len(commands) != 1 or commands[0].get('run') != expected
                        or commands[0].get('env') != {'CI_BUILD_FLAVOR': '${{ matrix.flavor }}'}):
                    problem(name, 'debug packaging must use the shared single-flavor selector')
                expected_matrix = "${{ fromJSON(inputs.flavor == 'both' && '[\"standard\",\"demo\"]' || format('[\"{0}\"]', inputs.flavor)) }}"
                if job.get('strategy') != {'fail-fast': False, 'max-parallel': 1, 'matrix': {'flavor': expected_matrix}}:
                    problem(name, 'both flavors must use independent, serialized matrix jobs without fail-fast')
            if cleanup:
                steps = job.get('steps', [])
                checkouts = [step for step in steps if isinstance(step, dict)
                             and str(step.get('uses', '')).casefold().startswith('actions/checkout@')]
                if len(checkouts) != 1 or checkouts[0].get('with', {}).get('ref') != '${{ github.event.repository.default_branch }}':
                    problem(name, 'cleanup must check out the default branch')
                commands = [step for step in steps if isinstance(step, dict)
                            and step.get('run') == 'python3 .github/scripts/cleanup-ci-evidence.py']
                expected_env = {
                    'CLEANUP_DEFAULT_BRANCH': '${{ github.event.repository.default_branch }}',
                    'GH_TOKEN': '${{ github.token }}',
                    'CLEANUP_EVENT': '${{ github.event_name }}',
                    'CLEANUP_DAYS': "${{ github.event_name == 'schedule' && '7' || format('{0}', inputs.older_than_days) }}",
                    'CLEANUP_SCOPE': "${{ github.event_name == 'schedule' && 'all-workflows' || inputs.scope }}",
                    'CLEANUP_DRY_RUN': "${{ github.event_name == 'schedule' && 'false' || format('{0}', inputs.dry_run) }}",
                }
                if len(commands) != 1 or commands[0].get('env') != expected_env:
                    problem(name, 'cleanup command and event-specific defaults/guards required')
                dispatch = triggers.get('workflow_dispatch') if isinstance(triggers, dict) else None
                required_inputs = {
                    'older_than_days': {'description': 'Delete completed runs older than this many days (0–365; 0 clears all completed runs)',
                                        'type': 'number', 'default': 7, 'required': True},
                    'scope': {'description': 'All workflows (including Dependabot), or emulator only',
                              'type': 'choice', 'options': ['all-workflows', 'emulator'], 'default': 'all-workflows'},
                    'dry_run': {'description': 'Preview only; turn off to delete runs, logs and all attached artifacts (including APKs)',
                                'type': 'boolean', 'default': True},
                }
                if not isinstance(dispatch, dict) or dispatch.get('inputs') != required_inputs:
                    problem(name, 'cleanup needs seven-day/manual-preview defaults and an explicit artifact-deletion warning')
    except (OSError, ValueError, TypeError, AttributeError, yaml.YAMLError) as error:
        problem(path.name, f'invalid workflow structure ({type(error).__name__})')
    return errors


def main():
    paths = sorted(path for path in Path('.github/workflows').iterdir()
                   if path.suffix in ('.yml', '.yaml'))
    errors = [f'{path.name}: {problem}' for path in paths for problem in check(path)]
    if not paths:
        errors.append('No workflow files found')
    for error in errors:
        print(error)
    print(f'CI policy: {len(paths)} workflows, {len(errors)} problems.')
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
