#include <QtTest>
#include <QTcpServer>
#include <QTcpSocket>
#include <QCryptographicHash>
#include <memory>
#include "NetworkBilling.hpp"
class BillingTests:public QObject {Q_OBJECT
private slots:
 void expiryEscapesAndUsesRecurringDate(){QJsonObject t{{"service","HOTSPOT"},{"account_id","*A"},{"account","a\"$b"},{"end_day",QDate(1970,1,1).daysTo(QDate(2030,1,2))}};auto script=NetworkBilling::expiryScript(t);QVERIFY(script.contains("20300102"));QVERIFY(script.contains("\\\"\\$b"));QVERIFY(script.contains("disabled=yes"));QVERIFY(script.contains("/ip hotspot active remove"));}
 void applyAndSuspendVerified(){run(false);}
 void lostWriteRemainsReview(){run(true);}
private:
 void run(bool dropWrite){
  QTcpServer server;QVERIFY(server.listen(QHostAddress::LocalHost));QJsonObject user{{".id","*A"},{"name","alice"},{"profile","basic"},{"disabled","yes"},{"bytes-in","3"},{"bytes-out","4"},{"limit-uptime","0s"},{"limit-bytes-total","0"}};QJsonObject scheduler;QStringList writes;
  connect(&server,&QTcpServer::newConnection,this,[&]{auto peer=server.nextPendingConnection();auto buffer=std::make_shared<QByteArray>();connect(peer,&QTcpSocket::readyRead,this,[&,peer,buffer]{*buffer+=peer->readAll();QStringList words;while(RouterCodec::takeSentence(*buffer,words)==1){auto path=words.first();QJsonObject attrs;for(int i=1;i<words.size();i++){auto w=words[i];int pos=w.indexOf('=',1);if(pos>1)attrs[w.mid(1,pos-1)]=w.mid(pos+1);}QJsonArray rows;
   if(path=="/login"){}else if(path=="/system/routerboard/print")rows.append(QJsonObject{{"serial-number","TEST123"}});else if(path=="/system/identity/print")rows.append(QJsonObject{{"name","Test router"}});else if(path=="/system/clock/print")rows.append(QJsonObject{{"date",QDate::currentDate().toString("yyyy-MM-dd")},{"time",QTime::currentTime().toString("HH:mm:ss")}});else if(path=="/ip/hotspot/user/profile/print"||path=="/ppp/profile/print")rows.append(QJsonObject{{"name","basic"}});else if(path=="/ip/hotspot/user/print")rows.append(user);else if(path=="/system/scheduler/print"){if(!scheduler.isEmpty())rows.append(scheduler);}else if(path=="/system/scheduler/add"||path=="/system/scheduler/set"){writes.append(path);if(dropWrite&&path.endsWith("/add")){peer->disconnectFromHost();return;}for(auto k:attrs.keys())scheduler[k]=attrs[k];scheduler[".id"]="*S";}else if(path=="/ip/hotspot/user/set"){writes.append(path);for(auto k:attrs.keys())user[k]=attrs[k];}
   for(auto v:rows){QStringList sentence{"!re"};auto row=v.toObject();for(auto k:row.keys())sentence.append("="+k+"="+row[k].toString());peer->write(RouterCodec::sentence(sentence));}peer->write(RouterCodec::sentence({"!done"}));
  }});});
  Business business(":memory:");QString fp=QString::fromLatin1(QCryptographicHash::hash("serial:TEST123",QCryptographicHash::Sha256).toHex());QJsonObject a{{"id","*A"},{"name","alice"},{"service","HOTSPOT"},{"profile","basic"},{"disabled",true}};QJsonObject catalog{{"fingerprint",fp},{"label","Test router"},{"accounts",QJsonArray{a}}};QCOMPARE(business.importRouterAccounts(catalog,{a},"EGP"),1);business.browse("subscribers");auto sub=business.rows().first().toMap()["id"].toString();QVERIFY(!business.perform("plan",{{"id","plan"},{"name","Month"},{"service","HOTSPOT"},{"currency","EGP"},{"price","10"},{"days",30}}).isEmpty());QVERIFY(!business.perform("renew",{{"id","invoice"},{"subscriber_id",sub},{"plan_id","plan"},{"paid","10"},{"method","CASH"}}).isEmpty());
  RouterClient router;router.configure("127.0.0.1",server.serverPort(),"admin","pw","API");NetworkBilling billing(&router,&business);billing.prepare("invoice","basic","10");QTRY_VERIFY_WITH_TIMEOUT(!billing.busy(),10000);QVERIFY2(!billing.target().isEmpty(),qPrintable(billing.status()));QVERIFY(writes.isEmpty());billing.apply();QTRY_VERIFY_WITH_TIMEOUT(!billing.busy(),10000);
  auto target=business.networkTarget("invoice");if(dropWrite){QCOMPARE(target["state"].toString(),QString("REVIEW"));QCOMPARE(writes.size(),1);QCOMPARE(user["disabled"].toString(),QString("yes"));return;}
  QCOMPARE(target["state"].toString(),QString("VERIFIED"));QCOMPARE(writes[0],QString("/system/scheduler/add"));QCOMPARE(writes[1],QString("/ip/hotspot/user/set"));QCOMPARE(user["limit-bytes-total"].toString(),QString::number(10485767));QCOMPARE(scheduler["interval"].toString(),QString("1m"));QVERIFY(business.perform("void_invoice",{{"id","void"},{"target_id","invoice"},{"reason","Cancel"}}).isEmpty());billing.apply(true);QTRY_VERIFY_WITH_TIMEOUT(!billing.busy(),10000);QCOMPARE(business.networkTarget("invoice")["state"].toString(),QString("SUSPENDED"));QCOMPARE(user["disabled"].toString(),QString("yes"));QVERIFY(!business.perform("void_invoice",{{"id","void"},{"target_id","invoice"},{"reason","Cancel"}}).isEmpty());
 }
};
QTEST_MAIN(BillingTests)
#include "test_billing.moc"
