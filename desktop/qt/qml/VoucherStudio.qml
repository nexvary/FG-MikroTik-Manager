import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: studio
 property bool arabic: true
 property bool advancedFields:false
 property var voucherStore: vouchers
 property var routerBackend: backend
 function tr(ar,en){return arabic?ar:en}
 function comboValue(control){var item=control.model[control.currentIndex];return item&&item.value!==undefined?item.value:control.currentText}
 function serverValue(){return server.text.trim().length?server.text.trim():"all"}
 function expiryEpoch(){
  var value=expiryDate.text.trim();if(!value.length)return null
  var date=value.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);var time=expiryTime.text.trim().match(/^(\d{1,2}):(\d{2})$/)
  if(!date||!time)return NaN
  var day=Number(date[1]),month=Number(date[2]),year=Number(date[3]),hour=Number(time[1]),minute=Number(time[2])
  if(hour>23||minute>59)return NaN
  var target=new Date(year,month-1,day,hour,minute,0,0)
  if(target.getFullYear()!==year||target.getMonth()!==month-1||target.getDate()!==day)return NaN
  return target.getTime()
 }
 function expiryValid(){var value=expiryEpoch();return value===null||(!isNaN(value)&&value>Date.now())}
 function closeDialogs(){cardPreview.close();activation.close()}
 function showStep(value){steps.currentIndex=value}
 function previewFirst(){studio.voucherStore.selectCard(0);studio.previewImage=studio.voucherStore.previewCard(0);cardPreview.open()}
 property string exportFormat: "PDF"
 property string previewImage:""
 Theme {id: theme}
 FileDialog {id:oneCardFile;fileMode:FileDialog.SaveFile;defaultSuffix:studio.exportFormat.toLowerCase();onAccepted:studio.voucherStore.exportFile(selectedFile,studio.exportFormat,paper.currentText,true)}
 FileDialog {id: saveFile; fileMode: FileDialog.SaveFile; defaultSuffix: studio.exportFormat.toLowerCase(); onAccepted: studio.voucherStore.exportFile(selectedFile,studio.exportFormat,paper.currentText)}
 FileDialog {id: archiveFile; fileMode: FileDialog.SaveFile; defaultSuffix: "fgbackup"; onAccepted: {studio.voucherStore.exportArchive(selectedFile,backupPassword.text);backupPassword.clear()}}
 FileDialog {id: importFile; fileMode: FileDialog.OpenFile; onAccepted: {studio.voucherStore.importArchive(selectedFile,backupPassword.text);backupPassword.clear()}}
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:studio.tr("أنشئ دفعة أو افتحها من الأرشيف، ثم فعّلها على الراوتر قبل استخدامها.","Create a batch or open an archive, then activate it on the router before use.")}
 TabBar {id:steps;objectName:"voucherWorkflowTabs";Layout.fillWidth:true
  FgTab {text:studio.tr("إنشاء الكروت","Create cards")}
  FgTab {text:studio.tr("تفعيل على الراوتر","Activate on router")}
  FgTab {text:studio.tr("طباعة ومشاركة","Print & share")}
  FgTab {text:studio.tr("الأرشيف","Archive")}
 }
 Text {text:studio.voucherStore.status;visible:text.length>0;color:theme.mint;Layout.fillWidth:true;wrapMode:Text.Wrap}
 StackLayout {Layout.fillWidth:true;Layout.fillHeight:true;currentIndex:steps.currentIndex
  ColumnLayout {spacing:12
   RowLayout {Layout.fillWidth:true
    Text {text:studio.tr("باقات الراوتر","Router profiles");color:theme.gold}
   FgCombo {id:routerProfiles;objectName:"voucherRouterProfiles";model:studio.voucherStore.profiles;textRole:"name";Layout.fillWidth:true;Layout.minimumWidth:120;enabled:studio.routerBackend.routerConnected&&!studio.voucherStore.busy&&count>0;displayText:count>0?currentText:studio.tr("اضغط قراءة الباقات","Load router profiles");onActivated:profile.text=currentText}
   FgButton {objectName:"voucherLoadProfilesButton";text:studio.voucherStore.busy?studio.tr("جارٍ المعالجة…","Processing…"):studio.tr("قراءة الباقات","Load profiles");enabled:studio.routerBackend.routerConnected&&!studio.routerBackend.busy&&!studio.voucherStore.busy&&studio.comboValue(mode)!=="OFFLINE";onClicked:studio.voucherStore.loadProfiles(studio.comboValue(mode))}
    Text {visible:studio.comboValue(mode)==="HOTSPOT";text:studio.tr("خادم HotSpot","HotSpot server");color:theme.silver}
    FgCombo {model:studio.voucherStore.servers;textRole:"name";visible:studio.comboValue(mode)==="HOTSPOT";Layout.fillWidth:true;Layout.minimumWidth:100;enabled:studio.routerBackend.routerConnected&&!studio.voucherStore.busy&&count>0;displayText:count>0?currentText:studio.tr("جميع الخوادم","All servers");onActivated:server.text=currentText}

   }
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:studio.routerBackend.routerConnected?theme.silver:theme.error;text:!studio.routerBackend.routerConnected?studio.tr("اتصل بالراوتر أولًا لقراءة الباقات والخوادم.","Connect a router first to load profiles and servers."):studio.voucherStore.profiles.length>0?studio.tr("اختر باقة الراوتر لتعبئة خانة الباقة في النموذج.","Select a router profile to fill the profile field in the form."):studio.tr("اختر الخدمة ثم اضغط قراءة الباقات. ستظهر نتيجة القراءة أعلى الشاشة.","Choose a service and load profiles. The result appears above.")}
 ScrollView {
  id: creationScroll; objectName:"voucherCreationScroll"
  Layout.fillWidth: true; Layout.fillHeight: true; contentWidth: availableWidth; clip: true
  Component.onCompleted: { if (contentItem && contentItem.boundsBehavior !== undefined) contentItem.boundsBehavior = Flickable.StopAtBounds }
  GridLayout {
   width: creationScroll.availableWidth; columns: 4; rowSpacing: 10; columnSpacing: 12
   Text {text: studio.tr("الخدمة","Service"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgCombo {id: mode; model: [{label:"HotSpot",value:"HOTSPOT"},{label:"User Manager",value:"USER_MANAGER"},{label:"PPPoE",value:"PPPOE"},{label:studio.tr("بدون تفعيل على الراوتر","Offline / no router activation"),value:"OFFLINE"}]; textRole:"label"; Layout.fillWidth: true;onActivated:studio.voucherStore.loadProfiles(studio.comboValue(mode))}
   Text {text: studio.tr("العدد","Quantity"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: quantity; text: "10"; Layout.fillWidth: true; inputMethodHints: Qt.ImhDigitsOnly}
   Text {text: studio.tr("الباقة","Profile"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: profile;objectName:"voucherProfileField"; text: "default"; Layout.fillWidth: true}
   Text {text: studio.tr("خادم HotSpot","HotSpot server"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: server; placeholderText: studio.tr("جميع خوادم HotSpot","All HotSpot servers"); Layout.fillWidth: true}
   Text {text: studio.tr("طول الكود","Code length"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: length; text: "8"; Layout.fillWidth: true}
   Text {text: studio.tr("نوع الأحرف","Characters"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgCombo {id: alphabet; model: [{label:studio.tr("أرقام فقط","Numbers only"),value:"NUMERIC"},{label:studio.tr("أرقام وحروف","Letters & numbers"),value:"ALPHANUMERIC"}]; textRole:"label"; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("بداية الكود","Prefix"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: prefix; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("نهاية الكود","Suffix"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: suffix; Layout.fillWidth: true}
   Text {text: studio.tr("كلمة المرور","Password"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgCombo {id: passwordMode; model: [{label:studio.tr("نفس اسم المستخدم","Same as username"),value:"SAME_AS_USERNAME"},{label:studio.tr("كلمة مرور عشوائية","Random password"),value:"RANDOM"}]; textRole:"label"; Layout.fillWidth: true}
   Text {text: studio.tr("طول كلمة المرور","Password length"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: passwordLength; text: "6"; Layout.fillWidth: true}
   Text {text: studio.tr("المدة","Duration"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: duration; text: "60"; Layout.fillWidth: true}
   Text {text: studio.tr("الوحدة","Unit"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgCombo {id: durationUnit; model: [{label:studio.tr("دقائق","Minutes"),value:"MINUTES"},{label:studio.tr("ساعات","Hours"),value:"HOURS"},{label:studio.tr("أيام","Days"),value:"DAYS"}]; textRole:"label"; Layout.fillWidth: true}
   Text {text: studio.tr("حجم البيانات MB","Data allowance MB"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: dataLimit; placeholderText: studio.tr("فارغ = بلا حد","Empty = unlimited"); Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("تاريخ الانتهاء","Expiry date"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: expiryDate; placeholderText: studio.tr("31/12/2026","31/12/2026"); Layout.fillWidth: true; inputMethodHints: Qt.ImhDigitsOnly}
   Text {visible:studio.advancedFields;text: studio.tr("وقت الانتهاء","Expiry time"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: expiryTime; text:"23:59"; placeholderText:"23:59"; Layout.fillWidth: true; inputMethodHints: Qt.ImhDigitsOnly}
   Text {visible:studio.advancedFields&&!studio.expiryValid();text:studio.tr("أدخل التاريخ بالشكل يوم/شهر/سنة والوقت HH:MM، ويجب أن يكونا في المستقبل.","Use DD/MM/YYYY and HH:MM; expiry must be in the future.");color:theme.error;Layout.columnSpan:4;Layout.fillWidth:true;wrapMode:Text.Wrap}
   Text {text: studio.tr("اسم الشبكة","Network name"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {id: network; text: "FG Machines WiFi"; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("الدعم","Support phone"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: phone; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("السعر الظاهر","Display price"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: price; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("رابط دخول QR","QR login URL"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: portal; Layout.fillWidth: true}
   Text {visible:studio.advancedFields;text: studio.tr("ملاحظة","Comment"); color: theme.gold;font.pixelSize:13;font.bold:true}
   FgField {visible:studio.advancedFields;id: comment; Layout.fillWidth: true; Layout.columnSpan: 3}
   FgButton {Layout.columnSpan:4;Layout.fillWidth:true;text:studio.advancedFields?studio.tr("إخفاء الخيارات الإضافية","Hide extra options"):studio.tr("خيارات إضافية: انتهاء الصلاحية والتصميم","Extra options: expiry and branding");onClicked:studio.advancedFields=!studio.advancedFields}
  }
 }
  FgButton {filled:true;objectName:"voucherGenerateButton";text: studio.tr("إنشاء الكروت والانتقال للتفعيل","Generate cards & continue"); enabled: !studio.voucherStore.busy&&studio.expiryValid(); accent: theme.mint; onClicked: {
   var request={quantity:Number(quantity.text),usernameLength:Number(length.text),passwordLength:Number(passwordLength.text),mode:studio.comboValue(mode),profile:profile.text,server:studio.serverValue(),prefix:prefix.text,suffix:suffix.text,passwordMode:studio.comboValue(passwordMode),characterSet:studio.comboValue(alphabet),durationValue:Number(duration.text),durationUnit:studio.comboValue(durationUnit),comment:comment.text,branding:{networkName:network.text,supportPhone:phone.text,priceText:price.text,portalLoginUrl:portal.text}}
   if(dataLimit.text.length)request.limitBytesTotal=String(Math.round(Number(dataLimit.text)*1048576))
   var absoluteExpiry=studio.expiryEpoch();if(absoluteExpiry!==null)request.absoluteExpiryEpochMs=String(absoluteExpiry)
   studio.voucherStore.generate(request)
   if(studio.voucherStore.cards.length>0&&studio.voucherStore.status.indexOf("Vouchers generated and encrypted")>=0) steps.currentIndex=1
  }}

  }
  ColumnLayout {spacing:12
   RowLayout {Layout.fillWidth:true
    NavIcon {kind:10;Layout.preferredWidth:32;Layout.preferredHeight:32}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:studio.routerBackend.routerConnected?theme.mint:theme.error;text:studio.routerBackend.routerConnected?studio.tr("الراوتر متصل — راجع الكروت ثم فعّل الدفعة","Router connected — review cards and activate batch"):studio.tr("الراوتر غير متصل — أعد الاتصال قبل التفعيل","Router disconnected — reconnect before activation")}
   }
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.gold;text:studio.tr("التفعيل يشمل الدفعة المفتوحة بالكامل. الكروت المسودة لا تعمل في صفحة الدخول.","Activation applies to the entire open batch. Draft cards cannot log in.")}
 ListView {
  id:batchList; objectName:"voucherBatchList"; boundsBehavior:Flickable.StopAtBounds
  Layout.fillWidth: true; Layout.fillHeight: true; model: studio.voucherStore.cards; clip: true; spacing: 6
  ScrollBar.vertical: ScrollBar {}
  delegate: Rectangle {required property var modelData;required property int index; width: ListView.view.width; height: 72; color: ListView.isCurrentItem ? "#183B47" : theme.panel; border.color: ListView.isCurrentItem ? theme.mint : "#294152"; radius: 12
   TapHandler {onTapped:{batchList.currentIndex=index;studio.voucherStore.selectCard(index)}}
   RowLayout {anchors.fill: parent; anchors.margins: 12; Text {text: modelData.username; color: theme.white; font.bold: true; Layout.fillWidth: true}  Text {text: modelData.profile; color: theme.muted; Layout.fillWidth: true} FgButton {text:studio.tr("نسخ للمشاركة","Copy to share");implicitHeight:36;onClicked:studio.routerBackend.copyText(studio.voucherStore.shareCard(index,studio.arabic))} Text {text: modelData.provisionState==="DRAFT"?studio.tr("مسودة — لم تُفعّل","Draft — not activated"):modelData.provisionState; color: modelData.provisionState==="CREATED"?theme.mint:theme.gold} ToolTip.visible: cardHover.hovered; ToolTip.text: modelData.provisionMessage||""; HoverHandler {id: cardHover}}
  }
 }

   RowLayout {Layout.fillWidth:true
    FgButton {text:studio.tr("رجوع للإنشاء","Back to creation");onClicked:steps.currentIndex=0}
    FgButton {objectName:"voucherActivateButton";filled:true;accent:theme.mint;text:studio.tr("تفعيل الدفعة على الراوتر","Activate batch on router");enabled:studio.routerBackend.routerConnected&&!studio.routerBackend.busy&&!studio.voucherStore.busy&&studio.voucherStore.cards.length>0;onClicked:activation.open()}
    FgButton {text:studio.tr("الطباعة والمشاركة","Print & share");enabled:studio.voucherStore.cards.length>0;onClicked:steps.currentIndex=2}
   }
  }
  ColumnLayout {spacing:16
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.white;font.pixelSize:20;text:studio.tr("طباعة وتصدير الدفعة المفتوحة","Print and export the open batch")}
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:studio.tr("اختر حجم الورق والصيغة. معاينة الكارت تتيح طباعته أو تصديره منفردًا.","Choose paper size and format. Preview a card to print or export it individually.")}
   Flow {Layout.fillWidth:true;spacing:10
  FgCombo {id: paper; model: ["A4","58","80"]}
  FgButton {text: studio.tr("طباعة","Print"); enabled: studio.voucherStore.cards.length>0&&!studio.voucherStore.busy; onClicked: studio.voucherStore.print(paper.currentText)}
  FgCombo {id: format; model: ["PDF","PNG","HTML","CSV","RSC"]}
  FgButton {text: studio.tr("تصدير","Export"); enabled: studio.voucherStore.cards.length>0&&!studio.voucherStore.busy; onClicked: {studio.exportFormat=format.currentText;saveFile.open()}}
    FgButton {text:studio.tr("معاينة الكارت المحدد","Preview selected card");enabled:studio.voucherStore.cards.length>0;onClicked:{studio.voucherStore.selectCard(Math.max(0,batchList.currentIndex));studio.previewImage=studio.voucherStore.previewCard(Math.max(0,batchList.currentIndex));cardPreview.open()}}
    FgButton {text:studio.tr("نسخ الكارت للمشاركة","Copy selected card");enabled:studio.voucherStore.cards.length>0;onClicked:studio.routerBackend.copyText(studio.voucherStore.shareCard(Math.max(0,batchList.currentIndex),studio.arabic))}
   }
   Item {Layout.fillHeight:true}
  }
  ColumnLayout {spacing:16
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.white;font.pixelSize:20;text:studio.tr("دفعات الكروت المحفوظة","Saved voucher batches")}
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:studio.tr("افتح دفعة لمراجعتها وتفعيلها، أو احفظ نسخة مشفرة من الأرشيف.","Open a batch to review and activate it, or save an encrypted archive backup.")}
 Flow {
  objectName:"voucherArchiveActions"
  Layout.fillWidth: true;spacing:8
  FgButton {text:studio.tr("السابق","Previous");enabled:studio.voucherStore.archivePage>0;onClicked:studio.voucherStore.setArchivePage(studio.voucherStore.archivePage-1)}
  FgButton {text:studio.tr("التالي","Next");enabled:(studio.voucherStore.archivePage+1)*20<studio.voucherStore.archiveCount;onClicked:studio.voucherStore.setArchivePage(studio.voucherStore.archivePage+1)}
  FgCombo {id: archive; model: studio.voucherStore.archive; textRole: "date"; Layout.preferredWidth: 260}
  FgButton {text: studio.tr("فتح دفعة","Open batch"); enabled: !studio.voucherStore.busy; onClicked: if(archive.currentIndex>=0){studio.voucherStore.openBatch(studio.voucherStore.archive[archive.currentIndex].id);steps.currentIndex=1}}
  FgField {id: backupPassword;width:240; echoMode: TextInput.Password; placeholderText: studio.tr("كلمة مرور النسخة المشفرة (12+)","Backup password (12+)"); Layout.fillWidth: true}
  FgButton {text: studio.tr("نسخ الأرشيف","Back up archive"); enabled: backupPassword.text.length>=12&&!studio.voucherStore.busy; onClicked: archiveFile.open()}
  FgButton {text: studio.tr("استيراد","Import"); enabled: backupPassword.text.length>=12&&!studio.voucherStore.busy; onClicked: importFile.open()}
 }

   Item {Layout.fillHeight:true}
  }
 }
 FgDialog {id: activation; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: Math.min(520,parent.width-32); title: studio.tr("تفعيل دفعة الكروت","Activate voucher batch"); contentItem: ColumnLayout {Text {Layout.fillWidth: true; wrapMode: Text.Wrap; text: studio.tr("سيُنشئ كروت الدفعة على الراوتر المتصل. راجع الخدمة والباقات والصلاحية أولًا.","Creates this batch on the connected router. Review service, profile and expiry first."); color: theme.gold;font.pixelSize:13;font.bold:true} FgButton {text: studio.tr("تأكيد التفعيل","Confirm activation"); enabled:studio.routerBackend.routerConnected&&!studio.routerBackend.busy&&!studio.voucherStore.busy&&studio.voucherStore.cards.length>0;onClicked: {studio.voucherStore.activate();activation.close()}}}}
 FgDialog {id:cardPreview;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:Math.min(700,parent.width-40);height:Math.min(560,parent.height-40);title:studio.tr("معاينة الكارت وQR","Voucher & QR preview")
  contentItem:ColumnLayout {Image {Layout.fillWidth:true;Layout.fillHeight:true;source:studio.previewImage;fillMode:Image.PreserveAspectFit} Flow {Layout.fillWidth:true;spacing:8;FgButton {text:"PDF";onClicked:{studio.exportFormat="PDF";oneCardFile.open()}} FgButton {text:"PNG";onClicked:{studio.exportFormat="PNG";oneCardFile.open()}} FgButton {text:studio.tr("طباعة الكارت","Print voucher");onClicked:studio.voucherStore.print(paper.currentText,true)} FgButton {text:studio.tr("رجوع","Back");onClicked:cardPreview.close()}}}
 }

}
