/* Education visual/practice companion. webOS/Tizen cannot enforce Android's foreground-app guard. */
window.Education = (function () {
  'use strict';
  var panel, active = false, question, answer = '', count = 0, feedback;
  function text(key) {
    var lang = (typeof Prefs !== 'undefined' && Prefs.language) ? Prefs.language() : 'en';
    if (!EducationText[lang]) lang = (navigator.language || 'en').split('-')[0];
    return (EducationText[lang] || EducationText.en)[key];
  }
  function el(tag, value, parent) {
    var n = document.createElement(tag); if (value) n.textContent = value;
    (parent || panel).appendChild(n); return n;
  }
  function button(label, run, parent) {
    var b = el('button', label, parent); b.onclick = run;
    if (!/^[0-9]+$/.test(label) && label !== '⌫' && label !== text('edu_ok')) {
      var icon = el('span', '', b); icon.className = 'sym edu-icon';
      var name = /YouTube|ABC/.test(label) ? 'play' : label === text('edu_games') ? 'gamepad' : label === text('edu_practice') ? 'sparkles' : 'settings';
      icon.style.webkitMaskImage = 'url(symbols/'+name+'.png)';icon.style.maskImage = 'url(symbols/'+name+'.png)';
    }
    return b;
  }
  function screen(title, sub) {
    if (!panel) { panel = el('section', '', document.getElementById('stage')); panel.id = 'education'; }
    active = true; panel.style.display = 'block'; panel.innerHTML = '';
    document.getElementById('stage').classList.add('education-open');
    var preview = {en:'Preview',pt:'Prévia',es:'Vista previa',de:'Vorschau',ru:'Предпросмотр'};
    el('div', 'Free My TV / Education · '+(preview[(navigator.language || 'en').split('-')[0]] || preview.en), panel).className = 'edu-brand';
    el('h1', title); el('p', sub);
  }
  function close() { active = false; panel.style.display = 'none'; document.getElementById('stage').classList.remove('education-open'); }
  function info() {
    screen(text('edu_parent'), text('edu_rule'));
    var lang = (navigator.language || 'en').split('-')[0];
    var notice = {
      en: 'Education controls are available in the Android app. This web version previews the design and maths only; it cannot block other apps or enforce viewing time on LG or Samsung TVs.',
      pt: 'Os controles Education estão no aplicativo Android. Esta versão web mostra o visual e a matemática; não bloqueia aplicativos nem limita o tempo em TVs LG ou Samsung.',
      es: 'Los controles Education están en Android. Esta versión web muestra el diseño y las matemáticas; no bloquea aplicaciones ni limita el tiempo en LG o Samsung.',
      de: 'Education-Kontrollen gibt es in der Android-App. Diese Webversion zeigt Design und Rechenübungen; sie sperrt keine Apps und begrenzt keine Fernsehzeit auf LG oder Samsung.',
      ru: 'Контроль Education доступен в Android. Эта веб-версия показывает дизайн и задачи, но не блокирует программы и не ограничивает время на LG или Samsung.'
    };
    el('p', notice[lang] || notice.en).className = 'edu-notice';
    button(text('edu_continue'), open).focus(); button(text('edu_adult'), close);
  }
  function open() {
    var titles = {en:'What would you like to watch?',pt:'O que você quer assistir?',es:'¿Qué quieres ver?',de:'Was möchtest du anschauen?',ru:'Что хочешь посмотреть?'};
    screen(titles[(navigator.language || 'en').split('-')[0]] || titles.en, '');
    var cards = el('div'); cards.className = 'edu-cards';
    button('▶\nYouTube Kids', info, cards).focus(); button('▶\nABC Kids', info, cards);
    button(text('edu_games'), info);
    var actions = el('div'); actions.className = 'edu-actions';
    button(text('edu_practice'), function () { count = 0; maths(); }, actions);
    button(text('edu_parent'), info, actions);
    el('p', 'Android · ' + text('edu_rule')).className = 'edu-note';
  }
  function maths() {
    var a = Math.floor(Math.random() * 6), b = Math.floor(Math.random() * 6);
    question = a + b; answer = '';
    screen(text('edu_practice'), text('edu_question').replace('%1$d', count + 1).replace('%2$d', '5'));
    var columns = el('div'); columns.className = 'edu-math-columns';
    var questionPanel = el('div', '', columns); questionPanel.className = 'edu-question';
    el('h2', a + ' + ' + b + ' = ?', questionPanel);
    var value = el('div', '?', questionPanel); value.className = 'edu-answer';
    feedback = el('p', text('edu_math_keypad_hint'), questionPanel);
    var pad = el('div', '', columns); pad.className = 'edu-pad';
    function digit(n) { if (answer.length < 3) answer += n; value.textContent = answer || '?'; }
    var i;
    for (i = 1; i <= 9; i++) (function (n) { button(String(n), function () { digit(n); }, pad); }(i));
    button('⌫', function () { answer = answer.slice(0, -1); value.textContent = answer || '?'; }, pad);
    button('0', function () { digit(0); }, pad);
    button(text('edu_ok'), function () {
      if (answer === '') return;
      if (Number(answer) !== question) { answer = ''; value.textContent = '?'; feedback.textContent = text('edu_try_again'); return; }
      count++;
      if (count < 5) { maths(); return; }
      screen(text('edu_well_done'), text('edu_practised')); button(text('edu_continue'), open).focus();
    }, pad);
    pad.firstChild.focus();
  }
  document.addEventListener('keydown', function (e) {
    if (!active) return;
    var code = e.keyCode;
    if (code === 27 || code === 8 || code === 461 || code === 10009) { close(); }
    else if (code >= 37 && code <= 40) {
      var items = panel.querySelectorAll('button'), at = 0, i;
      for (i = 0; i < items.length; i++) if (items[i] === document.activeElement) at = i;
      var step = panel.querySelector('.edu-pad') && (code === 38 || code === 40) ? 3 : 1;
      at = Math.max(0, Math.min(items.length - 1, at + ((code === 37 || code === 38) ? -step : step)));
      items[at].focus();
    } else if (code === 13) { document.activeElement.click(); }
    else return;
    e.preventDefault(); e.stopImmediatePropagation();
  }, true);
  return {open: open, active: function () { return active; }};
}());
