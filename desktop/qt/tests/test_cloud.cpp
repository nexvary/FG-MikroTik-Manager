#include <QtTest>
#include <QTcpServer>
#include <QSslSocket>
#include <QSslConfiguration>
#include <QSslKey>
#include <QFile>
#include <QJsonDocument>
#include <QRegularExpression>
#include <memory>
#include "Bridge.hpp"
#include "Business.hpp"
class CloudServer:public QTcpServer {
public:
 QJsonArray records;QString tenant,branch,role="owner";qint64 revision=1;bool rejectRevision=false;int uploads=0;
 void incomingConnection(qintptr descriptor)override{
  auto peer=new QSslSocket(this);QFile cert(":/tests/tls/server.pem"),key(":/tests/tls/server-key.pem");cert.open(QIODevice::ReadOnly);key.open(QIODevice::ReadOnly);peer->setLocalCertificate(QSslCertificate(cert.readAll()));peer->setPrivateKey(QSslKey(key.readAll(),QSsl::Rsa));peer->setPeerVerifyMode(QSslSocket::VerifyNone);peer->setSocketDescriptor(descriptor);
  auto buffer=std::make_shared<QByteArray>();connect(peer,&QSslSocket::readyRead,this,[this,peer,buffer]{*buffer+=peer->readAll();auto end=buffer->indexOf("\r\n\r\n");if(end<0)return;auto headers=buffer->left(end).split('\n');int size=0;for(auto header:headers)if(header.toLower().startsWith("content-length:"))size=header.mid(15).trimmed().toInt();if(buffer->size()<end+4+size)return;auto first=headers.first().split(' ');auto path=first.value(1);bool post=first.value(0)=="POST";QJsonObject response;int status=200;
   if(path=="/v1/login")response={{"token",QString(40,'t')},{"expires_in",900}};
   else if(path=="/v1/identity")response={{"tenant",tenant},{"branch",branch},{"role",role}};
   else if(path=="/v1/business/sync"&&!post)response={{"revision",revision},{"records",records}};
   else if(path=="/v1/business/sync"&&post){auto body=QJsonDocument::fromJson(buffer->mid(end+4,size)).object();uploads++;if(rejectRevision||body["revision"].toInteger()!=revision){status=409;response={{"error","revision"}};}else{records=body["records"].toArray();response={{"accepted",true},{"revision",++revision}};}}
   else {status=404;response={{"error","missing"}};}
   auto bytes=QJsonDocument(response).toJson(QJsonDocument::Compact);peer->write("HTTP/1.1 "+QByteArray::number(status)+" Test\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "+QByteArray::number(bytes.size())+"\r\n\r\n"+bytes);peer->disconnectFromHost();buffer->clear();
  });connect(peer,&QSslSocket::disconnected,peer,&QObject::deleteLater);peer->startServerEncryption();
 }
};
class CloudTests:public QObject {Q_OBJECT
 QSslConfiguration original;
private slots:
 void initTestCase(){QVERIFY(QSslSocket::supportsSsl());original=QSslConfiguration::defaultConfiguration();QFile ca(":/tests/tls/ca.pem");QVERIFY(ca.open(QIODevice::ReadOnly));auto trusted=original;auto authorities=trusted.caCertificates();authorities.append(QSslCertificate(ca.readAll()));trusted.setCaCertificates(authorities);QSslConfiguration::setDefaultConfiguration(trusted);}
 void cleanupTestCase(){QSslConfiguration::setDefaultConfiguration(original);}
 void twoWayReplicaAndRevisionConflict(){Business local(":memory:");local.perform("subscriber",{{"id","local"},{"name","Local customer"},{"service","HOTSPOT"},{"currency","EGP"}});auto initial=local.snapshot();auto scope=local.scope().split(" / ");Business remote(":memory:");remote.join(scope[0],scope[1],initial);remote.merge(initial);remote.perform("subscriber",{{"id","remote"},{"name","Remote customer"},{"service","HOTSPOT"},{"currency","EGP"}});auto replica=remote.snapshot();for(auto r:initial)for(auto other:replica)if(r.toObject()["table"]==other.toObject()["table"]&&r.toObject()["id"]==other.toObject()["id"])QCOMPARE(other,r);CloudServer server;server.tenant=scope[0];server.branch=scope[1];server.records=remote.snapshot();QVERIFY(server.listen(QHostAddress::LocalHost));Bridge bridge;bridge.setCommerce(&local);auto origin="https://127.0.0.1:"+QString::number(server.serverPort());bridge.login(origin,scope[0],scope[1],"owner","password");QTRY_VERIFY_WITH_TIMEOUT(bridge.scope().contains("owner"),10000);bridge.syncBusiness();QTRY_VERIFY_WITH_TIMEOUT(!bridge.busy(),10000);QVERIFY2(server.uploads==1,qPrintable(bridge.status()));local.browse("subscribers");QCOMPARE(local.rows().size(),2);QVERIFY(bridge.status().contains("Synchronized"));auto before=local.snapshot();server.rejectRevision=true;bridge.syncBusiness();QTRY_VERIFY_WITH_TIMEOUT(!bridge.busy(),10000);QCOMPARE(local.snapshot(),before);QCOMPARE(server.uploads,2);QVERIFY(bridge.status().contains("409"));}
 void wrongTenantNeverUploads(){Business local(":memory:");CloudServer server;server.tenant="different";server.branch="different";QVERIFY(server.listen(QHostAddress::LocalHost));Bridge bridge;bridge.setCommerce(&local);bridge.login("https://127.0.0.1:"+QString::number(server.serverPort()),"expected","expected","owner","pw");QTRY_VERIFY_WITH_TIMEOUT(!bridge.busy(),10000);QVERIFY(!bridge.connected());bridge.syncBusiness();QCOMPARE(server.uploads,0);}
 void templatesFillAndRejectMissingValues(){Bridge bridge;QCOMPARE(bridge.fillCommand("/ip hotspot user set {{user}} profile={{profile}}",{{"user","alice"},{"profile","premium"}}),QString("/ip hotspot user set \"alice\" profile=\"premium\""));QVERIFY(bridge.fillCommand("/ip hotspot user set {{user}} profile={{profile}}",{{"user","alice"}}).isEmpty());QVERIFY(bridge.fillCommand("/ip hotspot user set {{user}} profile=basic",{{"user","alice\n/system reboot"}}).isEmpty());QVERIFY2(bridge.commandLibrary().size() >= 176, qPrintable(QString("Expected expanded command library (>=176), got %1").arg(bridge.commandLibrary().size())));for(auto row:bridge.commandLibrary()){auto command=row.toMap()["command"].toString();QVariantMap fields;auto matches=QRegularExpression("\\{\\{([a-z]+)\\}\\}").globalMatch(command);while(matches.hasNext())fields[matches.next().captured(1)]="value";QVERIFY2(!bridge.fillCommand(command,fields).isEmpty(),qPrintable(command));}}
};
QTEST_MAIN(CloudTests)
#include "test_cloud.moc"
