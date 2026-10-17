#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Package an exact local release and allowlisted corresponding source."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
import shutil

ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--release', required=True, type=Path)
    p.add_argument('--out', default=Path('dist'), type=Path)
    a = p.parse_args()
    release = json.loads((a.release/'release.json').read_text(encoding='utf-8'))
    apks = ['PFDMetricsHUD.apk', 'PFDMetricsTool.apk']
    for name in apks:
        actual = hashlib.sha256((a.release/name).read_bytes()).hexdigest()
        if actual != release['sha256'][name]:
            raise SystemExit('APK checksum mismatch: '+name)
    source = [ROOT/x for x in ['README.md','BUILDING.md','TESTING.md','LICENSE',
        '.gitignore','.gitattributes','build.py','manifest-hud.xml','manifest-ui.xml','release-config.json',
        'installer/setup.cmd','installer/setup.ps1','scripts/package.py','tests/test_installer.py','tests/test_update_policy.py','tests/UpdatePolicyTest.java','tests/DeviceUpdateTest.java']]
    source += sorted((ROOT/'src').rglob('*.java'))
    source += sorted(x for x in (ROOT/'res').rglob('*') if x.is_file())
    a.out.mkdir(parents=True, exist_ok=True)
    base = 'PFD-Metrics-'+release['version']
    outputs = []
    for name in apks+['release.json']:
        target = a.out/name
        shutil.copy2(a.release/name, target)
        outputs.append(target)
    for kind in ['Source','Windows']:
        target = a.out/(base+'-'+kind+'.zip')
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
            for f in source:
                rel = f.relative_to(ROOT).as_posix()
                z.write(f, ('source/' if kind=='Windows' else '')+rel)
            if kind=='Windows':
                for name in apks+['release.json']:
                    z.write(a.release/name,name)
                for name in ['setup.cmd','setup.ps1']:
                    z.write(ROOT/'installer'/name,name)
                for name in ['README.md','LICENSE']:
                    z.write(ROOT/name,name)
        outputs.append(target)
        print(target.resolve())
    (a.out/'SHA256SUMS.txt').write_text(''.join(
        hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in outputs),encoding='ascii')

if __name__ == '__main__':
    main()
