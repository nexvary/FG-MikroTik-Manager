import QtQuick
import QtQuick.Controls.Basic
TabButton {
 id:control
 implicitHeight:46
 contentItem:Text {text:control.text;color:control.checked?"#42E6A4":"#C8D5DF";font.pixelSize:14;font.bold:control.checked;horizontalAlignment:Text.AlignHCenter;verticalAlignment:Text.AlignVCenter;elide:Text.ElideRight}
 background:Rectangle {color:control.checked?"#183B47":"#102331";border.color:control.checked?"#42E6A4":"#294152";radius:8}
}
