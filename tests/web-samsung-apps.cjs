const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Representative public package IDs, rather than a dump of someone's installed apps.
const fixture = [
  ['org.tizen.primevideo', ''], ['org.tizen.globoplayapp', ''],
  ['org.tizen.netflix-app', 'Netflix'], ['org.tizen.browser', 'Internet'],
  ['9Ur5IzDKqV.TizenYouTube', 'YouTube'], ['evKhCgZelL.AmazonIgnitionLauncher2', 'Prime Video'],
  ['org.tizen.tv-viewer', 'TV'], ['org.tizen.menu', 'Settings'],
  ['org.tizen.netflixlowmem', 'Netflix component'], ['com.samsung.tv.aria-dummy', 'Player'],
  ['FreeMyTVio.FreeMyTV', 'Free My TV']
].map(([id, name]) => ({ id, name, show: true, iconPath: '' }));
const launches = [];
const context = {
  window: { tizen: { application: {
    getCurrentApplication: () => ({ appInfo: { id: 'FreeMyTVio.FreeMyTV' } }),
    getAppsInfo: done => done(fixture),
    launch: (id, done) => { launches.push(id); done(); }
  } } }, setTimeout, clearTimeout
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/tv.js'), 'utf8'), context);
let apps;
context.TV.apps(list => { apps = list; });
for (const id of ['org.tizen.primevideo', 'org.tizen.globoplayapp', 'org.tizen.netflix-app',
  'org.tizen.browser', '9Ur5IzDKqV.TizenYouTube', 'evKhCgZelL.AmazonIgnitionLauncher2']) {
  assert(apps.some(app => app.id === id), `Expose user-facing app ${id}`);
}
for (const id of ['org.tizen.tv-viewer', 'org.tizen.menu', 'org.tizen.netflixlowmem',
  'com.samsung.tv.aria-dummy', 'FreeMyTVio.FreeMyTV']) {
  assert(!apps.some(app => app.id === id), `Keep internal/self app hidden: ${id}`);
}
assert.equal(apps.find(app => app.id === 'org.tizen.primevideo').title, 'Prime Video');
assert.equal(apps.find(app => app.id === 'org.tizen.globoplayapp').title, 'Globoplay');
assert.equal(apps.find(app => app.id === 'org.tizen.primevideo').icon, 'icons/prime.png');
assert(fs.existsSync(path.join(__dirname, '../web/icons/prime.png')));
let opened = false;
context.TV.launch('org.tizen.primevideo', null, ok => { opened = ok; });
assert(opened);
assert.deepEqual(launches, ['org.tizen.primevideo']);
console.log('Samsung app visibility, fallback names, icon and launch: passed');
