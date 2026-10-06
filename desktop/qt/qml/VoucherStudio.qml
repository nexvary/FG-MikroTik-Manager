import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: studio
 property bool arabic: true
 function tr(ar,en){return arabic?ar:en}
 property string exportFormat: "PDF"
 Theme {id: theme}
 FileDialog {id: saveFile; fileMode: FileDialog.SaveFile; defaultSuffix: studio.exportFormat.toLowerCase(); onAccepted: vouchers.exportFile(selectedFile,studio.exportFormat,paper.currentText)}
 FileDialog {id: archiveFile; fileMode: FileDialog.SaveFile; defaultSuffix: "fgbackup"; onAccepted: {vouchers.exportArchive(selectedFile,backupPassword.text);backupPassword.clear()}}
 FileDialog {id: importFile; fileMode: FileDialog.OpenFile; onAccepted: {vouchers.importArchive(selectedFile,backupPassword.text);backupPassword.clear()}}
 ScrollView {
  Layout.fillWidth: true; Layout.preferredHeight: 330; clip: true
  GridLayout {
   width: studio.width-24; columns: 4; rowSpacing: 10; columnSpacing: 12
   Text {text: studio.tr("الخدمة","Service"); color: theme.silver}
   ComboBox {id: mode; model: ["HOTSPOT","PPPOE","USER_MANAGER","OFFLINE"]; Layout.fillWidth: true}
   Text {text: studio.tr("العدد","Quantity"); color: theme.silver}
   FgField {id: quantity; text: "10"; Layout.fillWidth: true; inputMethodHints: Qt.ImhDigitsOnly}
   Text {text: studio.tr("الباقة","Profile"); color: theme.silver}
   FgField {id: profile; text: "default"; Layout.fillWidth: true}
   Text {text: studio.tr("خادم HotSpot","HotSpot server"); color: theme.silver}
   FgField {id: server; text: "all"; Layout.fillWidth: true}
   Text {text: studio.tr("طول الكود","Code length"); color: theme.silver}
   FgField {id: length; text: "8"; Layout.fillWidth: true}
   Text {text: studio.tr("نوع الأحرف","Characters"); color: theme.silver}
   ComboBox {id: alphabet; model: ["NUMERIC","ALPHANUMERIC"]; Layout.fillWidth: true}
   Text {text: studio.tr("بداية الكود","Prefix"); color: theme.silver}
   FgField {id: prefix; Layout.fillWidth: true}
   Text {text: studio.tr("نهاية الكود","Suffix"); color: theme.silver}
   FgField {id: suffix; Layout.fillWidth: true}
   Text {text: studio.tr("كلمة المرور","Password"); color: theme.silver}
   ComboBox {id: passwordMode; model: ["SAME_AS_USERNAME","RANDOM"]; Layout.fillWidth: true}
   Text {text: studio.tr("طول كلمة المرور","Password length"); color: theme.silver}
   FgField {id: passwordLength; text: "6"; Layout.fillWidth: true}
   Text {text: studio.tr("المدة","Duration"); color: theme.silver}
   FgField {id: duration; text: "60"; Layout.fillWidth: true}
   Text {text: studio.tr("الوحدة","Unit"); color: theme.silver}
   ComboBox {id: durationUnit; model: ["MINUTES","HOURS","DAYS"]; Layout.fillWidth: true}
   Text {text: studio.tr("حجم البيانات MB","Data allowance MB"); color: theme.silver}
   FgField {id: dataLimit; placeholderText: studio.tr("فارغ = بلا حد","Empty = unlimited"); Layout.fillWidth: true}
   Text {text: studio.tr("الانتهاء المطلق","Absolute expiry"); color: theme.silver}
   FgField {id: expiry; placeholderText: "2026-12-31T23:59:00+02:00"; Layout.fillWidth: true}
   Text {text: studio.tr("اسم الشبكة","Network name"); color: theme.silver}
   FgField {id: network; text: "FG Machines WiFi"; Layout.fillWidth: true}
   Text {text: studio.tr("الدعم","Support phone"); color: theme.silver}
   FgField {id: phone; Layout.fillWidth: true}
   Text {text: studio.tr("السعر الظاهر","Display price"); color: theme.silver}
   FgField {id: price; Layout.fillWidth: true}
   Text {text: studio.tr("رابط دخول QR","QR login URL"); color: theme.silver}
   FgField {id: portal; Layout.fillWidth: true}
   Text {text: studio.tr("ملاحظة","Comment"); color: theme.silver}
   FgField {id: comment; Layout.fillWidth: true; Layout.columnSpan: 3}
  }
 }
 RowLayout {
  Layout.fillWidth: true
  FgButton {text: studio.tr("إنشاء وحفظ","Generate & save"); enabled: !vouchers.busy; accent: theme.mint; onClicked: {
   var request={quantity:Number(quantity.text),usernameLength:Number(length.text),passwordLength:Number(passwordLength.text),mode:mode.currentText,profile:profile.text,server:server.text,prefix:prefix.text,suffix:suffix.text,passwordMode:passwordMode.currentText,characterSet:alphabet.currentText,durationValue:Number(duration.text),durationUnit:durationUnit.currentText,comment:comment.text,branding:{networkName:network.text,supportPhone:phone.text,priceText:price.text,portalLoginUrl:portal.text}}
   if(dataLimit.text.length)request.limitBytesTotal=String(Math.round(Number(dataLimit.text)*1048576))
   if(expiry.text.length)request.absoluteExpiryEpochMs=String(new Date(expiry.text).getTime())
   vouchers.generate(request)
  }}
  FgButton {text: studio.tr("تفعيل على الراوتر","Activate on router"); enabled: !vouchers.busy&&vouchers.cards.length>0; onClicked: activation.open()}
  ComboBox {id: paper; model: ["A4","58","80"]}
  FgButton {text: studio.tr("طباعة","Print"); enabled: vouchers.cards.length>0&&!vouchers.busy; onClicked: vouchers.print(paper.currentText)}
  ComboBox {id: format; model: ["PDF","PNG","CSV","RSC"]}
  FgButton {text: studio.tr("تصدير","Export"); enabled: vouchers.cards.length>0&&!vouchers.busy; onClicked: {studio.exportFormat=format.currentText;saveFile.open()}}
 }
 Dialog {id: activation; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: 520; title: studio.tr("تفعيل دفعة الكروت","Activate voucher batch"); contentItem: ColumnLayout {Text {Layout.fillWidth: true; wrapMode: Text.Wrap; text: studio.tr("سيُنشئ كروت الدفعة على الراوتر المتصل. راجع الخدمة والباقات والصلاحية أولًا.","Creates this batch on the connected router. Review service, profile and expiry first."); color: theme.silver} FgButton {text: studio.tr("تأكيد التفعيل","Confirm activation"); onClicked: {vouchers.activate();activation.close()}}}}
 Text {text: vouchers.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}
 RowLayout {
  Layout.fillWidth: true
  ComboBox {id: archive; model: vouchers.archive; textRole: "id"; Layout.preferredWidth: 260}
  FgButton {text: studio.tr("فتح دفعة","Open batch"); enabled: !vouchers.busy; onClicked: if(archive.currentIndex>=0)vouchers.openBatch(vouchers.archive[archive.currentIndex].id)}
  FgField {id: backupPassword; echoMode: TextInput.Password; placeholderText: studio.tr("كلمة مرور النسخة المشفرة (12+)","Backup password (12+)"); Layout.fillWidth: true}
  FgButton {text: studio.tr("نسخ الأرشيف","Back up archive"); enabled: backupPassword.text.length>=12&&!vouchers.busy; onClicked: archiveFile.open()}
  FgButton {text: studio.tr("استيراد","Import"); enabled: backupPassword.text.length>=12&&!vouchers.busy; onClicked: importFile.open()}
 }
 ListView {
  Layout.fillWidth: true; Layout.fillHeight: true; model: vouchers.cards; clip: true; spacing: 6
  ScrollBar.vertical: ScrollBar {}
  delegate: Rectangle {required property var modelData; width: ListView.view.width; height: 66; color: theme.panel; radius: 12
   RowLayout {anchors.fill: parent; anchors.margins: 12; Text {text: modelData.username; color: theme.white; font.bold: true; Layout.fillWidth: true} Text {text: modelData.password; color: theme.silver; Layout.fillWidth: true} Text {text: modelData.profile; color: theme.muted; Layout.fillWidth: true} Text {text: modelData.provisionState; color: modelData.provisionState==="CREATED"?theme.mint:theme.gold} ToolTip.visible: cardHover.hovered; ToolTip.text: modelData.provisionMessage||""; HoverHandler {id: cardHover}}
  }
 }
}
