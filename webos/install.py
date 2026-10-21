#!/usr/bin/env python3
"""Installs Free My TV on an LG TV in Developer Mode, over your Wi-Fi.

    python3 webos/install.py 192.168.1.50              # install or update, then open it
    python3 webos/install.py 192.168.1.50 --uninstall  # remove it

Before the first install, on the TV: install "Developer Mode" from the LG Content Store, sign in with a free LG
developer account (webostv.developer.lge.com), turn on Dev Mode Status (the TV restarts), then turn on Key Server.
The app shows the TV's IP address and a passphrase.

The first time, this downloads the TV's key from its Key Server and asks for that passphrase (type it here; it is
not stored). The unlocked key is kept in ~/.ssh/freemytv_webos_<IP>, so updates need neither. The app is copied over
SSH (port 9922) and installed with the TV's own installer (com.webos.appInstallService), the way LG's tools do it.
Install flow based on FreeMyTvWebOS by danielthejoker18 (MIT). Needs Python 3 and OpenSSH (ssh, ssh-keygen).
"""
import getpass
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
from webapp import say  # noqa: E402

USAGE_RU = """Устанавливает Free My TV на телевизор LG в режиме разработчика, через вашу сеть Wi-Fi.

    python3 webos/install.py 192.168.1.50              # установить или обновить, затем открыть
    python3 webos/install.py 192.168.1.50 --uninstall  # удалить

Перед первой установкой на телевизоре: поставьте из LG Content Store приложение Developer Mode, войдите в бесплатный
аккаунт разработчика LG (webostv.developer.lge.com), включите Dev Mode Status (телевизор перезагрузится), затем
Key Server. Приложение покажет IP-адрес телевизора и кодовую фразу.

В первый раз скрипт скачает ключ телевизора с его Key Server и спросит эту фразу (введите её здесь, она нигде не
сохраняется). Ключ, уже без фразы, остаётся в ~/.ssh/freemytv_webos_<IP>, поэтому для обновлений не нужно ни то, ни
другое.
"""
IPK = os.path.join(ROOT, 'FreeMyTV-webos.ipk')
with open(os.path.join(ROOT, 'webos', 'appinfo.json'), encoding='utf-8') as f:
    APP_ID = json.load(f)['id']
TEMP = '/media/developer/temp/freemytv.ipk'
SSH_OPTS = ['-o', 'StrictHostKeyChecking=no', '-o', 'UserKnownHostsFile=' + os.devnull, '-o', 'LogLevel=ERROR',
            '-o', 'HostKeyAlgorithms=+ssh-rsa', '-o', 'PubkeyAcceptedKeyTypes=+ssh-rsa', '-p', '9922']


def key_path(ip):
    return os.path.join(os.path.expanduser('~'), '.ssh', 'freemytv_webos_' + ip.replace('.', '_'))


def fetch_key(ip):
    """The TV's SSH key from its Key Server, unlocked with the passphrase the TV shows."""
    path = key_path(ip)
    if os.path.exists(path):
        return path
    url = 'http://%s:9991/webos_rsa' % ip
    print(say('Getting the TV\'s key from %s ...', 'Берём ключ телевизора с %s ...') % url)
    try:
        with urllib.request.urlopen(url, timeout=6) as r:
            data = r.read()
    except Exception as e:
        sys.exit(say('Couldn\'t reach the Key Server (%s). In the TV\'s Developer Mode app, turn on Key Server.',
                     'Key Server не отвечает (%s). Включите Key Server в приложении Developer Mode на телевизоре.') % e)
    passphrase = getpass.getpass(say('Passphrase shown in Developer Mode on the TV: ',
                                     'Кодовая фраза из Developer Mode на телевизоре: ')).strip()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = tempfile.NamedTemporaryFile(delete=False)
    tmp.write(data)
    tmp.close()
    os.chmod(tmp.name, 0o600)
    unlock = subprocess.run(['ssh-keygen', '-p', '-P', passphrase, '-N', '', '-f', tmp.name], capture_output=True, text=True)
    if unlock.returncode != 0:
        os.unlink(tmp.name)
        sys.exit(say('That passphrase didn\'t unlock the key. Check it on the TV (Developer Mode) and try again.',
                     'Эта кодовая фраза не подошла к ключу. Сверьте её с экраном телевизора (Developer Mode) и '
                     'попробуйте ещё раз.'))
    shutil.move(tmp.name, path)
    os.chmod(path, 0o600)
    return path


def ssh(ip, key, command, stdin=None, tty=False):
    args = ['ssh'] + (['-tt'] if tty else []) + SSH_OPTS + ['-i', key, 'prisoner@' + ip, command]
    return subprocess.run(args, stdin=stdin, capture_output=True, text=stdin is None)


def luna(ip, key, uri, payload, replies=1, wait=20):
    # luna-send-pub prints nothing without a terminal (-tt), and the installer streams several replies.
    # Older webOS ships BusyBox before 1.30, whose timeout only takes -t SECS.
    # Some (webOS 6) refuse the terminal; there -i streams the replies without one, until the timeout.
    timeout = "if timeout -t 1 true 2>/dev/null; then T='timeout -t %d'; else T='timeout %d'; fi; " % (wait, wait)
    message = "%s '%s'" % (uri, json.dumps(payload, separators=(',', ':')))
    out = ssh(ip, key, timeout + '$T luna-send-pub -n %d %s' % (replies, message), tty=True).stdout or ''
    if not out.strip():
        out = ssh(ip, key, timeout + '$T luna-send-pub -i %s </dev/null' % message).stdout or ''
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if not args or not re.match(r'^\d{1,3}(\.\d{1,3}){3}$', args[0]):
        sys.exit(say(__doc__, USAGE_RU))
    ip = args[0]
    if not shutil.which('ssh') or not shutil.which('ssh-keygen'):
        sys.exit(say('OpenSSH (ssh and ssh-keygen) is needed.', 'Нужен OpenSSH (ssh и ssh-keygen).'))
    key = fetch_key(ip)

    if '--uninstall' in sys.argv:
        out = luna(ip, key, 'luna://com.webos.appInstallService/dev/remove', {'id': APP_ID}, replies=3, wait=30)
        print(say('Removed.', 'Удалено.') if '"returnValue":true' in out else say('The TV said: ', 'Ответ телевизора: ') + out.strip())
        return

    if not os.path.exists(IPK):
        subprocess.run([sys.executable, os.path.join(ROOT, 'webos', 'build.py')], check=True)
    print(say('Copying the app to the TV ...', 'Копируем приложение на телевизор ...'))
    with open(IPK, 'rb') as f:
        copy = ssh(ip, key, 'mkdir -p /media/developer/temp && cat > ' + TEMP, stdin=f)
    if copy.returncode != 0:
        err = copy.stderr.decode('utf-8', 'replace') if isinstance(copy.stderr, bytes) else copy.stderr
        sys.exit(say('Couldn\'t copy it: %s\nIs Developer Mode still on? If the key changed, delete %s and try again.',
                     'Не удалось скопировать: %s\nDeveloper Mode ещё включён? Если ключ сменился, удалите %s и '
                     'попробуйте ещё раз.') % (err.strip(), key))
    print(say('Installing ...', 'Устанавливаем ...'))
    out = luna(ip, key, 'luna://com.webos.appInstallService/dev/install', {'id': APP_ID, 'ipkUrl': TEMP, 'subscribe': True},
               replies=30, wait=60)
    if '"state":"installed"' not in out:
        sys.exit(say('The TV didn\'t finish installing. It said:\n', 'Телевизор не закончил установку. Его ответ:\n') + out.strip())
    ssh(ip, key, 'rm -f ' + TEMP)
    luna(ip, key, 'luna://com.webos.applicationManager/launch', {'id': APP_ID})
    print(say('Installed and open on the TV.', 'Установлено и уже открыто на телевизоре.'))
    print(say('Tip: while Free My TV is open, hold a number key on the remote to open it with that key from anywhere.',
              'Совет: пока Free My TV открыт, зажмите на пульте цифровую кнопку, и она будет открывать его откуда угодно.'))
    print(say('Developer Mode lasts about 40 days: open the Developer Mode app now and then and press EXTEND.',
              'Developer Mode работает около 40 дней: время от времени открывайте приложение Developer Mode и нажимайте EXTEND.'))


if __name__ == '__main__':
    main()
