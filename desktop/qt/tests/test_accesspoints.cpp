#include <QtTest>
#include "AccessPoints.hpp"
class AccessPointTest:public QObject{Q_OBJECT
private slots:
void empty(){QVERIFY(AccessPointEngine::discover({}).isEmpty());}
void evidence(){QMap<QString,QJsonArray>d;d["interface/ethernet"]={QJsonObject{{"name","ether2"}}};d["ip/neighbor"]={QJsonObject{{"mac-address","02:11:22:33:44:01"},{"interface","ether2"},{"system-caps-enabled","wlan-access-point"}}};d["ip/arp"]={QJsonObject{{"mac-address","02:11:22:33:44:01"},{"address","192.168.1.2"}}};auto r=AccessPointEngine::discover(d);QCOMPARE(r.size(),1);QCOMPARE(r[0].toObject()["port"].toString(),"ether2");QCOMPARE(r[0].toObject()["classification"].toString(),"Confirmed AP");d.remove("interface/ethernet");QCOMPARE(AccessPointEngine::discover(d)[0].toObject()["port"].toString(),QString{});}
void ordinaryClient(){QMap<QString,QJsonArray>d{{"ip/dhcp-server/lease",{QJsonObject{{"mac-address","02:11:22:33:44:01"},{"host-name","TP-Link Access Point"}}}}};QCOMPARE(AccessPointEngine::discover(d)[0].toObject()["classification"].toString(),"Unknown network device");}
void invalid(){QVERIFY(AccessPointEngine::mac("FF:FF:FF:FF:FF:FF").isEmpty());QVERIFY(AccessPointEngine::mac("00:00:00:00:00:00").isEmpty());QCOMPARE(AccessPointEngine::mac("02-11-aa-33-44-01"),"02:11:AA:33:44:01");}
void association(){QMap<QString,QJsonArray>d{{"interface/bridge/host",{QJsonObject{{"mac-address","02:11:22:33:44:02"},{"on-interface","ether2"}}}},{"ip/hotspot/active",{QJsonObject{{"mac-address","02:11:22:33:44:02"},{"user","account"}}}}};QJsonObject ap{{"mac","02:11:22:33:44:01"},{"classification","Confirmed AP"},{"port","ether2"},{"mode","Bridge"}};QCOMPARE(AccessPointEngine::correlate(d,{ap})[0].toObject()["confidence"].toString(),"Inferred");ap["mode"]="NAT";QCOMPARE(AccessPointEngine::correlate(d,{ap})[0].toObject()["confidence"].toString(),"Unknown");ap["mode"]="Bridge";auto other=ap;other["mac"]="02:11:22:33:44:03";QCOMPARE(AccessPointEngine::correlate(d,{ap,other})[0].toObject()["confidence"].toString(),"Unknown");}
void exportCsv(){auto value=AccessPointEngine::csv({QJsonObject{{"shop","=1+1"},{"name","a\"b"}}});QVERIFY(value.contains("'=1+1"));QVERIFY(value.contains("a\"\"b"));QVERIFY(value.startsWith("\xef\xbb\xbf"));}
};
QTEST_MAIN(AccessPointTest)
#include "test_accesspoints.moc"
