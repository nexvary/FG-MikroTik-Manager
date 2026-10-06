import QtQuick
import QtQuick.Controls.Basic
TextField {
 color: "#F4F7FA"
 placeholderTextColor: "#8FA5B5"
 font.pixelSize: 15
 implicitHeight: 44
 selectByMouse: true
 padding: 12
 background: Rectangle {radius: 12; color: "#071C2B"; border.color: parent.activeFocus ? "#159DFF" : "#8FA5B5"}
}
