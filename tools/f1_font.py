#!/usr/bin/env python3
"""Downloads Formula 1's title font (Formula1-Black) for the Formula 1 card and for preview/home.html.

Run it on your own computer:  python3 tools/f1_font.py
It needs fontTools and brotli once:  pip3 install fonttools brotli
It writes app/assets/fonts/Formula1-Black.ttf (the app uses it when it is there, and ./build.sh packs it) and
preview/fonts/Formula1-Black.woff2. The font belongs to Formula One: both files are git-ignored, so they stay on your
machine and are not committed or shared with the app's source.
"""
import os
import sys
import urllib.request

URL = "https://www.formula1.com/s/fonts/formula1/Formula1-Black.woff2"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    req = urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.formula1.com/"})
    try:
        data = urllib.request.urlopen(req, timeout=30).read()
    except Exception as e:
        sys.exit("Couldn't download the font: %s" % e)
    web = os.path.join(ROOT, "preview", "fonts")
    os.makedirs(web, exist_ok=True)
    with open(os.path.join(web, "Formula1-Black.woff2"), "wb") as f:
        f.write(data)
    try:
        from fontTools.ttLib import TTFont
    except ImportError:
        sys.exit("The preview font is saved. For the app, run: pip3 install fonttools brotli   and then this again.")
    out = os.path.join(ROOT, "app", "assets", "fonts", "Formula1-Black.ttf")
    font = TTFont(os.path.join(web, "Formula1-Black.woff2"))
    font.flavor = None
    font.save(out)
    print("Saved", out, "and", os.path.join(web, "Formula1-Black.woff2"))


if __name__ == "__main__":
    main()
