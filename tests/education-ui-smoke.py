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

print(instrument('test'),flush=True)
print(instrument('seed',10797000),flush=True)
# Instrumentation stops the target package; explicitly rebind the test emulator's service.
adb('shell','settings','delete','secure','enabled_accessibility_services')
time.sleep(.3)
adb('shell','settings','put','secure','enabled_accessibility_services','io.freemytv/.EducationGuard')
adb('shell','settings','put','secure','accessibility_enabled','1')
time.sleep(1)
adb('shell','am','start','-n','io.freemytv/.HomeActivity')
root=wait_text('What would you like to watch?')
shot('education-kids.png')
# Process restart must retain Kids mode and its spent allowance.
adb('shell','am','force-stop','io.freemytv')
adb('shell','am','start','-n','io.freemytv/.HomeActivity')
root=wait_text('What would you like to watch?')
assert any('0 h 01 min' in s for s in texts(root)), 'restart reset the spent allowance'
adb('shell','settings','delete','secure','enabled_accessibility_services')
time.sleep(.3)
adb('shell','settings','put','secure','enabled_accessibility_services','io.freemytv/.EducationGuard')
time.sleep(1)
root=wait_text('What would you like to watch?')
click('YouTube Kids',root)
root=wait_text('Time for a maths adventure!')
assert not any(n.get('class')=='android.widget.ScrollView' for n in root.iter('node')), 'maths must fit without scrolling'
shot('education-maths.png')
number(999); click('OK')
root=wait_text('Nearly!')
assert any('Question 1 of 5' in s for s in texts(root)), 'wrong answer advanced challenge'
for index in range(5):
    root=wait_text('Question %d of 5'%(index+1))
    equation=next(s for s in texts(root) if re.match(r'^\d+ [ +−×]+ \d+ = \?$',s))
    a,op,b=re.match(r'^(\d+) ([+−×]) (\d+) =',equation).groups()
    a,b=int(a),int(b)
    answer=a+b if op=='+' else a-b if op=='−' else a*b
    number(answer); click('OK')
root=wait_text('You did it!')
shot('education-reward.png')
click('Back to my shows',root)
wait_text('0 h 30 min')
print('PASS: approved foreground app timed; expiry interrupts viewing; wrong answer retries; five answers award 30 minutes',flush=True)

adb('shell','am','start','-a','android.settings.SETTINGS')
wait_text('What would you like to watch?')
adb('shell','am','start','-n','io.freemytv/.SettingsActivity')
root=wait_text('What would you like to watch?')
click('Parent Mode',root)
root=wait_text('Enter your 4-digit PIN')
number(1111);click('OK');wait_text('PIN not accepted')
number(4826);click('OK');root=wait_text('Parent settings')
shot('education-parent.png')
click('Adult mode',root)
time.sleep(1)
state=instrument('inspect')
assert 'kids=false' in state and 'rewards=1' in state and 'practiceDays=1' in state,state
print('PASS: system/app settings return to Kids; incorrect PIN denied; correct PIN opens Parent Mode; adult exit preserves allowance and progress',flush=True)
print(state,flush=True)
adb('shell','settings','delete','secure','enabled_accessibility_services')
time.sleep(.3)
adb('shell','settings','put','secure','enabled_accessibility_services','io.freemytv/.EducationGuard')
time.sleep(1)
adb('shell','am','start','-n','io.freemytv/.HomeActivity')
root=wait_text('Kids mode')
node=next(n for n in root.iter('node') if n.get('text')=='Kids mode')
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
shot('education-home-shortcut.png')
adb('shell','am','instrument','-w','-e','click','"Kids mode"','io.freemytv.educationtests/io.freemytv.EducationUiObserver')
wait_text('What would you like to watch?')
print('PASS: home-screen Kids circle enters configured Kids mode without a parent PIN',flush=True)
print('Screenshots:',OUT,flush=True)
