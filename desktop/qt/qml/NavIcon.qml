import QtQuick
Item {
 id:badge
 property int kind:0
 property color ink:"#159DFF"
 width:32;height:32
 Rectangle {anchors.fill:parent;radius:Math.min(width,height)*0.27
  gradient:Gradient {GradientStop {position:0;color:Qt.rgba(badge.ink.r,badge.ink.g,badge.ink.b,0.22)} GradientStop {position:1;color:"#102331"}}
  border.color:Qt.rgba(badge.ink.r,badge.ink.g,badge.ink.b,0.5)
 }
 Canvas {
  id:glyph
  anchors.fill:parent;anchors.margins:3
  onWidthChanged:requestPaint();onHeightChanged:requestPaint()
  Connections {target:badge;function onInkChanged(){glyph.requestPaint()} function onKindChanged(){glyph.requestPaint()}}
 onPaint: {
  var c=getContext("2d");c.reset();c.scale(width/24,height/24);c.translate(2,2);c.scale(0.84,0.84);c.strokeStyle=badge.ink;c.fillStyle=Qt.rgba(badge.ink.r,badge.ink.g,badge.ink.b,0.22);c.lineWidth=2;c.lineCap="round";c.beginPath();
  if(badge.kind===0){for(var i=0;i<4;i++)c.rect(3+(i%2)*11,3+Math.floor(i/2)*11,7,7)}
  else if(badge.kind===1){c.rect(3,4,18,16);c.moveTo(7,9);c.lineTo(17,9);c.moveTo(7,14);c.lineTo(17,14)}
  else if(badge.kind===2){c.arc(12,7,4,0,Math.PI*2);c.moveTo(4,21);c.bezierCurveTo(4,12,20,12,20,21)}
  else if(badge.kind===3){c.moveTo(2,12);c.lineTo(7,12);c.lineTo(10,4);c.lineTo(14,20);c.lineTo(17,12);c.lineTo(22,12)}
  else if(badge.kind===6){c.rect(2,4,20,16);c.moveTo(2,9);c.lineTo(22,9);c.rect(5,13,5,4);c.rect(15,13,4,4)}
  else if(badge.kind===7){c.rect(2,3,20,15);c.moveTo(5,13);c.lineTo(8,10);c.lineTo(12,14);c.lineTo(18,7);c.moveTo(8,22);c.lineTo(16,22);c.moveTo(12,18);c.lineTo(12,22)}
  else if(badge.kind===8){c.arc(12,12,8,.2,Math.PI);c.moveTo(3,8);c.lineTo(4,12);c.lineTo(8,11);c.moveTo(20,12);c.arc(12,12,8,Math.PI+.2,Math.PI*2);c.moveTo(21,16);c.lineTo(20,12);c.lineTo(16,13)}
  else if(badge.kind===9){c.rect(5,2,14,20);c.moveTo(8,7);c.lineTo(16,7);c.moveTo(8,11);c.lineTo(16,11);c.moveTo(8,15);c.lineTo(16,15)}
  else if(badge.kind===10){c.arc(12,20,16,Math.PI*1.25,Math.PI*1.75);c.moveTo(5,12);c.arc(12,20,10,Math.PI*1.25,Math.PI*1.75);c.moveTo(9,17);c.arc(12,20,4,Math.PI*1.25,Math.PI*1.75);c.moveTo(12,21);c.lineTo(12,22)}
  else if(badge.kind===11){c.moveTo(12,2);c.lineTo(21,6);c.lineTo(19,15);c.lineTo(12,22);c.lineTo(5,15);c.lineTo(3,6);c.closePath();c.moveTo(7,11);c.lineTo(11,15);c.lineTo(17,8)}
  else if(badge.kind===5){c.arc(12,12,9,0,Math.PI*2);c.moveTo(12,10);c.lineTo(12,18);c.moveTo(12,6);c.lineTo(12,7)}
  else{c.rect(3,3,18,18);c.moveTo(7,8);c.lineTo(11,12);c.lineTo(7,16);c.moveTo(13,17);c.lineTo(18,17)}
  c.fill();c.stroke();
 }
 }
}
