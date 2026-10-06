import QtQuick
import QtQuick.Controls.Basic
Button {
 id: button
 property color accent: "#159DFF"
 implicitHeight: 44
 font.pixelSize: 15
 contentItem: Text {text: button.text; color: button.enabled ? "#F4F7FA" : "#8FA5B5"; font: button.font; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; elide: Text.ElideRight}
 background: Rectangle {radius: 12; color: button.down ? "#173F70" : button.hovered ? "#0E2A3D" : "#0A1F30"; border.color: button.enabled ? button.accent : "#4C6577"; border.width: button.activeFocus ? 2 : 1}
 padding: 12
}
