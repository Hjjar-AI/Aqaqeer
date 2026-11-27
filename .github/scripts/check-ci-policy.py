"""Enforce public CI safeguards and the single scheduled cleanup exception."""

from pathlib import Path
import json
import re

import yaml

from ci_yaml import load_yaml


PUBLIC_GUARD = '${{ github.event.repository.private == false }}'
CLEANUP_GUARD = "${{ github.event.repository.private == false && github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}"
PIN = re.compile(r'[\w.-]+/[\w.-]+(?:/[\w./-]+)?@[0-9a-f]{40}')
DEPENDENCY_CACHE_PATHS = '~/.gradle/caches/modules-2/files-2.1\n~/.gradle/caches/modules-2/metadata-2.*\n'
DEPENDENCY_CACHE_PREFIX = 'aqaqeer-dependencies-v2-${{ runner.os }}-${{ runner.arch }}-'
INSTRUMENTATION_CACHE_INPUT = "${{ matrix.check == 'debug-and-instrumentation-compilation' && 'true' || 'false' }}"
INSTRUMENTATION_CACHE_SEGMENT = "${{ inputs.instrumentation-cache == 'true' && 'instrumentation-' || '' }}"
DEPENDENCY_RESTORE_KEYS = (
    "${{ inputs.instrumentation-cache == 'true' && format('aqaqeer-dependencies-v2-{0}-{1}-instrumentation-', runner.os, runner.arch) || '' }}\n"
    "${{ inputs.instrumentation-cache == 'true' && format('aqaqeer-dependencies-v2-{0}-{1}-45591154331-1-verify-2', runner.os, runner.arch) || '' }}\n"
    + DEPENDENCY_CACHE_PREFIX + '\n'
)
WRAPPER_CACHE_PATH = '~/.gradle/wrapper/dists'
WRAPPER_RESTORE_GUARD = "${{ inputs.setup-gradle == 'true' }}"
WRAPPER_DEFAULT_BRANCH = "inputs.setup-gradle == 'true' && github.ref == format('refs/heads/{0}', github.event.repository.default_branch)"
WRAPPER_PROVISION_GUARD = "${{ " + WRAPPER_DEFAULT_BRANCH + " && steps.restore-wrapper.outputs.cache-hit != 'true' }}"
WRAPPER_SAVE_GUARD = "${{ " + WRAPPER_DEFAULT_BRANCH + " && steps.provision-wrapper.outcome == 'success' }}"


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
                if not isinstance(step['run'], str) or '${{' in step['run']:
                    problem(label, 'shell bodies must receive expressions through env, never inline')
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
                        if directory.name == 'setup-android':
                            local_steps = runs.get('steps')
                            if not isinstance(local_steps, list):
                                local_steps = []
                            setup = next((item for item in local_steps
                                          if isinstance(item, dict) and str(item.get('uses', '')).startswith('gradle/actions/setup-gradle@')), {})
                            if (local.get('inputs', {}).get('setup-gradle', {}).get('default') != 'true'
                                    or setup.get('if') != WRAPPER_RESTORE_GUARD):
                                problem(label, 'shared setup must default Gradle caching on and guard signing opt-out')
                            if local.get('inputs', {}).get('instrumentation-cache', {}).get('default') != 'false':
                                problem(label, 'instrumentation dependency preference must default off')
                            cache_paths = [
                                (item['uses'].split('@', 1)[0], item.get('with', {}).get('path'))
                                for item in local_steps if isinstance(item, dict)
                                and str(item.get('uses', '')).startswith(('actions/cache@', 'actions/cache/restore@', 'actions/cache/save@'))
                                and isinstance(item.get('with', {}), dict)
                            ]
                            expected_paths = {
                                ('actions/cache/restore', WRAPPER_CACHE_PATH), ('actions/cache/save', WRAPPER_CACHE_PATH),
                                ('actions/cache', DEPENDENCY_CACHE_PATHS), ('actions/cache/restore', DEPENDENCY_CACHE_PATHS),
                            }
                            if len(cache_paths) != len(expected_paths) or any(
                                    cache_path not in cache_paths for cache_path in expected_paths):
                                problem(label, 'shared setup requires separate wrapper and dependency restore/save pairs')
                            wrapper_stages = [
                                item for item in local_steps if isinstance(item, dict) and (
                                    item.get('id') in ('restore-wrapper', 'provision-wrapper') or
                                    str(item.get('uses', '')).startswith('actions/cache/save@'))
                            ]
                            if (len(wrapper_stages) != 3
                                    or wrapper_stages[0].get('id') != 'restore-wrapper'
                                    or wrapper_stages[1].get('id') != 'provision-wrapper'
                                    or wrapper_stages[1].get('run') != './gradlew --version'
                                    or wrapper_stages[1].get('if') != WRAPPER_PROVISION_GUARD
                                    or not str(wrapper_stages[2].get('uses', '')).startswith('actions/cache/save@')):
                                problem(label, 'restore, provision and save the verified wrapper in order before build tasks')
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
            elif name in ('actions/cache', 'actions/cache/restore', 'actions/cache/save') and stack and stack[-1].name == 'setup-android':
                if inputs.get('path') == WRAPPER_CACHE_PATH:
                    expected_guard = WRAPPER_SAVE_GUARD if name == 'actions/cache/save' else WRAPPER_RESTORE_GUARD
                    if step.get('if') != expected_guard or (name == 'actions/cache/restore' and step.get('id') != 'restore-wrapper'):
                        problem(label, 'wrapper caching must save only after successful default-branch provisioning')
                    expected_key = ("aqaqeer-gradle-wrapper-v1-${{ runner.os }}-${{ runner.arch }}-"
                                    "${{ hashFiles('gradle/wrapper/gradle-wrapper.properties') }}")
                    if inputs.get('key') != expected_key or 'restore-keys' in inputs:
                        problem(label, 'wrapper cache must be keyed by the exact wrapper properties')
                elif inputs.get('path') == DEPENDENCY_CACHE_PATHS:
                    expected_guard = ("${{ inputs.setup-gradle == 'true' && github.ref "
                                      + ('==' if name == 'actions/cache' else '!=')
                                      + " format('refs/heads/{0}', github.event.repository.default_branch) }}")
                    if step.get('if') != expected_guard:
                        problem(label, 'dependency caches must preserve default-branch-only writes and signing opt-out')
                    expected_key = (DEPENDENCY_CACHE_PREFIX + INSTRUMENTATION_CACHE_SEGMENT
                                    + '${{ github.run_id }}-${{ github.run_attempt }}-'
                                    + '${{ github.job }}-${{ strategy.job-index }}')
                    if inputs.get('key') != expected_key or inputs.get('restore-keys') != DEPENDENCY_RESTORE_KEYS:
                        problem(label, 'dependency caches must prefer the instrumentation scope and verified seed when requested')
                    if name == 'actions/cache' and step.get('env') != {
                            'TAR_OPTIONS': '--exclude=*.lock --exclude=gc.properties'}:
                        problem(label, 'dependency archives must exclude lock files and cleanup markers')
                else:
                    problem(label, 'Gradle cache paths must be limited to wrapper distributions or downloaded dependencies')
            elif name in ('actions/cache', 'actions/cache/restore', 'actions/cache/save'):
                expected_guard = ("${{ github.ref_name " + ('==' if name == 'actions/cache' else '!=')
                                  + " github.event.repository.default_branch }}")
                expected_key = ('aqaqeer-system-image-v1-${{ runner.os }}-${{ runner.arch }}-'
                                'api${{ inputs.api-level }}-default-x86_64')
                if (name == 'actions/cache/save' or not stack or stack[-1].name != 'cache-system-image'
                        or inputs.get('path') != '${{ steps.image.outputs.path }}'
                        or inputs.get('key') != expected_key or 'restore-keys' in inputs
                        or step.get('if') != expected_guard):
                    problem(label, 'system-image cache must use exact image keys and default-branch-only writes')
            elif name == 'gradle/actions/setup-gradle':
                if inputs.get('cache-provider') != 'external':
                    problem(label, 'Gradle tooling must leave caching to the explicit wrapper and dependency actions')
                if inputs.get('validate-wrappers', True) is not True:
                    problem(label, 'built-in wrapper-JAR validation must stay enabled')
                for key in ('cache-write-only', 'cache-disabled'):
                    if key in inputs and inputs[key] is not False:
                        problem(label, f'{key} must not override cache policy')

    try:
        workflow = load_yaml(path)
        def check_secrets(value, trail=()):
            if isinstance(value, dict):
                for key, child in value.items():
                    check_secrets(child, (*trail, key))
            elif isinstance(value, list):
                for index, child in enumerate(value):
                    check_secrets(child, (*trail, index))
            elif isinstance(value, str) and 'secrets.' in value:
                approved = False
                if len(trail) == 6 and trail[0] == 'jobs' and trail[2] == 'steps' and trail[4] == 'env':
                    job_name, index, key = trail[1], trail[3], trail[5]
                    step = workflow['jobs'][job_name]['steps'][index]
                    production = path.name == 'publish-release.yml' and job_name == 'publish'
                    debug = path.name in ('android.yml', 'publish-debug.yml') and job_name == 'sign-debug'
                    prefix = 'AQAQEER_RELEASE_' if production else 'AQAQEER_DEBUG_'
                    expected_run = 'python3 tools/sign_release.py --output-directory release-files' if production else 'python3 tools/sign_debug.py "$CI_BUILD_FLAVOR"'
                    allowed = {prefix + suffix for suffix in ('KEYSTORE_BASE64', 'KEY_ALIAS', 'STORE_PASSWORD', 'KEY_PASSWORD')}
                    approved = (production or debug) and step.get('run') == expected_run and key in allowed and value == '${{ secrets.' + key + ' }}'
                if not approved:
                    problem('/'.join(map(str, trail)), 'secret reference outside an approved isolated signing env')
        check_secrets(workflow)
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
        dispatch = triggers.get('workflow_dispatch') or {}
        for input_name, spec in dispatch.get('inputs', {}).items():
            context = f'{path.name}/input {input_name}'
            description = spec.get('description', '')
            if not isinstance(description, str) or not description.strip():
                problem(context, 'input hint must explain accepted values')
                continue
            options = spec.get('options', []) if spec.get('type') == 'choice' else (
                ['true', 'false'] if spec.get('type') == 'boolean' else [])
            for option in options:
                if not re.search(r'(?<![\w-])' + re.escape(str(option)) + r'(?![\w-])', description):
                    problem(context, 'input hint must list every allowed option')
            if 'default' in spec:
                default = spec['default']
                label = 'blank' if default == '' else str(default).lower() if isinstance(default, bool) else str(default)
                if not re.search(r'default:\s*' + re.escape(label) + r'(?![\w-])', description, re.I):
                    problem(context, 'input hint must state its configured default')

        jobs = workflow.get('jobs')
        if not isinstance(jobs, dict) or not jobs:
            return errors + ['nonempty jobs mapping required']
        expected_jobs = {
            'android.yml': {'reserve', 'build-debug', 'sign-debug'},
            'publish-debug.yml': {'reserve', 'build-debug', 'sign-debug', 'publish'},
            'publish-release.yml': {'reserve', 'build', 'publish'},
        }
        if path.name in expected_jobs and set(jobs) != expected_jobs[path.name]:
            problem(path.name, 'distribution jobs must preserve reservation/build/signing boundaries')
        if path.name == 'verification.yml':
            scope = dispatch.get('inputs', {}).get('scope', {})
            if (scope.get('type') != 'choice' or scope.get('default') != 'all'
                    or scope.get('options') != ['all', 'jvm'] or scope.get('required') is not True):
                problem(path.name, 'verification requires explicit all/default or jvm scope')
            selection = jobs.get('verify', {}).get('strategy', {}).get('matrix', {}).get('include', '')
            match = re.fullmatch(r"\$\{\{ fromJSON\(inputs.scope == 'jvm' && '([^']+)' \|\| '([^']+)'\) \}\}", selection)
            if not match:
                problem(path.name, 'verification matrix must select the fixed JVM/all job lists')
            else:
                jvm, all_checks = (json.loads(value) for value in match.groups())
                if ([entry.get('tasks') for entry in jvm] != [':app:testStandardDebugUnitTest', ':app:testDemoDebugUnitTest']
                        or any(entry.get('kind') != 'unit' for entry in jvm)
                        or all_checks[:2] != jvm or len(all_checks) != 4
                        or any(entry.get('kind') != 'compile' for entry in all_checks[2:])):
                    problem(path.name, 'JVM scope must exclude compilation-only jobs and retain both flavors')
        if path.name == 'cleanup-debug.yml':
            dispatch = triggers.get('workflow_dispatch', {})
            inputs = dispatch.get('inputs', {}) if isinstance(dispatch, dict) else {}
            if (set(inputs) != {'preview', 'older_than_days', 'keep'}
                    or inputs['preview'].get('default') is not True
                    or inputs['preview'].get('type') != 'boolean'
                    or inputs['older_than_days'].get('default') != 30
                    or inputs['keep'].get('default') != 5):
                problem(path.name, 'debug cleanup must default to preview, thirty days and keeping five')
        if path.name == 'secret-history.yml':
            steps = [step for job in jobs.values() for step in job.get('steps', [])]
            checkouts = [step for step in steps if str(step.get('uses', '')).startswith('actions/checkout@')]
            if len(checkouts) != 1 or checkouts[0].get('with', {}).get('fetch-depth') != 0:
                problem(path.name, 'history scan must fetch full history')
            if any(str(step.get('uses', '')).startswith('actions/upload-artifact@') for step in steps):
                problem(path.name, 'history findings must not be uploaded as public artifacts')
            if [step['run'] for step in steps if 'run' in step] != ['python3 tools/scan_history.py --install --hosted']:
                problem(path.name, 'history scan output must stay behind the redacting entrypoint')
        for name, job in jobs.items():
            if not isinstance(job, dict):
                problem(name, 'job must be a mapping')
                continue
            cleanup = path.name == 'cleanup-evidence.yml' and name == 'cleanup'
            default_only = path.name in ('android.yml', 'publish-debug.yml', 'publish-release.yml', 'emulator.yml', 'recovery.yml', 'release.yml', 'build-tooling.yml', 'secret-history.yml', 'cleanup-debug.yml', 'actionlint-updates.yml', 'recovery.yml')
            expected_guard = CLEANUP_GUARD if cleanup or default_only else PUBLIC_GUARD
            if path.name == 'emulator.yml':
                steps = job.get('steps', [])
                selectors = [step for step in steps if step.get('id') == 'selection']
                suite = 'demo' if name == 'emulator' else '"$CI_STANDARD_SUITE"'
                if len(selectors) != 1 or selectors[0].get('run') != f'python3 .github/scripts/run-emulator-checks.py --suite {suite} --selection-only':
                    problem(name, 'emulator selection must be validated before Android tooling')
                for step in steps:
                    guarded = (step.get('name', '').startswith(('Set up Android', 'Package ', 'Enable runner KVM', 'Run selected tests', 'Run offline standard')))
                    if guarded and step.get('if') != "${{ steps.selection.outputs.has_checks == 'true' }}":
                        problem(name, 'empty emulator batches must skip Android tooling and device setup')
            if path.name == 'emulator.yml' and name == 'emulator':
                expected_guard = "${{ inputs.demo_checks && github.event.repository.private == false && github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}"
                dispatch = triggers.get('workflow_dispatch', {})
                inputs = dispatch.get('inputs', {}) if isinstance(dispatch, dict) else {}
                if inputs.get('demo_retry_methods') != {
                    'description': 'Optional comma-separated exact Class#method selectors from the demo offline allowlist, without spaces or duplicates. Default: blank = all demo checks. Applies only when demo_checks=true, on both API 29 and 35.',
                    'type': 'string', 'required': False, 'default': '',
                } or job.get('env', {}).get('CI_DEMO_RETRY_METHODS') != '${{ inputs.demo_retry_methods }}':
                    problem(name, 'demo retries must retain the exact optional allowlist input and environment')
            if path.name == 'emulator.yml' and name == 'standard-functional':
                expected_guard = "${{ !cancelled() && inputs.standard_functional && github.event.repository.private == false && github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}"
                dispatch = triggers.get('workflow_dispatch', {})
                inputs = dispatch.get('inputs', {}) if isinstance(dispatch, dict) else {}
                if inputs.get('standard_functional') != {
                    'description': 'Options: false = omit standard batches; true = run 253 offline standard checks in four serialized API-35 batches after selected demo checks. Default: false.',
                    'type': 'boolean', 'default': False,
                } or job.get('needs') != 'emulator':
                    problem(name, 'standard functional checks must be opt-in and wait for the demo emulators')
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
            elif path.name in ('android.yml', 'publish-debug.yml', 'publish-release.yml') and name == 'reserve':
                allowed = {'contents': 'write'}
            elif path.name in ('publish-debug.yml', 'publish-release.yml') and name == 'publish':
                allowed = {'contents': 'write', 'actions': 'read'}
            elif path.name == 'cleanup-debug.yml' and name == 'cleanup':
                allowed = {'contents': 'write'}
            elif name == 'sign-debug' and path.name in ('android.yml', 'publish-debug.yml') or path.name == 'release.yml' and name == 'startup':
                allowed['actions'] = 'read'
            elif path.name == 'security.yml' and name == 'codeql':
                allowed['security-events'] = 'write'
            if 'permissions' in job and job['permissions'] != allowed:
                problem(name, 'unexpected job permissions')
            if (len(allowed) > 1 or allowed.get('contents') == 'write') and job.get('permissions') != allowed:
                problem(name, 'required scoped job permissions missing')
            if path.name == 'publish-debug.yml' and name == 'publish' and job.get('needs') != ['reserve', 'sign-debug']:
                problem(name, 'publication must depend on all selected debug builds succeeding')
            if path.name in ('publish-debug.yml', 'publish-release.yml') and name != 'reserve':
                if '${{ needs.reserve.outputs.code }}' not in job.get('name', ''):
                    problem(name, 'distribution job names must show the selected version code')
            if path.name == 'publish-release.yml':
                expected_dependencies = {'reserve': None, 'build': 'reserve', 'publish': ['reserve', 'build']}
                if name not in expected_dependencies or job.get('needs') != expected_dependencies.get(name):
                    problem(name, 'production jobs must reserve, build, then sign/publish in order')
                if name == 'publish' and job.get('environment') != 'release-signing':
                    problem(name, 'production signing must use the release-signing environment')
                if name == 'reserve':
                    version = dispatch.get('inputs', {}).get('version_code', {})
                    if (version.get('type') != 'string' or version.get('required') is not False
                            or version.get('default') != ''):
                        problem(name, 'production version override must be optional with blank automatic selection')
                    if job.get('outputs', {}).get('code') != '${{ steps.reserve.outputs.code }}':
                        problem(name, 'production reservation must expose the selected code')
                if name in ('build', 'publish'):
                    expected_code = '${{ needs.reserve.outputs.code }}'
                    required_keys = ['AQAQEER_MIN_BUILD_CODE'] if name == 'build' else ['AQAQEER_EXPECTED_RELEASE_CODE', 'CI_RELEASE_CODE']
                    for key in required_keys:
                        values = [step.get('env', {})[key] for step in job.get('steps', []) if key in step.get('env', {})]
                        if values != [expected_code]:
                            problem(name, f'{key} must use the selected reservation output')
                if name == 'publish':
                    publication_steps = [step for step in job.get('steps', []) if 'gh release create' in step.get('run', '')]
                    artifact_steps = [step for step in job.get('steps', []) if step.get('with', {}).get('name') == 'production-signed-${{ github.run_id }}']
                    if len(publication_steps) != 1 or publication_steps[0].get('if') != "${{ inputs.publication != 'artifacts' }}":
                        problem(name, 'artifact-only production mode must skip GitHub Release creation')
                    if len(artifact_steps) != 1 or artifact_steps[0].get('if') != "${{ inputs.publication == 'artifacts' }}":
                        problem(name, 'artifact-only production mode must retain the signed outputs')
            if 'uses' in job:
                problem(name, 'reusable workflow jobs are forbidden')
            reservation = path.name in ('android.yml', 'publish-debug.yml', 'publish-release.yml') and name == 'reserve'
            if reservation:
                if job.get('concurrency') != {'group': 'aqaqeer-version-reservations', 'cancel-in-progress': False}:
                    problem(name, 'all distributed builds require shared reservation serialization')
            elif 'concurrency' in job:
                problem(name, 'only reservation jobs may add shared job serialization')
            if path.name in ('android.yml', 'publish-debug.yml'):
                if name == 'build-debug' and job.get('needs') != 'reserve':
                    problem(name, 'debug builds must depend on reservation')
                if name == 'sign-debug' and (job.get('needs') != ['reserve', 'build-debug'] or job.get('environment') != 'debug-signing'):
                    problem(name, 'debug signing must be isolated after reservation/build in debug-signing')
            job_steps = job.get('steps', [])
            for index, step in enumerate(job_steps):
                if not isinstance(step, dict) or not str(step.get('uses', '')).startswith('reactivecircus/android-emulator-runner@'):
                    continue
                image_cache = job_steps[index - 1] if index else {}
                image_inputs = step.get('with', {})
                if (image_cache.get('uses') != './.github/actions/cache-system-image'
                        or str(image_cache.get('with', {}).get('api-level')) != str(image_inputs.get('api-level'))
                        or image_cache.get('if') != step.get('if')
                        or image_inputs.get('target') != 'default' or image_inputs.get('arch') != 'x86_64'
                        or image_inputs.get('force-avd-creation') != 'true'
                        or '-no-snapshot' not in image_inputs.get('emulator-options', '').split()):
                    problem(name, 'emulators require matching image-only caches and fresh AVDs without snapshots')
            for step in job.get('steps', []):
                if not isinstance(step, dict):
                    continue
                command = step.get('run', '')
                if path.name in ('release.yml', 'publish-release.yml') and ':app:assembleStandardRelease' in command:
                    if step.get('env', {}).get('AQAQEER_CI_RETRY_LOG') != 'ci-output/release/gradle-release-retry.log':
                        problem(name, 'release packaging retries must occur after the wrapper reserves its code')
                if path.name in ('dependencies.yml', 'quality.yml', 'security.yml') and './gradlew ' in command:
                    problem(name, 'non-packaging Gradle checks must use the bounded dependency retry helper')
            signing_only = ((path.name in ('android.yml', 'publish-debug.yml') and name == 'sign-debug')
                            or (path.name == 'publish-release.yml' and name == 'publish'))
            for step in job.get('steps', []):
                if isinstance(step, dict) and step.get('uses') == './.github/actions/setup-android':
                    expected = 'false' if signing_only else 'true'
                    if step.get('with', {}).get('setup-gradle', 'true') != expected:
                        problem(name, 'Gradle cache opt-out is restricted to signing-only jobs')
                    expected_cache = INSTRUMENTATION_CACHE_INPUT if path.name == 'verification.yml' and name == 'verify' else 'false'
                    if step.get('with', {}).get('instrumentation-cache', 'false') != expected_cache:
                        problem(name, 'only workflow 03 Android test compilation may prefer the instrumentation cache')
            steps_check(job.get('steps'), name)
            if path.name in ('android.yml', 'publish-debug.yml') and name == 'build-debug':
                steps = job.get('steps', [])
                commands = [step for step in steps if isinstance(step, dict) and step.get('id') == 'build']
                expected = ('set -euo pipefail\n'
                            'python3 tools/build_debug.py "$CI_BUILD_FLAVOR" --no-export \\\n'
                            '  2>&1 | tee ci-output/build/gradle-build.log\n')
                if (len(commands) != 1 or commands[0].get('run') != expected
                        or commands[0].get('env') != {'CI_BUILD_FLAVOR': '${{ matrix.flavor }}', 'AQAQEER_MIN_BUILD_CODE': '${{ needs.reserve.outputs.code }}', 'AQAQEER_CI_RETRY_LOG': 'ci-output/build/gradle-build-retry.log'}):
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
                    'older_than_days': {'description': 'Integer from 0 to 365; 0 = all completed runs, 1-365 = completed runs older than that many days. Default: 7.',
                                        'type': 'number', 'default': 7, 'required': True},
                    'scope': {'description': 'Options: all-workflows = every workflow including Dependabot; emulator = workflow 07 only. Default: all-workflows.',
                              'type': 'choice', 'options': ['all-workflows', 'emulator'], 'default': 'all-workflows'},
                    'dry_run': {'description': 'Options: true = preview only; false = delete eligible completed runs, logs and every attached artifact, including APKs. Default: true.',
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
