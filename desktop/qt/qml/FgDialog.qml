import QtQuick
import QtQuick.Controls.Basic
Dialog {
 id: control
 Theme {id: dialogTheme}
 palette.window: dialogTheme.panel
 palette.windowText: dialogTheme.white
 palette.text: dialogTheme.white
 palette.base: dialogTheme.navy
 palette.button: dialogTheme.raised
 palette.buttonText: dialogTheme.white
 palette.highlight: dialogTheme.mint
 header:Item {implicitWidth:360;implicitHeight:dialogTitle.implicitHeight+36
  Text {id:dialogTitle;anchors.left:parent.left;anchors.right:parent.right;anchors.top:parent.top;anchors.margins:18;text:control.title;color:dialogTheme.gold;font.pixelSize:20;font.bold:true;wrapMode:Text.Wrap}
 }
 background: Rectangle {color:dialogTheme.panel;radius:14;border.width:1;border.color:dialogTheme.muted}
}
