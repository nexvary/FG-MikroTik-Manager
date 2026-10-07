import QtQuick
import QtQuick.Controls.Basic
Button {
 id: button
 property color accent: "#159DFF"
 property bool filled:false
 implicitHeight: 44
 font.pixelSize: 15
 contentItem: Text {text: button.text; color: button.enabled ? (button.filled?"#05131F":button.accent) : "#8195A5"; font: button.font; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; elide: Text.ElideRight}
 background: Rectangle {radius: 12; color: button.down ? "#23495E" : button.filled&&button.enabled?button.accent:button.hovered ? "#1A3548" : "#102637"; border.color: button.enabled ? button.accent : "#4C6577"; border.width: button.activeFocus ? 2 : 1;Behavior on color {ColorAnimation {duration:120}}}
 padding: 12
}
