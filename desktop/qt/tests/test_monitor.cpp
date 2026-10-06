#include <QtTest>
#include "Monitor.hpp"
class MonitorTests:public QObject{Q_OBJECT
private slots:
 void failureThreshold(){MonitorHealth h;QVERIFY(h.stale(0));QCOMPARE(h.sample(0,0,false),QString{});QCOMPARE(h.failures,1);QCOMPARE(h.sample(30000,0,false),QString("UNREACHABLE"));QCOMPARE(h.sample(60000,0,false),QString{});QCOMPARE(h.sample(330000,0,false),QString("UNREACHABLE"));QCOMPARE(h.sample(360000,10,true),QString("RESOLVED:UNREACHABLE"));QVERIFY(!h.stale(450000));QVERIFY(h.stale(450001));}
 void cpuAndRecovery(){MonitorHealth h;QCOMPARE(h.sample(0,85,true),QString("HIGH_CPU"));QCOMPARE(h.sample(30000,0,false),QString{});QCOMPARE(h.incident,QString("HIGH_CPU"));QCOMPARE(h.sample(60000,84,true),QString("RESOLVED:HIGH_CPU"));QCOMPARE(h.failures,0);QCOMPARE(h.sample(90000,84,true),QString{});}
 void capacityAndPermission(){Monitor monitor;monitor.setAuthorization([]{return false;});monitor.start({{"id","a"},{"host","127.0.0.1"}},"secret");QVERIFY(!monitor.active());QVERIFY(monitor.routers().isEmpty());}
};
QTEST_MAIN(MonitorTests)
#include "test_monitor.moc"
