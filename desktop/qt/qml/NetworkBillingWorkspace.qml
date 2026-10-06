import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
ColumnLayout {
 id:pane
 property bool arabic:true
 property var selection:[]
 property var invoiceChoices:[]
 function tr(ar,en){return arabic?ar:en}
 Theme {id:theme}
 Text {Layout.fillWidth:true;text:pane.tr("ربط المشتركين والتجديد بالراوتر","Subscriber binding & router renewal");color:theme.gold;font.pixelSize:24;font.bold:true}
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:pane.tr("الربط يحفظ هوية الجهاز والحساب دون كلمات المرور. التجديد المالي يُحفظ أولًا؛ تطبيقه على الشبكة خطوة مستقلة تؤكدها هنا.","Bindings store device and account identity without passwords. Financial renewal is saved first; applying it to the network is a separate confirmed step.")}
 RowLayout {Layout.fillWidth:true
  FgButton {text:pane.tr("قراءة حسابات الراوتر","Load router accounts");enabled:!networkBilling.busy&&commerce.allowed("IMPORT")&&commerce.allowed("ROUTER");onClicked:{pane.selection=[];networkBilling.loadCatalog()}}
  ComboBox {id:currency;model:["EGP","USD","EUR","SAR","AED","TRY"]}
  FgButton {text:pane.tr("ربط المحدد بالمشتركين","Bind selected accounts");enabled:!networkBilling.busy&&pane.selection.length>0;onClicked:networkBilling.importSelected(pane.selection,currency.currentText)}
  Text {text:String(pane.selection.length)+pane.tr(" محدد"," selected");color:theme.mint}
 }
 ListView {Layout.fillWidth:true;Layout.preferredHeight:210;clip:true;model:networkBilling.accounts;ScrollBar.vertical:ScrollBar{}
  delegate:CheckBox {required property var modelData;required property int index;width:ListView.view.width;text:modelData.name+" • "+modelData.service+" • "+modelData.profile;checked:pane.selection.indexOf(String(index))>=0;onClicked:{let next=pane.selection.slice(),i=next.indexOf(String(index));if(checked&&i<0)next.push(String(index));else if(!checked&&i>=0)next.splice(i,1);pane.selection=next}}
 }
 RowLayout {Layout.fillWidth:true
  ComboBox {id:invoice;Layout.fillWidth:true;editable:true;model:pane.invoiceChoices;textRole:"display";valueRole:"id";property string text:editText===displayText?(currentValue||""):editText;onDownChanged:if(down)pane.invoiceChoices=commerce.choices("invoices")}
  FgField {id:profile;Layout.preferredWidth:180;placeholderText:pane.tr("اسم الباقة على الراوتر","Router profile name")}
  FgField {id:allowance;Layout.preferredWidth:140;text:"0";placeholderText:pane.tr("البيانات MB؛ 0 بلا حد","MB; 0 unlimited")}
  FgButton {text:pane.tr("معاينة التجديد","Preview renewal");enabled:!networkBilling.busy&&commerce.allowed("ROUTER");onClicked:networkBilling.prepare(invoice.text,profile.text,allowance.text)}
 }
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.mint;text:networkBilling.status}
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:networkBilling.target.account?pane.tr("الحساب: ","Account: ")+networkBilling.target.account+" • "+networkBilling.target.service+" • "+pane.tr("الباقة: ","Profile: ")+networkBilling.target.profile+" • "+pane.tr("حالة التطبيق: ","Applied state: ")+(networkBilling.target.state||"PENDING"):""}
 RowLayout {
  FgButton {text:pane.tr("تطبيق التجديد على الشبكة","Apply network renewal");enabled:!networkBilling.busy&&networkBilling.target.account!==undefined&&commerce.allowed("ROUTER");accent:theme.mint;onClicked:{confirm.suspending=false;confirm.open()}}
  FgButton {text:pane.tr("تعليق الحساب والتحقق","Suspend & verify account");enabled:!networkBilling.busy&&networkBilling.target.account!==undefined&&commerce.allowed("ROUTER");accent:theme.error;onClicked:{confirm.suspending=true;confirm.open()}}
 }
 Item {Layout.fillHeight:true}
 FgDialog {id:confirm;property bool suspending:false;parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:600;title:pane.tr("تأكيد تغيير الحساب","Confirm account change")
  contentItem:ColumnLayout {Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:confirm.suspending?pane.tr("سيُعطّل الحساب وتُفصل جلساته. لا تُلغِ الفاتورة قبل ظهور SUSPENDED.","This disables the account and disconnects sessions. Do not void the invoice until SUSPENDED is verified."):pane.tr("سيُثبّت قيد الانتهاء ويُتحقق منه قبل تفعيل الحساب. أي نتيجة غير مؤكدة تظل REVIEW.","Expiry protection is installed and verified before enabling the account. Uncertain outcomes remain REVIEW.")} FgButton {text:pane.tr("تأكيد التنفيذ","Confirm execution");onClicked:{networkBilling.apply(confirm.suspending);confirm.close()}}}
 }
}
