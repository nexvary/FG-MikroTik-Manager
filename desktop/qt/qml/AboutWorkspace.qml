import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
ColumnLayout {
 id:pane
 property bool arabic:true
 function tr(ar,en){return arabic?ar:en}
 Theme {id:theme}
 Image {source:"qrc:/packaging/fg-mtm.png";Layout.alignment:Qt.AlignHCenter;Layout.preferredWidth:112;Layout.preferredHeight:112;fillMode:Image.PreserveAspectFit}
 Text {Layout.fillWidth:true;horizontalAlignment:Text.AlignHCenter;text:"FG MTM • FG Machines";font.pixelSize:30;font.bold:true;color:theme.gold}
 Text {Layout.fillWidth:true;horizontalAlignment:Text.AlignHCenter;text:pane.tr("إدارة شبكات MikroTik والأعمال","MikroTik network & business management");font.pixelSize:18;color:theme.silver;wrapMode:Text.Wrap}
 Text {Layout.fillWidth:true;horizontalAlignment:Text.AlignHCenter;text:pane.tr("المطور الرئيسي: علاء محمد","Lead developer: Alaa Mohamed");font.pixelSize:18;color:theme.mint}
 Text {Layout.fillWidth:true;horizontalAlignment:Text.AlignHCenter;text:pane.tr("برنامج مجاني لوجه الله تعالى","A free application for the sake of Allah");color:theme.silver;wrapMode:Text.Wrap}
 FgButton {Layout.alignment:Qt.AlignHCenter;text:"fgmachines.org";onClicked:Qt.openUrlExternally("https://fgmachines.org")}
 FgButton {Layout.alignment:Qt.AlignHCenter;text:pane.tr("صفحة المطور","Developer Facebook");onClicked:Qt.openUrlExternally("https://www.facebook.com/share/1EKVAyZZ2C/")}
 FgButton {Layout.alignment:Qt.AlignHCenter;text:pane.tr("صفحة FG Machines","FG Machines Facebook");onClicked:Qt.openUrlExternally("https://www.facebook.com/share/1T7r3WpH8Y/")}
 Item {Layout.fillHeight:true}
}
