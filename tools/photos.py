#!/usr/bin/env python3
"""Puts your own photos on the TV: the home screen background and the screensaver (Google TV and Android TV only).

    python3 tools/photos.py <TV IP> --background tree.jpg
    python3 tools/photos.py <TV IP> --screensaver beach.jpg sunset.jpg hills.jpg
    python3 tools/photos.py <TV IP> --clear              # back to the picks' images

The screensaver shows them in the order given, one every 30 s, without a caption, instead of the picks' photos.
The background shows sharp behind the clock when there are no picks (no TMDB key). Each option replaces what was
there before; the other one is left alone. The photos go to the app's folder on the TV (Android/data/io.freemytv),
so uninstalling Free My TV removes them. On macOS, sips turns them into JPEGs sized for the TV (iPhone HEIC too);
elsewhere they go as they are (JPEG, PNG or WebP).
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

PKG = 'io.freemytv'
DIR = '/sdcard/Android/data/%s/files' % PKG
TYPES = ('.jpg', '.jpeg', '.png', '.webp')
# The screensaver's photos are 2112 px wide (10% over the screen, for the slow zoom).
WIDTH = 2112


def adb(serial, *args, check=True):
    r = subprocess.run(['adb', '-s', serial] + list(args), capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit('adb %s: %s' % (args[0], (r.stderr or r.stdout).strip()))
    return r.stdout


def prepare(path, tmp, n):
    """The file to send: a TV-sized JPEG on macOS, the original elsewhere."""
    if not os.path.isfile(path):
        sys.exit('Not found: %s' % path)
    ext = os.path.splitext(path)[1].lower()
    if shutil.which('sips'):
        out = os.path.join(tmp, '%02d.jpg' % n)
        r = subprocess.run(['sips', '-s', 'format', 'jpeg', '-s', 'formatOptions', '90', path, '--out', out],
                           capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit('Could not read %s: %s' % (path, r.stderr.strip()))
        w = subprocess.run(['sips', '-g', 'pixelWidth', out], capture_output=True, text=True).stdout.split()[-1]
        if int(w) > WIDTH:
            subprocess.run(['sips', '--resampleWidth', str(WIDTH), out], capture_output=True, check=True)
        return out
    if ext not in TYPES:
        sys.exit('%s: use JPEG, PNG or WebP photos (HEIC is converted only on macOS)' % path)
    return path


def replace(serial, folder, files, tmp):
    """Empties the folder on the TV and copies the photos in, numbered in order."""
    adb(serial, 'shell', 'rm -rf %s/%s && mkdir -p %s/%s' % (DIR, folder, DIR, folder))
    for i, path in enumerate(files, 1):
        src = prepare(path, tmp, i)
        ext = os.path.splitext(src)[1].lower()
        adb(serial, 'push', src, '%s/%s/%02d%s' % (DIR, folder, i, '.jpg' if ext == '.jpeg' else ext))
        print('  %s' % os.path.basename(path))


def main():
    ap = argparse.ArgumentParser(description='Your own photos on the Free My TV home screen and screensaver.')
    ap.add_argument('tv', help="the TV's IP address (or an adb serial, like 192.168.1.20:5555)")
    ap.add_argument('--background', metavar='PHOTO', help='the home screen background (shown when there are no picks)')
    ap.add_argument('--screensaver', metavar='PHOTO', nargs='+', help='the screensaver photos, in order')
    ap.add_argument('--clear', action='store_true', help='removes both: back to the picks\' images')
    a = ap.parse_args()
    if not (a.background or a.screensaver or a.clear):
        ap.error('say what to change: --background, --screensaver or --clear')
    if not shutil.which('adb'):
        sys.exit('adb not found: install the Android platform tools first (see the README).')

    serial = a.tv if ':' in a.tv else a.tv + ':5555'
    subprocess.run(['adb', 'connect', serial], capture_output=True, text=True)
    if 'device' not in adb(serial, 'get-state', check=False):
        sys.exit('Could not reach the TV at %s: is debugging on, and was "Allow USB debugging?" accepted?' % serial)
    if PKG not in adb(serial, 'shell', 'pm list packages %s' % PKG):
        sys.exit('Free My TV is not installed on this TV.')

    if a.clear:
        adb(serial, 'shell', 'rm -rf %s/photos %s/background' % (DIR, DIR))
        print('Removed your photos: the picks\' images are back.')
        return
    if a.screensaver:
        print('Screensaver:')
        with tempfile.TemporaryDirectory() as tmp:
            replace(serial, 'photos', a.screensaver, tmp)
    if a.background:
        print('Home screen background:')
        with tempfile.TemporaryDirectory() as tmp:
            replace(serial, 'background', [a.background], tmp)
    print('Done. The background shows the next time the home screen opens; the screensaver, the next time it starts.')


if __name__ == '__main__':
    main()
