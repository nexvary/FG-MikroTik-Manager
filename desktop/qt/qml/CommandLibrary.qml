import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
Dialog {
 id: library
 property bool arabic:true
 property var chosen:null
 property var fields:[]
 property var values:({})
 signal selected(string command)
 function tr(ar,en){return arabic?ar:en}
 function choose(item){chosen=item;let keys=[],m,re=/\{\{([a-z]+)\}\}/g;while((m=re.exec(item.command))!==null)if(keys.indexOf(m[1])<0)keys.push(m[1]);fields=keys;values=({})}
 function command(){return chosen?backend.fillCommand(chosen.command,values):""}
 title:tr("مكتبة أوامر MikroTik","MikroTik command library")
 modal:true;width:Math.min(840,parent.width-40);height:Math.min(680,parent.height-40);anchors.centerIn:parent
 Theme {id:theme}
 contentItem:ColumnLayout {
  FgField {id:search;Layout.fillWidth:true;placeholderText:library.tr("ابحث بالاسم أو القسم أو الأمر","Search name, category or command")}
  ListView {Layout.fillWidth:true;Layout.fillHeight:true;clip:true;spacing:6;model:backend.commandLibrary.filter(function(item){return JSON.stringify(item).toLowerCase().includes(search.text.toLowerCase())});ScrollBar.vertical:ScrollBar{}
   delegate:Rectangle {required property var modelData;width:ListView.view.width;height:76;color:library.chosen===modelData?theme.raised:theme.navy;border.color:theme.muted;radius:10
    ColumnLayout {anchors.fill:parent;anchors.margins:10
     Text {Layout.fillWidth:true;text:(library.arabic?modelData.categoryAr+" • "+modelData.ar:modelData.categoryEn+" • "+modelData.en)+" • "+modelData.risk;color:modelData.risk==="SAFE"?theme.mint:theme.gold;font.bold:true;elide:Text.ElideRight}
     Text {Layout.fillWidth:true;text:library.arabic?modelData.descriptionAr:modelData.descriptionEn;color:theme.silver;elide:Text.ElideRight}
    }
    TapHandler {onTapped:library.choose(modelData)}
   }
  }
  Text {Layout.fillWidth:true;color:theme.blue;wrapMode:Text.Wrap;text:library.chosen?library.chosen.command:""}
  Flow {Layout.fillWidth:true;spacing:8
   Repeater {model:library.fields;delegate:FgField {required property string modelData;width:220;placeholderText:modelData;onTextEdited:{let next=Object.assign({},library.values);next[modelData]=text;library.values=next}}}
  }
  RowLayout {
   FgButton {text:library.tr("إرسال إلى الترمنال","Use in terminal");enabled:library.chosen!==null;onClicked:{let c=library.command();if(c.length){library.selected(c);library.close()}}}
   FgButton {text:library.tr("نسخ للمشاركة","Copy to share");enabled:library.chosen!==null;onClicked:{let c=library.command();if(c.length)backend.copyText(c)}}
   FgButton {text:library.tr("إغلاق","Close");onClicked:library.close()}
  }
 }
}
