"""Run on a disposable Android emulator only. Replaces its FreeMyTV family settings with fixtures.
Build and install the production APK, EducationTests.apk and PlayerFixture.apk first.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5580')
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'This test must never run on a physical TV'
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / '.local-test'
OUT.mkdir(exist_ok=True)
ADB = str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')

def adb(*cmd):
    return subprocess.check_output([ADB, '-s', args.serial, *map(str,cmd)],encoding='utf-8',errors='replace',timeout=30)

def instrument(action, used=None):
    cmd=['shell','am','instrument','-w','-e','action',action]
    if used is not None: cmd += ['-e','used',str(used)]
    result=adb(*cmd,'io.freemytv.educationtests/io.freemytv.EducationInstrumentation')
    assert 'FAIL:' not in result, result
    return result

def ui():
    output=adb('shell','am','instrument','-w','io.freemytv.educationtests/io.freemytv.EducationUiObserver')
    return ET.fromstring(output.split('UIXML:',1)[1].split('\n',1)[0])

def texts(root): return [n.get('text','') for n in root.iter('node')]

def click(label, root=None):
    root = root if root is not None else ui()
    node=next(n for n in root.iter('node') if n.get('text','').startswith(label) and n.get('class')=='android.widget.Button')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)
    time.sleep(.35)

def number(value):
    adb('shell','input','keyevent',*[7+int(d) for d in str(value)])

def shot(name):
    adb('shell','screencap','-p','/sdcard/education-shot.png')
    adb('pull','/sdcard/education-shot.png',OUT/name)

def wait_text(part, timeout=15):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        root=ui()
        if any(part in s for s in texts(root)): return root
        time.sleep(.3)
    raise AssertionError('Screen missing: '+part)


result=adb('shell','am','instrument','-w','-e','action','seed','-e','used','0','-e','sourceUsed','57000','-e','limitMinutes','1','io.freemytv.educationtests/io.freemytv.EducationInstrumentation')
assert 'FAIL' not in result,result
adb('shell','settings','delete','secure','enabled_accessibility_services');time.sleep(.3)
adb('shell','settings','put','secure','enabled_accessibility_services','io.freemytv/.EducationGuard');time.sleep(1)
adb('shell','am','start','-n','io.freemytv/.HomeActivity')
root=wait_text('What would you like to watch?')
click('YouTube Kids',root)
root=wait_text('Time for a maths adventure!')
assert not any(n.get('class')=='android.widget.ScrollView' for n in root.iter('node'))
for index in range(5):
    root=wait_text('Question %d of 5'%(index+1))
    equation=next(s for s in texts(root) if re.match(r'^\d+ [ +−×]+ \d+ = \?$',s))
    a,op,b=re.match(r'^(\d+) ([+−×]) (\d+) =',equation).groups();a,b=int(a),int(b)
    number(a+b if op=='+' else a-b if op=='−' else a*b);click('OK')
wait_text('Extra time is ready');click('Back to my shows')
root=wait_text('What would you like to watch?')
assert any('3 h 00 min' in s for s in texts(root)), 'per-source exhaustion changed overall budget'
assert any('YouTube Kids\n30 min left'==s for s in texts(root)), texts(root)
click('Parent Mode',root);wait_text('Enter your 4-digit PIN');number(4826);click('OK');wait_text('Parent settings')
shot('education-parent-compact.png')
click('Time limits');wait_text('All apps and consoles combined')
shot('education-limits.png')
click('YouTube Kids · 1 min per day')
root=wait_text('Daily limit in minutes')
node=next(n for n in root.iter('node') if n.get('text')=='Daily limit in minutes')
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')));adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)
wait_text('Enter 1–720 minutes');number(90);click('OK');wait_text('YouTube Kids · 90 min per day')
state=instrument('inspect');assert 'youtubeLimit=5400000' in state,state
used=int(re.search(r'used=(\d+)',state).group(1));assert 0<used<60000,state
print('PASS: per-app expiry with total time remaining; source-specific maths reward; parent changes a daily limit without resetting used time',flush=True)
