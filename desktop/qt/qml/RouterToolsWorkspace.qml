import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: workspace
 property bool arabic: true
 property string inspectedInterface:""
 signal connectRouter()
 property alias currentTab:tabs.currentIndex
 property alias onlineOnly:subscribers.onlineOnly
 property string userId: ""
 property string logoDataUri:routerTools.portalDesign.logoDataUri||""
 property var selectedNetworks: []
 function showTab(index,online){tabs.currentIndex=index;subscribers.onlineOnly=online||false}
 function tr(ar,en){return arabic?ar:en}
 function design(){return {networkName:networkName.text,supportPhone:support.text,welcome:welcome.text,color:color.text,terms:terms.text,website:website.text,logoDataUri:workspace.logoDataUri}}
 Theme {id: theme}
 FileDialog {id:logoPicker;fileMode:FileDialog.OpenFile;nameFilters:["Images (*.png *.jpg *.jpeg *.webp)"];onAccepted:{let logo=routerTools.portalLogo(selectedFile);if(logo.length)workspace.logoDataUri=logo}}
 RowLayout {Layout.fillWidth: true; FgButton {text: workspace.tr("فحص وتحديث","Inspect & refresh"); enabled: !routerTools.busy&&!backend.busy; onClicked: routerTools.inspect(workspace.inspectedInterface); accent: theme.mint} Text {text: routerTools.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}}
 TabBar {id: tabs; Layout.fillWidth: true; FgTab {text: workspace.tr("التشخيص","Diagnostics")} FgTab {text: workspace.tr("إعداد HotSpot","HotSpot setup")} FgTab {text: workspace.tr("المشتركون","Subscribers")} FgTab {text: workspace.tr("الحماية","Protection")} FgTab {text: workspace.tr("صفحة الدخول","Login portal")} FgTab {text: workspace.tr("النسخ والساعة","Backup & clock")}}
 StackLayout {
  currentIndex: tabs.currentIndex; Layout.fillWidth: true; Layout.fillHeight: true
  ColumnLayout {spacing:14
   Rectangle {visible:routerTools.checks.length===0;Layout.fillWidth:true;implicitHeight:136;radius:18;color:theme.raised;border.color:"#315366"
    RowLayout {anchors.fill:parent;anchors.margins:20;spacing:18
     NavIcon {kind:7;ink:theme.blue;Layout.preferredWidth:66;Layout.preferredHeight:66}
     ColumnLayout {Layout.fillWidth:true;spacing:8
      Text {Layout.fillWidth:true;wrapMode:Text.Wrap;text:workspace.tr("ابدأ بفحص شبكتك","Start with a network check");color:theme.gold;font.pixelSize:24;font.bold:true}
      Text {Layout.fillWidth:true;wrapMode:Text.Wrap;text:workspace.tr("اربط الراوتر ثم افحص إعدادات الإنترنت وHotSpot والحماية. نتائج الفحص تظهر بعد قراءة الجهاز فقط.","Connect a router to inspect Internet, HotSpot and protection. Results appear only after reading the device.");color:theme.silver;font.pixelSize:14}
     }
    }
   }
   GridLayout {visible:routerTools.checks.length===0;Layout.fillWidth:true;columns:3;columnSpacing:12
    Repeater {model:[{ar:"١ · اتصال الراوتر",en:"1 · Connect router",hintAr:"العنوان والحساب وAPI",hintEn:"Address, account and API",icon:10,tint:theme.blue,action:0},{ar:"٢ · فحص الجاهزية",en:"2 · Inspect readiness",hintAr:"DHCP • DNS • NAT • HotSpot",hintEn:"DHCP • DNS • NAT • HotSpot",icon:7,tint:theme.mint,action:1},{ar:"٣ · مراجعة الحماية",en:"3 · Review protection",hintAr:"الشبكات المختارة وفلترة DNS",hintEn:"Selected networks and DNS filtering",icon:11,tint:"#B897FF",action:2}]
     delegate:Rectangle {required property var modelData;Layout.fillWidth:true;implicitHeight:172;radius:14;color:theme.panel;border.color:"#294152"
      ColumnLayout {anchors.fill:parent;anchors.margins:16;spacing:9
       NavIcon {kind:modelData.icon;ink:modelData.tint;Layout.preferredWidth:42;Layout.preferredHeight:42}
       Text {Layout.fillWidth:true;wrapMode:Text.Wrap;text:workspace.tr(modelData.ar,modelData.en);color:modelData.tint;font.pixelSize:16;font.bold:true}
       Text {Layout.fillWidth:true;wrapMode:Text.Wrap;text:workspace.tr(modelData.hintAr,modelData.hintEn);color:theme.silver;font.pixelSize:12}
       FgButton {Layout.fillWidth:true;implicitHeight:36;text:workspace.tr("فتح","Open");accent:modelData.tint;onClicked:{if(modelData.action===0)workspace.connectRouter();else if(modelData.action===1)routerTools.inspect(workspace.inspectedInterface);else tabs.currentIndex=3}}
      }
     }
    }
   }
   ScrollView {Layout.fillWidth:true;Layout.fillHeight:true;clip: true; ListView {model: routerTools.checks; implicitWidth: workspace.width-24; spacing: 6; delegate: Rectangle {required property var modelData; width: ListView.view.width; height: 82; radius: 12; color: theme.panel; RowLayout {anchors.fill: parent; anchors.margins: 14; Text {text: workspace.tr(modelData.ar||modelData.check,modelData.en||modelData.check); color: theme.white; Layout.fillWidth: true;wrapMode:Text.Wrap} Text {text: (modelData.detail||"").includes(" • ")?(modelData.detail||"").split(" • ")[workspace.arabic?0:1]:modelData.detail; color: theme.muted; Layout.fillWidth: true; elide: Text.ElideRight} Text {text: modelData.state; color: modelData.state==="READY"?theme.mint:theme.gold}}}}}
  }
  ScrollView {clip: true; ColumnLayout {width: workspace.width-24; spacing: 12
   Text {text: workspace.tr("اختر شبكة العملاء. الخطة تستبعد واجهة الإنترنت وتحافظ على البوابة الحالية.","Select the client network. The plan excludes WAN and preserves the current gateway."); color: theme.silver; wrapMode: Text.Wrap; Layout.fillWidth: true}
   GridLayout {columns: 2; Layout.fillWidth: true
    Text {text: workspace.tr("واجهة العملاء","Client interface"); color: theme.silver} FgCombo {id: client; model: routerTools.interfaces; textRole: "name"; Layout.fillWidth: true;onActivated:workspace.inspectedInterface=currentText}
    Text {text: workspace.tr("عنوان البوابة","Gateway CIDR"); color: theme.silver} FgField {id: gateway; text: "192.168.10.1/24"; Layout.fillWidth: true}
    Text {text: workspace.tr("شبكة العملاء","Client subnet"); color: theme.silver} FgField {id: subnet; text: "192.168.10.0/24"; Layout.fillWidth: true}
    Text {text: workspace.tr("مدى العناوين","Address pool"); color: theme.silver} FgField {id: pool; text: "192.168.10.10-192.168.10.250"; Layout.fillWidth: true}
    Text {text: workspace.tr("اسم الدخول","Login DNS name"); color: theme.silver} FgField {id: dnsName; text: "wifi.local"; Layout.fillWidth: true}
   }
   CheckBox {id: syncTime; checked: true; text: workspace.tr("ضبط الساعة وتفعيل NTP تلقائيًا","Set clock and enable NTP automatically")}
   CheckBox {id: replacePortal; text: workspace.tr("استبدال صفحة الدخول الموجودة","Replace existing login portal")}
   RowLayout {FgButton {text: workspace.tr("معاينة خطة HotSpot","Preview HotSpot plan"); enabled: !routerTools.busy; onClicked: routerTools.planHotspot({interface:client.currentText,gateway:gateway.text,network:subnet.text,pool:pool.text,dnsName:dnsName.text,synchronizeTime:syncTime.checked,replacePortal:replacePortal.checked,design:workspace.design()})} FgField {id: wan; placeholderText: workspace.tr("واجهة الإنترنت","WAN interface")} FgButton {text: workspace.tr("خطة جمع منافذ العملاء","Preview client bridge"); enabled: !routerTools.busy; onClicked: routerTools.planPorts(client.currentText,wan.text)}}
   Repeater {model: routerTools.changes; Text {required property var modelData; text: modelData.label+" • /"+modelData.menu+"/"+modelData.action; color: theme.gold; wrapMode: Text.Wrap; Layout.fillWidth: true}}
   RowLayout {FgField {id: backupPassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور النسخة الاحتياطية (12+)","Router backup password (12+)"); Layout.fillWidth: true} FgButton {text: workspace.tr("تأكيد الخطة وبدء الإعداد","Confirm plan & configure"); enabled: backupPassword.text.length>=12&&!routerTools.busy; onClicked: {routerTools.apply(backupPassword.text);backupPassword.clear()}}}
  }}
  SubscriberWorkspace {id:subscribers;arabic:workspace.arabic}
  ScrollView {clip: true; ColumnLayout {width: workspace.width-24; spacing: 14
   Text {text: workspace.tr("فلترة DNS للشبكات الخاصة المختارة. تُحفظ الإعدادات السابقة لاستعادتها.","DNS filtering for selected private client networks. Previous settings are saved for restoration."); color: theme.silver; Layout.fillWidth: true; wrapMode: Text.Wrap}
   Repeater {model:routerTools.dhcpNetworks;CheckBox {required property var modelData; text:modelData.address+" • "+workspace.tr("DNS: ","DNS: ")+(modelData["dns-server"]||"");checked:workspace.selectedNetworks.indexOf(modelData[".id"])>=0;onClicked:{let next=workspace.selectedNetworks.slice(),i=next.indexOf(modelData[".id"]);if(checked&&i<0)next.push(modelData[".id"]);else if(!checked&&i>=0)next.splice(i,1);workspace.selectedNetworks=next}}}
   FgCombo {id: dnsMode; model: ["FAMILY","ADS_TRACKERS"]; Layout.fillWidth: true}
   FgButton {text: workspace.tr("معاينة خطة الحماية","Preview protection plan"); enabled: !routerTools.busy; onClicked: routerTools.planDns(workspace.selectedNetworks,dnsMode.currentText)}
   Repeater {model: routerTools.changes; Text {required property var modelData; text: modelData.label+" • "+JSON.stringify(modelData.attributes); color: theme.gold; Layout.fillWidth: true; wrapMode: Text.Wrap}}
   RowLayout {FgField {id: dnsBackup; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور النسخة الاحتياطية (12+)","Backup password (12+)"); Layout.fillWidth: true} FgButton {text: workspace.tr("تأكيد الحماية","Confirm protection"); enabled: dnsBackup.text.length>=12&&!routerTools.busy; onClicked: {routerTools.apply(dnsBackup.text);dnsBackup.clear()}}}
   FgButton {text: workspace.tr("استعادة DNS السابق","Restore previous DNS"); enabled: !routerTools.busy; onClicked: routerTools.restoreDns()}
  }}
  ScrollView {clip: true; ColumnLayout {width: workspace.width-24; spacing: 12
   GridLayout {columns: 2; Layout.fillWidth: true
    Text {text: workspace.tr("اسم الشبكة","Network name"); color: theme.silver} FgField {id: networkName; text:routerTools.portalDesign.networkName||"FG Machines WiFi"; Layout.fillWidth: true}
    Text {text: workspace.tr("هاتف الدعم","Support phone"); color: theme.silver} FgField {id: support;text:routerTools.portalDesign.supportPhone||""; Layout.fillWidth: true}
    Text {text: workspace.tr("الترحيب","Welcome"); color: theme.silver} FgField {id: welcome; text:routerTools.portalDesign.welcome||"أهلاً بك في شبكتنا"; Layout.fillWidth: true}
    Text {text: workspace.tr("لون الصفحة","Page color"); color: theme.silver} FgField {id: color; text:routerTools.portalDesign.color||"#159DFF"; Layout.fillWidth: true}
    Text {text: workspace.tr("الشروط","Terms"); color: theme.silver} FgField {id: terms;text:routerTools.portalDesign.terms||""; Layout.fillWidth: true}
    Text {text: workspace.tr("الموقع HTTPS","Website HTTPS"); color: theme.silver} FgField {id: website;text:routerTools.portalDesign.website||""; Layout.fillWidth: true}
    Text {text: workspace.tr("بروفايل HotSpot","HotSpot profile"); color: theme.silver} FgCombo {id: serverProfile; model: routerTools.profiles; textRole: "name"; Layout.fillWidth: true}
   }
   Flow {Layout.fillWidth:true;spacing:8;FgButton {text:workspace.tr("اختيار شعار","Choose logo");onClicked:logoPicker.open()} FgButton {text:workspace.tr("حفظ التصميم","Save design");onClicked:routerTools.savePortalDesign(workspace.design())} FgButton {text:workspace.tr("معاينة الحالة","Status preview");onClicked:routerTools.previewPortal(workspace.design(),"status.html")} FgButton {text: workspace.tr("معاينة الصفحة","Preview portal"); onClicked: routerTools.previewPortal(workspace.design())} FgButton {text: workspace.tr("تثبيت الصفحة","Install portal"); enabled: serverProfile.currentIndex>=0&&!routerTools.busy; onClicked: portalInstall.open()} FgButton {text: workspace.tr("استعادة الصفحة السابقة","Restore previous portal"); enabled: !routerTools.busy; onClicked: routerTools.restorePortal()}}
   Text {text: workspace.tr("تُرفع الملفات إلى مجلد جديد وتُفحص قبل تحويل صفحة الدخول إليه.","Files are uploaded to a new directory and verified before switching the login portal."); color: theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap}
  }}
  ColumnLayout {
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:workspace.tr("تُحفظ نسخة مشفرة قبل ضبط الساعة أو إعادة التشغيل أو الاستعادة. يمكنك عرض كلمات فتح النسخ المحفوظة على هذا الكمبيوتر.","An encrypted backup is saved before setting the clock, rebooting or restoring. Saved backup passwords can be viewed on this PC.")}
   Flow {Layout.fillWidth:true;spacing:8;FgButton {text:workspace.tr("تحديث النسخ","Refresh backups");enabled:!routerTools.busy;onClicked:routerTools.loadBackups()} FgCombo {id:backupList;width:280;model:routerTools.backups;textRole:"name";onActivated:restoreName.text=currentText} FgButton {text:workspace.tr("كلمة فتح النسخة","Backup password");enabled:backupList.currentIndex>=0&&!routerTools.busy;onClicked:{let secret=routerTools.backupPassword(backupList.currentText);if(secret.length){savedSecret.text=secret;secretDialog.open()}}} FgButton {text:workspace.tr("إصلاح DNS المفقود","Repair missing DNS");enabled:!routerTools.busy;onClicked:dnsRepair.open()}}
   FgButton {text: workspace.tr("ضبط الساعة وNTP","Set clock & NTP"); enabled: !routerTools.busy; onClicked: routerTools.synchronizeClock()}
   RowLayout {FgField {id: routerBackupPassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور النسخة (12+)","Backup password (12+)"); Layout.fillWidth: true} FgButton {text: workspace.tr("حفظ نسخة الراوتر","Save router backup"); enabled: routerBackupPassword.text.length>=12&&!routerTools.busy; onClicked: {routerTools.backup(routerBackupPassword.text);routerBackupPassword.clear()}}}
   RowLayout {FgField {id: restoreName; placeholderText: "fg-mtm-....backup"; Layout.fillWidth: true} FgField {id: restorePassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور الاستعادة","Restore password"); Layout.fillWidth: true} FgButton {text: workspace.tr("استعادة الراوتر","Restore router"); enabled: restoreName.text.length>0&&restorePassword.text.length>0&&!routerTools.busy; onClicked: routerRestore.open()}}
   FgButton {text: workspace.tr("تصدير إعدادات الراوتر","Export router configuration"); enabled: !routerTools.busy; onClicked: routerTools.exportConfiguration()}
   FgButton {text: workspace.tr("مسح ذاكرة DNS","Flush DNS cache"); enabled: !routerTools.busy; onClicked: routerTools.flushDns()}
   FgButton {text: workspace.tr("إعادة تشغيل الراوتر","Reboot router"); accent: theme.error; enabled: !routerTools.busy; onClicked: routerReboot.open()}
   Item {Layout.fillHeight: true}
  }
 }
 FgDialog {id: userAction; property string action: ""; function openFor(value){action=value;open()} parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("تأكيد إجراء المشترك","Confirm subscriber action"); contentItem: ColumnLayout {Text {text: userAction.action+" • "+workspace.userId; color: theme.white} FgButton {text: workspace.tr("تأكيد","Confirm"); onClicked: {routerTools.subscriber(userAction.action,workspace.userId,{});userAction.close()}}}}
 FgDialog {id: portalInstall; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("تأكيد تثبيت صفحة الدخول","Confirm portal installation"); contentItem: FgButton {text: workspace.tr("تثبيت على البروفايل المحدد","Install on selected profile"); onClicked: {routerTools.installPortal(routerTools.profiles[serverProfile.currentIndex][".id"],workspace.design());portalInstall.close()}}}
 FgDialog {id: routerRestore; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("استعادة إعدادات الراوتر","Restore router configuration"); contentItem: ColumnLayout {Text {text: workspace.tr("قد تنقطع الجلسة أثناء استعادة إعدادات الراوتر.","The connection may close during router restoration."); color: theme.white} FgButton {text: workspace.tr("تأكيد الاستعادة","Confirm restore"); onClicked: {routerTools.restoreBackup(restoreName.text,restorePassword.text);restorePassword.clear();routerRestore.close()}}}}
 FgDialog {id: routerReboot; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("إعادة تشغيل الراوتر","Reboot router"); contentItem: FgButton {text: workspace.tr("تأكيد إعادة التشغيل","Confirm reboot"); onClicked: {routerTools.reboot();routerReboot.close()}}}
 FgDialog {id:secretDialog;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:480;title:workspace.tr("كلمة فتح النسخة المحفوظة","Saved backup password");onClosed:savedSecret.clear();contentItem:FgField {id:savedSecret;readOnly:true;selectByMouse:true}}
 FgDialog {id:dnsRepair;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:Math.min(580,parent.width-40);title:workspace.tr("تأكيد إصلاح DNS","Confirm DNS repair");contentItem:ColumnLayout {Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:workspace.tr("سيُحفظ Backup مشفر ثم تُضبط خوادم DNS فقط عندما تكون الإعدادات الحالية فارغة.","An encrypted backup is saved before assigning DNS servers only when current settings are empty.")} FgButton {text:workspace.tr("تأكيد الإصلاح","Confirm repair");onClicked:{routerTools.repairDns();dnsRepair.close()}}}}

}
