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
 color: "#07121B"
 property bool arabic: true
 property bool inventoryView: false
 property string routerGroup:"ALL"
 readonly property int toolsTab:toolsWorkspace.currentTab
 readonly property bool onlineOnly:toolsWorkspace.onlineOnly
 readonly property string commerceTable:localBusiness.selectedTable
 property string page: "home"
 property var history: []
 property int businessPage: 0
 property string selectedRouterId: ""
 property var selectedRouterRow: ({})
 property var routerModule: backend.module(menu.currentText)
 property var tableKeys: ["subscribers","plans","invoices","ledger","sales","expenses","team_members","reseller_entries","router_bindings","network_jobs","payment_details","invoice_voids","sale_voids","import_batches","audit","organizations","branches"]
 property var tableAr: ["المشتركون","الباقات","الفواتير","السجل المالي","المبيعات","المصروفات","الموظفون والموزعون","حركة الموزعين","الراوترات","مهام الشبكة","تفاصيل الدفع","إلغاء الفواتير","إلغاء المبيعات","دفعات الاستيراد","التدقيق","المؤسسة","الفروع"]
 function tr(ar,en){return arabic?ar:en}
 function snapshot(){return {page:page,routerGroup:routerGroup,toolsTab:toolsWorkspace.currentTab,online:toolsWorkspace.onlineOnly,commerceTable:localBusiness.selectedTable,routerMenu:menu.currentText}}
 function navigate(value){if(value!==page){history=history.concat([snapshot()]);page=value;backend.clearView();if(page==="business")filter()}}
 function navigateTask(value){var parts=value.split(":");if(parts[0]===page&&parts.length>1)history=history.concat([snapshot()]);navigate(parts[0]);if(parts[0]==="router"){routerGroup=parts[1]||"ALL";menu.currentIndex=0}if(parts[0]==="tools")toolsWorkspace.showTab(Number(parts[1]||0),parts[2]==="online");if(parts[0]==="commerce"&&parts[1])localBusiness.openTable(parts[1])}
 function goBack(){if(!history.length)return;var items=history.slice(),previous=items.pop();history=items;page=previous.page;routerGroup=previous.routerGroup;menu.currentIndex=menu.model.indexOf(previous.routerMenu);if(page==="commerce")localBusiness.openTable(previous.commerceTable);toolsWorkspace.showTab(previous.toolsTab,previous.online);backend.clearView();if(page==="business")filter()}
 function showPlans(){navigateTask("commerce:plans")}
 function showSales(){navigateTask("commerce:sales")}
 function showSubscribers(){navigateTask("tools:2")}
 function showOnline(){navigateTask("tools:2:online")}

 function closeReviewDialogs(){deviceDetails.close();connectionOptions.close();discoveryDetails.close();localBusiness.closeDialogs();voucherStudio.closeDialogs();adminEditor.close();terminalWindow.hide()}
 function showBusinessEditor(){localBusiness.showEditorFor("renew")}
 function showVoucherPreview(){voucherStudio.previewFirst()}
 function showRouterEditor(){adminEditor.open()}
 function showConnectionOptions(){connectionOptions.open()}
 function showDiscoveryDetails(){discoveryDetails.open()}
 function showHotspotProof(manual){page="tools";toolsWorkspace.showWanProof(manual)}
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
   Layout.fillHeight: true; Layout.preferredWidth: 276; color: theme.navy
   ColumnLayout {
    anchors.fill: parent; anchors.margins: 16; spacing: 10
    Text {text: "FG MACHINES"; color: theme.silver; font.pixelSize: 23; font.bold: true}
    Text {text: "FG MTM"; color: theme.blue; font.pixelSize: 18; font.bold: true}
    Text {text: root.tr("إدارة شبكتك وأعمالك","Network & business"); color: theme.muted; font.pixelSize: 13}
    Rectangle {Layout.fillWidth: true; height: 1; color: theme.silver; opacity: 0.5}
    ScrollView {id:navScroll;Layout.fillWidth: true;Layout.fillHeight: true;clip:true;contentWidth:availableWidth;ScrollBar.vertical:ScrollBar {policy:ScrollBar.AlwaysOn;width:5;background:Rectangle {color:theme.panel;radius:2} contentItem:Rectangle {color:theme.mint;opacity:0.55;radius:2}}
    ColumnLayout {width:navScroll.availableWidth-10;spacing:8
    Repeater {
     model: [{key:"accesspoints",ar:"نقاط الوصول المتصلة",en:"Connected Access Points",icon:19},{key:"home",ar:"الرئيسية",en:"Main menu",icon:0},{key:"tools",ar:"الإعداد المتقدم",en:"Advanced Setup",icon:3},{key:"router:NETWORK",ar:"الشبكة والاتصال",en:"Network & connectivity",icon:19},{key:"router:SYSTEM",ar:"النظام والأمان",en:"System & security",icon:11},{key:"vouchers",ar:"إنشاء الكروت",en:"Voucher Studio",icon:6},{key:"commerce",ar:"المشتركون والحسابات",en:"Subscribers & accounts",icon:2},{key:"about",ar:"عن المطور",en:"About developer",icon:5},{key:"billing",ar:"ربط وتجديد الشبكة",en:"Bindings & renewal",icon:18},{key:"transfer",ar:"الاستيراد والتقارير",en:"Import & reports",icon:14},{key:"monitor",ar:"المراقبة والتنبيهات",en:"Monitoring & alerts",icon:7},{key:"business",ar:"أعمال الخادم",en:"Server business",icon:13},{key:"radius",ar:"RADIUS",en:"RADIUS",icon:13},{key:"diagnostics",ar:"تشخيص الخادم",en:"Server diagnostics",icon:3},{key:"settings",ar:"الاتصال بالخادم",en:"Server connection",icon:17}]
     delegate: FgButton {
      id:sidebarButton
      required property var modelData
      property bool selected:root.page===modelData.key.split(":")[0]&&(modelData.key.indexOf("router:")!==0||root.routerGroup===modelData.key.split(":")[1])
      property color sectionColor:[theme.blue,theme.gold,theme.mint,"#B897FF","#FF91AB","#7ED9F5"][modelData.icon%6]
      Layout.fillWidth:true;implicitHeight:52;text:root.tr(modelData.ar,modelData.en);padding:8
      contentItem:RowLayout {spacing:12
       NavIcon {kind:sidebarButton.modelData.icon;ink:sidebarButton.sectionColor;Layout.preferredWidth:40;Layout.preferredHeight:40}
       Text {Layout.fillWidth:true;text:sidebarButton.text;color:sidebarButton.selected?sidebarButton.sectionColor:theme.silver;font.pixelSize:14;font.bold:sidebarButton.selected;wrapMode:Text.Wrap;horizontalAlignment:root.arabic?Text.AlignRight:Text.AlignLeft;verticalAlignment:Text.AlignVCenter}
      }
      background:Rectangle {radius:12;color:sidebarButton.selected?"#193448":sidebarButton.hovered?theme.raised:"#0B1D2B";border.color:sidebarButton.selected?sidebarButton.sectionColor:"#223B4D"}
      accent: root.page===modelData.key.split(":")[0]&&(modelData.key.indexOf("router:")!==0||root.routerGroup===modelData.key.split(":")[1]) ? theme.blue : theme.muted
      onClicked: root.navigateTask(modelData.key)
     }
    }
    }}
    FgButton {Layout.fillWidth: true; text: root.tr("ترمنال عائم • F4","Floating terminal • F4"); accent: theme.mint; onClicked: {terminalWindow.show();terminalWindow.raise()}}
    FgButton {Layout.fillWidth: true; text: root.arabic ? "English" : "العربية"; onClicked: root.arabic=!root.arabic}
    Text {text: "FG Machines · 0.17.4"; color: theme.muted; font.pixelSize: 12}
   }
  }
  ColumnLayout {
   Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 20; spacing: 12
   RowLayout {
    Layout.fillWidth: true
    FgButton {text: root.tr("رجوع","Back"); enabled: root.history.length>0; onClicked:root.goBack()}
    Text {text: root.tr(({accesspoints:"نقاط الوصول المتصلة",home:"الرئيسية",business:"أعمال الخادم",commerce:"الأعمال المحلية",vouchers:"الكروت والأرشيف",radius:"RADIUS",router:"الراوتر والأوامر",tools:"إدارة الشبكة",diagnostics:"تشخيص الخادم",settings:"الاتصال بالخادم",monitor:"المراقبة والتنبيهات",transfer:"الاستيراد والتقارير",billing:"ربط وتجديد الشبكة",about:"عنا"})[root.page],({accesspoints:"Connected Access Points",home:"Overview",business:"Server business",commerce:"Local business",vouchers:"Vouchers & archive",radius:"RADIUS",router:"Router & commands",tools:"Network tools",diagnostics:"Server diagnostics",settings:"Server connection",monitor:"Monitoring & alerts",transfer:"Import & reports",billing:"Bindings & renewal",about:"About"})[root.page]); color: theme.gold; font.pixelSize: 26; font.bold: true; Layout.fillWidth: true}
    Rectangle {width: 10; height: 10; radius: 5; color: backend.routerConnected ? theme.mint : theme.muted}
    Text {text: backend.routerConnected?root.tr("الراوتر متصل","Router connected"):root.tr("الراوتر غير متصل","Router disconnected"); color: theme.silver}
   }
   Text {Layout.fillWidth: true; text: backend.status.includes(" • ") ? backend.status.split(" • ")[root.arabic?0:1] : backend.status; color: theme.mint; wrapMode: Text.Wrap}
   HomeWorkspace {routerConnected:backend.routerConnected;serverConnected:backend.connected;visible:root.page==="home";arabic:root.arabic;Layout.fillWidth:true;Layout.fillHeight:true;onOpenPage:function(key){root.navigateTask(key)}}
   AccessPointWorkspace {visible:root.page==="accesspoints";arabic:root.arabic;Layout.fillWidth:true;Layout.fillHeight:true}
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
     FgCombo {id: tables; Layout.preferredWidth: 220; model: root.arabic ? root.tableAr : root.tableKeys; onActivated: {root.businessPage=0;root.filter()}}
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
   ColumnLayout {
    visible: root.page==="router"; Layout.fillWidth:true; spacing:10
    Rectangle {
     Layout.fillWidth:true; implicitHeight:connectionLayout.implicitHeight+28
     radius:14;color:theme.panel;border.color:"#315366"
     ColumnLayout {
      id:connectionLayout;anchors.fill:parent;anchors.margins:14;spacing:10
      RowLayout {
       Layout.fillWidth:true
       NavIcon {kind:10;ink:theme.blue;Layout.preferredWidth:32;Layout.preferredHeight:32}
       Text {text:root.tr("اتصال الراوتر","Router connection");color:theme.white;font.pixelSize:18;font.bold:true;Layout.fillWidth:true}
       FgButton {objectName:"discoverRoutersButton";text:root.tr("اكتشاف الراوترات","Discover routers");accent:theme.mint;enabled:!backend.busy;onClicked:{root.inventoryView=true;root.selectedRouterId="";root.selectedRouterRow=({});backend.clearView();backend.discoverRouters()}}
       FgButton {objectName:"connectionOptionsButton";text:root.tr("المحفوظ والمتقدم","Saved & advanced");accent:theme.gold;onClicked:connectionOptions.open()}
      }
      GridLayout {
       Layout.fillWidth:true;columns:3;columnSpacing:12;rowSpacing:4
       Text {text:root.tr("عنوان الراوتر","Router address");color:theme.silver;Layout.fillWidth:true}
       Text {text:root.tr("اسم المستخدم","Username");color:theme.silver;Layout.fillWidth:true}
       Text {text:root.tr("كلمة المرور","Password");color:theme.silver;Layout.fillWidth:true}
       FgField {id:routerUrl;objectName:"routerAddressField";Layout.fillWidth:true;Layout.minimumWidth:100;placeholderText:"192.168.88.1";LayoutMirroring.enabled:false}
       FgField {id:routerUser;Layout.fillWidth:true;Layout.minimumWidth:80;placeholderText:"admin";LayoutMirroring.enabled:false}
       FgField {id:routerPassword;Layout.fillWidth:true;Layout.minimumWidth:80;echoMode:TextInput.Password;placeholderText:root.tr("كلمة مرور الراوتر","Router password");LayoutMirroring.enabled:false}
      }
      RowLayout {
       Layout.fillWidth:true;spacing:12
       Text {text:root.tr("البروتوكول","Protocol");color:theme.silver}
       FgCombo {id:routerProtocol;Layout.preferredWidth:140;model:["REST","API_SSL","API","AUTO","REST_HTTP"];LayoutMirroring.enabled:false;onActivated:routerPort.text=currentIndex===0?"443":currentIndex===1?"8729":currentIndex===4?"80":"8728"}
       Text {text:root.tr("المنفذ","Port");color:theme.silver}
       FgField {id:routerPort;Layout.preferredWidth:84;LayoutMirroring.enabled:false;text:"443";validator:IntValidator {bottom:1;top:65535} inputMethodHints:Qt.ImhDigitsOnly}
       Item {Layout.fillWidth:true}
       FgButton {objectName:"connectRouterButton";text:root.tr("اتصال وقراءة","Connect & read");filled:true;accent:theme.blue;enabled:!backend.busy&&routerUrl.text.trim().length>0&&routerUser.text.trim().length>0&&routerPort.acceptableInput;onClicked:{root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.connectRouter(routerUrl.text,Number(routerPort.text),routerUser.text,routerPassword.text,routerProtocol.currentText,menu.currentText);if(!rememberRouterPassword.checked)routerPassword.clear()}}
      }
     }
    }
    RowLayout {
     Layout.fillWidth:true;spacing:8
     Text {text:root.tr("القسم","Section");color:theme.silver}
     FgCombo {id:menu;Layout.fillWidth:true;Layout.minimumWidth:100;model:backend.menus.filter(function(key){return root.routerGroup==="ALL"||backend.module(key).group===root.routerGroup});LayoutMirroring.enabled:false;onActivated:{root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.clearView()}}
     FgButton {text:root.tr("قراءة","Read");enabled:backend.routerConnected&&!backend.busy;onClicked:{root.inventoryView=false;root.selectedRouterId="";root.selectedRouterRow=({});backend.command("/"+menu.currentText+" print")}}
     FgButton {text:root.tr("الأجهزة والأكسسات","Devices & APs");enabled:backend.routerConnected&&!backend.busy;onClicked:{root.inventoryView=true;root.selectedRouterId="";root.selectedRouterRow=({});backend.clearView();backend.discoverNetworkDevices()}}
    }
    RowLayout {
     Layout.fillWidth:true;spacing:8
     FgButton {text:root.tr("إضافة / تعديل","Add / edit");enabled:!root.inventoryView&&backend.routerConnected&&!backend.busy&&(root.routerModule.create||root.routerModule.edit);onClicked:adminEditor.open()}
     FgButton {text:root.tr("تفعيل","Enable");enabled:!root.inventoryView&&root.routerModule.toggle&&root.selectedRouterId.length>0&&!backend.busy;onClicked:backend.admin(menu.currentText,"enable",root.selectedRouterId,"{}")}
     FgButton {text:root.tr("تعطيل","Disable");enabled:!root.inventoryView&&root.routerModule.toggle&&root.selectedRouterId.length>0&&!backend.busy;onClicked:backend.admin(menu.currentText,"disable",root.selectedRouterId,"{}")}
     FgButton {text:root.tr("حذف","Delete");accent:theme.error;enabled:!root.inventoryView&&root.routerModule.delete&&root.selectedRouterId.length>0&&!backend.busy;onClicked:backend.admin(menu.currentText,"remove",root.selectedRouterId,"{}")}
     Item {Layout.fillWidth:true}
     FgButton {objectName:"discoveryDiagnosticsButton";text:root.tr("تشخيص الاكتشاف","Discovery details");visible:backend.discoveryDiagnostics.length>0;accent:theme.gold;onClicked:discoveryDetails.open()}
    }
    Text {
     Layout.fillWidth:true;wrapMode:Text.Wrap;color:root.inventoryView?theme.mint:theme.muted;font.pixelSize:14
     text:root.inventoryView?(backend.busy?root.tr("جارٍ البحث… ستظهر الأجهزة هنا.","Searching… Devices will appear here."):backend.rows.length>0?root.tr("اختر جهازًا من النتائج لتعبئة عنوانه، ثم أدخل حساب الراوتر واضغط اتصال.","Select a device to fill its address, then enter router credentials and connect."):root.tr("لم تظهر أجهزة. تحقق من الشبكة أو أدخل عنوان الراوتر يدويًا. تفاصيل الفحص متاحة في تشخيص الاكتشاف.","No devices found. Check your network or enter the address manually. Open discovery details for the scan report.")):root.tr("اختر القسم لقراءته، ثم اختر صفًا لإدارة العنصر.","Read a section, then select a row to manage the item.")
    }
   }
   RouterToolsWorkspace {onConnectRouter:root.navigateTask("router");id:toolsWorkspace;enabled:!networkBilling.busy;visible: root.page==="tools"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   BusinessWorkspace {id:localBusiness;visible: root.page==="commerce"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   VoucherStudio {id:voucherStudio;visible: root.page==="vouchers"; arabic: root.arabic; Layout.fillWidth: true; Layout.fillHeight: true}
   Rectangle {
    visible: root.page!=="about"&&root.page!=="billing"&&root.page!=="transfer"&&root.page!=="monitor"&&root.page!=="tools"&&root.page!=="commerce"&&root.page!=="vouchers"&&root.page!=="home"&&root.page!=="settings"; Layout.fillWidth: true; Layout.fillHeight: true; color: theme.navy; radius: 14; border.color: theme.muted; clip: true
    ListView {
     id:inventoryResults;objectName:"discoveryResultsList";visible:root.page==="router"&&root.inventoryView
     anchors.fill:parent;anchors.margins:12;clip:true;spacing:8;model:backend.rows
     ScrollBar.vertical:ScrollBar {}
     delegate:Rectangle {
      required property var modelData
      width:inventoryResults.width;implicitHeight:deviceContent.implicitHeight+24;radius:10
      color:root.selectedRouterRow===modelData?theme.raised:theme.panel;border.color:theme.muted
      RowLayout {
       id:deviceContent;anchors.fill:parent;anchors.margins:12;spacing:12
       NavIcon {kind:10;ink:theme.mint;Layout.preferredWidth:36;Layout.preferredHeight:36}
       ColumnLayout {
        Layout.fillWidth:true;spacing:5
        Text {Layout.fillWidth:true;text:modelData.name||modelData.identity||root.tr("جهاز شبكة","Network device");color:theme.white;font.bold:true;font.pixelSize:16;elide:Text.ElideRight}
        Text {Layout.fillWidth:true;text:modelData.host||root.tr("لا يوجد عنوان IP؛ لا يمكن الاتصال بهذا الصف","No IP address; this entry cannot be connected");color:theme.blue;font.pixelSize:16;LayoutMirroring.enabled:false;elide:Text.ElideRight}
        Text {Layout.fillWidth:true;text:(modelData.method||modelData.source||"")+"  ·  "+(modelData.verification||modelData.evidence||"");color:theme.muted;font.pixelSize:14;wrapMode:Text.Wrap}
       }
       FgButton {text:root.tr("التفاصيل","Details");onClicked:{deviceDetails.record=modelData;deviceDetails.open()}}
       FgButton {text:root.tr("اختيار","Select");accent:theme.mint;enabled:Boolean(modelData.host);onClicked:{root.selectedRouterRow=modelData;root.selectedRouterId="";routerUrl.text=modelData.host;routerUser.forceActiveFocus()}}
      }
      TapHandler {onTapped:{root.selectedRouterRow=parent.modelData;root.selectedRouterId="";if(parent.modelData.host){routerUrl.text=parent.modelData.host;routerUser.forceActiveFocus()}}}
     }
    }
    Flickable {
     visible:root.page!=="router"||!root.inventoryView
     objectName:"routerResultsTable"; anchors.fill: parent; anchors.margins: 12; contentWidth: Math.max(width,backend.columns.length*190); contentHeight: height; clip: true
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
        TapHandler {onTapped: if(root.page==="router"){root.selectedRouterId=parent.modelData[".id"]||"";root.selectedRouterRow=parent.modelData;if(parent.modelData.host){routerUrl.text=parent.modelData.host;routerUser.forceActiveFocus()}}}
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
  id:deviceDetails;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;property var record:({})
  width:Math.min(640,parent.width-40);height:Math.min(500,parent.height-40);title:root.tr("تفاصيل الجهاز","Device details")
  contentItem:ScrollView {clip:true;TextArea {readOnly:true;selectByMouse:true;wrapMode:TextEdit.Wrap;color:theme.silver;font.pixelSize:15;LayoutMirroring.enabled:false;text:JSON.stringify(deviceDetails.record,null,2);background:Rectangle {color:theme.navy}}}
 }
 FgDialog {
  id:connectionOptions;objectName:"connectionOptionsDialog";parent:Overlay.overlay;anchors.centerIn:parent;modal:true
  width:Math.min(620,parent.width-40);height:Math.min(540,parent.height-40)
  title:root.tr("الاتصالات المحفوظة والإعداد المتقدم","Saved connections & advanced settings")
  contentItem:ColumnLayout {
   spacing:12
   ScrollView {
   id:connectionOptionsScroll;Layout.fillWidth:true;Layout.fillHeight:true;clip:true;contentWidth:availableWidth
   ColumnLayout {
    width:connectionOptionsScroll.availableWidth;spacing:12
    Text {text:root.tr("استدعاء اتصال محفوظ","Load a saved connection");color:theme.gold;font.bold:true}
    FgCombo {id:savedRouter;Layout.fillWidth:true;model:backend.profiles;textRole:"name";onActivated:{let p=backend.profiles[currentIndex];if(!p)return;routerUrl.text=p.host;routerUser.text=p.user;routerPort.text=String(p.port);routerProtocol.currentIndex=routerProtocol.model.indexOf(p.protocol);rememberRouterPassword.checked=Boolean(p.hasPassword);routerPassword.text=p.hasPassword?backend.routerProfilePassword(p.id):"";connectionOptions.close()}}
    FgButton {text:root.tr("حذف الاتصال المحدد","Delete selected connection");accent:theme.error;enabled:savedRouter.currentIndex>=0&&savedRouter.currentIndex<backend.profiles.length&&!backend.busy;onClicked:{backend.deleteRouterProfile(backend.profiles[savedRouter.currentIndex].id);routerPassword.clear();rememberRouterPassword.checked=false}}
    Rectangle {Layout.fillWidth:true;implicitHeight:1;color:"#315366"}
    Text {text:root.tr("حفظ الاتصال الحالي","Save current connection");color:theme.gold;font.bold:true}
    FgField {id:profileName;Layout.fillWidth:true;placeholderText:root.tr("اسم واضح للراوتر، مثل فرع دمياط","Router name, for example Main branch");horizontalAlignment:root.arabic?Text.AlignRight:Text.AlignLeft}
    CheckBox {id:rememberRouterPassword;Layout.fillWidth:true;text:root.tr("حفظ كلمة المرور على هذا الكمبيوتر","Remember password on this PC");palette.windowText:theme.silver;font.pixelSize:14}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.muted;text:root.tr("تُشفّر كلمة المرور وتُربط بحساب Windows الحالي.","The password is encrypted for the current Windows account.");font.pixelSize:14}
    FgButton {text:root.tr("حفظ الاتصال","Save connection");accent:theme.gold;enabled:profileName.text.trim().length>0&&routerUrl.text.trim().length>0&&routerUser.text.trim().length>0&&routerPort.acceptableInput&&!backend.busy;onClicked:backend.saveRouterProfile(profileName.text,commerce.scope,routerUrl.text,Number(routerPort.text),routerUser.text,routerProtocol.currentText,routerPassword.text,rememberRouterPassword.checked)}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:root.tr("اختر البروتوكول والمنفذ المفعّلين في الراوتر.","Select the protocol and port enabled on your router.");font.pixelSize:14}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;LayoutMirroring.enabled:false;color:theme.silver;font.pixelSize:14;text:"REST: 443  •  API-SSL: 8729  •  API: 8728"}
   }
   }
   FgButton {objectName:"connectionOptionsCloseButton";text:root.tr("إغلاق","Close");onClicked:connectionOptions.close()}
  }
 }
 FgDialog {
  id:discoveryDetails;objectName:"discoveryDetailsDialog";parent:Overlay.overlay;anchors.centerIn:parent;modal:true
  width:Math.min(760,parent.width-40);height:Math.min(580,parent.height-40)
  title:root.tr("تشخيص اكتشاف الراوترات","Router discovery diagnostics")
  contentItem:ColumnLayout {
   spacing:12
   Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;font.pixelSize:14;text:root.tr("تقرير كل بطاقة شبكة. أجهزة DHCP وARP لا تثبت أن الجهاز متصل الآن. ضبط الأكسس يعتمد على موديله.","Report per network adapter. DHCP/ARP entries do not confirm current connectivity. AP configuration depends on its model.")}
   ListView {
    id:discoveryReport;Layout.fillWidth:true;Layout.fillHeight:true;clip:true;spacing:10;model:backend.discoveryDiagnostics
    ScrollBar.vertical:ScrollBar {}
    delegate:Rectangle {
     required property var modelData
     width:discoveryReport.width;implicitHeight:adapterLayout.implicitHeight+24;radius:12;color:theme.panel;border.color:"#315366"
     ColumnLayout {
      id:adapterLayout;anchors.fill:parent;anchors.margins:12;spacing:6
      Text {Layout.fillWidth:true;text:modelData.adapter||"—";color:theme.gold;font.pixelSize:16;font.bold:true;wrapMode:Text.Wrap;LayoutMirroring.enabled:false}
      Text {Layout.fillWidth:true;text:"IPv4: "+(modelData.ipv4||"—")+"    CIDR: "+(modelData.subnet||"—");color:theme.white;font.pixelSize:14;wrapMode:Text.Wrap;LayoutMirroring.enabled:false}
      Text {Layout.fillWidth:true;text:"Broadcast: "+(modelData.broadcast||"—")+"    UDP 5678: "+(modelData.udp5678||"—");color:theme.silver;font.pixelSize:14;wrapMode:Text.Wrap;LayoutMirroring.enabled:false}
      Text {Layout.fillWidth:true;text:"MNDP: "+(modelData.mndp||"—")+"\n"+root.tr("الفحص البديل: ","Fallback: ")+(modelData.scan||"—");color:theme.muted;font.pixelSize:14;wrapMode:Text.Wrap;LayoutMirroring.enabled:false}
     }
    }
   }
   FgButton {text:root.tr("إغلاق","Close");onClicked:discoveryDetails.close()}
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

 CommandLibrary {id:commandLibrary;parent:Overlay.overlay;arabic:root.arabic;onSelected:function(command){commandInput.text=command;root.showTerminal();Qt.callLater(function(){commandInput.forceActiveFocus()})}}
 Window {
  id: terminalWindow
  title: root.tr("FG MTM — ترمنال الراوتر السريع","FG MTM — Quick Router terminal")
  width: 1040; height: 680; minimumWidth: 760; minimumHeight: 520; color: theme.black
  transientParent: root; flags: Qt.Window
  property var recentCommands: []
  property var quickCommands: [
   {ar:"موارد الراوتر",en:"Resources",command:"/system resource print"},
   {ar:"الواجهات",en:"Interfaces",command:"/interface print"},
   {ar:"عناوين IP",en:"IP addresses",command:"/ip address print"},
   {ar:"DHCP Leases",en:"DHCP leases",command:"/ip dhcp-server lease print"},
   {ar:"المتصلون HotSpot",en:"HotSpot active",command:"/ip hotspot active print"},
   {ar:"سجل الأحداث",en:"Event log",command:"/log print"},
   {ar:"Firewall",en:"Firewall",command:"/ip firewall filter print"},
   {ar:"NAT",en:"NAT",command:"/ip firewall nat print"}
  ]
  function remember(command){
   let value=command.trim()
   if(!value.length)return
   let next=[value]
   for(let i=0;i<recentCommands.length&&next.length<10;i++)if(recentCommands[i]!==value)next.push(recentCommands[i])
   recentCommands=next
  }
  function run(command){
   let value=command.trim()
   if(!value.length||backend.busy)return
   remember(value);commandInput.text=value;backend.command(value)
  }
  Shortcut {sequence:"Ctrl+K";context:Qt.WindowShortcut;onActivated:commandLibrary.open()}
  Shortcut {sequence:"Ctrl+L";context:Qt.WindowShortcut;onActivated:backend.clearTerminal()}
  ColumnLayout {
   anchors.fill: parent; anchors.margins: 18; spacing: 12
   RowLayout {
    Layout.fillWidth:true
    ColumnLayout {Layout.fillWidth:true;spacing:2
     Text {text: root.tr("ترمنال RouterOS السريع","Quick RouterOS terminal");color:theme.gold;font.pixelSize:22;font.bold:true}
     Text {text: root.tr("أوامر جاهزة + مكتبة شاملة + مراجعة تلقائية قبل أي تغيير","Quick actions + full command library + automatic review before changes");color:theme.silver;wrapMode:Text.Wrap;Layout.fillWidth:true}
    }
    Rectangle {implicitWidth:170;implicitHeight:38;radius:19;color:backend.routerConnected?"#123B31":"#28333D";border.color:backend.routerConnected?theme.mint:theme.muted
     Text {anchors.centerIn:parent;text:backend.routerConnected?root.tr("● الراوتر متصل","● Router connected"):root.tr("○ غير متصل","○ Disconnected");color:backend.routerConnected?theme.mint:theme.silver;font.bold:true}
    }
   }
   Rectangle {
    Layout.fillWidth:true;implicitHeight:154;color:theme.panel;border.color:"#284457";radius:14
    ColumnLayout {anchors.fill:parent;anchors.margins:12;spacing:8
     RowLayout {Layout.fillWidth:true
      Text {Layout.fillWidth:true;text:root.tr("أوامر سريعة","Quick actions");color:theme.mint;font.bold:true;font.pixelSize:16}
      Text {text:backend.commandLibrary.length+" "+root.tr("أمر في المكتبة","commands in library");color:theme.muted;font.pixelSize:12}
     }
     GridLayout {Layout.fillWidth:true;columns:4;columnSpacing:8;rowSpacing:8
      Repeater {model:terminalWindow.quickCommands;delegate:FgButton {
       required property var modelData
       Layout.fillWidth:true;text:root.arabic?modelData.ar:modelData.en;accent:theme.blue
       onClicked:terminalWindow.run(modelData.command)
      }}
     }
    }
   }
   RowLayout {
    Layout.fillWidth:true;Layout.fillHeight:true;spacing:12
    Rectangle {
     Layout.fillWidth:true;Layout.fillHeight:true;color:theme.navy;border.color:"#284457";radius:14
     ColumnLayout {anchors.fill:parent;anchors.margins:10;spacing:6
      RowLayout {Layout.fillWidth:true
       Text {Layout.fillWidth:true;text:root.tr("نتيجة الأوامر","Command output");color:theme.blue;font.bold:true}
       FgButton {text:root.tr("مسح Ctrl+L","Clear Ctrl+L");onClicked:backend.clearTerminal()}
      }
      ScrollView {Layout.fillWidth:true;Layout.fillHeight:true
       TextArea {text:backend.terminal.length?backend.terminal:root.tr("نفّذ أمرًا من الأزرار السريعة أو افتح المكتبة.","Run a quick action or open the command library.");readOnly:true;selectByMouse:true;color:backend.terminal.length?theme.white:theme.muted;font.family:"Consolas";font.pixelSize:14;wrapMode:TextEdit.Wrap;background:Rectangle {color:"#06121C";radius:10}}
      }
     }
    }
    Rectangle {
     Layout.preferredWidth:300;Layout.fillHeight:true;color:theme.panel;border.color:"#284457";radius:14
     ColumnLayout {anchors.fill:parent;anchors.margins:12;spacing:10
      Text {text:root.tr("المساعدة السريعة","Quick help");color:theme.gold;font.bold:true;font.pixelSize:16}
      Text {Layout.fillWidth:true;text:root.tr("Ctrl+K يفتح مكتبة الأوامر. Enter ينفّذ الأمر. أوامر التغيير لا تُنفّذ مباشرة؛ تظهر شاشة مراجعة أولًا.","Ctrl+K opens the library. Enter runs the command. Changing commands are never applied directly; a review screen appears first.");color:theme.silver;wrapMode:Text.Wrap}
      FgButton {Layout.fillWidth:true;text:root.tr("فتح مكتبة الأوامر","Open command library")+" • Ctrl+K";filled:true;accent:theme.blue;onClicked:commandLibrary.open()}
      Text {visible:terminalWindow.recentCommands.length>0;text:root.tr("الأوامر الأخيرة","Recent commands");color:theme.mint;font.bold:true}
      FgCombo {id:recentCommand;visible:terminalWindow.recentCommands.length>0;Layout.fillWidth:true;model:terminalWindow.recentCommands;onActivated:commandInput.text=currentText}
      FgButton {visible:terminalWindow.recentCommands.length>0;Layout.fillWidth:true;text:root.tr("استخدم الأمر المحدد","Use selected command");onClicked:commandInput.text=recentCommand.currentText}
      Item {Layout.fillHeight:true}
      Text {Layout.fillWidth:true;text:root.tr("للسلامة: لا تدعم النافذة سلاسل الأوامر المركبة أو أوامر shell. الأسرار لا تظهر في سجل النتائج.","Safety: compound command chains and shell commands are not accepted. Secrets are redacted from results.");color:theme.muted;font.pixelSize:12;wrapMode:Text.Wrap}
     }
    }
   }
   Text {text:root.tr("الأمر","Command");color:theme.silver;font.bold:true}
   FgField {id:commandInput;Layout.fillWidth:true;placeholderText:"/system resource print";LayoutMirroring.enabled:false;onAccepted:terminalWindow.run(text)}
   Flow {Layout.fillWidth:true;spacing:8
    FgButton {text:root.tr("تنفيذ / مراجعة","Run / review");filled:true;accent:theme.mint;enabled:!backend.busy&&commandInput.text.trim().length>0;onClicked:terminalWindow.run(commandInput.text)}
    FgButton {text:root.tr("المكتبة","Library");accent:theme.blue;onClicked:commandLibrary.open()}
    FgButton {text:root.tr("نسخ الأمر","Copy command");enabled:commandInput.text.trim().length>0;onClicked:backend.copyText(commandInput.text)}
    FgButton {text:root.tr("مسح خانة الأمر","Clear command");onClicked:commandInput.clear()}
   }
  }
 }
}
