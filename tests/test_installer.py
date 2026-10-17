# SPDX-License-Identifier: GPL-3.0-only
"""Windows integration tests of the real installer using a simulated adb process."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MOCK = r'''
import sys, os, json
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['MOCK_LOG'],'a') as f: f.write(json.dumps(a)+'\n')
mode=os.environ['MOCK_MODE']
if a[:1]==['-s']: a=a[2:]
if a==['start-server']:
 print('* daemon started successfully',file=sys.stderr)
elif a==['devices','-l']:
 print('List of devices attached')
 if mode!='none': print('TEST123\t'+('unauthorized' if mode=='unauthorized' else 'offline' if mode=='offline' else 'device')+' product:yvr_d3')
 if mode=='multiple': print('SECOND\tdevice')
elif a[:2]==['shell','getprop']:
 print(('OTHER' if mode=='wrong_model' else 'PFDM_MR' if mode=='underscore_model' else 'PFDM MR') if a[2]=='ro.product.model' else '34')
elif a[:1]==['install']:
 assert a[1:3]==['--no-incremental','-r'] and Path(a[3]).is_file(),a
 if mode=='install_error': print('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]'); sys.exit(1)
 print('Success')
elif a[:3]==['shell','pm','grant']:
 if mode=='grant_error': print('SecurityException: permission denied'); sys.exit(1)
elif a[:3]==['shell','appops','set']: pass
elif a[:3]==['shell','appops','get']:
 print(a[4]+(': default' if mode=='appop_error' else ': allow'))
elif a[:3]==['shell','dumpsys','package']:
 print('versionName=1.0.1')
 for p in ['READ_LOGS','DUMP','POST_NOTIFICATIONS']:
  print('android.permission.'+p+': granted='+('false' if mode=='permission_missing' else 'true'))
elif a[:3]==['shell','am','start']: print('Starting: Intent')
else: print('Unexpected adb command: '+repr(a)); sys.exit(2)
'''

@unittest.skipUnless(os.name == 'nt', 'Windows PowerShell installer')
class InstallerTests(unittest.TestCase):
    def run_case(self, mode, corrupt=False, missing=False, serial=None):
        test_root = ROOT/'test-output'
        test_root.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='PFD setup with spaces ', dir=test_root) as tmp:
            p=Path(tmp)
            assert p.resolve().is_relative_to(test_root.resolve())
            shutil.copy2(ROOT/'installer/setup.ps1',p/'setup.ps1')
            (p/'mock.py').write_text(MOCK)
            (p/'adb.cmd').write_text('@echo off\r\n"'+os.sys.executable+'" "%~dp0mock.py" %*\r\nexit /b %errorlevel%\r\n')
            hashes={}
            for name in ['PFDMetricsHUD.apk','PFDMetricsTool.apk']:
                (p/name).write_bytes(b'test apk '+name.encode())
                hashes[name]=hashlib.sha256((p/name).read_bytes()).hexdigest()
            (p/'release.json').write_text(json.dumps({'version':'1.0.1','sha256':hashes}))
            if corrupt: (p/'PFDMetricsHUD.apk').write_bytes(b'corrupted')
            if missing: (p/'PFDMetricsTool.apk').unlink()
            env=dict(os.environ,MOCK_MODE=mode,MOCK_LOG=str(p/'calls.jsonl'))
            # Do not leak PowerShell 7 module paths into Windows PowerShell 5.1.
            env.pop('PSModulePath',None)
            cmd=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(p/'setup.ps1'),'-AdbPath',str(p/'adb.cmd'),'-NoDownload']
            if serial: cmd+=['-Serial',serial]
            r=subprocess.run(cmd,env=env,capture_output=True,text=True,errors='replace',timeout=30,cwd=p.parent)
            calls=[json.loads(x) for x in (p/'calls.jsonl').read_text().splitlines()] if (p/'calls.jsonl').exists() else []
            return r,calls

    def test_success_and_paths_with_spaces(self):
        r,calls=self.run_case('ok')
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        commands=[x[2:] if x[:1]==['-s'] else x for x in calls]
        self.assertEqual(sum(x[:1]==['install'] for x in commands),2)
        self.assertEqual(sum(x[:3]==['shell','pm','grant'] for x in commands),3)
        self.assertEqual(sum(x[:3]==['shell','appops','set'] for x in commands),4)
        self.assertEqual(commands[-1][:3],['shell','am','start'])

    def test_errors_never_report_success_or_launch(self):
        for mode in ['none','offline','unauthorized','multiple','wrong_model','install_error','grant_error','permission_missing','appop_error']:
            with self.subTest(mode=mode):
                r,calls=self.run_case(mode)
                self.assertNotEqual(r.returncode,0,r.stdout)
                self.assertNotIn('FERTIG:',r.stdout)
                self.assertFalse(any('am' in c and 'start' in c for c in calls))

    def test_corrupt_or_incomplete_release_never_touches_adb(self):
        for opts in [dict(corrupt=True),dict(missing=True)]:
            r,calls=self.run_case('ok',**opts)
            self.assertNotEqual(r.returncode,0)
            self.assertEqual(calls,[])

    def test_explicit_serial_selects_one_of_multiple_devices(self):
        r,calls=self.run_case('multiple',serial='TEST123')
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertTrue(all(c[1]=='TEST123' for c in calls if c[:1]==['-s']))

    def test_underscore_model(self):
        r,_=self.run_case('underscore_model')
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__': unittest.main()

