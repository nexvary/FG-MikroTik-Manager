import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Dialogs
ScrollView {
 id:pane;property bool arabic:true;property bool reports:false;property string format:"CSV";property var selected:({});property bool showUnknown:false
 function tr(ar,en){return arabic?ar:en}
 clip:true;contentWidth:availableWidth
 Theme{id:theme}
 FileDialog{id:csv;fileMode:FileDialog.SaveFile;nameFilters:[pane.format+" (*."+pane.format.toLowerCase()+")"];defaultSuffix:pane.format.toLowerCase();onAccepted:accessPoints.exportReport(selectedFile,pane.format)}
 FgDialog{id:details;title:pane.tr("تفاصيل نقطة الوصول","Access point details");width:Math.min(560,pane.width);modal:true
  ColumnLayout{width:parent.width;spacing:12
   Label{text:pane.selected.name||"";color:theme.gold;wrapMode:Text.Wrap;Layout.fillWidth:true;font.pixelSize:20}
   Label{text:(pane.selected.mac||"")+" • "+(pane.selected.ip||"");color:theme.silver;wrapMode:Text.Wrap;Layout.fillWidth:true;LayoutMirroring.enabled:false}
   Label{text:pane.tr("المصادر: ","Sources: ")+(pane.selected.sources||"");color:theme.muted;wrapMode:Text.Wrap;Layout.fillWidth:true}
   FgField{id:shop;Layout.fillWidth:true;placeholderText:pane.tr("اسم المحل","Shop name");maximumLength:120}
   CheckBox{id:verified;text:pane.tr("تحققت بنفسي أنه Access Point","I verified this device is an AP")}
   FgCombo{id:mode;Layout.fillWidth:true;model:["Unknown","Bridge","NAT"]}
   Label{text:pane.tr("تأكيدك محلي ولا يغيّر إعدادات الجهاز. NAT قد يخفي العملاء خلفه.","Your confirmation is local and does not change the device. NAT may hide downstream clients.");color:theme.gold;wrapMode:Text.Wrap;Layout.fillWidth:true}
   RowLayout{FgButton{text:pane.tr("حفظ","Save");onClicked:{accessPoints.saveMapping(pane.selected.mac,shop.text,verified.checked,mode.currentText);details.close()}}FgButton{text:pane.tr("رجوع","Back");onClicked:details.close()}}
  }
 }
 ColumnLayout{width:pane.availableWidth;spacing:14
  Rectangle{Layout.fillWidth:true;implicitHeight:100;radius:16;color:theme.panel;border.color:theme.blue
   RowLayout{anchors.fill:parent;anchors.margins:16;spacing:16
    Image{source:"qrc:/qml/icons/network.svg";Layout.preferredWidth:64;Layout.preferredHeight:64}
    ColumnLayout{Layout.fillWidth:true
     Label{text:pane.tr("نقاط وصول مؤكدة: ","Confirmed access points: ")+accessPoints.confirmedCount;color:theme.gold;font.pixelSize:24;font.bold:true;wrapMode:Text.Wrap;Layout.fillWidth:true}
     Label{text:pane.tr("اكتشاف بالأدلة • تسمية المحلات • رصد محلي","Evidence-based discovery • shop names • local observations");color:theme.silver;wrapMode:Text.Wrap;Layout.fillWidth:true}
    }
   }
  }
  RowLayout{Layout.fillWidth:true
   FgButton{text:pane.tr("تحديث","Refresh");enabled:!backend.busy&&!accessPoints.busy;onClicked:accessPoints.refresh()}
   FgButton{text:pane.reports?pane.tr("الأجهزة","Devices"):pane.tr("التقارير والمقارنة","Reports & comparison");onClicked:pane.reports=!pane.reports}
   Item{Layout.fillWidth:true}
  }
  ProgressBar{visible:accessPoints.busy;indeterminate:true;Layout.fillWidth:true}
  Label{text:accessPoints.status;color:theme.gold;wrapMode:Text.Wrap;Layout.fillWidth:true}
  Label{text:pane.tr("المنفذ هو مسار المرور وقد يكون خلفه سويتش. الربط بالعملاء استنتاجي. التحديث اليدوي محدود بمرة كل 30 ثانية.","The port is an upstream path and may contain a switch. Client association is inferred. Refresh is limited to every 30 seconds.");color:theme.muted;wrapMode:Text.Wrap;Layout.fillWidth:true}
  CheckBox{visible:!pane.reports;text:pane.tr("عرض الأجهزة غير المصنفة أيضًا","Also show unclassified devices");checked:pane.showUnknown;onToggled:pane.showUnknown=checked}
  ColumnLayout{visible:!pane.reports;Layout.fillWidth:true;spacing:10
   Repeater{model:accessPoints.devices
    Rectangle{required property var modelData;Layout.fillWidth:true;visible:pane.showUnknown||modelData.classification==="Confirmed AP"||modelData.classification==="Likely AP";implicitHeight:deviceColumn.implicitHeight+32;radius:14;color:theme.panel;border.color:theme.muted
     ColumnLayout{id:deviceColumn;anchors.left:parent.left;anchors.right:parent.right;anchors.top:parent.top;anchors.margins:16;spacing:8
      RowLayout{Layout.fillWidth:true
       Label{text:modelData.shop||modelData.name;color:theme.mint;font.pixelSize:20;font.bold:true;Layout.fillWidth:true;wrapMode:Text.Wrap}
       FgButton{text:pane.tr("التفاصيل والتسمية","Details & name");onClicked:{pane.selected=modelData;shop.text=modelData.shop||"";verified.checked=modelData.reason==="User verified";mode.currentIndex=Math.max(0,mode.model.indexOf(modelData.mode));details.open()}}
      }
      Label{text:modelData.classification+" • "+modelData.reason;color:theme.gold;Layout.fillWidth:true;wrapMode:Text.Wrap}
      Label{text:(modelData.ip||"N/A")+"   |   "+modelData.mac+"   |   "+(modelData.port||"Unknown port");color:theme.silver;Layout.fillWidth:true;wrapMode:Text.Wrap;LayoutMirroring.enabled:false}
      Label{text:pane.tr("جلسات نشطة منسوبة استنتاجيًا: ","Inferred active sessions: ")+modelData.active;color:theme.silver}
      Label{visible:modelData.mode==="NAT";text:pane.tr("هذا الجهاز يعمل بطريقة قد تقلل دقة إحصائيات العملاء.","This device operates in a mode that can reduce client-statistics accuracy.");color:theme.gold;wrapMode:Text.Wrap;Layout.fillWidth:true}
     }
    }
   }
   Label{visible:accessPoints.devices.length===0;text:pane.tr("اتصل بالراوتر ثم اضغط تحديث. عدم ظهور دليل لا يعني عدم وجود نقاط وصول.","Connect to the router and refresh. Missing evidence does not mean there are no access points.");color:theme.silver;wrapMode:Text.Wrap;Layout.fillWidth:true}
  }
  ColumnLayout{visible:pane.reports;Layout.fillWidth:true;spacing:12
   Label{text:pane.tr("تقارير الرصد المحلي — آخر 90 يومًا","Local observation reports — last 90 days");color:theme.silver;font.pixelSize:20}
   RowLayout{Layout.fillWidth:true
    FgField{id:from;Layout.fillWidth:true;text:Qt.formatDate(new Date(),"yyyy-MM-dd");placeholderText:"From YYYY-MM-DD"}
    FgField{id:through;Layout.fillWidth:true;text:Qt.formatDate(new Date(),"yyyy-MM-dd");placeholderText:"Through YYYY-MM-DD"}
    FgButton{text:pane.tr("عرض المقارنة","Compare");onClicked:accessPoints.report(from.text,through.text)}
    FgCombo{model:["CSV","PDF","XLSX"];onCurrentTextChanged:pane.format=currentText}
    FgButton{text:pane.tr("تصدير التقرير","Export Report");onClicked:csv.open()}
   }
   Repeater{model:accessPoints.summary
    Rectangle{required property var modelData;Layout.fillWidth:true;implicitHeight:reportColumn.implicitHeight+32;radius:14;color:theme.panel;border.color:theme.blue
     ColumnLayout{id:reportColumn;anchors.left:parent.left;anchors.right:parent.right;anchors.top:parent.top;anchors.margins:16;spacing:8
      Label{text:modelData.shop||modelData.name;color:theme.mint;font.pixelSize:20;Layout.fillWidth:true;wrapMode:Text.Wrap}
      Label{text:pane.tr("عملاء مرصودون: ","Observed clients: ")+modelData.observedClients+pane.tr(" • حسابات: "," • Accounts: ")+modelData.observedAccounts;color:theme.silver;Layout.fillWidth:true;wrapMode:Text.Wrap}
      Label{text:pane.tr("الكروت والمبيعات والترافيك للفترة: غير متاح","Period cards, sales and traffic: N/A");color:theme.gold;Layout.fillWidth:true;wrapMode:Text.Wrap}
     }
    }
   }
   Label{text:pane.tr("هذه عينات وقت تشغيل الصفحة وليست سجل جلسات كاملًا. عدد الحسابات لا يعني عدد الكروت المباعة. لا تُنسب مبيعات إلى نقطة وصول بلا دليل.","These are screen-time samples, not a complete session ledger. Accounts are not sold vouchers. Sales are not attributed without evidence.");color:theme.muted;wrapMode:Text.Wrap;Layout.fillWidth:true}
  }
 }
}
