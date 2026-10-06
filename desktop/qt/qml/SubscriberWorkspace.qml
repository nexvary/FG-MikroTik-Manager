import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
ColumnLayout {
 id:pane
 property bool arabic:true
 property bool onlineOnly:false
 property string userId:""
 property var selectedUser:({})
 function tr(ar,en){return arabic?ar:en}
 function metric(value,unit){return value===null||value===undefined?tr("غير محدد / غير متاح","Unlimited / unavailable"):String(value)+" "+unit}
 function stateLabel(value){return tr(({ACTIVE:"متصل",OFFLINE:"غير متصل",EXPIRED:"منتهي",DISABLED:"معطل",UNKNOWN:"الجلسات غير متاحة"})[value],({ACTIVE:"Active",OFFLINE:"Offline",EXPIRED:"Expired",DISABLED:"Disabled",UNKNOWN:"Sessions unavailable"})[value])}
 Theme {id:theme}
 Connections {target:backend;function onChanged(){if(!backend.routerConnected){pane.userId="";pane.selectedUser=({})}}}
 Flow {Layout.fillWidth:true;spacing:8
  FgButton {text:pane.tr("تحديث المشتركين والمتصلين","Refresh subscribers & sessions");enabled:backend.routerConnected&&!backend.busy;onClicked:{pane.userId="";routerTools.refreshSubscribers()}}
  CheckBox {text:pane.tr("المتصلون الآن فقط","Online now only");checked:pane.onlineOnly;onToggled:pane.onlineOnly=checked}
  FgField {id:search;width:240;placeholderText:pane.tr("بحث بالكود أو الباقة","Search code or profile")}
 }
 Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:!backend.routerConnected?pane.tr("اتصل بالراوتر أولًا، ثم اضغط تحديث.","Connect a router, then refresh."):routerTools.sessionsKnown?pane.tr("الجلسات المتصلة: ","Connected sessions: ")+routerTools.activeSessions.length:pane.tr("لم تتوفر قراءة الجلسات؛ لا يمكن تأكيد المتصلين أو الاستهلاك الكامل.","Sessions are unavailable; online status and full usage cannot be confirmed.")}
 Flow {Layout.fillWidth:true;spacing:8
  FgButton {text:pane.tr("تفعيل","Enable");enabled:pane.userId.length>0&&!backend.busy;onClicked:routerTools.subscriber("enable",pane.userId,{})}
  FgButton {text:pane.tr("تعطيل","Disable");enabled:pane.userId.length>0&&!backend.busy;onClicked:confirm.openFor("disable")}
  FgButton {text:pane.tr("فصل الجلسة","Disconnect");enabled:pane.userId.length>0&&!backend.busy;onClicked:confirm.openFor("disconnect")}
  FgButton {text:pane.tr("حذف","Delete");accent:theme.error;enabled:pane.userId.length>0&&!backend.busy;onClicked:confirm.openFor("delete")}
  FgField {id:newProfile;width:150;placeholderText:pane.tr("الباقة الجديدة","New profile")}
  FgButton {text:pane.tr("تغيير الباقة","Change profile");enabled:pane.userId.length>0&&newProfile.text.length>0&&!backend.busy;onClicked:confirm.openFor("update")}
  FgField {id:seconds;width:150;placeholderText:pane.tr("زيادة الوقت بالثواني","Extra seconds")}
  FgButton {text:pane.tr("زيادة الوقت","Add time");enabled:pane.userId.length>0&&Number(seconds.text)>=60&&!backend.busy;onClicked:confirm.openFor("add_time")}
 }
 Text {visible:pane.userId.length>0;Layout.fillWidth:true;color:theme.gold;text:pane.tr("المحدد: ","Selected: ")+(pane.selectedUser.name||pane.userId)}
 ListView {id:users;Layout.fillWidth:true;Layout.fillHeight:true;clip:true;spacing:8;ScrollBar.vertical:ScrollBar{}
  model:routerTools.users.filter(function(row){return (!pane.onlineOnly||Number(row.sessionCount)>0)&&(!search.text.length||((row.name||"")+" "+(row.profile||"")).toLowerCase().includes(search.text.toLowerCase()))})
  delegate:Rectangle {required property var modelData;width:users.width;height:details.implicitHeight+28;radius:12;color:pane.userId===modelData[".id"]?theme.raised:theme.panel;border.color:pane.userId===modelData[".id"]?theme.blue:theme.muted
   ColumnLayout {id:details;anchors.left:parent.left;anchors.right:parent.right;anchors.top:parent.top;anchors.margins:14;spacing:6
    RowLayout {Layout.fillWidth:true;Text {text:modelData.name;color:theme.white;font.bold:true;font.pixelSize:18;Layout.fillWidth:true} Text {text:pane.stateLabel(modelData.usageState);color:modelData.usageState==="ACTIVE"?theme.mint:theme.gold}}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:pane.tr("الباقة: ","Profile: ")+(modelData.profile||"")+" • "+pane.tr("مدة الاستخدام: ","Allowance: ")+(modelData["limit-uptime"]||pane.tr("غير محددة","Unlimited"))}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:pane.tr("المستخدم: ","Used: ")+pane.metric(modelData.usedSeconds,"s")+" • "+pane.tr("المتبقي: ","Remaining: ")+pane.metric(modelData.remainingSeconds,"s")}
    Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:"↑ "+pane.metric(modelData.uploadBytes,"B")+"   ↓ "+pane.metric(modelData.downloadBytes,"B")+" • "+pane.tr("البيانات المتبقية: ","Data remaining: ")+pane.metric(modelData.remainingBytes,"B")}
   }
   TapHandler {onTapped:{pane.userId=parent.modelData[".id"];pane.selectedUser=parent.modelData}}
  }
 }
 FgDialog {id:confirm;property string action:"";property string target:"";property var fields:({});function openFor(value){action=value;target=pane.userId;fields=value==="update"?{attributes:{profile:newProfile.text}}:value==="add_time"?{seconds:seconds.text}:({});open()}parent:Overlay.overlay;anchors.centerIn:parent;modal:true;width:Math.min(540,parent.width-40);title:pane.tr("تأكيد إجراء المشترك","Confirm subscriber action")
  contentItem:ColumnLayout {Text {Layout.fillWidth:true;wrapMode:Text.Wrap;color:theme.silver;text:(pane.selectedUser.name||confirm.target)+" • "+confirm.action+(confirm.action==="update"?" • "+confirm.fields.attributes.profile:confirm.action==="add_time"?" • "+confirm.fields.seconds+" s":"")} FgButton {text:pane.tr("تأكيد","Confirm");enabled:!backend.busy;onClicked:{routerTools.subscriber(confirm.action,confirm.target,confirm.fields);confirm.close()}} FgButton {text:pane.tr("رجوع","Back");onClicked:confirm.close()}}
 }
}
