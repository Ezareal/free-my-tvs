/* Cheap built-in motion. Pause while hidden; respect reduced-motion preferences. */
(function () {
  'use strict';
  var root=document.getElementById('ambient'), blobs=root.children, x=0,y=0,tx=0,ty=0;
  var reduce=window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)');
  document.addEventListener('keydown',function(e){
    if(e.keyCode===37)tx=-1;if(e.keyCode===39)tx=1;
    if(e.keyCode===38)ty=-1;if(e.keyCode===40)ty=1;
  });
  setInterval(function(){
    if(document.hidden)return;
    var t=Date.now()/1000, hour=new Date().getHours();
    root.style.opacity=hour>=6&&hour<18?'0.85':'1';
    x+=(tx-x)*.075;y+=(ty-y)*.075;tx*=.985;ty*=.985;
    for(var i=0;i<3;i++){
      var transform=reduce&&reduce.matches?'none':'translate('+((Math.sin(t*.035+i*2)*.065+x*.012)*1920)+'px,'+((Math.cos(t*.028+i*2.6)*.15+y*.012)*1080)+'px)';
      blobs[i].style.transform=transform;blobs[i].style.webkitTransform=transform;
    }
  },100);
}());
