import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class UpdatePolicyTests(unittest.TestCase):
    def test_java_policy(self):
        jdk = Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
        javac = str(jdk/'javac.exe') if jdk and os.name == 'nt' else shutil.which('javac')
        java = str(jdk/'java.exe') if jdk and os.name == 'nt' else shutil.which('java')
        self.assertTrue(javac and java, 'JDK required for updater tests')
        work = ROOT/'test-output'
        work.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=work) as tmp:
            subprocess.run([javac,'-d',tmp,str(ROOT/'src/com/pfd/metricstool/UpdatePolicy.java'),
                str(ROOT/'tests/UpdatePolicyTest.java')],check=True)
            subprocess.run([java,'-cp',tmp,'UpdatePolicyTest'],check=True)

if __name__ == '__main__': unittest.main()
