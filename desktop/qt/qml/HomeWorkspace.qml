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
   Repeater {model:[{key:"tools",ar:"الإعداد المتقدم",en:"Advanced Setup",hintAr:"حالة الراوتر • HotSpot • التشخيص",hintEn:"Router status • HotSpot • diagnostics",icon:7},{key:"router:NETWORK",ar:"الشبكة والاتصال",en:"Network & connectivity",hintAr:"Wi-Fi • IP • DHCP • DNS • PPP",hintEn:"Wi-Fi • IP • DHCP • DNS • PPP",icon:10},{key:"router:SYSTEM",ar:"النظام والأمان",en:"System & security",hintAr:"Firewall • حسابات • أوامر",hintEn:"Firewall • accounts • commands",icon:11},{key:"vouchers",ar:"إنشاء الكروت",en:"Voucher Studio",hintAr:"المدة • السعر • الانتهاء • QR",hintEn:"Duration • price • expiry • QR",icon:6},{key:"commerce",ar:"المشتركون والحسابات",en:"Subscribers & accounts",hintAr:"المستحقات • المدفوعات • السجل",hintEn:"Charges • payments • ledger",icon:2},{key:"about",ar:"عن المطور",en:"About developer",hintAr:"FG Machines • الروابط",hintEn:"FG Machines • links",icon:5},{key:"tools:2:online",ar:"المتصلون الآن",en:"Online now",hintAr:"الجلسات • الوقت • الاستهلاك",hintEn:"Sessions • time • usage",icon:10},{key:"tools:2",ar:"كروت الراوتر",en:"Router vouchers",hintAr:"تفعيل • فصل • وقت • باقة",hintEn:"Enable • disconnect • time • profile",icon:6},{key:"commerce:plans",ar:"الباقات",en:"Plans",hintAr:"المدة • السعر • الاشتراكات",hintEn:"Duration • price • subscriptions",icon:9},{key:"commerce:sales",ar:"المبيعات",en:"Sales",hintAr:"التحصيل • الفواتير • الإيصالات",hintEn:"Payments • invoices • receipts",icon:1},{key:"monitor",ar:"المراقبة والتنبيهات",en:"Monitoring & alerts",hintAr:"انقطاع • عودة • أداء",hintEn:"Outages • recovery • health",icon:7},{key:"settings",ar:"مزامنة الخادم",en:"Server synchronization",hintAr:"HTTPS • الفروع • الأعمال",hintEn:"HTTPS • branches • business",icon:8}]
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
