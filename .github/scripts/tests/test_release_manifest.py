"""Offline checks for the packaged permission policy used by release inspection."""

import importlib.util
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('check_release', SCRIPTS / 'check-release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

ANDROID = '{http://schemas.android.com/apk/res/android}'
SCHEDULER_PERMISSIONS = (
    'android.permission.INTERNET',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.RECEIVE_BOOT_COMPLETED',
    'android.permission.WAKE_LOCK',
)
RECEIVER = 'com.aqaqeer.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'


def manifest(permissions=SCHEDULER_PERMISSIONS, protection=None):
    root = ET.Element('manifest', {'package': 'com.aqaqeer.app'})
    for name in permissions:
        ET.SubElement(root, 'uses-permission', {ANDROID + 'name': name})
    if protection is not None:
        ET.SubElement(root, 'permission', {ANDROID + 'name': RECEIVER, ANDROID + 'protectionLevel': protection})
    ET.SubElement(root, 'application', {ANDROID + 'allowBackup': 'false', ANDROID + 'usesCleartextTraffic': 'false'})
    return ET.tostring(root, encoding='unicode')


class ReleaseManifestTest(unittest.TestCase):
    def test_reader_scheduler_permissions_are_required(self):
        self.assertEqual(sorted(SCHEDULER_PERMISSIONS), release.inspect_manifest(manifest()))
        for missing in ('android.permission.RECEIVE_BOOT_COMPLETED', 'android.permission.WAKE_LOCK'):
            with self.subTest(missing=missing), self.assertRaisesRegex(ValueError, 'permission set'):
                release.inspect_manifest(manifest(tuple(name for name in SCHEDULER_PERMISSIONS if name != missing)))

    def test_foreground_service_and_notification_permissions_are_rejected(self):
        for extra in ('android.permission.FOREGROUND_SERVICE', 'android.permission.POST_NOTIFICATIONS'):
            with self.subTest(extra=extra), self.assertRaisesRegex(ValueError, 'permission set'):
                release.inspect_manifest(manifest((*SCHEDULER_PERMISSIONS, extra)))

    def test_optional_androidx_receiver_permission_still_requires_signature_protection(self):
        permissions = (*SCHEDULER_PERMISSIONS, RECEIVER)
        self.assertEqual(sorted(permissions), release.inspect_manifest(manifest(permissions, 'signature')))
        with self.assertRaisesRegex(ValueError, 'lacks its signature declaration'):
            release.inspect_manifest(manifest(permissions))
        with self.assertRaisesRegex(ValueError, 'must be signature-protected'):
            release.inspect_manifest(manifest(permissions, 'normal'))
