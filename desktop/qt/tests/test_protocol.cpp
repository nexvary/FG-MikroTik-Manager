#include <QtTest>
#include <QJsonObject>
#include "../src/Protocol.hpp"
class ProtocolTest:public QObject {Q_OBJECT
private slots:
void origin(){QVERIFY(Protocol::validOrigin("https://3.65.234.184"));QVERIFY(!Protocol::validOrigin("http://3.65.234.184"));QVERIFY(!Protocol::validOrigin("https://user:pass@example.com"));QVERIFY(!Protocol::validOrigin("https://example.com/panel"));}
void modules(){QCOMPARE(Protocol::modules().size(),33);QVERIFY(Protocol::menus().contains("interface/wireless"));QVERIFY(Protocol::menus().contains("system/clock"));}
void terminal(){QCOMPARE(Protocol::menuForCommand("/ip hotspot user print"),QString("ip/hotspot/user"));QVERIFY(Protocol::menuForCommand("/ip hotspot user remove").isEmpty());QVERIFY(Protocol::menuForCommand("/system resource print; reboot").isEmpty());}
void csv(){QCOMPARE(Protocol::csvCell("=HYPERLINK(x)"),QString("\"'=HYPERLINK(x)\""));QCOMPARE(Protocol::jsonText(QJsonValue(qint64(9007199254740993LL))),QString("9007199254740993"));}
void secrets(){QJsonObject o{{"name","alice"},{"password","secret"},{"private-key","secret"}};QCOMPARE(Protocol::redact(o).toObject().size(),1);}
};QTEST_GUILESS_MAIN(ProtocolTest)
#include "test_protocol.moc"
