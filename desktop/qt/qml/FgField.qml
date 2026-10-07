import QtQuick
import QtQuick.Controls.Basic
TextField {
 color: "#7ED9F5"
 placeholderTextColor: "#61798B"
 font.pixelSize: 15
 implicitHeight: 44
 selectByMouse: true
 padding: 12
 background: Rectangle {radius: 12; color: "#102331"; border.color: parent.activeFocus ? "#159DFF" : "#61798B"}
}
