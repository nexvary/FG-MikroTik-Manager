import QtQuick
Canvas {
 property int kind: 0
 property color ink: "#C8D5DF"
 width: 24; height: 24
 onInkChanged: requestPaint()
 onPaint: {
  var c=getContext("2d");c.reset();c.strokeStyle=ink;c.lineWidth=1.8;c.lineCap="round";c.beginPath();
  if(kind===0){for(var i=0;i<4;i++)c.rect(3+(i%2)*11,3+Math.floor(i/2)*11,7,7)}
  else if(kind===1){c.rect(3,4,18,16);c.moveTo(7,9);c.lineTo(17,9);c.moveTo(7,14);c.lineTo(17,14)}
  else if(kind===2){c.arc(12,7,4,0,Math.PI*2);c.moveTo(4,21);c.bezierCurveTo(4,12,20,12,20,21)}
  else if(kind===3){c.moveTo(2,12);c.lineTo(7,12);c.lineTo(10,4);c.lineTo(14,20);c.lineTo(17,12);c.lineTo(22,12)}
  else{c.rect(3,3,18,18);c.moveTo(7,8);c.lineTo(11,12);c.lineTo(7,16);c.moveTo(13,17);c.lineTo(18,17)}
  c.stroke();
 }
}
