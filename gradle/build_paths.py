"""Managed workstation output paths, with unchanged defaults on CI/other machines."""
import json
import os
from pathlib import Path


def project_work(project):
    registry = Path('/opt/android-shared/projects.json')
    if os.environ.get('GITHUB_ACTIONS') == 'true' or os.environ.get('ANDROID_SHARED_DISABLE') == '1' or not registry.is_file():
        return None
    entry = json.loads(registry.read_text()).get(str(Path(project).resolve()))
    return Path(entry['work']) if entry else None


def app_build(project):
    work = project_work(project)
    return work / 'build/app' if work else Path(project) / 'app/build'
