import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: workspace
 property bool arabic: true
 property string userId: ""
 property string logoDataUri:routerTools.portalDesign.logoDataUri||""
 property var selectedNetworks: []
 function tr(ar,en){return arabic?ar:en}
 function design(){return {networkName:networkName.text,supportPhone:support.text,welcome:welcome.text,color:color.text,terms:terms.text,website:website.text,logoDataUri:workspace.logoDataUri}}
 Theme {id: theme}
 FileDialog {id:logoPicker;fileMode:FileDialog.OpenFile;nameFilters:["Images (*.png *.jpg *.jpeg *.webp)"];onAccepted:{let logo=routerTools.portalLogo(selectedFile);if(logo.length)workspace.logoDataUri=logo}}
 RowLayout {Layout.fillWidth: true; FgButton {text: workspace.tr("فحص وتحديث","Inspect & refresh"); enabled: !routerTools.busy&&!backend.busy; onClicked: routerTools.inspect(); accent: theme.mint} Text {text: routerTools.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}}
 TabBar {id: tabs; Layout.fillWidth: true; FgTab {text: workspace.tr("التشخيص","Diagnostics")} FgTab {text: workspace.tr("إعداد HotSpot","HotSpot setup")} FgTab {text: workspace.tr("المشتركون","Subscribers")} FgTab {text: workspace.tr("الحماية","Protection")} FgTab {text: workspace.tr("صفحة الدخول","Login portal")} FgTab {text: workspace.tr("النسخ والساعة","Backup & clock")}}
 StackLayout {
  currentIndex: tabs.currentIndex; Layout.fillWidth: true; Layout.fillHeight: true
  ColumnLayout {Text {visible:routerTools.checks.length===0;Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:workspace.tr("اتصل بالراوتر من قسم الراوترات، ثم اضغط فحص وتحديث لعرض جاهزية الشبكة.","Connect in the Routers section, then select Inspect & refresh to see network readiness.")} ScrollView {Layout.fillWidth:true;Layout.fillHeight:true;clip: true; ListView {model: routerTools.checks; implicitWidth: workspace.width-24; spacing: 6; delegate: Rectangle {required property var modelData; width: ListView.view.width; height: 62; radius: 12; color: theme.panel; RowLayout {anchors.fill: parent; anchors.margins: 14; Text {text: modelData.check; color: theme.white; Layout.fillWidth: true} Text {text: modelData.detail; color: theme.muted; Layout.fillWidth: true; elide: Text.ElideRight} Text {text: modelData.state; color: modelData.state==="READY"?theme.mint:theme.gold}}}}}}
  ScrollView {clip: true; ColumnLayout {width: workspace.width-24; spacing: 12
   Text {text: workspace.tr("اختر شبكة العملاء. الخطة تستبعد واجهة الإنترنت وتحافظ على البوابة الحالية.","Select the client network. The plan excludes WAN and preserves the current gateway."); color: theme.silver; wrapMode: Text.Wrap; Layout.fillWidth: true}
   GridLayout {columns: 2; Layout.fillWidth: true
    Text {text: workspace.tr("واجهة العملاء","Client interface"); color: theme.silver} ComboBox {id: client; model: routerTools.interfaces; textRole: "name"; Layout.fillWidth: true}
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
  ColumnLayout {
   RowLayout {Layout.fillWidth: true; FgButton {text: workspace.tr("تفعيل","Enable"); enabled: workspace.userId.length>0&&!routerTools.busy; onClicked: routerTools.subscriber("enable",workspace.userId,{})} FgButton {text: workspace.tr("تعطيل","Disable"); enabled: workspace.userId.length>0&&!routerTools.busy; onClicked: userAction.openFor("disable")} FgButton {text: workspace.tr("فصل الجلسة","Disconnect"); enabled: workspace.userId.length>0&&!routerTools.busy; onClicked: userAction.openFor("disconnect")} FgButton {text: workspace.tr("حذف","Delete"); accent: theme.error; enabled: workspace.userId.length>0&&!routerTools.busy; onClicked: userAction.openFor("delete")}}
   RowLayout {FgField {id: userProfile; placeholderText: workspace.tr("الباقة الجديدة","New profile"); Layout.fillWidth: true} FgButton {text: workspace.tr("تغيير الباقة","Change profile"); enabled: workspace.userId.length>0&&userProfile.text.length>0&&!routerTools.busy; onClicked: routerTools.subscriber("update",workspace.userId,{attributes:{profile:userProfile.text}})} FgField {id: seconds; placeholderText: workspace.tr("زيادة الوقت بالثواني","Extra seconds"); Layout.fillWidth: true} FgButton {text: workspace.tr("زيادة الوقت","Add time"); enabled: workspace.userId.length>0&&!routerTools.busy; onClicked: routerTools.subscriber("add_time",workspace.userId,{seconds:seconds.text})}}
   ListView {Layout.fillWidth: true; Layout.fillHeight: true; model: routerTools.users; clip: true; spacing: 6; ScrollBar.vertical: ScrollBar {} delegate: Rectangle {required property var modelData; width: ListView.view.width; height: 74; radius: 12; color: workspace.userId===modelData[".id"]?theme.raised:theme.panel; border.color: workspace.userId===modelData[".id"]?theme.blue:theme.muted; Text {anchors.fill: parent; anchors.margins: 14; color: theme.white; wrapMode: Text.Wrap; text: modelData.name+" • "+modelData.profile+"\n"+workspace.tr("الاستخدام: ","Usage: ")+(modelData["bytes-in"]||"0")+" / "+(modelData["bytes-out"]||"0")+" • "+(modelData.uptime||"0")+" • "+(modelData.disabled||"no")} TapHandler {onTapped: workspace.userId=parent.modelData[".id"]}}}
  }
  ScrollView {clip: true; ColumnLayout {width: workspace.width-24; spacing: 14
   Text {text: workspace.tr("فلترة DNS للشبكات الخاصة المختارة. تُحفظ الإعدادات السابقة لاستعادتها.","DNS filtering for selected private client networks. Previous settings are saved for restoration."); color: theme.silver; Layout.fillWidth: true; wrapMode: Text.Wrap}
   Repeater {model:routerTools.dhcpNetworks;CheckBox {required property var modelData; text:modelData.address+" • "+workspace.tr("DNS: ","DNS: ")+(modelData["dns-server"]||"");checked:workspace.selectedNetworks.indexOf(modelData[".id"])>=0;onClicked:{let next=workspace.selectedNetworks.slice(),i=next.indexOf(modelData[".id"]);if(checked&&i<0)next.push(modelData[".id"]);else if(!checked&&i>=0)next.splice(i,1);workspace.selectedNetworks=next}}}
   ComboBox {id: dnsMode; model: ["FAMILY","ADS_TRACKERS"]; Layout.fillWidth: true}
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
    Text {text: workspace.tr("بروفايل HotSpot","HotSpot profile"); color: theme.silver} ComboBox {id: serverProfile; model: routerTools.profiles; textRole: "name"; Layout.fillWidth: true}
   }
   Flow {Layout.fillWidth:true;spacing:8;FgButton {text:workspace.tr("اختيار شعار","Choose logo");onClicked:logoPicker.open()} FgButton {text:workspace.tr("حفظ التصميم","Save design");onClicked:routerTools.savePortalDesign(workspace.design())} FgButton {text:workspace.tr("معاينة الحالة","Status preview");onClicked:routerTools.previewPortal(workspace.design(),"status.html")} FgButton {text: workspace.tr("معاينة الصفحة","Preview portal"); onClicked: routerTools.previewPortal(workspace.design())} FgButton {text: workspace.tr("تثبيت الصفحة","Install portal"); enabled: serverProfile.currentIndex>=0&&!routerTools.busy; onClicked: portalInstall.open()} FgButton {text: workspace.tr("استعادة الصفحة السابقة","Restore previous portal"); enabled: !routerTools.busy; onClicked: routerTools.restorePortal()}}
   Text {text: workspace.tr("تُرفع الملفات إلى مجلد جديد وتُفحص قبل تحويل صفحة الدخول إليه.","Files are uploaded to a new directory and verified before switching the login portal."); color: theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap}
  }}
  ColumnLayout {
   FgButton {text: workspace.tr("ضبط الساعة وNTP","Set clock & NTP"); enabled: !routerTools.busy; onClicked: routerTools.synchronizeClock()}
   RowLayout {FgField {id: routerBackupPassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور النسخة (12+)","Backup password (12+)"); Layout.fillWidth: true} FgButton {text: workspace.tr("حفظ نسخة الراوتر","Save router backup"); enabled: routerBackupPassword.text.length>=12&&!routerTools.busy; onClicked: {routerTools.backup(routerBackupPassword.text);routerBackupPassword.clear()}}}
   RowLayout {FgField {id: restoreName; placeholderText: "fg-mtm-....backup"; Layout.fillWidth: true} FgField {id: restorePassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور الاستعادة","Restore password"); Layout.fillWidth: true} FgButton {text: workspace.tr("استعادة الراوتر","Restore router"); enabled: restoreName.text.length>0&&!routerTools.busy; onClicked: routerRestore.open()}}
   FgButton {text: workspace.tr("تصدير إعدادات الراوتر","Export router configuration"); enabled: !routerTools.busy; onClicked: routerTools.exportConfiguration()}
   FgButton {text: workspace.tr("مسح ذاكرة DNS","Flush DNS cache"); enabled: !routerTools.busy; onClicked: routerTools.flushDns()}
   FgButton {text: workspace.tr("إعادة تشغيل الراوتر","Reboot router"); accent: theme.error; enabled: !routerTools.busy; onClicked: routerReboot.open()}
   Item {Layout.fillHeight: true}
  }
 }
 Dialog {id: userAction; property string action: ""; function openFor(value){action=value;open()} parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("تأكيد إجراء المشترك","Confirm subscriber action"); contentItem: ColumnLayout {Text {text: userAction.action+" • "+workspace.userId; color: theme.white} FgButton {text: workspace.tr("تأكيد","Confirm"); onClicked: {routerTools.subscriber(userAction.action,workspace.userId,{});userAction.close()}}}}
 Dialog {id: portalInstall; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("تأكيد تثبيت صفحة الدخول","Confirm portal installation"); contentItem: FgButton {text: workspace.tr("تثبيت على البروفايل المحدد","Install on selected profile"); onClicked: {routerTools.installPortal(routerTools.profiles[serverProfile.currentIndex][".id"],workspace.design());portalInstall.close()}}}
 Dialog {id: routerRestore; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("استعادة إعدادات الراوتر","Restore router configuration"); contentItem: ColumnLayout {Text {text: workspace.tr("قد تنقطع الجلسة أثناء استعادة إعدادات الراوتر.","The connection may close during router restoration."); color: theme.white} FgButton {text: workspace.tr("تأكيد الاستعادة","Confirm restore"); onClicked: {routerTools.restoreBackup(restoreName.text,restorePassword.text);restorePassword.clear();routerRestore.close()}}}}
 Dialog {id: routerReboot; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; title: workspace.tr("إعادة تشغيل الراوتر","Reboot router"); contentItem: FgButton {text: workspace.tr("تأكيد إعادة التشغيل","Confirm reboot"); onClicked: {routerTools.reboot();routerReboot.close()}}}
}
