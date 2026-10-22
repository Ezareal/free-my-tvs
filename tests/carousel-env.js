(function () {
  var picks = [], colors = ['#164A75', '#3B4667', '#52644D', '#784C45', '#55516D', '#3A686C'];
  for (var i = 0; i < 24; i++) {
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="780" height="439"><rect width="780" height="439" fill="' + colors[i % 6] + '"/><text x="40" y="240" fill="white" font-size="64">Sample ' + (i + 1) + '</text></svg>';
    picks.push({id:i + 1, kind:i % 2 ? 'tv' : 'movie', title:'Sample ' + (i + 1), detailed:true,
      app:i % 2 ? 'netflix' : 'prime', backdrop:'data:image/svg+xml,' + encodeURIComponent(svg),
      meta:'2026 · ' + (i % 2 ? 'Series · Netflix' : 'Movie · Prime Video')});
  }
  localStorage.setItem('fmtv.recs', JSON.stringify({picks:picks}));
  localStorage.setItem('fmtv.prefs', JSON.stringify({language:'en', theme:'light', quotes:false}));
  window.previewRequests = 0;
  window.XMLHttpRequest = function () {
    this.open = function () {};
    this.setRequestHeader = function () {};
    this.send = function () { window.previewRequests++; };
  };
})();
