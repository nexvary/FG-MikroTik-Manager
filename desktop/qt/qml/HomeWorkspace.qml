import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
ScrollView {
 id:pane
 property bool arabic:true
 signal openPage(string key)
 function tr(ar,en){return arabic?ar:en}
 clip:true;contentWidth:availableWidth
 Theme {id:theme}
 ColumnLayout {width:pane.availableWidth;spacing:18
  RowLayout {Layout.fillWidth:true
   Image {source:"qrc:/packaging/fg-mtm.png";Layout.preferredWidth:70;Layout.preferredHeight:70;fillMode:Image.PreserveAspectFit}
   ColumnLayout {Layout.fillWidth:true
    Text {Layout.fillWidth:true;text:pane.tr("إدارة شبكتك وأعمالك","Your network and business");color:theme.gold;font.pixelSize:28;font.bold:true;wrapMode:Text.Wrap}
    Text {Layout.fillWidth:true;text:pane.tr("ابدأ بالمهمة التي تحتاجها","Start with the task you need");color:theme.silver;font.pixelSize:17}
   }
  }
  GridLayout {Layout.fillWidth:true;columns:pane.availableWidth>720?3:2;columnSpacing:12;rowSpacing:12
   Repeater {model:[{key:"vouchers",ar:"إنشاء كارت",en:"Create vouchers",hintAr:"QR • حفظ • طباعة",hintEn:"QR • save • print",icon:6},{key:"tools",ar:"المشتركون",en:"Subscribers",hintAr:"تفعيل • فصل • إدارة",hintEn:"Enable • disconnect • manage",icon:2},{key:"commerce",ar:"الباقات والمبيعات",en:"Plans & sales",hintAr:"تجديد • دفع • فواتير",hintEn:"Renewals • payments • invoices",icon:1},{key:"router",ar:"الراوترات",en:"Routers",hintAr:"اكتشاف • اتصال • أوامر",hintEn:"Discover • connect • commands",icon:4},{key:"monitor",ar:"المراقبة والتنبيهات",en:"Monitoring & alerts",hintAr:"انقطاع • عودة • أداء",hintEn:"Outages • recovery • health",icon:7},{key:"tools",ar:"تشخيص وإعداد الشبكة",en:"Diagnostics & setup",hintAr:"HotSpot • حماية • نسخ",hintEn:"HotSpot • protection • backups",icon:11},{key:"billing",ar:"ربط وتجديد الشبكة",en:"Bindings & renewal",hintAr:"المشترك • الحساب • الصلاحية",hintEn:"Subscriber • account • expiry",icon:9},{key:"transfer",ar:"الاستيراد والتقارير",en:"Import & reports",hintAr:"CSV • حسابات • عملات",hintEn:"CSV • accounting • currencies",icon:1},{key:"settings",ar:"مزامنة الخادم",en:"Server synchronization",hintAr:"HTTPS • الفروع • الأعمال",hintEn:"HTTPS • branches • business",icon:8}]
    delegate:Rectangle {required property var modelData;Layout.fillWidth:true;Layout.preferredHeight:150;radius:14;color:theme.panel;border.color:hover.hovered?theme.mint:theme.muted;border.width:hover.hovered?2:1
     ColumnLayout {anchors.fill:parent;anchors.margins:15;spacing:8
      NavIcon {kind:modelData.icon;ink:theme.mint;width:32;height:32}
      Text {Layout.fillWidth:true;text:pane.tr(modelData.ar,modelData.en);color:theme.white;font.bold:true;font.pixelSize:17;wrapMode:Text.Wrap}
      Text {Layout.fillWidth:true;text:pane.tr(modelData.hintAr,modelData.hintEn);color:theme.silver;font.pixelSize:12;wrapMode:Text.Wrap}
     }
     HoverHandler {id:hover}
     TapHandler {onTapped:pane.openPage(modelData.key)}
    }
   }
  }
  Text {Layout.fillWidth:true;color:theme.muted;wrapMode:Text.Wrap;text:pane.tr("المزامنة والمراقبة اختيارية. اتصل بالراوتر لإدارة الشبكة، ويمكنك حفظ أعمالك والكروت المحلية دون اتصال.","Synchronization and monitoring are optional. Connect a router to manage the network; local business and offline vouchers work without a connection.")}
 }
}
