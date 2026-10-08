#include <QtTest>
#include "RouterTools.hpp"
#include <QTcpServer>
#include <QTcpSocket>
#include <QStandardPaths>
#include <memory>
class ToolTests:public QObject{Q_OBJECT
 QJsonObject fixture(){return {{"interface",QJsonArray{QJsonObject{{"name","ether1"},{"type","ether"},{"running","yes"}},QJsonObject{{"name","ether2"},{"type","ether"},{"running","yes"}},QJsonObject{{"name","ether3"},{"type","ether"},{"running","no"}}}},{"ip/route",QJsonArray{QJsonObject{{"dst-address","0.0.0.0/0"},{"active","yes"},{"immediate-gw","192.168.1.1%ether1"}}}},{"ip/dns",QJsonArray{QJsonObject{{"servers",""},{"allow-remote-requests","no"}}}}};}
private slots:
 void subscriberUsageIncludesSessions(){
  QJsonObject user{{"name","alice"},{"uptime","1d00:00:00"},{"limit-uptime","2d"},{"bytes-in","9007199254740993"},{"bytes-out","100"},{"limit-bytes-total","9007199254741993"}};
  QJsonArray sessions{QJsonObject{{"user","alice"},{"uptime","10m"},{"bytes-in","100"},{"bytes-out","200"}},QJsonObject{{"user","bob"},{"uptime","5h"},{"bytes-in","200"},{"bytes-out","200"}}};
  auto result=RouterTools::subscriberUsage(user,sessions);
  QCOMPARE(result["usageState"].toString(),QString("ACTIVE"));QCOMPARE(result["sessionCount"].toInt(),1);
  QCOMPARE(result["usedSeconds"].toString(),QString("87000"));QCOMPARE(result["remainingSeconds"].toString(),QString("85800"));
  QCOMPARE(result["uploadBytes"].toString(),QString("9007199254741093"));QCOMPARE(result["remainingBytes"].toString(),QString("600"));
  result=RouterTools::subscriberUsage(user,{},false);QCOMPARE(result["usageState"].toString(),QString("UNKNOWN"));QVERIFY(result["remainingSeconds"].isNull());QVERIFY(result["sessionCount"].isNull());
  user["limit-uptime"]="1m";QCOMPARE(RouterTools::subscriberUsage(user,sessions)["usageState"].toString(),QString("EXPIRED"));
 }
 void subscriberUsageUnknownAndUnlimited(){
  QJsonObject user{{"name","alice"},{"uptime","0s"},{"limit-uptime","0s"},{"bytes-in","0"},{"bytes-out","0"},{"limit-bytes-total","0"},{"password","hidden"}};
  auto result=RouterTools::subscriberUsage(user,{});QVERIFY(result["remainingSeconds"].isNull());QVERIFY(result["remainingBytes"].isNull());QCOMPARE(result["usageState"].toString(),QString("OFFLINE"));QVERIFY(result["password"]!="hidden");
  user["uptime"]="invalid";QVERIFY(RouterTools::subscriberUsage(user,{})["usedSeconds"].isNull());
  user["uptime"]="999999999999999999999w";QVERIFY(RouterTools::subscriberUsage(user,{})["usedSeconds"].isNull());
  user["bytes-in"]="9223372036854775807";user["bytes-out"]="1";QVERIFY(RouterTools::subscriberUsage(user,{})["usedBytes"].isNull());
 }

 void readinessIsBoundToSelectedClientNetwork(){
  auto f=fixture();for(auto menu:{"interface/bridge","interface/bridge/port","interface/pppoe-client","ip/dhcp-client","ip/address","ip/dhcp-server","ip/dhcp-server/network","ip/pool","ip/hotspot","ip/hotspot/profile","ip/hotspot/user/profile","ip/firewall/nat","ip/firewall/filter","ip/service","user","file","system/clock","system/scheduler","system/resource"})if(!f.contains(menu))f[menu]=QJsonArray{};
  f["ip/address"]=QJsonArray{QJsonObject{{"interface","ether2"},{"address","192.168.10.1/24"}}};f["ip/dhcp-server"]=QJsonArray{QJsonObject{{"interface","ether3"},{"name","wrong-network"},{"address-pool","other"}}};
  auto state=[](QJsonArray checks,QString key){for(auto row:checks)if(row.toObject()["key"]==key)return row.toObject()["state"].toString();return QString{};};
  auto checks=RouterTools::readinessChecks(f,{},"ether2");QCOMPARE(state(checks,"dhcp"),QString("NEEDS_SETUP"));QCOMPARE(state(checks,"cpu"),QString("NEEDS_SETUP"));QCOMPARE(state(checks,"client"),QString("READY"));QCOMPARE(state(checks,"dhcp-network"),QString("NEEDS_SETUP"));
  f["ip/dhcp-server"]=QJsonArray{QJsonObject{{"interface","ether2"},{"name","clients"},{"address-pool","client-pool"}}};f["ip/pool"]=QJsonArray{QJsonObject{{"name","client-pool"},{"ranges","192.168.10.10-192.168.10.200"}}};
  checks=RouterTools::readinessChecks(f,{},"ether2");QCOMPARE(state(checks,"dhcp"),QString("READY"));QCOMPARE(state(checks,"pool"),QString("READY"));QCOMPARE(state(RouterTools::readinessChecks(f,{"ip/dhcp-server"},"ether2"),"pool"),QString("UNKNOWN"));
 }
 void pingSummaries(){auto result=RouterTools::pingEvidence(QJsonArray{QJsonObject{{"time","2ms500us"}},QJsonObject{{"status","timeout"}},QJsonObject{{"time","500us"}}});QCOMPARE(result["received"].toInt(),2);QCOMPARE(result["latency_ms"].toDouble(),1.5);QVERIFY(result["loss_percent"].toDouble()>33);result=RouterTools::pingEvidence(QJsonArray{QJsonObject{{"sent","3"},{"received","1"},{"avg-rtt","4ms"}}});QCOMPARE(result["received"].toInt(),1);QCOMPARE(result["latency_ms"].toDouble(),4.0);QVERIFY(RouterTools::pingEvidence({})["loss_percent"].isNull());}
 void hotspotPreservesWan(){auto f=fixture();QJsonObject request{{"interface","ether2"},{"gateway","192.168.10.1/24"},{"network","192.168.10.0/24"},{"pool","192.168.10.10-192.168.10.250"},{"dnsName","wifi.local"}};auto plan=RouterTools::hotspotPlan(f,request,"192.168.1.2");QCOMPARE(plan["wan"].toString(),QString("ether1"));QVERIFY(plan["changes"].toArray().size()>5);for(auto v:plan["changes"].toArray()){auto step=v.toObject();auto attrs=step["attributes"].toObject();QVERIFY(attrs["interface"]!="ether1");if(step["menu"]=="ip/dns")QVERIFY(!attrs.contains("allow-remote-requests"));}request["interface"]="ether1";QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,request,"192.168.1.2"),std::runtime_error);request["interface"]="ether2";request["pool"]="192.168.10.1-192.168.10.200";QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,request,"192.168.1.2"),std::runtime_error);}
 void wanDetectionMatrix(){
  auto f=fixture();auto d=RouterTools::detectWan(f);QCOMPARE(d["name"].toString(),QString("ether1"));QCOMPARE(d["source"].toString(),QString("Active default route"));
  f["ip/route"]=QJsonArray{};f["ip/dhcp-client"]=QJsonArray{QJsonObject{{"interface","ether2"},{"status","bound"}}};d=RouterTools::detectWan(f);QCOMPARE(d["name"].toString(),QString("ether2"));QCOMPARE(d["source"].toString(),QString("Bound DHCP client"));
  f["interface"]=QJsonArray{QJsonObject{{"name","pppoe-out1"},{"type","pppoe-out"},{"running","yes"}},QJsonObject{{"name","ether2"},{"type","ether"},{"running","yes"}}};f["ip/dhcp-client"]=QJsonArray{};f["interface/pppoe-client"]=QJsonArray{QJsonObject{{"name","pppoe-out1"},{"interface","ether1"},{"running","yes"}}};d=RouterTools::detectWan(f);QCOMPARE(d["name"].toString(),QString("pppoe-out1"));QCOMPARE(d["source"].toString(),QString("Connected PPPoE client"));
  f["interface/pppoe-client"]=QJsonArray{};f["interface"]=QJsonArray{QJsonObject{{"name","lte1"},{"type","lte"},{"running","yes"}},QJsonObject{{"name","ether2"},{"type","ether"},{"running","yes"}}};d=RouterTools::detectWan(f);QCOMPARE(d["name"].toString(),QString("lte1"));
 }
 void currentNetworkFixtureDetectsEther1AndKeepsFgClients(){
  auto f=fixture();f["interface"]=QJsonArray{QJsonObject{{"name","ether1"},{"type","ether"},{"running","yes"}},QJsonObject{{"name","fg-clients"},{"type","bridge"},{"running","yes"}}};f["ip/address"]=QJsonArray{QJsonObject{{"interface","fg-clients"},{"address","192.168.10.1/24"}}};
  QJsonObject request{{"interface","fg-clients"},{"gateway","192.168.10.1/24"},{"network","192.168.10.0/24"},{"pool","192.168.10.10-192.168.10.250"},{"dnsName","wifi.local"}};auto plan=RouterTools::hotspotPlan(f,request,"192.168.1.2");QCOMPARE(plan["wan"].toString(),QString("ether1"));for(auto v:plan["changes"].toArray()){auto step=v.toObject();if(step["menu"]=="ip/firewall/nat")QCOMPARE(step["attributes"].toObject()["out-interface"].toString(),QString("ether1"));}
  request["wan"]="fg-clients";QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,request,"192.168.1.2"),std::runtime_error);
 }
 void overlapRejected(){auto f=fixture();f["ip/address"]=QJsonArray{QJsonObject{{"interface","ether3"},{"address","192.168.10.1/24"}}};QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,{{"interface","ether2"},{"gateway","192.168.10.1/24"},{"network","192.168.10.0/24"},{"pool","192.168.10.10-192.168.10.250"}},"192.168.1.2"),std::runtime_error);}
 void numericGatewayAndV6Matrix(){
  for(auto name:{"ether1","wan","pppoe-out1","lte1","vlan-wan","sfp1","bridge-wan"}){
   QJsonObject f{{"interface",QJsonArray{QJsonObject{{"name",name}}}},{"ip/address",QJsonArray{QJsonObject{{"interface",name},{"address","192.168.1.2/24"}}}},{"ip/route",QJsonArray{QJsonObject{{"dst-address","0.0.0.0/0"},{"active","yes"},{"gateway","192.168.1.1"}}}}};
   QCOMPARE(RouterTools::detectWan(f)["name"].toString(),QString(name));
   f["ip/route"]=QJsonArray{QJsonObject{{"dst-address","0.0.0.0/0"},{"active","yes"},{"gateway-status",QString("192.168.1.1 reachable via ")+name}}};QCOMPARE(RouterTools::detectWan(f)["name"].toString(),QString(name));
  }
 }
 void ambiguousInactiveAndDisabledWan(){auto f=fixture();f["ip/route"]=QJsonArray{QJsonObject{{"dst-address","0.0.0.0/0"},{"active","yes"},{"gateway","ether1"}},QJsonObject{{"dst-address","0.0.0.0/0"},{"active","yes"},{"gateway","ether2"}}};QVERIFY(RouterTools::detectWan(f).isEmpty());f["ip/route"]=QJsonArray{QJsonObject{{"dst-address","0.0.0.0/0"},{"active","no"},{"gateway","ether1"}}};QVERIFY(RouterTools::detectWan(f).isEmpty());f=fixture();f["interface"]=QJsonArray{QJsonObject{{"name","ether1"},{"disabled","yes"}}};QVERIFY(RouterTools::detectWan(f).isEmpty());}
 void manualWanAndExistingNat(){auto f=fixture();QJsonObject r{{"interface","ether2"},{"wan","ether1"},{"gateway","192.168.10.1/24"},{"network","192.168.10.0/24"},{"pool","192.168.10.10-192.168.10.250"},{"dnsName","wifi.local"}};
  auto countNat=[](QJsonObject p){int n=0;for(auto c:p["changes"].toArray())if(c.toObject()["menu"]=="ip/firewall/nat")n++;return n;};
  QCOMPARE(countNat(RouterTools::hotspotPlan(f,r,"192.168.1.2")),1);
  f["ip/firewall/nat"]=QJsonArray{QJsonObject{{"chain","srcnat"},{"action","masquerade"},{"out-interface","ether1"}}};QCOMPARE(countNat(RouterTools::hotspotPlan(f,r,"192.168.1.2")),0);
  f["ip/firewall/nat"]=QJsonArray{QJsonObject{{"chain","srcnat"},{"action","masquerade"},{"out-interface-list","WAN"}}};f["interface/list/member"]=QJsonArray{QJsonObject{{"list","WAN"},{"interface","ether1"}}};QCOMPARE(countNat(RouterTools::hotspotPlan(f,r,"192.168.1.2")),0);
  f["ip/route"]=QJsonArray{};QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,r,"192.168.1.2"),std::runtime_error);r["wan"]="";QVERIFY_EXCEPTION_THROWN(RouterTools::hotspotPlan(f,r,"192.168.1.2"),std::runtime_error);
 }
 void portsExcludeWanAndOccupied(){auto f=fixture();f["ip/dhcp-client"]=QJsonArray{QJsonObject{{"interface","ether1"}}};auto plan=RouterTools::portsPlan(f,"ether2","ether1","192.168.1.2");for(auto value:plan["changes"].toArray()){auto step=value.toObject();QVERIFY(step["attributes"].toObject()["interface"]!="ether1");}f["ip/firewall/filter"]=QJsonArray{QJsonObject{{"in-interface","ether2"}}};QVERIFY_EXCEPTION_THROWN(RouterTools::portsPlan(f,"ether2","ether1","192.168.1.2"),std::runtime_error);}
 void dnsPrivateOnly(){QVERIFY(RouterTools::privateSubnet("10.0.0.0/8"));QVERIFY(RouterTools::privateSubnet("172.16.0.0/12"));QVERIFY(RouterTools::privateSubnet("192.168.10.0/24"));QVERIFY(RouterTools::privateSubnet("100.64.0.0/10"));QVERIFY(!RouterTools::privateSubnet("8.8.8.0/24"));QVERIFY(!RouterTools::privateSubnet("192.168.10.1/24"));auto f=fixture();f["ip/dhcp-server/network"]=QJsonArray{QJsonObject{{".id","*1"},{"address","192.168.10.0/24"},{"dns-server","192.168.10.1"}}};auto plan=RouterTools::dnsPlan(f,{"*1"},"FAMILY");for(auto v:plan["changes"].toArray())QVERIFY(!v.toObject()["attributes"].toObject().contains("allow-remote-requests"));f["ip/dhcp-server/network"]=QJsonArray{QJsonObject{{".id","*1"},{"address","8.8.8.0/24"}}};QVERIFY_EXCEPTION_THROWN(RouterTools::dnsPlan(f,{"*1"},"FAMILY"),std::runtime_error);}
 void portalUploadReadback_data(){
  QTest::addColumn<bool>("corrupt");QTest::addColumn<int>("missingReads");
  QTest::newRow("large-contents-omitted-in-normal-print")<<false<<0;
  QTest::newRow("delayed-explicit-readback")<<false<<2;
  QTest::newRow("corrupt-content-never-bound")<<true<<0;
 }
 void portalUploadReadback(){
  QFETCH(bool,corrupt);QFETCH(int,missingReads);
  QStandardPaths::setTestModeEnabled(true);
  QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));
  QJsonArray files{QJsonObject{{".id","*flash"},{"name","flash"},{"type","disk"}}};
  QJsonObject profile{{".id","*P"},{"name","clients"},{"html-directory","flash/old"},{"html-directory-override",""}};
  QJsonObject clock{{"date",QDate::currentDate().toString("yyyy-MM-dd")},{"time",QTime::currentTime().toString("HH:mm:ss")}};
  int contentReads=0,bindings=0;bool allFilesVerifiedAtBinding=false;
  auto assets=RouterTools::renderPortal({{"networkName",QString::fromUtf8("شبكة العملاء")}});
  QVERIFY(assets["login.html"].toString().toUtf8().size()>4096);
  connect(&server,&QTcpServer::newConnection,this,[&]{
   auto peer=server.nextPendingConnection();auto buffer=std::make_shared<QByteArray>();
   connect(peer,&QTcpSocket::readyRead,this,[&,peer,buffer]{
    *buffer+=peer->readAll();QStringList words;
    while(RouterCodec::takeSentence(*buffer,words)==1){
     auto command=words.first();QJsonObject attrs;for(auto word:words.mid(1)){auto split=word.indexOf('=',1);if(split>0)attrs[word.mid(1,split-1)]=word.mid(split+1);}
     QJsonArray rows;
     if(command=="/system/backup/save")files.append(QJsonObject{{"name",attrs["name"].toString()+".backup"},{"type","backup"}});
     else if(command=="/system/clock/print")rows.append(clock);
     else if(command=="/system/clock/set")for(auto key:attrs.keys())clock[key]=attrs[key];
     else if(command=="/system/ntp/client/print")rows.append(QJsonObject{{"enabled","yes"}});
     else if(command=="/system/ntp/client/servers/print")rows.append(QJsonObject{{"address","time.cloudflare.com"},{"enabled","yes"}});
     else if(command=="/interface/print")rows.append(QJsonObject{{"name","ether1"},{"type","ether"},{"mac-address","AA:BB:CC:DD:EE:FF"}});
     else if(command=="/ip/hotspot/profile/print")rows.append(profile);
     else if(command=="/file/add"){
      auto row=attrs;row[".id"]="*F"+QString::number(files.size());files.append(row);
     }else if(command=="/file/set"){
      for(int i=0;i<files.size();i++){auto row=files[i].toObject();if(row[".id"]==attrs[".id"]){row["contents"]=attrs["contents"];row["size"]=QString::number(attrs["contents"].toString().toUtf8().size());files[i]=row;}}
     }else if(command=="/file/print"){
      for(auto value:files){auto row=value.toObject();row.remove("contents");rows.append(row);}
     }else if(command=="/file/read"){
      contentReads++;if(missingReads>0){missingReads--;}
      else for(auto value:files){auto row=value.toObject();if(row["name"]==attrs["file"]){
       auto bytes=row["contents"].toString().toUtf8().mid(attrs["offset"].toString().toInt(),attrs["chunk-size"].toString().toInt());
       rows.append(QJsonObject{{"data",corrupt?QString("corrupt"):QString::fromUtf8(bytes)}});
      }}
     }else if(command=="/ip/hotspot/profile/set"){
      bindings++;auto directory=attrs["html-directory"].toString();allFilesVerifiedAtBinding=true;
      for(auto name:assets.keys()){bool found=false;for(auto value:files){auto row=value.toObject();if(row["name"]==directory+"/"+name&&row["contents"]==assets[name])found=true;}allFilesVerifiedAtBinding&=found;}
      for(auto key:attrs.keys())profile[key]=attrs[key];
     }
     if(command=="/file/read"&&rows.size()==1){peer->write(RouterCodec::sentence({"!done","=data="+rows.first().toObject()["data"].toString()}));continue;}
     QByteArray reply;for(auto value:rows){QStringList sentence{"!re"};auto row=value.toObject();for(auto key:row.keys())sentence.append("="+key+"="+row[key].toString());reply+=RouterCodec::sentence(sentence);}reply+=RouterCodec::sentence({"!done"});peer->write(reply);
    }
   });
  });
  RouterClient client;client.configure("127.0.0.1",server.serverPort(),"admin","pw","API");
  RouterTools tools(&client);tools.installPortal("*P",{{"networkName",QString::fromUtf8("شبكة العملاء")}});
  QTRY_VERIFY_WITH_TIMEOUT(!tools.busy(),15000);
  if(corrupt){QCOMPARE(bindings,0);QCOMPARE(profile["html-directory"].toString(),QString("flash/old"));QVERIFY(tools.status().contains("PORTAL_UPLOAD_VERIFY_FAILED"));QVERIFY(tools.status().contains("alogin.html"));}
  else{QVERIFY2(tools.status().contains("Portal installed and verified"),qPrintable(tools.status()));QCOMPARE(bindings,1);QVERIFY(allFilesVerifiedAtBinding);QCOMPARE(contentReads,assets.size()+(QString(QTest::currentDataTag())=="delayed-explicit-readback"?2:0));}
 }
 void portalEscapes(){auto assets=RouterTools::renderPortal({{"networkName","<script>alert(1)</script>"},{"color","#159DFF"},{"website","javascript:alert(1)"}});QVERIFY(!assets["login.html"].toString().contains("<script>alert(1)</script>"));QVERIFY(assets["login.html"].toString().contains("&lt;script&gt;"));QVERIFY(!assets["login.html"].toString().contains("javascript:alert"));QVERIFY_EXCEPTION_THROWN(RouterTools::renderPortal({{"color","red;display:none"}}),std::runtime_error);}
};
QTEST_MAIN(ToolTests)
#include "test_tools.moc"
