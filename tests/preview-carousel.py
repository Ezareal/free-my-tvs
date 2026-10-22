"""Create an isolated carousel test page with synthetic data and no real API credential."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
import webapp

out = ROOT / 'out/carousel-test'
config = dict(webapp.GENERIC)
config.update(tmdbKey='preview-only-not-a-credential', language='en', showCrypto=False)
for name, content in webapp.files(config, 'tests/preview-carousel.py').items():
    target = out / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
index = out / 'index.html'
html = index.read_text(encoding='utf-8')
html = html.replace('<script src="config.js">', '<script src="carousel-env.js"></script>\n<script src="config.js">')
html = html.replace('<script src="home.js">', '<script>Data.image = function (path) { return path || ""; };</script>\n<script src="home.js">')
html = html.replace('</body>', '<script src="carousel-browser.js"></script></body>')
index.write_text(html, encoding='utf-8')
for name in ['carousel-env.js', 'carousel-browser.js']:
    (out / name).write_bytes((ROOT / 'tests' / name).read_bytes())
print('Created out/carousel-test/index.html with synthetic data. No personal configuration was read.')
