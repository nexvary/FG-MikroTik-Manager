import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
FgDialog {
 id: library
 property bool arabic:true
 property var chosen:null
 property var fields:[]
 property var values:({})
 property string selectedCategory:"ALL"
 signal selected(string command)
 function tr(ar,en){return arabic?ar:en}
 function choose(item){chosen=item;let keys=[],m,re=/\{\{([a-z]+)\}\}/g;while((m=re.exec(item.command))!==null)if(keys.indexOf(m[1])<0)keys.push(m[1]);fields=keys;values=({})}
 function command(){return chosen?backend.fillCommand(chosen.command,values):""}
 function categories(){
  let list=["ALL"]
  for(let i=0;i<backend.commandLibrary.length;i++){let key=backend.commandLibrary[i].categoryEn;if(list.indexOf(key)<0)list.push(key)}
  return list
 }
 function categoryLabel(key){
  if(key==="ALL")return tr("الكل","All")
  for(let i=0;i<backend.commandLibrary.length;i++){let item=backend.commandLibrary[i];if(item.categoryEn===key)return arabic?item.categoryAr:item.categoryEn}
  return key
 }
 function filtered(){
  let q=search.text.toLowerCase().trim()
  return backend.commandLibrary.filter(function(item){
   let categoryOk=library.selectedCategory==="ALL"||item.categoryEn===library.selectedCategory
   let hay=(item.ar+" "+item.en+" "+item.categoryAr+" "+item.categoryEn+" "+item.command+" "+item.descriptionAr+" "+item.descriptionEn).toLowerCase()
   return categoryOk&&(!q.length||hay.includes(q))
  })
 }
 function riskColor(risk){return risk==="SAFE"?theme.mint:risk==="DANGEROUS"?theme.error:theme.gold}
 title:tr("مكتبة أوامر MikroTik","MikroTik command library")
 modal:true;width:Math.min(980,parent.width-32);height:Math.min(760,parent.height-32);anchors.centerIn:parent
 Theme {id:theme}
 contentItem:ColumnLayout {
  spacing:10
  RowLayout {
   Layout.fillWidth:true
   FgField {id:search;Layout.fillWidth:true;placeholderText:library.tr("ابحث بالاسم أو القسم أو الأمر","Search by name, category or command")}
   Text {text:library.filtered().length+" / "+backend.commandLibrary.length;color:theme.muted;font.bold:true}
  }
  ScrollView {Layout.fillWidth:true;Layout.preferredHeight:56;contentHeight:48;clip:true
   Row {spacing:8
    Repeater {model:library.categories();delegate:FgButton {
     required property string modelData
     text:library.categoryLabel(modelData);filled:library.selectedCategory===modelData
     accent:library.selectedCategory===modelData?theme.blue:theme.muted
     onClicked:library.selectedCategory=modelData
    }}
   }
  }
  RowLayout {
   Layout.fillWidth:true;Layout.fillHeight:true;spacing:12
   Rectangle {
    Layout.fillWidth:true;Layout.fillHeight:true;Layout.minimumWidth:420;color:theme.navy;border.color:theme.muted;radius:12
    ListView {
     anchors.fill:parent;anchors.margins:8;clip:true;spacing:6;model:library.filtered();ScrollBar.vertical:ScrollBar{}
     delegate:Rectangle {
      required property var modelData
      width:ListView.view.width;height:82
      color:library.chosen&&library.chosen.command===modelData.command?theme.raised:"#091A27"
      border.color:library.chosen&&library.chosen.command===modelData.command?library.riskColor(modelData.risk):"#284457";radius:10
      ColumnLayout {anchors.fill:parent;anchors.margins:10;spacing:3
       RowLayout {Layout.fillWidth:true
        Text {Layout.fillWidth:true;text:library.arabic?modelData.ar:modelData.en;color:theme.white;font.bold:true;elide:Text.ElideRight}
        Rectangle {implicitWidth:riskText.implicitWidth+14;implicitHeight:24;radius:12;color:library.riskColor(modelData.risk);opacity:.9
         Text {id:riskText;anchors.centerIn:parent;text:modelData.risk;color:"#06131C";font.pixelSize:11;font.bold:true}
        }
       }
       Text {Layout.fillWidth:true;text:library.arabic?modelData.categoryAr:modelData.categoryEn;color:theme.blue;font.pixelSize:12;elide:Text.ElideRight}
       Text {Layout.fillWidth:true;text:library.arabic?modelData.descriptionAr:modelData.descriptionEn;color:theme.silver;font.pixelSize:12;elide:Text.ElideRight}
      }
      TapHandler {onTapped:library.choose(modelData)}
     }
    }
   }
   Rectangle {
    Layout.preferredWidth:350;Layout.fillHeight:true;color:theme.panel;border.color:theme.muted;radius:12
    ColumnLayout {anchors.fill:parent;anchors.margins:14;spacing:10
     Text {Layout.fillWidth:true;text:library.chosen?(library.arabic?library.chosen.ar:library.chosen.en):library.tr("اختر أمرًا من القائمة","Select a command");color:theme.gold;font.pixelSize:19;font.bold:true;wrapMode:Text.Wrap}
     Text {Layout.fillWidth:true;text:library.chosen?(library.arabic?library.chosen.descriptionAr:library.chosen.descriptionEn):library.tr("يمكنك البحث أو اختيار قسم ثم تعبئة المتغيرات.","Search or select a category, then fill any required values.");color:theme.silver;wrapMode:Text.Wrap}
     Rectangle {Layout.fillWidth:true;implicitHeight:Math.max(70,commandPreview.implicitHeight+20);color:theme.black;border.color:theme.blue;radius:10
      Text {id:commandPreview;anchors.fill:parent;anchors.margins:10;text:library.chosen?library.chosen.command:"";color:theme.mint;font.family:"Consolas";wrapMode:Text.Wrap;verticalAlignment:Text.AlignVCenter}
     }
     ScrollView {Layout.fillWidth:true;Layout.fillHeight:true;clip:true
      ColumnLayout {width:parent.width;spacing:8
       Repeater {model:library.fields;delegate:ColumnLayout {
        required property string modelData
        width:parent.width
        Text {text:modelData;color:theme.silver;font.bold:true}
        FgField {width:parent.width;placeholderText:library.tr("قيمة "+modelData,"Value for "+modelData);LayoutMirroring.enabled:false;onTextEdited:{let next=Object.assign({},library.values);next[modelData]=text;library.values=next}}
       }}
      }
     }
     Text {Layout.fillWidth:true;visible:library.chosen!==null;color:library.chosen?library.riskColor(library.chosen.risk):theme.muted;text:library.chosen&&library.chosen.risk==="SAFE"?library.tr("قراءة فقط — لا يغيّر الإعدادات","Read only — no settings are changed"):library.tr("سيتم إرسال الأمر إلى الترمنال للمراجعة قبل التنفيذ.","The command is sent to the terminal for review before execution.");wrapMode:Text.Wrap;font.bold:true}
     Flow {Layout.fillWidth:true;spacing:8
      FgButton {text:library.tr("استخدم في الترمنال","Use in terminal");enabled:library.chosen!==null;filled:true;accent:theme.blue;onClicked:{let c=library.command();if(c.length){library.selected(c);library.close()}}}
      FgButton {text:library.tr("نسخ","Copy");enabled:library.chosen!==null;onClicked:{let c=library.command();if(c.length)backend.copyText(c)}}
      FgButton {text:library.tr("إغلاق","Close");onClicked:library.close()}
     }
    }
   }
  }
 }
}
