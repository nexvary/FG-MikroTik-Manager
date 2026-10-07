import QtQuick
Item {
 id:icon
 property int kind:0
 property color ink:"#159DFF"
 readonly property var names:({0:"dashboard",1:"sales",2:"subscribers",3:"diagnostics",4:"terminal",5:"developer",6:"voucher",7:"monitor",8:"cloud",9:"plans",10:"router",11:"security",12:"online",13:"server",14:"import",17:"settings",18:"renewal",19:"network"})
 width:48;height:48
 Image {anchors.fill:parent;source:Qt.resolvedUrl("icons/"+(icon.names[icon.kind]||"terminal")+".svg");fillMode:Image.PreserveAspectFit;sourceSize.width:Math.ceil(width*Screen.devicePixelRatio);sourceSize.height:Math.ceil(height*Screen.devicePixelRatio);smooth:true;mipmap:true}
}
