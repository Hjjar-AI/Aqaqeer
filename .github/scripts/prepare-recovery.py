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


def configure_avd(avd_home):
    directory = avd_home / 'aqaqeer-recovery.avd'
    config = directory / 'config.ini'
    if directory.is_symlink() or config.is_symlink() or not config.is_file():
        raise ValueError('Expected the owned newly created recovery AVD configuration')
    before = config.read_text()
    lines = before.splitlines()
    values = [line.split('=', 1)[1].strip() for line in lines if line.startswith('AvdId=')]
    if values != ['aqaqeer-recovery']:
        raise ValueError('Unexpected recovery AVD identity')
    if sum(line.startswith('vm.heapSize=') for line in lines) > 1:
        raise ValueError('Ambiguous AVD heap setting')
    # The pinned runner writes hw.heapSize, which is not the emulator's vm.heapSize key.
    lines = [line for line in lines if not line.startswith(('vm.heapSize=', 'hw.heapSize='))]
    config.write_text('\n'.join(lines + ['vm.heapSize=384']) + '\n')
    return {'avdId': values[0], 'configuredVmHeapSizeMiB': 384,
            'previousHeapSettings': [line for line in before.splitlines() if line.startswith(('vm.heapSize=', 'hw.heapSize='))]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--run', action='store_true')
    mode.add_argument('--configure-avd', action='store_true')
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise ValueError('GitHub-hosted runner required')
    output = ROOT / 'ci-output/recovery'
    output.mkdir(parents=True, exist_ok=True)
    if args.configure_avd:
        receipt = configure_avd(Path(os.environ['ANDROID_AVD_HOME']))
        (output / 'avd-heap.json').write_text(json.dumps(receipt, indent=2) + '\n')
        return
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
