const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../web');
const cached = { picks: [
  { id: 10, kind: 'movie', title: 'First Film', detailed: true, app: 'prime' },
  { id: 20, kind: 'tv', title: 'First Series', detailed: true, app: 'netflix' },
  { id: 30, kind: 'movie', title: 'Second Film', detailed: true, app: 'netflix' }
] };
function load(key, fixture = cached) {
  const requests = [];
  class FakeXHR {
    open(method, url) { this.url = url; }
    setRequestHeader() {}
    send() { requests.push(this.url); }
    abort() {}
  }
  const context = { window: { CONFIG: { tmdbKey: key, latitude: '', longitude: '', showCrypto: false } },
    TV: { zone: () => 'America/Sao_Paulo' }, navigator: { language: 'pt-BR' },
    document: { documentElement: {} },
    localStorage: { getItem: name => name === 'fmtv.recs' ? JSON.stringify(fixture) : null, setItem() {} },
    XMLHttpRequest: FakeXHR, setTimeout: () => 1, clearTimeout() {}, console };
  vm.createContext(context);
  for (const name of ['strings.js', 'data.js']) vm.runInContext(fs.readFileSync(path.join(web, name), 'utf8'), context);
  context.I18N.set('pt');
  context.Data.setApps([{ id: 'org.tizen.primevideo', title: 'Prime Video' },
    { id: 'org.tizen.netflix-app', title: 'Netflix' }]);
  return { context, requests };
}
const missing = load('');
assert.equal(missing.context.Data.hasKey(), false);
for (const kind of [null, 'movie', 'tv']) assert.equal(missing.context.Data.picks(kind).length, 0);
assert.match(missing.context.Data.recommendationStatus(), /chave TMDB/);
missing.context.Data.refresh();
assert(!missing.requests.some(url => url.includes('themoviedb.org')), 'No TMDB request without a key');
for (const language of ['pt', 'en', 'es', 'de', 'ru']) {
  missing.context.I18N.set(language);
  const message = missing.context.Data.recommendationStatus();
  assert(message.length > 20);
  if (language !== 'en') assert.notEqual(message, 'Recommendations are not configured. Add a TMDB key to enable Explore, Movies and Shows.');
}
const configured = load('local-test-key-never-sent-to-network');
assert.equal(configured.context.Data.hasKey(), true);
assert.equal(configured.context.Data.picks(null).length, 3);
assert.equal(configured.context.Data.picks('movie').length, 2);
assert.equal(configured.context.Data.picks('tv').length, 1);
assert(configured.context.Data.picks('movie').every(pick => pick.kind === 'movie'));
assert(configured.context.Data.picks('tv').every(pick => pick.kind === 'tv'));
assert.match(configured.context.Data.recommendationStatus(), /recomendação/);
const many = {picks: Array.from({length: 40}, (_, i) => ({id: Math.floor(i/2), kind: i%2 ? 'tv' : 'movie', title:'Title '+i, detailed:true, app:i%2 ? 'netflix' : 'prime'}))};
many.picks.splice(0, 0, {id:999,kind:'movie',title:'Pending',detailed:false,app:'prime'}, {id:998,kind:'tv',title:'Removed provider',detailed:true,app:'missing'});
const carousel = load('test-only', many);
assert.equal(carousel.context.Data.carousel(null).length,24);
assert.equal(carousel.context.Data.carousel('movie').length,20);
assert.equal(carousel.context.Data.carousel('tv').length,20);
assert.equal(new Set(carousel.context.Data.carousel(null).map(p => p.kind+':'+p.id)).size,24);
assert(!carousel.context.Data.carousel(null).some(p => p.id===999 || p.id===998));
for(let i=0;i<1000;i++) carousel.context.Data.carousel(null);
assert.equal(carousel.requests.length,0,'Reading the catalogue must never start requests');
carousel.context.Data.setApps([{id:'org.tizen.netflix-app',title:'Netflix'}]);
assert(carousel.context.Data.carousel(null).every(p => p.app==='netflix'));
assert.equal(missing.context.Data.carousel(null).length,0);
assert.equal(load('test-only',{picks:[]}).context.Data.carousel(null).length,0);
console.log(JSON.stringify({ passed: true, missingKeyReproducesAllEmptyTabs: true,
  configuredKeyFiltersExploreMoviesShows: true, emptyStateTranslatedLanguages: 5,
  carouselBoundedFilteredAndOffline: true,
  realNetworkRequests: 0 }, null, 2));
