import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
Rectangle {
 id:pane
 property bool arabic:true
 function tr(ar,en){return arabic?ar:en}
 color:"#071722";radius:18;border.color:"#284457"
 Theme {id:theme}
 ScrollView {
  id:aboutScroll;anchors.fill:parent;anchors.margins:16;clip:true;contentWidth:availableWidth
  ColumnLayout {
   width:aboutScroll.availableWidth;spacing:14
   Rectangle {
    Layout.fillWidth:true;implicitHeight:210;radius:20;color:"#0B2131";border.color:theme.gold
    RowLayout {anchors.fill:parent;anchors.margins:24;spacing:24
     Rectangle {
      Layout.preferredWidth:150;Layout.preferredHeight:150;radius:30;color:"#07121B";border.color:theme.gold;border.width:2
      Image {anchors.centerIn:parent;width:122;height:122;source:"qrc:/packaging/fg-mtm.png";fillMode:Image.PreserveAspectFit}
     }
     ColumnLayout {Layout.fillWidth:true;spacing:8
      Text {Layout.fillWidth:true;text:"FG MTM";font.pixelSize:34;font.bold:true;color:theme.gold;horizontalAlignment:pane.arabic?Text.AlignRight:Text.AlignLeft}
      Text {Layout.fillWidth:true;text:"FG Machines";font.pixelSize:22;font.bold:true;color:theme.blue;horizontalAlignment:pane.arabic?Text.AlignRight:Text.AlignLeft}
      Text {Layout.fillWidth:true;text:pane.tr("منصة إدارة MikroTik والشبكات والأعمال","MikroTik, network and business management platform");font.pixelSize:17;color:theme.silver;wrapMode:Text.Wrap;horizontalAlignment:pane.arabic?Text.AlignRight:Text.AlignLeft}
      RowLayout {
       spacing:8
       Rectangle {implicitWidth:116;implicitHeight:30;radius:15;color:"#123B31";border.color:theme.mint;Text {anchors.centerIn:parent;text:"Windows";color:theme.mint;font.bold:true}}
       Rectangle {implicitWidth:116;implicitHeight:30;radius:15;color:"#142C3F";border.color:theme.blue;Text {anchors.centerIn:parent;text:"v0.17.4";color:theme.blue;font.bold:true}}
      }
     }
    }
   }
   GridLayout {
    Layout.fillWidth:true;columns:2;columnSpacing:14;rowSpacing:14
    Rectangle {
     Layout.fillWidth:true;Layout.preferredHeight:164;radius:16;color:theme.panel;border.color:"#35556A"
     ColumnLayout {anchors.fill:parent;anchors.margins:16;spacing:8
      Text {text:pane.tr("المطور","Developer");color:theme.gold;font.pixelSize:18;font.bold:true}
      Text {Layout.fillWidth:true;text:pane.tr("علاء محمد","Alaa Mohamed");color:theme.white;font.pixelSize:22;font.bold:true;wrapMode:Text.Wrap}
      Text {Layout.fillWidth:true;text:pane.tr("التطوير الرئيسي، تصميم المنتج، وهندسة تجربة الاستخدام.","Lead development, product design and user-experience engineering.");color:theme.silver;wrapMode:Text.Wrap}
     }
    }
    Rectangle {
     Layout.fillWidth:true;Layout.preferredHeight:164;radius:16;color:theme.panel;border.color:"#35556A"
     ColumnLayout {anchors.fill:parent;anchors.margins:16;spacing:8
      Text {text:pane.tr("فلسفة المنتج","Product principles");color:theme.mint;font.pixelSize:18;font.bold:true}
      Text {Layout.fillWidth:true;text:pane.tr("واجهة واضحة • تشغيل محلي آمن • مراجعة قبل التغييرات الحساسة • أدوات عملية لإدارة الشبكة.","Clear UI • secure local operation • review before sensitive changes • practical network-management tools.");color:theme.silver;wrapMode:Text.Wrap}
      Text {Layout.fillWidth:true;text:pane.tr("الأسرار وكلمات المرور لا تُعرض في سجلات النتائج.","Secrets and passwords are redacted from result logs.");color:theme.muted;wrapMode:Text.Wrap}
     }
    }
   }
   Rectangle {
    Layout.fillWidth:true;implicitHeight:112;radius:16;color:"#0A1D2B";border.color:"#35556A"
    RowLayout {anchors.fill:parent;anchors.margins:16;spacing:12
     ColumnLayout {Layout.fillWidth:true;spacing:4
      Text {text:pane.tr("مشروع مجاني","Free application");color:theme.blue;font.pixelSize:18;font.bold:true}
      Text {Layout.fillWidth:true;text:pane.tr("برنامج مجاني لوجه الله تعالى، مع هدف واضح: تبسيط إدارة الشبكات بدون التضحية بالأمان أو القدرة المهنية.","A free application built to simplify network administration without sacrificing security or professional capability.");color:theme.silver;wrapMode:Text.Wrap}
     }
    }
   }
   Rectangle {
    Layout.fillWidth:true;implicitHeight:132;radius:16;color:theme.panel;border.color:"#35556A"
    ColumnLayout {anchors.fill:parent;anchors.margins:16;spacing:10
     Text {text:pane.tr("روابط رسمية","Official links");color:theme.gold;font.pixelSize:18;font.bold:true}
     Flow {Layout.fillWidth:true;spacing:10
      FgButton {text:"fgmachines.org";filled:true;accent:theme.blue;onClicked:Qt.openUrlExternally("https://fgmachines.org")}
      FgButton {text:pane.tr("صفحة المطور","Developer Facebook");accent:theme.mint;onClicked:Qt.openUrlExternally("https://www.facebook.com/share/1EKVAyZZ2C/")}
      FgButton {text:pane.tr("FG Machines على Facebook","FG Machines Facebook");accent:theme.gold;onClicked:Qt.openUrlExternally("https://www.facebook.com/share/1T7r3WpH8Y/")}
     }
    }
   }
   Text {Layout.fillWidth:true;text:pane.tr("FG MTM ليس تطبيقًا رسميًا من MikroTik. MikroTik وRouterOS علامتان تجاريتان لأصحابهما.","FG MTM is not an official MikroTik application. MikroTik and RouterOS are trademarks of their respective owners.");color:theme.muted;font.pixelSize:12;wrapMode:Text.Wrap;horizontalAlignment:Text.AlignHCenter}
   Item {Layout.preferredHeight:6}
  }
 }
}
