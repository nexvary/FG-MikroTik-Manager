import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ColumnLayout {
 id:pane
 property bool arabic:true
 function tr(ar,en){return arabic?ar:en}
 function amount(value){let s=String(value),negative=s[0]==='-';if(negative)s=s.substring(1);s=s.padStart(3,'0');return (negative?'-':'')+s.slice(0,-2)+'.'+s.slice(-2)}
 Theme {id:theme}
 FileDialog {id:importFile;fileMode:FileDialog.OpenFile;nameFilters:["CSV (*.csv)"];onAccepted:commerce.previewImport(selectedFile)}
 FileDialog {id:templateFile;fileMode:FileDialog.SaveFile;defaultSuffix:"csv";onAccepted:commerce.exportTemplate(selectedFile)}
 FileDialog {id:financialFile;fileMode:FileDialog.SaveFile;defaultSuffix:"csv";onAccepted:commerce.exportFinancial(selectedFile,fromDate.text,untilDate.text)}
 Text {Layout.fillWidth:true;text:pane.tr("استيراد المشتركين والتقارير","Subscriber import & reports");font.pixelSize:24;font.bold:true;color:theme.gold}
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:pane.tr("ملف المشتركين: الاسم، الهاتف، الخدمة، حساب الراوتر، العملة. حتى 1000 مشترك؛ راجع المعاينة ثم احفظ دفعة واحدة.","Subscriber file: name, phone, service, router account and currency. Up to 1,000 subscribers; review the preview before one atomic import.")}
 RowLayout {
  FgButton {text:pane.tr("تحميل القالب","Save template");enabled:commerce.allowed("IMPORT");onClicked:templateFile.open()}
  FgButton {text:pane.tr("معاينة CSV","Preview CSV");enabled:commerce.allowed("IMPORT");onClicked:importFile.open()}
  FgButton {text:pane.tr("تأكيد الاستيراد","Confirm import");enabled:commerce.importRows.length>0&&commerce.allowed("IMPORT");accent:theme.mint;onClicked:commerce.importSubscribers()}
 }
 ListView {Layout.fillWidth:true;Layout.preferredHeight:180;clip:true;model:commerce.importRows;spacing:6;ScrollBar.vertical:ScrollBar{}
  delegate:Text {required property var modelData;width:ListView.view.width;color:theme.silver;wrapMode:Text.Wrap;text:modelData.name+" • "+modelData.phone+" • "+modelData.service+" • "+modelData.account+" • "+modelData.currency}
 }
 RowLayout {Layout.fillWidth:true
  FgField {id:fromDate;Layout.fillWidth:true;text:new Date().toISOString().slice(0,8)+"01";placeholderText:pane.tr("من YYYY-MM-DD","From YYYY-MM-DD")}
  FgField {id:untilDate;Layout.fillWidth:true;text:new Date().toISOString().slice(0,10);placeholderText:pane.tr("حتى YYYY-MM-DD","Until YYYY-MM-DD")}
  FgButton {text:pane.tr("عرض التقرير","Show report");enabled:commerce.allowed("READ");onClicked:commerce.report(fromDate.text,untilDate.text)}
  FgButton {text:pane.tr("تصدير الفترة كاملة","Export full period");enabled:commerce.allowed("EXPORT");onClicked:financialFile.open()}
 }
 Text {Layout.fillWidth:true;color:theme.mint;wrapMode:Text.Wrap;text:commerce.status}
 ListView {Layout.fillWidth:true;Layout.fillHeight:true;model:commerce.reports;spacing:8;clip:true;ScrollBar.vertical:ScrollBar{}
  delegate:Rectangle {required property var modelData;width:ListView.view.width;height:110;radius:12;color:theme.raised;border.color:theme.muted
   ColumnLayout {anchors.fill:parent;anchors.margins:14
    Text {text:modelData.currency;color:theme.gold;font.bold:true;font.pixelSize:18}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:pane.tr("المديونيات: ","Charges: ")+pane.amount(modelData.charges_minor)+" • "+pane.tr("التحصيل: ","Receipts: ")+pane.amount(modelData.payments_minor)+" • "+pane.tr("المصروفات: ","Expenses: ")+pane.amount(modelData.expenses_minor)}
    Text {text:pane.tr("الرصيد لكل الفترات: ","All-time balance: ")+pane.amount(modelData.balance_minor);color:theme.mint}
   }
  }
 }
}
