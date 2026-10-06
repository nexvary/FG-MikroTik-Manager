import QtQuick
import QtQuick.Controls.Basic
TabButton {
 id:control
 implicitHeight:46
 contentItem:Text {text:control.text;color:control.checked?"#42E6A4":"#C8D5DF";font.pixelSize:14;horizontalAlignment:Text.AlignHCenter;verticalAlignment:Text.AlignVCenter;elide:Text.ElideRight}
 background:Rectangle {color:control.checked?"#0E2A3D":"#0A1F30";border.color:control.checked?"#42E6A4":"#8FA5B5";radius:8}
}
