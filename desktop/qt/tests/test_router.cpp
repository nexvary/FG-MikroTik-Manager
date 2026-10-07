#include <QtTest>
#include <QTcpServer>
#include <QTcpSocket>
#include <memory>
#include <QSettings>
#include <QCoreApplication>
#include "RouterClient.hpp"
#include "NetworkInventory.hpp"
#include "Bridge.hpp"
class RouterTests:public QObject{Q_OBJECT
private slots:
 void mndpParserAndScanCap(){
  QByteArray b(4,'\0');auto tlv=[&](quint16 type,QByteArray value){b.append(char(type>>8));b.append(char(type&255));b.append(char(value.size()>>8));b.append(char(value.size()&255));b+=value;};
  tlv(1,QByteArray::fromHex("AABBCCDDEEFF"));tlv(5,"branch-router");tlv(7,"7.20");tlv(8,"MikroTik");tlv(12,"hEX");tlv(17,QByteArray::fromHex("C0A85801"));tlv(16,"bridge");
  auto row=RouterDiscoveryCodec::parseMndp(b,QHostAddress("192.168.88.254"));QCOMPARE(row["host"].toString(),QString("192.168.88.1"));QCOMPARE(row["mac"].toString(),QString("AA:BB:CC:DD:EE:FF"));QCOMPARE(row["name"].toString(),QString("branch-router"));QCOMPARE(row["method"].toString(),QString("MNDP"));QCOMPARE(row["verification"].toString(),QString("MNDP RouterOS"));
  auto hosts=RouterDiscoveryCodec::scanHosts(QHostAddress("192.168.88.10"),16,254);QCOMPARE(hosts.size(),253);QVERIFY(!hosts.contains("192.168.88.10"));QVERIFY(hosts.contains("192.168.88.1"));QVERIFY(!hosts.contains("192.168.89.1"));
  QCOMPARE(RouterDiscoveryCodec::scanHosts(QHostAddress("192.168.88.1"),31,254).size(),0);QVERIFY(RouterDiscoveryCodec::parseMndp(QByteArray(3,'\0'),QHostAddress("192.168.88.1")).isEmpty());
 }
 void inventoryMergeAndConflicts(){
  QJsonArray neighbors{QJsonObject{{"address","192.168.88.2"},{"mac-address","aa:bb:cc:dd:ee:01"},{"identity","AP one"},{"platform","MikroTik"}}};
  QJsonArray leases{QJsonObject{{"active-address","192.168.88.2"},{"active-mac-address","AA:BB:CC:DD:EE:01"}},QJsonObject{{"address","192.168.88.3"},{"mac-address","AA:BB:CC:DD:EE:02"},{"host-name","AP two"}}};
  QJsonArray arp{QJsonObject{{"address","192.168.88.2"}},QJsonObject{{"address","invalid"}},QJsonObject{{"address","192.168.88.3"},{"mac-address","AA:BB:CC:DD:EE:03"}}};
  auto rows=NetworkInventory::combine(neighbors,leases,arp);QCOMPARE(rows.size(),3);
  auto first=rows[0].toObject();QCOMPARE(first["name"].toString(),QString("AP one"));QCOMPARE(first["management"].toString(),QString("RouterOS advertised"));QCOMPARE(first["source"].toString(),QString("Neighbor / DHCP / ARP"));
  QCOMPARE(rows[1].toObject()["management"].toString(),QString("Unverified device"));QCOMPARE(rows[1].toObject()["host"],rows[2].toObject()["host"]);
 }
 void inventoryUsesOnlyReadsAndKeepsPartialResults(){
  QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));QStringList commands;
  connect(&server,&QTcpServer::newConnection,this,[&]{auto peer=server.nextPendingConnection();auto buffer=std::make_shared<QByteArray>();connect(peer,&QTcpSocket::readyRead,this,[&,peer,buffer]{*buffer+=peer->readAll();QStringList words;while(RouterCodec::takeSentence(*buffer,words)==1){auto command=words.first();commands.append(command);if(command=="/login")peer->write(RouterCodec::sentence({"!done"}));else if(command=="/ip/neighbor/print")peer->write(RouterCodec::sentence({"!re","=address=192.168.88.2","=identity=AP one","=platform=MikroTik"})+RouterCodec::sentence({"!done"}));else if(command=="/ip/dhcp-server/lease/print")peer->write(RouterCodec::sentence({"!trap","=message=not permitted"})+RouterCodec::sentence({"!done"}));else peer->write(RouterCodec::sentence({"!re","=address=192.168.88.3","=mac-address=AA:BB:CC:DD:EE:02"})+RouterCodec::sentence({"!done"}));}});});
  Bridge bridge;bool authenticated=false;bridge.routerTransport()->configure("127.0.0.1",server.serverPort(),"admin","pw","API");bridge.routerTransport()->read("system/identity",[&](RouterReply r){authenticated=r.ok();});QTRY_VERIFY_WITH_TIMEOUT(authenticated,5000);commands.clear();
  bridge.discoverNetworkDevices();QTRY_VERIFY_WITH_TIMEOUT(!bridge.busy(),5000);QCOMPARE(bridge.rows().size(),2);QVERIFY(bridge.status().contains("not permitted"));QCOMPARE(commands,QStringList({"/ip/neighbor/print","/ip/dhcp-server/lease/print","/ip/arp/print"}));
 }
 void framing(){for(quint32 n:{0u,127u,128u,16383u,16384u,2097151u,2097152u}){QByteArray b=RouterCodec::length(n)+QByteArray(n,'a')+(n?QByteArray(1,'\0'):QByteArray{});QStringList words;QCOMPARE(RouterCodec::takeSentence(b,words),1);QVERIFY(b.isEmpty());if(n)QCOMPARE(words.first().size(),qsizetype(n));}QByteArray partial=RouterCodec::sentence({"!re","=name=مستخدم"});auto saved=partial;partial.chop(1);QStringList words;QCOMPARE(RouterCodec::takeSentence(partial,words),0);QCOMPARE(partial,saved.left(saved.size()-1));partial.append('\0');QCOMPARE(RouterCodec::takeSentence(partial,words),1);QCOMPARE(words[1],QString("=name=مستخدم"));QByteArray invalid(1,char(0xff));QCOMPARE(RouterCodec::takeSentence(invalid,words),-1);}
 void parser(){auto c=RouterCodec::parse("/ip hotspot user set *A comment=\"hello world\" disabled=no");QVERIFY(c.valid());QCOMPARE(c.menu,QString("ip/hotspot/user"));QCOMPARE(c.selector,QString("*A"));QCOMPARE(c.attributes["comment"].toString(),QString("hello world"));QVERIFY(!RouterCodec::parse("/ip hotspot user remove [find]").valid());QVERIFY(!RouterCodec::parse("/ip hotspot user add name=x; /system reboot").valid());QVERIFY(!RouterCodec::parse("/ip dns set servers=\"broken").valid());QCOMPARE(RouterCodec::parse("/interface disable ether1").risk,QString("DANGEROUS"));QCOMPARE(RouterCodec::parse("/ip/hotspot/user print").risk,QString("READ"));}
 void lostMutationNotRepeated(){QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));int mutations=0,logins=0;QByteArray buffer;connect(&server,&QTcpServer::newConnection,this,[&]{auto peer=server.nextPendingConnection();connect(peer,&QTcpSocket::readyRead,this,[&,peer]{buffer+=peer->readAll();QStringList words;while(RouterCodec::takeSentence(buffer,words)==1){if(words.first()=="/login"){logins++;peer->write(RouterCodec::sentence({"!done"}));}else if(words.first()=="/ip/hotspot/user/add"){mutations++;peer->disconnectFromHost();}}});});RouterClient client;client.configure("127.0.0.1",server.serverPort(),"admin","pw","API");int started=0;QString finished;client.setAudit([&](QString command){started++;return QJsonObject{{"command",command}};},[&](QJsonObject,QString state){finished=state;});bool done=false;RouterReply result;client.execute("ip/hotspot/user","add",{{"name","test"}},[&](RouterReply r){result=r;done=true;});QTRY_VERIFY_WITH_TIMEOUT(done,5000);QVERIFY(!result.ok());QVERIFY(result.uncertain);QCOMPARE(mutations,1);QCOMPARE(logins,1);QCOMPARE(started,1);QCOMPARE(finished,QString("REVIEW"));QVERIFY(!client.busy());}
 void lostReadReconnectsOnce(){QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));int reads=0;connect(&server,&QTcpServer::newConnection,this,[&]{auto peer=server.nextPendingConnection();auto buffer=std::make_shared<QByteArray>();connect(peer,&QTcpSocket::readyRead,this,[&,peer,buffer]{*buffer+=peer->readAll();QStringList words;while(RouterCodec::takeSentence(*buffer,words)==1){if(words.first()=="/login")peer->write(RouterCodec::sentence({"!done"}));else{reads++;if(reads==1)peer->disconnectFromHost();else peer->write(RouterCodec::sentence({"!re","=name=Recovered"})+RouterCodec::sentence({"!done"}));}}});});RouterClient client;client.configure("127.0.0.1",server.serverPort(),"admin","pw","API");bool done=false;RouterReply reply;client.read("system/identity",[&](RouterReply r){reply=r;done=true;});QTRY_VERIFY_WITH_TIMEOUT(done,5000);QVERIFY(reply.ok());QCOMPARE(reads,2);QCOMPARE(reply.rows[0].toObject()["name"].toString(),QString("Recovered"));}
 void savedRouterPasswordUsesWindowsProtection(){
#ifdef Q_OS_WIN
  QCoreApplication::setOrganizationName("FG Machines Tests");QCoreApplication::setApplicationName("FG MTM Router Profile Tests");QSettings settings;settings.remove("routers/profiles");
  Bridge bridge;bridge.saveRouterProfile("Office","branch","192.168.88.1",8728,"admin","API","S3cret-pass",true);
  auto profiles=bridge.profiles();QCOMPARE(profiles.size(),1);auto profile=profiles.first().toMap();QVERIFY(profile["hasPassword"].toBool());QVERIFY(!profile.contains("password_dpapi"));QCOMPARE(bridge.routerProfilePassword(profile["id"].toString()),QString("S3cret-pass"));
  auto raw=settings.value("routers/profiles").toList().first().toMap();QVERIFY(raw.contains("password_dpapi"));QVERIFY(!raw["password_dpapi"].toString().contains("S3cret-pass"));bridge.deleteRouterProfile(profile["id"].toString());QVERIFY(bridge.profiles().isEmpty());
#else
  QSKIP("Windows DPAPI test");
#endif
 }
 void apiRowsAndTrap(){QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));QByteArray buffer;connect(&server,&QTcpServer::newConnection,this,[&]{auto peer=server.nextPendingConnection();connect(peer,&QTcpSocket::readyRead,this,[&,peer]{buffer+=peer->readAll();QStringList words;while(RouterCodec::takeSentence(buffer,words)==1){if(words.first()=="/login")peer->write(RouterCodec::sentence({"!done"}));else if(words.first().endsWith("/print"))peer->write(RouterCodec::sentence({"!re","=.id=*1","=name=ether1"})+RouterCodec::sentence({"!done"}));else peer->write(RouterCodec::sentence({"!trap","=message=not permitted"})+RouterCodec::sentence({"!done"}));}});});RouterClient client;client.configure("127.0.0.1",server.serverPort(),"admin","pw","API");bool done=false;RouterReply result;client.read("interface",[&](RouterReply r){result=r;done=true;});QTRY_VERIFY_WITH_TIMEOUT(done,5000);QVERIFY(result.ok());QCOMPARE(result.rows.first().toObject()["name"].toString(),QString("ether1"));done=false;client.execute("interface","disable",{{".id","*1"}},[&](RouterReply r){result=r;done=true;});QTRY_VERIFY_WITH_TIMEOUT(done,5000);QVERIFY(!result.ok());QVERIFY(!result.uncertain);QCOMPARE(result.error,QString("not permitted"));}
};
QTEST_GUILESS_MAIN(RouterTests)
#include "test_router.moc"
