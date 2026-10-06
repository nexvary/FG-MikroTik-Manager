import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
Item {
 id: pane
 property bool arabic: true
 function tr(ar,en){return arabic?ar:en}
 Theme {id: theme}
 ColumnLayout {
  anchors.fill: parent;spacing: 12
  Text {Layout.fillWidth:true;color:theme.silver;wrapMode:Text.Wrap;text:pane.tr("تابع أربعة راوترات، بحد اتصالين متزامنين. التنبيهات: انقطاع وعودة وارتفاع المعالج. كلمات المرور لهذه الجلسة فقط.","Monitor four routers with two concurrent connections. Alerts cover outages, recovery and high CPU. Passwords are kept for this session only.")}
  RowLayout {
   Layout.fillWidth:true
   ComboBox {id: profile;Layout.fillWidth:true;model:backend.profiles;textRole:"name"}
   FgField {id: secret;Layout.preferredWidth:200;echoMode:TextInput.Password;placeholderText:pane.tr("كلمة مرور الراوتر","Router password")}
   FgButton {text:pane.tr("بدء المراقبة","Start monitoring");enabled:commerce.allowed("ROUTER")&&backend.profiles.length>0;onClicked:{monitor.start(backend.profiles[profile.currentIndex],secret.text);secret.clear()}}
   FgButton {text:pane.tr("إيقاف الكل","Stop all");onClicked:monitor.stopAll()}
  }
  CheckBox {text:pane.tr("استمرار المراقبة في الخلفية عند إغلاق النافذة","Keep monitoring in the background when the window closes");enabled:monitor.trayAvailable;checked:monitor.background;onToggled:monitor.background=checked}
  Text {Layout.fillWidth:true;color:theme.mint;wrapMode:Text.Wrap;text:monitor.status}
  ListView {
   Layout.fillWidth:true;Layout.preferredHeight:260;clip:true;spacing:10;model:monitor.routers;ScrollBar.vertical:ScrollBar{}
   delegate:Rectangle {
    required property var modelData
    width:ListView.view.width;height:112;radius:12;color:theme.raised;border.color:modelData.stale||modelData.incident?theme.gold:theme.mint
    ColumnLayout {anchors.fill:parent;anchors.margins:14
     RowLayout {Layout.fillWidth:true;Text {Layout.fillWidth:true;color:theme.white;font.bold:true;text:modelData.name+" • "+modelData.host} FgButton {text:pane.tr("إيقاف","Stop");onClicked:monitor.stop(modelData.id)}}
     Text {Layout.fillWidth:true;color:theme.silver;wrapMode:Text.Wrap;text:modelData.checking?pane.tr("جارٍ الفحص…","Checking…"):modelData.incident==="UNREACHABLE"?pane.tr("انقطع الاتصال","Unreachable"):modelData.incident==="HIGH_CPU"?pane.tr("المعالج مرتفع","High CPU"):modelData.stale?pane.tr("البيانات قديمة أو لم تصل بعد","Stale or awaiting first sample"):pane.tr("متصل • البيانات حديثة","Online • current data")}
     Text {Layout.fillWidth:true;color:theme.muted;elide:Text.ElideRight;text:{let r=modelData.snapshot.resource||{};return "CPU: "+(r["cpu-load"]||"—")+"% • "+pane.tr("الذاكرة الحرة: ","Free memory: ")+(r["free-memory"]?Math.round(Number(r["free-memory"])/1048576)+" MB":"—")+" • "+pane.tr("مدة التشغيل: ","Uptime: ")+(r.uptime||"—")+" • "+pane.tr("الواجهات: ","Interfaces: ")+(modelData.snapshot.interfaces||[]).length}}
    }
   }
  }
  Text {text:pane.tr("سجل التنبيهات","Alert history");color:theme.gold;font.bold:true}
  ListView {Layout.fillWidth:true;Layout.fillHeight:true;clip:true;spacing:6;model:monitor.events;ScrollBar.vertical:ScrollBar{}
   delegate:Text {required property var modelData;width:ListView.view.width;color:theme.silver;wrapMode:Text.Wrap;text:modelData.time+" • "+modelData.router+" • "+(modelData.event==="UNREACHABLE"?pane.tr("انقطاع","Outage"):modelData.event==="HIGH_CPU"?pane.tr("ارتفاع المعالج","High CPU"):pane.tr("عودة إلى الحالة الطبيعية","Recovered"))}
  }
 }
}
