import QtQuick
import QtQuick.Controls.Basic
ComboBox {
 id:control
 implicitHeight:44
 leftPadding:12;rightPadding:30
 font.pixelSize:14
 contentItem:Text {text:control.displayText;color:control.enabled?"#7ED9F5":"#8195A5";font:control.font;verticalAlignment:Text.AlignVCenter;elide:Text.ElideRight}
 background:Rectangle {radius:11;color:control.hovered?"#18384A":"#102331";border.color:control.activeFocus?"#159DFF":"#61798B"}
 indicator:Text {text:"⌄";color:"#FFBE43";font.pixelSize:22;x:control.width-width-10;y:(control.height-height)/2}
 delegate:ItemDelegate {width:Math.max(control.width,260);implicitHeight:44
  contentItem:Text {text:control.textAt(index);color:highlighted?"#42E6A4":"#C8D5DF";font.pixelSize:14;verticalAlignment:Text.AlignVCenter;elide:Text.ElideRight}
  highlighted:control.highlightedIndex===index
  background:Rectangle {color:parent.highlighted?"#1D4055":"#102331"}
 }
 popup:Popup {y:control.height+4;width:Math.max(control.width,260);implicitHeight:Math.min(contentItem.implicitHeight+16,320);padding:8
  contentItem:ListView {clip:true;implicitHeight:contentHeight;model:control.popup.visible?control.delegateModel:null;currentIndex:control.highlightedIndex;ScrollBar.vertical:ScrollBar{}}
  background:Rectangle {color:"#102331";radius:12;border.color:"#3F677F"}
 }
}
