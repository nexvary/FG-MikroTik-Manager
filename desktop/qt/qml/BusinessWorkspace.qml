import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: workspace
 property bool arabic: true
 property var walletData:({})
 property string selectedId: ""
 property string selectedTable: "subscribers"
 property string requestId: ""
 property int page: 0
 property var operations: ["subscriber","plan","renew","charge","payment","sale","expense","reverse","void_invoice","void_sale","reverse_expense","team","team_enable","team_disable","wallet","branch","select_branch","create_account","reset_password"]
 property var enLabels:["Add subscriber","Add plan","Renew subscription","Add charge","Collect payment","Record sale","Record expense","Reverse ledger entry","Void invoice","Void sale","Reverse expense","Add staff / reseller","Enable staff","Disable staff","Reseller wallet entry","Add branch","Select branch","Create staff account","Reset password"]
 property var subscriberChoices:[]
 property var planChoices:[]
 property var memberChoices:[]
 property var labels: ["إضافة مشترك","إضافة باقة","تجديد اشتراك","إضافة مديونية","تحصيل دفعة","عملية بيع","مصروف","عكس قيد مالي","إلغاء فاتورة","إلغاء بيع","عكس مصروف","إضافة موظف / موزع","تفعيل موظف","تعطيل موظف","حركة محفظة موزع","إضافة فرع","اختيار فرع","إنشاء حساب موظف","تغيير كلمة مرور"]
 property string op: operations[action.currentIndex]
 function tr(ar,en){return arabic?ar:en}
 function includes(values){return values.indexOf(op)>=0}
 function amount(value){let s=String(value),negative=s[0]==='-';if(negative)s=s.substring(1);s=s.padStart(3,'0');return (negative?'-':'')+s.slice(0,-2)+'.'+s.slice(-2)}
 function label(key){let names={name:["الاسم","Name"],phone:["الهاتف","Phone"],account:["حساب الراوتر","Router account"],service:["الخدمة","Service"],currency:["العملة","Currency"],amount_minor:["المبلغ","Amount"],paid_minor:["المحصّل","Collected"],price_minor:["السعر","Price"],balance_minor:["الرصيد","Balance"],days:["الأيام","Days"],kind:["النوع","Type"],note:["ملاحظات","Notes"],reason:["السبب","Reason"],role:["الدور","Role"],active:["الحالة","State"],method:["طريقة الدفع","Payment method"],reference:["مرجع الدفع","Payment reference"],profile:["الباقة على الراوتر","Router profile"],state:["حالة التطبيق","Applied state"],actor:["الحساب","Account"],action:["الإجراء","Action"],created_at:["التاريخ","Date"],customer:["العميل","Customer"],customer_name:["المشترك","Subscriber"],plan_name:["الباقة","Plan"],starts_day:["بداية الاشتراك","Subscription start"],ends_day:["النهاية غير شاملة","End exclusive"],username:["اسم الحساب","Username"],id:["المرجع","Reference"]};return names[key]?tr(names[key][0],names[key][1]):key.replace(/_/g," ")}
 function tableName(key){let names={subscribers:["المشتركون","Subscribers"],plans:["الباقات","Plans"],invoices:["الفواتير","Invoices"],ledger:["السجل المالي","Ledger"],sales:["المبيعات","Sales"],expenses:["المصروفات","Expenses"],team_members:["الموظفون والموزعون","Staff & resellers"],reseller_entries:["حركة الموزعين","Reseller entries"],local_accounts:["حسابات الدخول","Sign-in accounts"]};return names[key]?tr(names[key][0],names[key][1]):key.replace(/_/g," ")}
 function details(row){let fields=[];for(let key in row){if(["organization_id","branch_id","sequence","items_json","name","customer","customer_name","username"].indexOf(key)>=0)continue;let value=String(row[key]);if(key.endsWith("_minor"))value=amount(value)+" "+(row.currency||"");else if(key==="created_at")value=new Date(Number(value)).toLocaleString();else if(key==="starts_day"||key==="ends_day")value=new Date(Number(value)*86400000).toISOString().slice(0,10);fields.push(label(key)+": "+value)}return fields.join("   •   ")}
 function closeDialogs(){editor.close();wallet.close()}
 function requiredPermission(){let permissions={subscriber:"CUSTOMER",plan:"CONFIGURE",renew:"POST",charge:"POST",payment:"POST",sale:"POST",expense:"CONFIGURE",reverse:"REVERSE",void_invoice:"REVERSE",void_sale:"REVERSE",reverse_expense:"REVERSE",team:"TEAM",team_enable:"TEAM",team_disable:"TEAM",wallet:"WALLET",branch:"BRANCHES",select_branch:"BRANCHES",create_account:"AUTH",reset_password:"AUTH"};return permissions[workspace.op]||"AUTH"}
 function showEditorFor(operation){let index=workspace.operations.indexOf(operation);if(index>=0)action.currentIndex=index;workspace.showEditor()}
 function showEditor(){workspace.subscriberChoices=commerce.choices("subscribers");workspace.planChoices=commerce.choices("plans");workspace.memberChoices=commerce.allowed("TEAM")?commerce.choices("team_members"):[];workspace.requestId="";editor.open()}
 function refresh(){commerce.browse(selectedTable,search.text,page)}
 Theme {id: theme}
 FileDialog {id: csv; fileMode: FileDialog.SaveFile; defaultSuffix: "csv"; onAccepted: commerce.exportCsv(selectedFile)}
 FileDialog {id: receipt; fileMode: FileDialog.SaveFile; defaultSuffix: "pdf"; onAccepted: commerce.receipt(workspace.selectedId,workspace.selectedTable==="sales",selectedFile,paper.currentText,workspace.arabic)}
 FileDialog {id:receiptHtml;fileMode:FileDialog.SaveFile;defaultSuffix:"html";onAccepted:commerce.receiptHtmlFile(workspace.selectedId,workspace.selectedTable==="sales",selectedFile,workspace.arabic)}
 FileDialog {id: backup; fileMode: FileDialog.SaveFile; defaultSuffix: "fgbackup"; onAccepted: {commerce.exportBackup(selectedFile,backupPassword.text);backupPassword.clear()}}
 FileDialog {id: restore; fileMode: FileDialog.OpenFile; onAccepted: {commerce.restoreBackup(selectedFile,backupPassword.text);backupPassword.clear();workspace.refresh()}}
 Flow {
  Layout.fillWidth: true;spacing:8
  Text {width:workspace.width;text: workspace.tr("مساحة الأعمال المحلية","Local business workspace")+" • "+commerce.role; color: theme.mint; Layout.fillWidth: true}
  FgField {id: localUser;width:150; text: "owner"; placeholderText: workspace.tr("الحساب المحلي","Local account"); Layout.preferredWidth: 150}
  FgField {id: localPassword;width:190; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة المرور","Password"); Layout.preferredWidth: 190}
  FgButton {text: commerce.enrolled?workspace.tr("دخول","Sign in"):workspace.tr("إنشاء المالك","Create owner"); onClicked: {if(commerce.enrolled)commerce.login(localUser.text,localPassword.text);else commerce.enroll(localPassword.text);localPassword.clear();workspace.refresh()}}
  FgButton {text: workspace.tr("قفل","Lock"); onClicked: commerce.lock()}
 }
 Text {text:commerce.scopeName;ToolTip.visible:scopeHover.hovered;ToolTip.text:commerce.scope;HoverHandler {id:scopeHover} color: theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true}
 Flow {
  Layout.fillWidth: true; spacing: 8
  ComboBox {id: records; textRole:"display";valueRole:"id";model:commerce.tables.concat(["local_accounts"]).map(function(key){return {id:key,display:workspace.tableName(key)}}); currentIndex:2;onModelChanged:Qt.callLater(function(){records.currentIndex=records.model.findIndex(function(item){return item.id===workspace.selectedTable})});width:190; Layout.preferredWidth: 190; onActivated: {workspace.selectedTable=currentValue;workspace.page=0;workspace.selectedId="";workspace.refresh()}}
  FgField {id: search; width: Math.max(180,workspace.width-770); placeholderText: workspace.tr("بحث","Search"); onTextEdited: {workspace.page=0;workspace.refresh()}}
  FgButton {text: workspace.tr("تحديث","Refresh"); onClicked: workspace.refresh()}
  FgButton {text: workspace.tr("CSV للصفحة","Page CSV"); enabled: commerce.allowed("EXPORT"); onClicked: csv.open()}
  FgButton {text: workspace.tr("إيصال PDF","Receipt PDF"); enabled: workspace.selectedId.length>0&&(workspace.selectedTable==="sales"||workspace.selectedTable==="invoices"); onClicked: receipt.open()}
  FgButton {text:workspace.tr("طباعة","Print");enabled:workspace.selectedId.length>0&&(workspace.selectedTable==="sales"||workspace.selectedTable==="invoices");onClicked:commerce.printReceipt(workspace.selectedId,workspace.selectedTable==="sales",paper.currentText,workspace.arabic)}
  FgButton {text:"HTML";enabled:workspace.selectedId.length>0&&(workspace.selectedTable==="sales"||workspace.selectedTable==="invoices");onClicked:receiptHtml.open()}
  ComboBox {id: paper; model: ["A4","58","80"]}
 }
 RowLayout {
  Layout.fillWidth: true
  ComboBox {id: action; model: workspace.arabic?workspace.labels:workspace.enLabels; Layout.preferredWidth: 230}
  FgButton {text: workspace.tr("فتح العملية","Open operation");enabled:commerce.allowed(workspace.requiredPermission()); onClicked:workspace.showEditor(); accent: theme.gold}
  FgButton {text: workspace.tr("السابق","Previous"); enabled: workspace.page>0; onClicked: {workspace.page--;workspace.refresh()}}
  FgButton {text: workspace.tr("التالي","Next"); enabled: commerce.rows.length===50; onClicked: {workspace.page++;workspace.refresh()}}
  FgButton {text:workspace.tr("عرض المحفظة","View wallet");enabled:commerce.role==="RESELLER"||commerce.allowed("TEAM");onClicked:{workspace.walletData=commerce.resellerWallet(commerce.role==="RESELLER"?"":workspace.selectedId);if(workspace.walletData.name)wallet.open()}}
  Item {Layout.fillWidth: true}
 }
 Text {text: commerce.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}
 ScrollView {
  Layout.fillWidth: true; Layout.fillHeight: true; clip: true
  ListView {
   model: commerce.rows; implicitWidth: workspace.width-20; spacing: 5
   delegate: Rectangle {required property var modelData; required property int index; width: ListView.view.width; height: details.implicitHeight+rowTitle.implicitHeight+38; radius: 12; color: workspace.selectedId===(modelData.id||"")?theme.raised:theme.panel; border.color: workspace.selectedId===(modelData.id||"")?theme.blue:theme.muted
    Column {anchors.fill:parent;anchors.margins:12;spacing:8
     Text {id:rowTitle;width:parent.width;wrapMode:Text.Wrap;color:theme.gold;font.bold:true;font.pixelSize:17;text:modelData.name||modelData.customer||modelData.customer_name||modelData.username||workspace.tableName(workspace.selectedTable)}
     Text {id:details;width:parent.width;wrapMode:Text.Wrap;color:theme.silver;text:workspace.details(modelData)}
    }
    TapHandler {onTapped: workspace.selectedId=parent.modelData.id||""}
   }
  }
 }
 RowLayout {
  Layout.fillWidth: true
  FgField {id: backupPassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة مرور نسخة الأعمال (12+)","Business backup password (12+)"); Layout.fillWidth: true}
  FgButton {text: workspace.tr("نسخة احتياطية","Back up"); enabled: commerce.allowed("BRANCHES")&&backupPassword.text.length>=12; onClicked: backup.open()}
  FgButton {text: workspace.tr("استعادة","Restore"); enabled: !commerce.enrolled&&backupPassword.text.length>=12; onClicked: restore.open()}
 }
 FgDialog {
  id: editor; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: Math.min(780,parent.width-40); height: Math.min(660,parent.height-40); title: workspace.arabic?workspace.labels[action.currentIndex]:workspace.enLabels[action.currentIndex]
  contentItem: ColumnLayout {
   ScrollView {Layout.fillWidth: true; Layout.fillHeight: true; clip: true
    GridLayout {width: editor.width-60; columns: 2; columnSpacing: 12; rowSpacing: 10
     Text {text: workspace.tr("الاسم","Name"); color: theme.silver; visible: workspace.includes(["subscriber","plan","team","branch"])}
     FgField {id: name; Layout.fillWidth: true; visible: workspace.includes(["subscriber","plan","team","branch"])}
     Text {text: workspace.tr("الهاتف","Phone"); color: theme.silver; visible: workspace.includes(["subscriber","team"])}
     FgField {id: phone; Layout.fillWidth: true; visible: workspace.includes(["subscriber","team"])}
     Text {text: workspace.tr("نوع الخدمة","Service"); color: theme.silver; visible: workspace.includes(["subscriber","plan"])}
     ComboBox {id: service; model: ["HOTSPOT","PPPOE","OTHER"]; Layout.fillWidth: true; visible: workspace.includes(["subscriber","plan"])}
     Text {text: workspace.tr("حساب الراوتر","Router account"); color: theme.silver; visible: workspace.op==="subscriber"}
     FgField {id: account; Layout.fillWidth: true; visible: workspace.op==="subscriber"}
     Text {text: workspace.tr("العملة","Currency"); color: theme.silver; visible: workspace.includes(["subscriber","plan","team","expense"])}
     ComboBox {id: currency; model: ["EGP","USD","EUR","SAR","AED","TRY"]; Layout.fillWidth: true; visible: workspace.includes(["subscriber","plan","team","expense"])}
     Text {text: workspace.tr("معرف المشترك","Subscriber ID"); color: theme.silver; visible: workspace.includes(["renew","charge","payment","sale"])}
     ComboBox {id: subscriber;property string text:editText===displayText?(currentValue||""):editText;editable:true;model:workspace.subscriberChoices;textRole:"display";valueRole:"id"; Layout.fillWidth: true; visible: workspace.includes(["renew","charge","payment","sale"])}
     Text {text: workspace.tr("معرف الباقة","Plan ID"); color: theme.silver; visible: workspace.op==="renew"}
     ComboBox {id: plan;property string text:editText===displayText?(currentValue||""):editText;editable:true;model:workspace.planChoices;textRole:"display";valueRole:"id"; Layout.fillWidth: true; visible: workspace.op==="renew"}
     Text {text: workspace.tr("السعر / المبلغ","Price / amount"); color: theme.silver; visible: workspace.includes(["plan","charge","payment","expense","wallet"])}
     FgField {id: amount; Layout.fillWidth: true; placeholderText: "0.00"; visible: workspace.includes(["plan","charge","payment","expense","wallet"])}
     Text {text: workspace.tr("الأيام","Days"); color: theme.silver; visible: workspace.op==="plan"}
     FgField {id: days; text: "30"; Layout.fillWidth: true; visible: workspace.op==="plan"}
     Text {text: workspace.tr("المدفوع الآن","Paid now"); color: theme.silver; visible: workspace.includes(["renew","sale"])}
     FgField {id: paid; text: "0"; Layout.fillWidth: true; visible: workspace.includes(["renew","sale"])}
     Text {text: workspace.tr("طريقة الدفع","Payment method"); color: theme.silver; visible: workspace.includes(["renew","sale","payment"])}
     ComboBox {id: method; model: ["CASH","VODAFONE_CASH","INSTAPAY","BANK","OTHER"]; Layout.fillWidth: true; visible: workspace.includes(["renew","sale","payment"])}
     Text {text: workspace.tr("مرجع التحويل","Payment reference"); color: theme.silver; visible: workspace.includes(["renew","sale","payment"])}
     FgField {id: reference; Layout.fillWidth: true; visible: workspace.includes(["renew","sale","payment"])}
     Text {text: workspace.tr("تصنيف المصروف","Expense category"); color: theme.silver; visible: workspace.op==="expense"}
     FgField {id: category; Layout.fillWidth: true; visible: workspace.op==="expense"}
     Text {text: workspace.tr("الملاحظة / سبب العكس","Note / reversal reason"); color: theme.silver}
     FgField {id: note; Layout.fillWidth: true}
     Text {text: workspace.tr("معرف العنصر المحدد","Selected item ID"); color: theme.silver; visible: workspace.includes(["reverse","void_invoice","void_sale","reverse_expense","team_enable","team_disable","select_branch","reset_password"])}
     FgField {id: target; text: workspace.selectedId; Layout.fillWidth: true; visible: workspace.includes(["reverse","void_invoice","void_sale","reverse_expense","team_enable","team_disable","select_branch","reset_password"])}
     Text {text: workspace.tr("الدور","Role"); color: theme.silver; visible: workspace.op==="team"}
     ComboBox {id: role; model: ["ADMIN","MANAGER","TECHNICIAN","CASHIER","RESELLER","READ_ONLY"]; Layout.fillWidth: true; visible: workspace.op==="team"}
     Text {text: workspace.tr("العمولة (نقطة أساس: 100 = 1%)","Commission (basis points: 100 = 1%)"); color: theme.silver; visible: workspace.op==="team"; wrapMode: Text.Wrap}
     FgField {id: commission; text: "0"; Layout.fillWidth: true; visible: workspace.op==="team"}
     Text {text: workspace.tr("معرف الموظف / الموزع","Member / reseller ID"); color: theme.silver; visible: workspace.includes(["wallet","create_account"])}
     ComboBox {id: member;property string text:editText===displayText?(currentValue||""):editText;editable:true;model:workspace.memberChoices;textRole:"display";valueRole:"id"; Layout.fillWidth: true; visible: workspace.includes(["wallet","create_account"])}
     Text {text: workspace.tr("حركة المحفظة","Wallet entry"); color: theme.silver; visible: workspace.op==="wallet"}
     ComboBox {id: kind; model: ["DEPOSIT","WITHDRAWAL","COMMISSION","REVERSAL"]; Layout.fillWidth: true; visible: workspace.op==="wallet"}
     Text {text: workspace.tr("معرف البيع للعمولة","Commission sale ID"); color: theme.silver; visible: workspace.op==="wallet"&&kind.currentText==="COMMISSION"}
     FgField {id: sale; Layout.fillWidth: true; visible: workspace.op==="wallet"&&kind.currentText==="COMMISSION"}
     Text {text: workspace.tr("معرف الحركة المراد عكسها","Wallet reversal entry ID"); color: theme.silver; visible: workspace.op==="wallet"&&kind.currentText==="REVERSAL"}
     FgField {id: walletTarget; Layout.fillWidth: true; visible: workspace.op==="wallet"&&kind.currentText==="REVERSAL"}
     Text {text: workspace.tr("اسم الحساب","Account username"); color: theme.silver; visible: workspace.op==="create_account"}
     FgField {id: username; Layout.fillWidth: true; visible: workspace.op==="create_account"}
     Text {text: workspace.tr("كلمة المرور (12–128)","Password (12–128)"); color: theme.silver; visible: workspace.includes(["create_account","reset_password"])}
     FgField {id: password; echoMode: TextInput.Password; Layout.fillWidth: true; visible: workspace.includes(["create_account","reset_password"])}
     Text {text: workspace.tr("بنود البيع","Sale items"); color: theme.silver; visible: workspace.op==="sale"}
     ColumnLayout {visible: workspace.op==="sale"; Layout.fillWidth: true
      RowLayout {FgField {id: itemName; placeholderText: workspace.tr("الصنف","Item"); Layout.fillWidth: true} FgField {id: itemQuantity; text: "1"; Layout.preferredWidth: 70} FgField {id: itemPrice; placeholderText: "0.00"; Layout.preferredWidth: 100} FgButton {text: "+"; onClicked: lines.append({name:itemName.text,quantity:Number(itemQuantity.text),unitPrice:itemPrice.text})}}
      Repeater {model: lines; RowLayout {required property string name; required property int quantity; required property string unitPrice; required property int index; Text {text: name+" × "+quantity+" • "+unitPrice; color: theme.silver; Layout.fillWidth: true} FgButton {text: "×"; onClicked: lines.remove(index)}}}
     }
    }
   }
   Text {text: commerce.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}
   FgButton {text: workspace.tr("حفظ العملية","Save operation");enabled:commerce.allowed(workspace.requiredPermission()); accent: theme.mint; onClicked: {
    if(!workspace.requestId.length)workspace.requestId=commerce.newRequestId();var data={id:workspace.requestId,name:name.text,phone:phone.text,service:service.currentText,account:account.text,currency:currency.currentText,subscriber_id:subscriber.text,plan_id:plan.text,price:amount.text,amount:amount.text,days:Number(days.text),paid:paid.text,method:method.currentText,reference:reference.text,category:category.text,note:note.text,reason:note.text,target_id:target.text,role:role.currentText,commission_bps:Number(commission.text),member_id:member.text,kind:kind.currentText,sale_id:sale.text,reversal_of:walletTarget.text,username:username.text,password:password.text}
    var items=[];for(var i=0;i<lines.count;i++){var l=lines.get(i);items.push({name:l.name,quantity:l.quantity,unitPrice:l.unitPrice})}data.lines=items
    var saved=commerce.perform(workspace.op,data);password.clear();if(saved.length){editor.close();workspace.refresh()}
   }}
  }
 }
 FgDialog {id:wallet;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:Math.min(700,parent.width-40);height:Math.min(520,parent.height-40);title:workspace.tr("محفظة الموزع","Reseller wallet")
  contentItem:ColumnLayout {Text {Layout.fillWidth:true;color:theme.gold;text:(workspace.walletData.name||"")+" • "+workspace.tr("الرصيد بالوحدة الصغرى: ","Balance in minor units: ")+(workspace.walletData.balance_minor||"0")+" "+(workspace.walletData.currency||"")} ListView {Layout.fillWidth:true;Layout.fillHeight:true;clip:true;model:workspace.walletData.entries||[];delegate:Text {required property var modelData;width:ListView.view.width;wrapMode:Text.Wrap;color:theme.silver;text:modelData.kind+" • "+modelData.amount_minor+" • "+modelData.note}}}
 }
 ListModel {id: lines}
 Component.onCompleted: workspace.refresh()
}
