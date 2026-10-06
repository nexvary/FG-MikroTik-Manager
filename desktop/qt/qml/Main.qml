import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
import QtQuick.Window
ApplicationWindow {
 id: root
 width: 1280; height: 800; minimumWidth: 1000; minimumHeight: 650
 onClosing:function(close){if(monitor.keepRunning){close.accepted=false;root.hide()}}
 visible: true; title: "FG MTM • FG Machines"
 color: theme.black
 property bool arabic: true
 property bool inventoryView: false
 property string page: "home"
 property var history: []
 property int businessPage: 0
 property string selectedRouterId: ""
 property var selectedRouterRow: ({})
 property var routerModule: backend.module(menu.currentText)
 property var tableKeys: ["subscribers","plans","invoices","ledger","sales","expenses","team_members","reseller_entries","router_bindings","network_jobs","payment_details","invoice_voids","sale_voids","import_batches","audit","organizations","branches"]
 property var tableAr: ["المشتركون","الباقات","الفواتير","السجل المالي","المبيعات","المصروفات","الموظفون والموزعون","حركة الموزعين","الراوترات","مهام الشبكة","تفاصيل الدفع","إلغاء الفواتير","إلغاء المبيعات","دفعات الاستيراد","التدقيق","المؤسسة","الفروع"]
 function tr(ar,en){return arabic?ar:en}
 function navigate(value){if(value!==page){history=history.concat([page]);page=value;backend.clearView();if(page==="business")filter()}}
 function closeReviewDialogs(){localBusiness.closeDialogs();voucherStudio.closeDialogs();adminEditor.close();terminalWindow.hide()}
 function showBusinessEditor(){localBusiness.showEditorFor("renew")}
 function showVoucherPreview(){voucherStudio.previewFirst()}
 function showRouterEditor(){adminEditor.open()}
 function showTerminal(){terminalWindow.show();terminalWindow.raise()}
 function filter(){backend.filter(tableKeys[tables.currentIndex],search.text,businessPage)}
 Theme {id: theme}
 palette.windowText: theme.white; palette.text: theme.white; palette.buttonText: theme.white; palette.base: theme.navy; palette.highlight: theme.blue; palette.button: theme.panel
 font.family: "Segoe UI"; font.pixelSize: 15
 LayoutMirroring.enabled: arabic; LayoutMirroring.childrenInherit: true
 Shortcut {sequence: "F4"; onActivated: root.showTerminal()}
 FileDialog {id: csvDialog; title: root.tr("تصدير CSV","Export CSV"); fileMode: FileDialog.SaveFile; nameFilters: ["CSV (*.csv)"]; defaultSuffix: "csv"; onAccepted: backend.exportCsv(selectedFile)}
 RowLayout {
  anchors.fill: parent; spacing: 0
  Rectangle {
   Layout.fillHeight: true; Layout.preferredWidth: 240; color: theme.navy
   ColumnLayout {
    anchors.fill: parent; anchors.margins: 18; spacing: 12
    Text {text: "FG MACHINES"; color: theme.silver; font.pixelSize: 23; font.bold: true}
    Text {text: "FG MTM"; color: theme.blue; font.pixelSize: 18; font.bold: true}
    Text {text: root.tr("إدارة شبكتك وأعمالك","Network & business"); color: theme.muted; font.pixelSize: 13}
    Rectangle {Layout.fillWidth: true; height: 1; color: theme.silver; opacity: 0.5}
    ScrollView {id:navScroll;Layout.fillWidth: true;Layout.fillHeight: true;clip:true;contentWidth:availableWidth;ScrollBar.vertical:ScrollBar {policy:ScrollBar.AlwaysOn;width:5;background:Rectangle {color:theme.panel;radius:2} contentItem:Rectangle {color:theme.mint;opacity:0.55;radius:2}}
    ColumnLayout {width:navScroll.availableWidth-10;spacing:8
    Repeater {
     model: [{key:"home",ar:"الرئيسية",en:"Overview",icon:0},{key:"commerce",ar:"الأعمال المحلية",en:"Local business",icon:1},{key:"business",ar:"أعمال الخادم",en:"Server business",icon:1},{key:"vouchers",ar:"الكروت والأرشيف",en:"Vouchers & archive",icon:2},{key:"billing",ar:"ربط وتجديد الشبكة",en:"Bindings & renewal",icon:1},{key:"transfer",ar:"الاستيراد والتقارير",en:"Import & reports",icon:1},{key:"monitor",ar:"المراقبة والتنبيهات",en:"Monitoring & alerts",icon:3},{key:"radius",ar:"RADIUS",en:"RADIUS",icon:2},{key:"tools",ar:"إدارة الشبكة",en:"Network tools",icon:3},{key:"router",ar:"الراوتر والأوامر",en:"Router & commands",icon:4},{key:"diagnostics",ar:"تشخيص الخادم",en:"Server diagnostics",icon:3},{key:"settings",ar:"الاتصال بالخادم",en:"Server connection",icon:4},{key:"about",ar:"عنا",en:"About",icon:5}]
     delegate: FgButton {
      required property var modelData
      Layout.fillWidth: true; text: root.tr(modelData.ar,modelData.en); leftPadding: 42; rightPadding: 42
      accent: root.page===modelData.key ? theme.blue : theme.muted
      onClicked: root.navigate(modelData.key)
      NavIcon {anchors.left: parent.left; anchors.leftMargin: 12; anchors.verticalCenter: parent.verticalCenter; kind: parent.modelData.icon; ink: parent.accent}
     }
    }
    }}
    FgButton {Layout.fillWidth: true; text: root.tr("ترمنال عائم • F4","Floating terminal • F4"); accent: theme.mint; onClicked: {terminalWindow.show();terminalWindow.raise()}}
    FgButton {Layout.fillWidth: true; text: root.arabic ? "English" : "العربية"; onClicked: root.arabic=!root.arabic}
    Text {text: "FG Machines · 0.14.0"; color: theme.muted; font.pixelSize: 12}
   }
  }
  ColumnLayout {
   Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 24; spacing: 16
   RowLayout {
    Layout.fillWidth: true
    FgButton {text: root.tr("رجوع","Back"); enabled: root.history.length>0; onClicked: {var items=root.history.slice();root.page=items.pop();root.history=items;backend.clearView();if(root.page==="business")root.filter()}}
    Text {text: root.tr(({home:"الرئيسية",business:"أعمال الخادم",commerce:"الأعمال المحلية",vouchers:"الكروت والأرشيف",radius:"RADIUS",router:"الراوتر والأوامر",tools:"إدارة الشبكة",diagnostics:"تشخيص الخادم",settings:"الاتصال بالخادم",monitor:"المراقبة والتنبيهات",transfer:"الاستيراد والتقارير",billing:"ربط وتجديد الشبكة",about:"عنا"})[root.page],({home:"Overview",business:"Server business",commerce:"Local business",vouchers:"Vouchers & archive",radius:"RADIUS",router:"Router & commands",tools:"Network tools",diagnostics:"Server diagnostics",settings:"Server connection",monitor:"Monitoring & alerts",transfer:"Import & reports",billing:"Bindings & renewal",about:"About"})[root.page]); color: theme.white; font.pixelSize: 28; font.bold: true; Layout.fillWidth: true}
    Rectangle {width: 10; height: 10; radius: 5; color: backend.connected ? theme.mint : theme.muted}
    Text {text: backend.connected?root.tr("متصل","Connected"):root.tr("غير متصل","Disconnected"); color: theme.silver}
   }
   Text {Layout.fillWidth: true; text: backend.status.includes(" • ") ? backend.status.split(" • ")[root.arabic?0:1] : backend.status; color: theme.mint; wrapMode: Text.Wrap}
   HomeWorkspace {visible:root.page==="home";arabic:root.arabic;Layout.fillWidth:true;Layout.fillHeight:true;onOpenPage:function(key){root.navigate(key)}}
   AboutWorkspace {visible:root.page==="about";Layout.fillWidth:true;Layout.fillHeight:true;arabic:root.arabic}
   NetworkBillingWorkspace {visible:root.page==="billing";enabled:!routerTools.busy;Layout.fillWidth:true;Layout.fillHeight:true;arabic:root.arabic}
   BusinessTransferWorkspace {visible:root.page==="transfer";Layout.fillWidth:true;Layout.fillHeight:true;arabic:root.arabic}
   MonitorWorkspace {visible:root.page==="monitor";Layout.fillWidth:true;Layout.fillHeight:true;arabic:root.arabic}
   GridLayout {
    visible: root.page==="settings"; Layout.fillWidth: true; columns: 2; columnSpacing: 16; rowSpacing: 12
    Text {text: root.tr("عنوان HTTPS","HTTPS address"); color: theme.silver}
    FgField {id: serverUrl; Layout.fillWidth: true; text: "https://3.65.234.184"; placeholderText: "https://server-ip"; LayoutMirroring.enabled: false}
    Text {text: root.tr("المؤسسة","Tenant"); color: theme.silver}
    FgField {id: tenant; text:"d1d1af2d-10f7-41ab-ae96-bdc10333d781"; Layout.fillWidth: true; LayoutMirroring.enabled: false}
    Text {text: root.tr("الفرع","Branch"); color: theme.silver}
    FgField {id: branch; text:"8f2aeb05-aac9-4769-a2a4-920aa1e1859e"; Layout.fillWidth: true; LayoutMirroring.enabled: false}
    Text {text: root.tr("اسم الحساب","Username"); color: theme.silver}
    FgField {id: username; Layout.fillWidth: true; LayoutMirroring.enabled: false}
    Text {text: root.tr("كلمة المرور","Password"); color: theme.silver}
    FgField {id: password; Layout.fillWidth: true; echoMode: TextInput.Password; LayoutMirroring.enabled: false}
    FgButton {text: root.tr("دخول","Sign in"); enabled: !backend.busy; onClicked: {backend.login(serverUrl.text,tenant.text,branch.text,username.text,password.text);password.clear()}}
    FgButton {text: root.tr("خروج","Sign out"); enabled: !backend.busy; onClicked: backend.logout()}
    CheckBox {id:joinEmpty;Layout.columnSpan:2;text:root.tr("ضم سجل Windows الفارغ لفرع الخادم، قبل إعداد المالك المحلي","Join an empty Windows store to the server branch before local owner setup")}
    FgButton {Layout.columnSpan:2;text:root.tr("مزامنة الأعمال في الاتجاهين","Synchronize business both ways");enabled:backend.connected&&!backend.busy&&commerce.allowed("BRANCHES");onClicked:backend.syncBusiness(joinEmpty.checked)}
    Text {Layout.columnSpan: 2; Layout.fillWidth: true; text: backend.scope; color: theme.mint; wrapMode: Text.Wrap}
   }
   ColumnLayout {
    visible: root.page==="business"; Layout.fillWidth: true
    RowLayout {
     Layout.fillWidth: true
     ComboBox {id: tables; Layout.preferredWidth: 220; model: root.arabic ? root.tableAr : root.tableKeys; onActivated: {root.businessPage=0;root.filter()}}
     FgField {id: search; Layout.fillWidth: true; placeholderText: root.tr("ابحث في السجلات","Search records"); onTextEdited: {root.businessPage=0;root.filter()}}
     FgButton {text: root.tr("قراءة","Read"); enabled: !backend.busy; onClicked: {tables.currentIndex=0;root.businessPage=0;backend.business()}}
     FgButton {text: "CSV"; enabled: backend.connected&&!backend.busy; onClicked: csvDialog.open()}
    }
    Text {text: root.tr("عرض فقط. قيم _minor بوحدات العملة الصغرى. CSV يشمل كل النتائج المطابقة.","Read only. _minor fields are minor currency units. CSV includes all matching records."); color: theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true}
    RowLayout {
     FgButton {text: root.tr("السابق","Previous"); enabled: root.businessPage>0; onClicked: {root.businessPage--;root.filter()}}
     FgButton {text: root.tr("التالي","Next"); enabled: backend.rows.length===50; onClicked: {root.businessPage++;root.filter()}}
    }
   }
   RowLayout {
    visible: root.page==="radius"; Layout.fillWidth: true
    FgButton {text: root.tr("المستخدمون","Users"); enabled: !backend.busy; onClicked: backend.radius(false)}
    FgButton {text: root.tr("الجلسات","Sessions"); enabled: !backend.busy; onClicked: backend.radius(true)}
   }
   RowLayout {
    visible: root.page==="diagnostics"; Layout.fillWidth: true
    FgButton {text: root.tr("بدء تشخيص الخادم","Run server diagnostics"); enabled: !backend.busy; onClicked: backend.diagnose(); accent: theme.mint}
    Text {text: root.tr("الفحوص من الخادم، لا من الكمبيوتر.","Tests run on FG Server, not this PC."); color: theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap}
   }
   GridLayout {
    visible: root.page==="router"; columns: 2; Layout.fillWidth: true
    FgField {id: routerUrl; Layout.fillWidth: true; placeholderText: root.tr("عنوان IP أو اسم الراوتر","Router IP or hostname"); LayoutMirroring.enabled: false}
    ComboBox {id: menu; model: backend.menus; Layout.fillWidth: true;onActivated:{root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.clearView()}}
    FgField {id: routerUser; Layout.fillWidth: true; placeholderText: root.tr("حساب الراوتر","Router username"); LayoutMirroring.enabled: false}
    FgField {id: routerPassword; Layout.fillWidth: true; echoMode: TextInput.Password; placeholderText: root.tr("كلمة المرور","Password"); LayoutMirroring.enabled: false}
    ComboBox {id: routerProtocol; model: ["REST","API_SSL","API","AUTO","REST_HTTP"]; Layout.fillWidth: true; onActivated: routerPort.text=currentIndex===0?"443":currentIndex===1?"8729":currentIndex===4?"80":"8728"}
    FgField {id: routerPort; text: "443"; placeholderText: root.tr("المنفذ","Port")}
    FgButton {text: root.tr("اتصال وقراءة","Connect & read"); enabled: !backend.busy; onClicked: {root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.connectRouter(routerUrl.text,Number(routerPort.text),routerUser.text,routerPassword.text,routerProtocol.currentText,menu.currentText);routerPassword.clear()}}
    FgButton {text: root.tr("قراءة القسم","Read section"); enabled: !backend.busy; onClicked: {root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.command("/"+menu.currentText+" print")}}
    RowLayout {Layout.columnSpan: 2
     FgButton {text: root.tr("اكتشاف ميكروتيك","Discover MikroTik"); enabled:!backend.busy; onClicked: {root.inventoryView=true;root.selectedRouterId="";root.selectedRouterRow=({});backend.discoverRouters()}}
     FgButton {text: root.tr("إضافة / تعديل","Add / edit"); enabled: !root.inventoryView&&backend.routerConnected&&!backend.busy&&(root.routerModule.create||root.routerModule.edit); onClicked:adminEditor.open()}
     FgButton {text: root.tr("تفعيل","Enable"); enabled: root.routerModule.toggle&&root.selectedRouterId.length>0&&!backend.busy; onClicked: backend.admin(menu.currentText,"enable",root.selectedRouterId,"{}")}
     FgButton {text: root.tr("تعطيل","Disable"); enabled: root.routerModule.toggle&&root.selectedRouterId.length>0&&!backend.busy; onClicked: backend.admin(menu.currentText,"disable",root.selectedRouterId,"{}")}
     FgButton {text: root.tr("حذف","Delete"); accent: theme.error; enabled: root.routerModule.delete&&root.selectedRouterId.length>0&&!backend.busy; onClicked: backend.admin(menu.currentText,"remove",root.selectedRouterId,"{}")}
    }
    RowLayout {Layout.columnSpan:2;Layout.fillWidth:true
     FgField {id:profileName;Layout.fillWidth:true;placeholderText:root.tr("اسم الراوتر لحفظ الاتصال","Router name to save connection")}
     FgButton {text:root.tr("حفظ الاتصال","Save connection");onClicked:backend.saveRouterProfile(profileName.text,commerce.scope,routerUrl.text,Number(routerPort.text),routerUser.text,routerProtocol.currentText)}
     ComboBox {id:savedRouter;Layout.fillWidth:true;model:backend.profiles;textRole:"name";onActivated:{let p=backend.profiles[currentIndex];routerUrl.text=p.host;routerUser.text=p.user;routerPort.text=String(p.port);routerProtocol.currentIndex=routerProtocol.model.indexOf(p.protocol)}}
     FgButton {text:root.tr("حذف المحفوظ","Delete saved");enabled:backend.profiles.length>0;onClicked:backend.deleteRouterProfile(backend.profiles[savedRouter.currentIndex].id)}
    }
    FgButton {Layout.columnSpan:2;text:root.tr("أجهزة الشبكة والأكسسات","Network devices & access points");enabled:backend.routerConnected&&!backend.busy;onClicked:{root.inventoryView=true;root.selectedRouterId="";root.selectedRouterRow=({});backend.discoverNetworkDevices()}}
    Text {Layout.columnSpan:2;Layout.fillWidth:true;color:theme.muted;wrapMode:Text.Wrap;text:root.tr("اختر جهازًا لنسخ عنوانه، ثم احفظ كل ميكروتيك باسم مستقل. أجهزة DHCP وARP ليست مؤكدة الاتصال، وضبط الأكسس يعتمد على موديله.","Select a device to fill its IP, then save each MikroTik with a separate name. DHCP/ARP devices are not verified online; AP configuration depends on its model.")}
    Text {Layout.columnSpan: 2; Layout.fillWidth: true; text: root.tr("RouterOS API / API-SSL / REST. اختر صفًا لتعديل العنصر. الأسرار محجوبة.","RouterOS API / API-SSL / REST. Select a row to edit. Secrets are redacted."); color: theme.muted; wrapMode: Text.Wrap}
   }
   RouterToolsWorkspace {enabled:!networkBilling.busy;visible: root.page==="tools"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   BusinessWorkspace {id:localBusiness;visible: root.page==="commerce"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   VoucherStudio {id:voucherStudio;visible: root.page==="vouchers"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   Rectangle {
    visible: root.page!=="about"&&root.page!=="billing"&&root.page!=="transfer"&&root.page!=="monitor"&&root.page!=="tools"&&root.page!=="commerce"&&root.page!=="vouchers"&&root.page!=="home"&&root.page!=="settings"; Layout.fillWidth: true; Layout.fillHeight: true; color: theme.navy; radius: 14; border.color: theme.muted; clip: true
    Flickable {
     anchors.fill: parent; anchors.margins: 12; contentWidth: Math.max(width,backend.columns.length*190); contentHeight: height; clip: true
     ScrollBar.horizontal: ScrollBar {}
     ColumnLayout {
      width: parent.contentWidth; height: parent.height; spacing: 8
      Row {
       Repeater {model: backend.columns; Text {required property string modelData; text: modelData; width: 190; color: theme.blue; font.bold: true; elide: Text.ElideRight; padding: 8}}
      }
      ListView {
       Layout.fillWidth: true; Layout.fillHeight: true; clip: true; model: backend.rows; spacing: 2
       ScrollBar.vertical: ScrollBar {}
       delegate: Rectangle {
        required property var modelData
        required property int index
        width: ListView.view.width; height: 46; color: root.page==="router"&&root.selectedRouterId===modelData[".id"] ? theme.blue : index%2===0 ? theme.panel : theme.raised
        TapHandler {onTapped: if(root.page==="router"){root.selectedRouterId=parent.modelData[".id"]||"";root.selectedRouterRow=parent.modelData;if(parent.modelData.host)routerUrl.text=parent.modelData.host}}
        Row {Repeater {model: backend.columns; Text {required property string modelData; text: parent.parent.modelData[modelData]||""; width: 190; height: 46; padding: 8; color: theme.white; elide: Text.ElideRight; verticalAlignment: Text.AlignVCenter; ToolTip.visible: hovered.hovered; ToolTip.text: text; HoverHandler {id: hovered}}}}
       }
      }
     }
    }
   }
   Item {visible: root.page==="settings"; Layout.fillHeight: true}
  }
 }
 FgDialog {
  id: commandPreview; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: Math.min(640,parent.width-40)
  title: root.tr("مراجعة أمر الراوتر","Review router command")
  contentItem: ColumnLayout {Text {text: backend.preview; color: theme.white; Layout.fillWidth: true; wrapMode: Text.Wrap} FgButton {text: root.tr("تأكيد التنفيذ","Confirm execution"); enabled: !backend.busy; onClicked: {backend.executePreview();commandPreview.close()}}}
 }
 Connections {target: backend; function onChanged(){if(backend.preview.length>0&&!commandPreview.visible)commandPreview.open()}}
 FgDialog {
  id: adminEditor; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: Math.min(680,parent.width-40);height:Math.min(620,parent.height-40)
  property var values:({})
  function put(key,value){let next=Object.assign({},values);if(value.length)next[key]=value;else delete next[key];values=next}
  onOpened:{values=({});advancedFields.text=""}
  title:root.tr("إدارة العنصر","Manage item")+" • /"+menu.currentText
  contentItem:ColumnLayout {
   Text {Layout.fillWidth:true;color:theme.silver;wrapMode:Text.Wrap;text:root.tr("املأ الحقول التي تريد إرسالها. الحقول الفارغة لا تغير القيم الحالية، وكلمة المرور لا تُعرض.","Fill the fields you want to send. Empty fields preserve current values; passwords are never shown.")}
   ScrollView {id:moduleScroll;Layout.fillWidth:true;Layout.fillHeight:true;clip:true;contentWidth:availableWidth
    ColumnLayout {width:moduleScroll.availableWidth;spacing:12
     Repeater {model:root.routerModule.fields||[];delegate:ColumnLayout {required property var modelData;Layout.fillWidth:true
      Text {text:root.tr(modelData.ar,modelData.en);color:theme.silver}
      FgField {id:editorField;Layout.fillWidth:true;Connections {target:adminEditor;function onOpened(){editorField.clear()}} echoMode:modelData.key==="password"?TextInput.Password:TextInput.Normal;placeholderText:root.selectedRouterRow[modelData.key]||modelData.key;onTextEdited:adminEditor.put(modelData.key,text)}
     }}
     Text {text:root.tr("حقول إضافية JSON (اختياري)","Additional JSON fields (optional)");color:theme.silver}
     TextArea {id:advancedFields;Layout.fillWidth:true;Layout.preferredHeight:100;placeholderText:'{"comment":"..."}';color:theme.white;selectByMouse:true;background:Rectangle {color:theme.navy}}
    }
   }
   Flow {Layout.fillWidth:true;spacing:8
    FgButton {text:root.tr("إضافة","Add");enabled:root.routerModule.create&&!backend.busy;onClicked:adminEditor.submit("add")}
    FgButton {text:root.tr("تعديل المحدد","Edit selected");enabled:root.routerModule.edit&&!backend.busy&&(root.selectedRouterId.length>0||menu.currentText==="ip/dns"||menu.currentText==="system/clock"||menu.currentText==="system/identity");onClicked:adminEditor.submit("set")}
    FgButton {text:root.tr("إلغاء","Cancel");onClicked:adminEditor.close()}
   }
  }
  function submit(action){let fields=Object.assign({},values);try{if(advancedFields.text.trim().length){let extra=JSON.parse(advancedFields.text);if(!extra||Array.isArray(extra)||typeof extra!=="object")throw "JSON";for(let key in extra){if(typeof extra[key]!=="string"||key===".id")throw "JSON";fields[key]=extra[key]}}backend.admin(menu.currentText,action,action==="set"?root.selectedRouterId:"",JSON.stringify(fields));close()}catch(error){advancedFields.placeholderText=root.tr("أدخل JSON بقيم نصية صحيحة","Enter valid JSON with string values")}}
 }

 CommandLibrary {id:commandLibrary;parent:Overlay.overlay;arabic:root.arabic;onSelected:function(command){commandInput.text=command;root.showTerminal()}}
 Window {
  id: terminalWindow
  title: root.tr("FG MTM — ترمنال الراوتر","FG MTM — Router terminal")
  width: 800; height: 500; minimumWidth: 550; minimumHeight: 350; color: theme.black
  transientParent: root; flags: Qt.Window
  ColumnLayout {
   anchors.fill: parent; anchors.margins: 18; spacing: 12
   Text {text: root.tr("أوامر RouterOS • راجع التغييرات قبل التنفيذ","RouterOS commands • review changes before execution"); color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}
   ScrollView {Layout.fillWidth: true; Layout.fillHeight: true; TextArea {text: backend.terminal; readOnly: true; selectByMouse: true; color: theme.white; font.family: "Consolas"; font.pixelSize: 14; wrapMode: TextEdit.Wrap; background: Rectangle {color: theme.navy}}}
   FgField {id: commandInput; Layout.fillWidth: true; placeholderText: "/system resource print"; LayoutMirroring.enabled: false; onAccepted: if(!backend.busy)backend.command(text)}
   Flow {Layout.fillWidth:true;spacing:8
    FgButton {text: root.tr("معاينة / تنفيذ","Preview / run"); enabled: !backend.busy; onClicked: backend.command(commandInput.text)}
    FgButton {text: root.tr("المكتبة","Library");onClicked:commandLibrary.open()}
    FgButton {text: root.tr("نسخ","Copy");onClicked:backend.copyText(commandInput.text)}
    FgButton {text: root.tr("مسح","Clear"); onClicked: backend.clearTerminal()}
   }
  }
 }
}
