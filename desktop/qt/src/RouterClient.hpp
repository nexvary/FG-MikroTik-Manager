#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QSslSocket>
#include <QTimer>
#include <QJsonArray>
#include <QJsonObject>
#include <QVariantList>
#include <QPointer>
#include <functional>
struct RouterReply { QJsonArray rows; QString error; bool uncertain=false; bool ok()const{return error.isEmpty();} };
struct RouterCommand {QString menu,action,selector,error,risk; QJsonObject attributes; bool valid()const{return error.isEmpty()&&!menu.isEmpty();}};
namespace RouterCodec {
 QByteArray length(quint32 size);
 QByteArray sentence(const QStringList &words);
 // 0: partial, 1: complete, -1: malformed. Buffer consumed only for a full sentence.
 int takeSentence(QByteArray &buffer,QStringList &words);
 RouterCommand parse(const QString &line);
}
namespace RouterDiscoveryCodec {
 QJsonObject parseMndp(const QByteArray &datagram,const QHostAddress &sender);
 QStringList scanHosts(const QHostAddress &local,int prefixLength,int cap=254);
}
class RouterClient : public QObject {
 Q_OBJECT
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(bool connected READ connected NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
public:
 using Done=std::function<void(RouterReply)>;
 explicit RouterClient(QObject *parent=nullptr);
 ~RouterClient();
 bool busy()const{return active||pendingRetry||discovering;} bool connected()const{return authenticated;} QString status()const{return message;}
 QVariantList discoveryDiagnostics()const{return discoveryDiagnosticsRows.toVariantList();}
 bool authorized()const{return authorization();}
 void setAudit(std::function<QJsonObject(QString)> begin,std::function<void(QJsonObject,QString)> finish){auditBegin=std::move(begin);auditFinish=std::move(finish);}
 void setAuthorization(std::function<bool()> check){authorization=std::move(check);}
 QString identityKey()const{return host+":"+QString::number(port)+":"+username;}
 void configure(QString host,int port,QString user,QString password,QString protocol);
 void close();
 void read(QString menu,Done done);
 void execute(QString menu,QString action,QJsonObject attributes,Done done);
 void command(QString text,Done done);
 void discover(QVariantList savedProfiles={});
 QJsonArray discovered()const{return neighbors;}
signals: void changed(); void discoveryReady(QJsonArray routers);
private:
 QNetworkAccessManager net; QSslSocket socket; QTimer deadline;
 QString host,username,password,protocol,message;int port=443;
 bool active=false,authenticated=false,loggingIn=false,wrote=false;
 QByteArray input; QString path,action;QJsonObject attributes;QJsonArray rows,neighbors,discoveryDiagnosticsRows;QString trap;Done callback;quint64 generation=0,configVersion=0,discoveryGeneration=0;bool autoMode=false,negotiated=false,selecting=false,retrying=false,pendingRetry=false,discovering=false;int readRetries=0;
 QPointer<QObject> discoveryContext;
 QJsonObject auditAttempt;std::function<QJsonObject(QString)> auditBegin;std::function<void(QJsonObject,QString)> auditFinish;
 std::function<bool()> authorization=[](){return true;};
 void sendLogin();void sendPending();void receive();void finish(RouterReply result);void fail(QString reason,bool io=false);void cancelDiscovery();
};
