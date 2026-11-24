"""Prepare a strict scenario allowlist; execute only inside the owned hosted emulator action."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
spec = importlib.util.spec_from_file_location('recovery_campaign', ROOT / 'tools/run_restore_process_death.py')
campaign = importlib.util.module_from_spec(spec)
spec.loader.exec_module(campaign)


def selected_scenarios(value):
    allowed = [case['name'] for case in campaign.scenarios()]
    if value == 'none':
        return []
    if not value:
        return allowed
    requested = value.split(',')
    if (len(set(requested)) != len(requested) or any(not item or item != item.strip() or item not in allowed for item in requested)):
        raise ValueError('Expected unique exact recovery scenario names, without spaces')
    return requested


def selected_resources(value):
    if not value:
        return list(campaign.RESOURCE_METHODS)
    requested = value.split(',')
    if len(set(requested)) != len(requested) or any(item not in campaign.RESOURCE_METHODS for item in requested):
        raise ValueError('Expected unique exact resource method names, without spaces')
    return requested


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', action='store_true')
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise ValueError('GitHub-hosted runner required')
    output = ROOT / 'ci-output/recovery'
    output.mkdir(parents=True, exist_ok=True)
    path = output / 'arguments.json'
    if not args.run:
        resources = os.environ.get('CI_RECOVERY_RESOURCES', 'true')
        if resources not in ('true', 'false'):
            raise ValueError('Explicit resource boolean required')
        if not selected_scenarios(os.environ.get('CI_RECOVERY_SCENARIOS', '')) and resources != 'true':
            raise ValueError('Empty recovery selection requires resources=true')
        path.write_text(json.dumps({'resourceMethods': selected_resources(os.environ.get('CI_RECOVERY_RESOURCE_METHODS', '')), 'scenarios': selected_scenarios(os.environ.get('CI_RECOVERY_SCENARIOS', '')),
                                    'resources': resources == 'true'}, indent=2) + '\n')
        return
    selection = json.loads(path.read_text())
    names = selection['scenarios']
    if (not isinstance(names, list) or selected_scenarios(','.join(names) if names else 'none') != names
            or type(selection['resources']) is not bool or not names and not selection['resources']
            or selected_resources(','.join(selection['resourceMethods'])) != selection['resourceMethods']):
        raise ValueError('Invalid prepared recovery selection')
    command = [sys.executable, str(ROOT / 'tools/run_restore_process_death.py'), '--serial', 'emulator-5554',
               '--hosted-emulator', '--verification-init', str(ROOT / '.github/verification.init.gradle')]
    if selection['resources']:
        command.append('--resource-tests')
        for method in selection['resourceMethods']:
            command += ['--resource-method', method]
    if not names:
        command.append('--resources-only')
    for name in names:
        command += ['--only', name]
    raise SystemExit(subprocess.run(command, cwd=ROOT).returncode)


if __name__ == '__main__':
    main()
