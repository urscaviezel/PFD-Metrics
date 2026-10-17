#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Offline Android build; no Gradle dependencies or personal machine paths."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent

def run(*args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk', default=os.getenv('ANDROID_SDK_ROOT') or os.getenv('ANDROID_HOME'))
    p.add_argument('--build-tools-dir', help='Defaults to latest SDK build-tools (35+ required)')
    p.add_argument('--jdk', default=os.getenv('JAVA_HOME'))
    p.add_argument('--keystore', required=True, help='Private signing key; NEVER commit it')
    p.add_argument('--alias', required=True)
    p.add_argument('--out', default='build')
    a = p.parse_args()
    if not a.sdk:
        p.error('Set ANDROID_SDK_ROOT or pass --sdk')
    sdk = Path(a.sdk).resolve()
    if a.build_tools_dir:
        bt = Path(a.build_tools_dir).resolve()
    else:
        versions = [x for x in (sdk/'build-tools').iterdir() if all(v.isdigit() for v in x.name.split('.'))]
        bt = max(versions, key=lambda x: tuple(map(int, x.name.split('.'))))
    properties = (bt/'source.properties').read_text()
    revision = next(x.split('=', 1)[1].strip() for x in properties.splitlines() if x.startswith('Pkg.Revision'))
    if int(revision.split('.')[0]) < 35:
        p.error('Android build-tools 35 or newer required (older D8 fails with JDK 21)')
    suffix = '.exe' if os.name == 'nt' else ''
    jdk = Path(a.jdk).resolve() if a.jdk else Path(shutil.which('javac') or '').resolve().parent.parent
    java, javac, jar = [jdk/'bin'/(x+suffix) for x in ('java','javac','jar')]
    android = sdk/'platforms/android-34/android.jar'
    key = Path(a.keystore).resolve()
    for f in [java, javac, jar, android, key, bt/('aapt2'+suffix), bt/('zipalign'+suffix), bt/'lib/d8.jar', bt/'lib/apksigner.jar']:
        if not f.is_file():
            p.error(f'Missing build input: {f}')
    for name in ['PFD_STORE_PASSWORD', 'PFD_KEY_PASSWORD']:
        if not os.getenv(name):
            p.error(f'Set {name} in your local environment')
    out = Path(a.out).resolve()
    # No recursive cleanup: fail if reused to avoid stale compiled classes.
    out.mkdir(parents=True, exist_ok=False)
    for name in ['classes','dex','assets','release']:
        (out/name).mkdir()
    ns = '{http://schemas.android.com/apk/res/android}'
    hud = ET.parse(ROOT/'manifest-hud.xml').getroot()
    ui = ET.parse(ROOT/'manifest-ui.xml').getroot()
    version = hud.get(ns+'versionName')
    if version != ui.get(ns+'versionName'):
        p.error('HUD and UI version names must match')
    config = json.loads((ROOT/'release-config.json').read_text(encoding='utf-8'))
    source_hash = hashlib.sha256()
    inputs = [ROOT/'build.py', ROOT/'manifest-hud.xml', ROOT/'manifest-ui.xml', ROOT/'release-config.json']
    inputs += sorted((ROOT/'src').rglob('*.java'))
    inputs += sorted(x for x in (ROOT/'res').rglob('*') if x.is_file())
    for f in inputs:
        source_hash.update(f.relative_to(ROOT).as_posix().encode()+b'\0'+f.read_bytes()+b'\0')
    info = {'version':version, 'uiVersionCode':int(ui.get(ns+'versionCode')),
        'hudVersionCode':int(hud.get(ns+'versionCode')),
        'builtAt':datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC'),
        'sourceId':source_hash.hexdigest()[:12], 'githubRepository':config['githubRepository']}
    (out/'assets/build-info.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
    run(bt/('aapt2'+suffix), 'compile', '--dir', ROOT/'res', '-o', out/'compiled.zip')
    sources = out/'sources.txt'
    sources.write_text('\n'.join('"'+x.as_posix()+'"' for x in sorted((ROOT/'src').rglob('*.java'))), encoding='utf-8')
    run(javac, '-nowarn', '-Xlint:-options', '-source','8','-target','8','-encoding','UTF-8',
        '-bootclasspath', str(android)+os.pathsep+str(bt/'core-lambda-stubs.jar'),
        '-d', out/'classes', '@'+str(sources))
    with zipfile.ZipFile(out/'classes.jar', 'w', zipfile.ZIP_DEFLATED) as z:
        for file in sorted((out/'classes').rglob('*.class')):
            z.write(file, file.relative_to(out/'classes').as_posix())
    run(java, '-cp', bt/'lib/d8.jar', 'com.android.tools.r8.D8', '--release','--min-api','29',
        '--lib', android, '--output', out/'dex', out/'classes.jar')
    for component in ['HUD','Tool']:
        manifest = ROOT/('manifest-hud.xml' if component == 'HUD' else 'manifest-ui.xml')
        unsigned, aligned = out/(component+'-unsigned.apk'), out/(component+'-aligned.apk')
        signed = out/'release'/('PFDMetrics'+component+'.apk')
        assets = ['-A', out/'assets']
        run(bt/('aapt2'+suffix), 'link', '-I', android, '--manifest', manifest, '-o', unsigned,
            '--min-sdk-version','29','--target-sdk-version','34', *assets, out/'compiled.zip')
        with zipfile.ZipFile(unsigned,'a') as z:
            z.write(out/'dex/classes.dex','classes.dex')
        run(bt/('zipalign'+suffix), '-f','4',unsigned,aligned)
        run(java,'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias',a.alias,
            '--ks-pass','env:PFD_STORE_PASSWORD','--key-pass','env:PFD_KEY_PASSWORD','--out',signed,aligned)
        run(java,'-jar',bt/'lib/apksigner.jar','verify',signed)
        if component == 'HUD':
            shutil.copy2(signed,out/'assets/PFDMetricsHUD.apk')
    release = dict(info, sha256={x.name:hashlib.sha256(x.read_bytes()).hexdigest() for x in (out/'release').glob('*.apk')})
    (out/'release/release.json').write_text(json.dumps(release,indent=2)+'\n',encoding='utf-8')
    print('Verified release:',out/'release')

if __name__ == '__main__':
    main()
