(function () {

  var button = document.createElement('button');
  button.textContent = 'Run carousel regression test'; button.style.cssText = 'position:fixed;right:12px;bottom:8px;z-index:99;font:14px sans-serif'; document.body.appendChild(button);
  var report = document.createElement('pre'); report.id = 'test-results'; report.style.cssText = 'position:fixed;left:8px;bottom:0;z-index:99;background:white;font:14px monospace'; document.body.appendChild(report);
  button.onclick = function () {
    var checks = [], maxNodes = 0, layoutReads = 0, launch = '', beforeRequests = window.previewRequests;
    function assert(value, text) { if (!value) throw Error(text); checks.push(text); }
    function nodes() { return document.getElementById('cards').children; }
    function step(x,y) { FreeMyTV.move(x,y); maxNodes = Math.max(maxNodes,nodes().length); }
    try {
      FreeMyTV.state.zone = 'shelf'; FreeMyTV.state.shelf = 0; step(0,-1);
      assert(nodes().length <= 5, 'bounded initial DOM');
      var retained = nodes()[1], originalRect = Element.prototype.getBoundingClientRect;
      Element.prototype.getBoundingClientRect = function () { layoutReads++; return originalRect.apply(this,arguments); };
      for (var i=0;i<40;i++) step(1,0);
      assert(FreeMyTV.state.card === 23,'right boundary reaches title 24');
      assert(document.querySelector('#cards .focus')._cardIndex === 23,'global focus matches last title');
      for (i=0;i<40;i++) step(-1,0);
      assert(FreeMyTV.state.card === 0,'left boundary stays at title 1');
      step(1,0); step(1,0);
      var keep = document.querySelector('#cards .focus'); step(1,0);
      assert(keep.parentNode === document.getElementById('cards'),'retained nodes reused');
      for(i=0;i<1000;i++) step(i%48<24 ? 1 : -1,0);
      assert(layoutReads === 0,'no synchronous layout reads on horizontal navigation');
      Element.prototype.getBoundingClientRect = originalRect;
      assert(maxNodes<=5,'maximum five card nodes during 1083 moves');
      assert(window.previewRequests === beforeRequests,'no metadata requests during navigation');
      var p = Data.carousel(null)[FreeMyTV.state.card], expected = Data.appFor(p.app).id, oldLaunch=TV.launch;
      TV.launch = function(id,params,done) { launch=id; if(done)done(true); };
      FreeMyTV.activate(); TV.launch=oldLaunch;
      assert(launch===expected,'OK opens provider of selected offscreen catalogue title');
      step(0,1); assert(FreeMyTV.state.zone==='shelf','down returns to shelf');
      step(0,-1); assert(FreeMyTV.state.zone==='cards' && document.querySelector('#cards .focus'),'up maps visible card to global index');
      step(0,-1); step(1,0); step(0,1);
      assert(FreeMyTV.state.tab===1 && FreeMyTV.state.card===0,'Movies tab starts at first title');
      assert(Data.carousel('movie').length===12,'Movies contains all 12 cached movies');
      step(1,0); step(1,0); step(1,0);
      var originalCarousel=Data.carousel, selected=originalCarousel('movie')[FreeMyTV.state.card];
      Data.carousel=function(kind) { return originalCarousel(kind).reverse(); };
      Data.settingsChanged();
      var refreshed=Data.carousel('movie')[FreeMyTV.state.card];
      assert(refreshed.id===selected.id && refreshed.kind===selected.kind,'refresh preserves selected title after reorder');
      Data.carousel=function() { return []; }; Data.settingsChanged();
      assert(!nodes().length && FreeMyTV.state.zone==='shelf' && !document.getElementById('carousel-position').textContent,'empty catalogue clears cards and returns focus to shelf');
      Data.carousel=originalCarousel; Data.settingsChanged();
      step(0,-1);
      report.textContent=JSON.stringify({passed:true,checks:checks,maxCardNodes:maxNodes,horizontalLayoutReads:layoutReads,metadataRequestsDuringMoves:window.previewRequests-beforeRequests},null,2);
      step(1,0); step(1,0); step(1,0);
    } catch(e) { report.textContent='FAILED: '+e.stack; }
  };
  var pace = document.createElement('button');
  pace.textContent = 'Test continuous movement'; pace.style.cssText='position:fixed;right:230px;bottom:8px;z-index:99;font:14px sans-serif'; document.body.appendChild(pace);
  pace.onclick = function () {
    var intervals=[], start=performance.now(), last=start, moved=start, steps=0, max=0;
    var initial=window.previewRequests;
    FreeMyTV.state.zone='cards';
    function frame(now) {
      intervals.push(now-last); last=now;
      if(now-moved>=80) {
        document.dispatchEvent(new KeyboardEvent('keydown',{keyCode:steps%20<10?39:37,which:steps%20<10?39:37,bubbles:true}));
        moved=now; steps++; max=Math.max(max,document.getElementById('cards').children.length);
      }
      if(now-start<5000) requestAnimationFrame(frame);
      else {
        intervals.sort(function(a,b){return a-b;});
        report.textContent=JSON.stringify({pacedTest:true,durationMs:Math.round(now-start),keyEvents:steps,maxCardNodes:max,metadataRequests:window.previewRequests-initial,medianFrameMs:Math.round(intervals[Math.floor(intervals.length*.5)]*100)/100,p95FrameMs:Math.round(intervals[Math.floor(intervals.length*.95)]*100)/100,environment:'desktop browser; not TV FPS'},null,2);
      }
    }
    requestAnimationFrame(frame);
  };
  var burst=document.createElement('button'); burst.textContent='Test key repeat queue'; burst.style.cssText='position:fixed;right:440px;bottom:8px;z-index:99;font:14px sans-serif'; document.body.appendChild(burst);
  burst.onclick=function() {
    var state=FreeMyTV.state;
    state.zone='cards';
    while(state.card>0) FreeMyTV.move(-1,0);
    function key(code) { document.dispatchEvent(new KeyboardEvent('keydown',{keyCode:code,which:code,bubbles:true})); }
    for(var i=0;i<200;i++) key(39);
    var immediate=state.card;
    setTimeout(function() {
      var settled=state.card;
      key(39); key(39); key(40);
      var before=state.card;
      setTimeout(function() {
        report.textContent=JSON.stringify({keyboardQueueTest:true,passed:immediate===1 && settled===2 && state.card===before && state.zone==='shelf',burstKeyEvents:200,immediateMoves:immediate,settledMoves:settled,pendingMoveCancelledOnVerticalNavigation:state.card===before && state.zone==='shelf'},null,2);
      },240);
    },240);
  };
})();
