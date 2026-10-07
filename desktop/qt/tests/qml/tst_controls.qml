import QtQuick
import QtTest
import "../../qml"
Item {
 width:500;height:300
 FgCombo {id:combo;editable:true;width:320;model:["first","second"]}
 TestCase {
  name:"DropdownInteraction";when:windowShown
  function test_manual_value(){combo.editable=true;combo.contentItem.forceActiveFocus();combo.contentItem.selectAll();keyClick(Qt.Key_A);keyClick(Qt.Key_B);compare(combo.editText,"ab")}
  function test_popup_keyboard_selection(){combo.editable=false;combo.currentIndex=0;mouseClick(combo,combo.width-12,combo.height/2);tryCompare(combo.popup,"visible",true);keyClick(Qt.Key_Down);keyClick(Qt.Key_Return);compare(combo.currentIndex,1);compare(combo.currentText,"second")}
 }
}
