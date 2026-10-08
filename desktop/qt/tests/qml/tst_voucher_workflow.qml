import QtQuick
import QtQuick.Controls.Basic
import QtTest
import "../../qml"
Item {
 width:1100;height:700
 QtObject {id:store
  property var cards:[{username:"41822",password:"41822",profile:"default",provisionState:"DRAFT"}]
  property var archive:[]
  property var profiles:[]
  property var servers:[]
  property string status:""
  property bool busy:false
  property int archivePage:0
  property int archiveCount:0
  property int loads:0
  function loadProfiles(mode){loads++;profiles=[{name:"default"},{name:"basic"}];servers=[{name:"hotspot1"}];status="Profiles loaded"}
  function selectCard(index){}
  function previewCard(index){return ""}
 }
 QtObject {id:router;property bool routerConnected:false;property bool busy:false;function copyText(text){}}
 VoucherStudio {id:studio;anchors.fill:parent;voucherStore:store;routerBackend:router}
 TestCase {
  name:"VoucherWorkflow";when:windowShown
  function init(){studio.showStep(0);studio.advancedFields=false;router.routerConnected=false;router.busy=false;store.busy=false;store.profiles=[];store.loads=0;store.status=""}
  function test_disconnected_activation_blocked(){studio.showStep(1);wait(30);verify(!findChild(studio,"voucherActivateButton").enabled);router.routerConnected=true;wait(30);verify(findChild(studio,"voucherActivateButton").enabled);store.busy=true;verify(!findChild(studio,"voucherActivateButton").enabled)}
  function test_profile_read_and_selection(){var read=findChild(studio,"voucherLoadProfilesButton");verify(!read.enabled);router.routerConnected=true;verify(read.enabled);read.clicked();compare(store.loads,1);var combo=findChild(studio,"voucherRouterProfiles");tryCompare(combo,"count",2);verify(combo.enabled);combo.currentIndex=1;combo.activated(1);compare(combo.currentText,"basic");compare(findChild(studio,"voucherProfileField").text,"basic")}
  function test_tabs_keep_header_and_bounds_stable(){var tabs=findChild(studio,"voucherWorkflowTabs");var start=tabs.y;for(var i=0;i<4;i++){studio.showStep(i);wait(30);compare(tabs.y,start);verify(tabs.visible);grabImage(studio).save("/tmp/voucher-step-"+i+".png")}studio.showStep(0);wait(30);var scroll=findChild(studio,"voucherCreationScroll");compare(scroll.contentItem.boundsBehavior,Flickable.StopAtBounds);verify(scroll.height>330)}
 }
}
