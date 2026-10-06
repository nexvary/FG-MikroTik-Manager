import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id: workspace
 property bool arabic: true
 property string selectedId: ""
 property string selectedTable: "subscribers"
 property string requestId: ""
 property int page: 0
 property var operations: ["subscriber","plan","renew","charge","payment","sale","expense","reverse","void_invoice","void_sale","reverse_expense","team","team_enable","team_disable","wallet","branch","select_branch","create_account","reset_password"]
 property var labels: ["إضافة مشترك","إضافة باقة","تجديد اشتراك","إضافة مديونية","تحصيل دفعة","عملية بيع","مصروف","عكس قيد مالي","إلغاء فاتورة","إلغاء بيع","عكس مصروف","إضافة موظف / موزع","تفعيل موظف","تعطيل موظف","حركة محفظة موزع","إضافة فرع","اختيار فرع","إنشاء حساب موظف","تغيير كلمة مرور"]
 property string op: operations[action.currentIndex]
 function tr(ar,en){return arabic?ar:en}
 function includes(values){return values.indexOf(op)>=0}
 function refresh(){commerce.browse(selectedTable,search.text,page)}
 Theme {id: theme}
 FileDialog {id: csv; fileMode: FileDialog.SaveFile; defaultSuffix: "csv"; onAccepted: commerce.exportCsv(selectedFile)}
 FileDialog {id: receipt; fileMode: FileDialog.SaveFile; defaultSuffix: "pdf"; onAccepted: commerce.receipt(workspace.selectedId,workspace.selectedTable==="sales",selectedFile,paper.currentText,workspace.arabic)}
 FileDialog {id: backup; fileMode: FileDialog.SaveFile; defaultSuffix: "fgbackup"; onAccepted: {commerce.exportBackup(selectedFile,backupPassword.text);backupPassword.clear()}}
 FileDialog {id: restore; fileMode: FileDialog.OpenFile; onAccepted: {commerce.restoreBackup(selectedFile,backupPassword.text);backupPassword.clear();workspace.refresh()}}
 RowLayout {
  Layout.fillWidth: true
  Text {text: workspace.tr("مساحة الأعمال المحلية","Local business workspace")+" • "+commerce.role; color: theme.mint; Layout.fillWidth: true}
  FgField {id: localUser; text: "owner"; placeholderText: workspace.tr("الحساب المحلي","Local account"); Layout.preferredWidth: 150}
  FgField {id: localPassword; echoMode: TextInput.Password; placeholderText: workspace.tr("كلمة المرور","Password"); Layout.preferredWidth: 190}
  FgButton {text: commerce.enrolled?workspace.tr("دخول","Sign in"):workspace.tr("إنشاء المالك","Create owner"); onClicked: {if(commerce.enrolled)commerce.login(localUser.text,localPassword.text);else commerce.enroll(localPassword.text);localPassword.clear();workspace.refresh()}}
  FgButton {text: workspace.tr("قفل","Lock"); onClicked: commerce.lock()}
 }
 Text {text: commerce.scope; color: theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true}
 RowLayout {
  Layout.fillWidth: true
  ComboBox {id: records; model: commerce.tables; currentIndex: 2; Layout.preferredWidth: 190; onActivated: {workspace.selectedTable=currentText;workspace.page=0;workspace.selectedId="";workspace.refresh()}}
  FgField {id: search; placeholderText: workspace.tr("بحث","Search"); Layout.fillWidth: true; onTextEdited: {workspace.page=0;workspace.refresh()}}
  FgButton {text: workspace.tr("تحديث","Refresh"); onClicked: workspace.refresh()}
  FgButton {text: "CSV"; enabled: commerce.allowed("EXPORT"); onClicked: csv.open()}
  FgButton {text: workspace.tr("إيصال PDF","Receipt PDF"); enabled: workspace.selectedId.length>0&&(workspace.selectedTable==="sales"||workspace.selectedTable==="invoices"); onClicked: receipt.open()}
  ComboBox {id: paper; model: ["A4","58","80"]}
 }
 RowLayout {
  Layout.fillWidth: true
  ComboBox {id: action; model: workspace.arabic?workspace.labels:workspace.operations; Layout.preferredWidth: 230}
  FgButton {text: workspace.tr("فتح العملية","Open operation"); onClicked: editor.open(); accent: theme.gold}
  FgButton {text: workspace.tr("السابق","Previous"); enabled: workspace.page>0; onClicked: {workspace.page--;workspace.refresh()}}
  FgButton {text: workspace.tr("التالي","Next"); enabled: commerce.rows.length===50; onClicked: {workspace.page++;workspace.refresh()}}
  Item {Layout.fillWidth: true}
 }
 Text {text: commerce.status; color: theme.mint; Layout.fillWidth: true; wrapMode: Text.Wrap}
 ScrollView {
  Layout.fillWidth: true; Layout.fillHeight: true; clip: true
  ListView {
   model: commerce.rows; implicitWidth: workspace.width-20; spacing: 5
   delegate: Rectangle {required property var modelData; required property int index; width: ListView.view.width; height: details.implicitHeight+24; radius: 12; color: workspace.selectedId===(modelData.id||"")?theme.raised:theme.panel; border.color: workspace.selectedId===(modelData.id||"")?theme.blue:theme.muted
    Text {id: details; anchors.fill: parent; anchors.margins: 12; wrapMode: Text.Wrap; color: theme.white; text: {var fields=[];for(var key in modelData)fields.push(key+": "+modelData[key]);return fields.join("   •   ")}}
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
 Dialog {
  id: editor; parent: Overlay.overlay; anchors.centerIn: parent; modal: true; width: 780; height: Math.min(660,parent.height-40); title: workspace.arabic?workspace.labels[action.currentIndex]:workspace.op
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
     FgField {id: subscriber; Layout.fillWidth: true; text: workspace.selectedTable==="subscribers"?workspace.selectedId:""; visible: workspace.includes(["renew","charge","payment","sale"])}
     Text {text: workspace.tr("معرف الباقة","Plan ID"); color: theme.silver; visible: workspace.op==="renew"}
     FgField {id: plan; Layout.fillWidth: true; visible: workspace.op==="renew"}
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
     FgField {id: member; Layout.fillWidth: true; visible: workspace.includes(["wallet","create_account"])}
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
   FgButton {text: workspace.tr("حفظ العملية","Save operation"); accent: theme.mint; onClicked: {
    var data={name:name.text,phone:phone.text,service:service.currentText,account:account.text,currency:currency.currentText,subscriber_id:subscriber.text,plan_id:plan.text,price:amount.text,amount:amount.text,days:Number(days.text),paid:paid.text,method:method.currentText,reference:reference.text,category:category.text,note:note.text,reason:note.text,target_id:target.text,role:role.currentText,commission_bps:Number(commission.text),member_id:member.text,kind:kind.currentText,sale_id:sale.text,reversal_of:walletTarget.text,username:username.text,password:password.text}
    var items=[];for(var i=0;i<lines.count;i++){var l=lines.get(i);items.push({name:l.name,quantity:l.quantity,unitPrice:l.unitPrice})}data.lines=items
    var saved=commerce.perform(workspace.op,data);password.clear();if(saved.length){editor.close();workspace.refresh()}
   }}
  }
 }
 ListModel {id: lines}
 Component.onCompleted: workspace.refresh()
}
