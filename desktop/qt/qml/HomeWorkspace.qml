import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
ScrollView {
 id:pane
 property bool arabic:true
 property bool routerConnected:false
 property bool serverConnected:false
 signal openPage(string key)
 function tr(ar,en){return arabic?ar:en}
 clip:true;contentWidth:availableWidth
 Theme {id:theme}
 ColumnLayout {width:pane.availableWidth;spacing:14
  Rectangle {Layout.fillWidth:true;implicitHeight:116;radius:20
   gradient:Gradient {GradientStop {position:0;color:"#14354A"} GradientStop {position:1;color:"#0B202D"}}
   border.color:"#2C5269"
   RowLayout {anchors.fill:parent;anchors.margins:20;spacing:18
    Image {source:"qrc:/packaging/fg-mtm.png";Layout.preferredWidth:72;Layout.preferredHeight:72;fillMode:Image.PreserveAspectFit}
    ColumnLayout {Layout.fillWidth:true;spacing:6
     Text {Layout.fillWidth:true;text:pane.tr("مركز إدارة الشبكة والأعمال","Network & business command center");color:theme.gold;font.pixelSize:25;font.bold:true;wrapMode:Text.Wrap}
     Text {Layout.fillWidth:true;text:pane.tr("شبكتك، مشتركونك، ومبيعاتك في مكان واحد","Your routers, subscribers and sales in one place");color:theme.silver;font.pixelSize:14;wrapMode:Text.Wrap}
    }
   }
  }
  GridLayout {Layout.fillWidth:true;columns:3;columnSpacing:10
   Repeater {model:[{ar:"اتصال الراوتر",en:"Router connection",tint:theme.gold,icon:10,key:"router"},{ar:"مزامنة الأعمال",en:"Business synchronization",tint:theme.blue,icon:8,key:"settings"},{ar:"الكروت المحلية",en:"Local vouchers",tint:theme.mint,icon:6,key:"vouchers"}]
    delegate:Rectangle {id:statusTile;required property var modelData;property color statusAccent:modelData.key==="router"?(pane.routerConnected?theme.mint:theme.gold):modelData.tint;Layout.fillWidth:true;implicitHeight:76;radius:13;color:theme.raised;border.color:"#294152"
     RowLayout {anchors.fill:parent;anchors.margins:12;spacing:10
      NavIcon {kind:modelData.icon;ink:statusTile.statusAccent;Layout.preferredWidth:36;Layout.preferredHeight:36}
      ColumnLayout {Layout.fillWidth:true;spacing:4
       Text {Layout.fillWidth:true;text:pane.tr(modelData.ar,modelData.en);color:theme.muted;font.pixelSize:12;wrapMode:Text.Wrap}
       Text {Layout.fillWidth:true;text:modelData.key==="router"?(pane.routerConnected?pane.tr("متصل","Connected"):pane.tr("غير متصل","Disconnected")):modelData.key==="settings"?(pane.serverConnected?pane.tr("الخادم متصل","Server connected"):pane.tr("الخادم غير متصل","Server disconnected")):pane.tr("إنشاء وحفظ دون اتصال","Create & save offline");color:statusTile.statusAccent;font.pixelSize:13;font.bold:true;wrapMode:Text.Wrap}
      }
     }
     TapHandler {onTapped:pane.openPage(modelData.key)}
    }
   }
  }
  Text {text:pane.tr("مساحات العمل","Workspaces");color:theme.silver;font.pixelSize:19;font.bold:true}
  GridLayout {Layout.fillWidth:true;columns:pane.availableWidth>=650?3:2;columnSpacing:10;rowSpacing:10
   Repeater {model:[{key:"tools",ar:"الإعداد المتقدم",en:"Advanced Setup",hintAr:"حالة الراوتر • HotSpot • التشخيص",hintEn:"Router status • HotSpot • diagnostics",icon:7},{key:"router:NETWORK",ar:"الشبكة والاتصال",en:"Network & connectivity",hintAr:"Wi-Fi • IP • DHCP • DNS • PPP",hintEn:"Wi-Fi • IP • DHCP • DNS • PPP",icon:10},{key:"router:SYSTEM",ar:"النظام والأمان",en:"System & security",hintAr:"Firewall • حسابات • أوامر",hintEn:"Firewall • accounts • commands",icon:11},{key:"vouchers",ar:"إنشاء الكروت",en:"Voucher Studio",hintAr:"المدة • السعر • الانتهاء • QR",hintEn:"Duration • price • expiry • QR",icon:6},{key:"commerce",ar:"المشتركون والحسابات",en:"Subscribers & accounts",hintAr:"المستحقات • المدفوعات • السجل",hintEn:"Charges • payments • ledger",icon:2},{key:"about",ar:"عن المطور",en:"About developer",hintAr:"FG Machines • الروابط",hintEn:"FG Machines • links",icon:5},{key:"tools:2:online",ar:"المتصلون الآن",en:"Online now",hintAr:"الجلسات • الوقت • الاستهلاك",hintEn:"Sessions • time • usage",icon:10},{key:"tools:2",ar:"كروت الراوتر",en:"Router vouchers",hintAr:"تفعيل • فصل • وقت • باقة",hintEn:"Enable • disconnect • time • profile",icon:6},{key:"commerce:plans",ar:"الباقات",en:"Plans",hintAr:"المدة • السعر • الاشتراكات",hintEn:"Duration • price • subscriptions",icon:9},{key:"commerce:sales",ar:"المبيعات",en:"Sales",hintAr:"التحصيل • الفواتير • الإيصالات",hintEn:"Payments • invoices • receipts",icon:1},{key:"monitor",ar:"المراقبة والتنبيهات",en:"Monitoring & alerts",hintAr:"انقطاع • عودة • أداء",hintEn:"Outages • recovery • health",icon:7},{key:"settings",ar:"مزامنة الخادم",en:"Server synchronization",hintAr:"HTTPS • الفروع • الأعمال",hintEn:"HTTPS • branches • business",icon:8}]
    delegate:Rectangle {id:tile;required property var modelData
     property color accent:[theme.blue,theme.mint,"#B897FF",theme.gold,"#FF91AB","#7ED9F5"][modelData.icon%6]
     Layout.fillWidth:true;Layout.preferredHeight:126;radius:16;color:hover.hovered?"#183647":theme.panel;border.color:hover.hovered?tile.accent:"#294152";border.width:hover.hovered?2:1
     Rectangle {anchors.top:parent.top;anchors.left:parent.left;anchors.right:parent.right;height:3;radius:2;color:tile.accent;opacity:0.8}
     RowLayout {anchors.fill:parent;anchors.margins:15;spacing:12
      NavIcon {kind:modelData.icon;ink:tile.accent;Layout.preferredWidth:48;Layout.preferredHeight:48}
      ColumnLayout {Layout.fillWidth:true;spacing:8
       Text {Layout.fillWidth:true;text:pane.tr(modelData.ar,modelData.en);color:tile.accent;font.bold:true;font.pixelSize:17;wrapMode:Text.Wrap}
       Text {Layout.fillWidth:true;text:pane.tr(modelData.hintAr,modelData.hintEn);color:theme.silver;font.pixelSize:12;wrapMode:Text.Wrap}
      }
     }
     HoverHandler {id:hover;cursorShape:Qt.PointingHandCursor}
     TapHandler {onTapped:pane.openPage(modelData.key)}
    }
   }
  }
 }
}
