#pragma once
#include "RouterClient.hpp"
#include "Protocol.hpp"
#include "RouterJob.hpp"
#include <QJsonObject>
#include <QVariantList>
#include <QSet>
class RouterTools:public QObject{
 Q_OBJECT
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(QVariantList checks READ checks NOTIFY changed)
 Q_PROPERTY(QVariantList changes READ changes NOTIFY changed)
 Q_PROPERTY(QVariantList backups READ backups NOTIFY changed)
 Q_PROPERTY(QVariantList interfaces READ interfaces NOTIFY changed)
 Q_PROPERTY(QVariantList dhcpNetworks READ dhcpNetworks NOTIFY changed)
 Q_PROPERTY(QVariantList users READ users NOTIFY changed)
 Q_PROPERTY(QVariantList activeSessions READ activeSessions NOTIFY changed)
 Q_PROPERTY(bool sessionsKnown READ sessionsKnown NOTIFY changed)
 Q_PROPERTY(QVariantList profiles READ profiles NOTIFY changed)
 Q_PROPERTY(QVariantMap portalDesign READ portalDesign NOTIFY changed)
 Q_PROPERTY(QString portalPreview READ portalPreview NOTIFY changed)
public:
 RouterTools(RouterClient *client,QObject*p=nullptr);~RouterTools();
 bool busy()const{return working;}QString status()const{return message;}QVariantList checks()const{return report.toVariantList();}QVariantList changes()const{return pending["changes"].toArray().toVariantList();}
 QVariantList interfaces()const{return tables["interface"].toArray().toVariantList();}QVariantList dhcpNetworks()const{return client->authorized()?tables["ip/dhcp-server/network"].toArray().toVariantList():QVariantList{};}
 QVariantList users()const;
 QVariantList activeSessions()const{return client->authorized()&&client->connected()?Protocol::redact(tables["ip/hotspot/active"]).toArray().toVariantList():QVariantList{};}
 bool sessionsKnown()const{return client->authorized()&&client->connected()&&tables.contains("ip/hotspot/active")&&!unavailable.contains("ip/hotspot/active");}
 static QJsonObject subscriberUsage(QJsonObject user,QJsonArray sessions,bool sessionsKnown=true);QVariantList profiles()const{return tables["ip/hotspot/profile"].toArray().toVariantList();}QString portalPreview()const{return preview;}
 static bool validCidr(QString cidr);static QString network(QString cidr);static bool privateSubnet(QString cidr);
 static QJsonObject hotspotPlan(QJsonObject tables,QJsonObject request,QString management);
 static QJsonObject portsPlan(QJsonObject tables,QString client,QString wan,QString management);
 static QJsonObject dnsPlan(QJsonObject tables,QSet<QString> networks,QString mode);
 static QJsonObject pingEvidence(QJsonArray rows);
 static QJsonObject renderPortal(QJsonObject design);
 Q_INVOKABLE void inspect();
 Q_INVOKABLE void refreshSubscribers();
 Q_INVOKABLE void planHotspot(QJsonObject request);
 Q_INVOKABLE void planPorts(QString client,QString wan);
 Q_INVOKABLE void planDns(QStringList networkIds,QString mode);
 Q_INVOKABLE void apply(QString backupPassword);
 Q_INVOKABLE void restoreDns();
 Q_INVOKABLE void synchronizeClock();
 Q_INVOKABLE void repairDns();
 Q_INVOKABLE void loadBackups();
 Q_INVOKABLE QString backupPassword(QString file);
 QVariantList backups()const;
 Q_INVOKABLE void backup(QString password);
 Q_INVOKABLE void restoreBackup(QString file,QString password);
 Q_INVOKABLE void exportConfiguration();
 Q_INVOKABLE void reboot();
 Q_INVOKABLE void flushDns();
 Q_INVOKABLE void subscriber(QString operation,QString id,QJsonObject fields);
 Q_INVOKABLE void previewPortal(QJsonObject design,QString page="login.html");
 Q_INVOKABLE QString portalLogo(QUrl file);
 Q_INVOKABLE void savePortalDesign(QJsonObject design);
 QVariantMap portalDesign()const;
 Q_INVOKABLE void installPortal(QString profile,QJsonObject design);
 Q_INVOKABLE void restorePortal();
signals:void changed();
private:
 RouterClient *client;bool working=false;QString message,preview;QJsonObject tables,pending,journal;QJsonArray report;QSet<QString> unavailable;std::shared_ptr<int> lifetime=std::make_shared<int>(0);RouterJob job;
 RouterAwait call(QString menu,QString action="print",QJsonObject attrs={},bool required=true){return {client,lifetime,menu,action,attrs,{},required};}
 void launch(QString op,QJsonObject fields={});RouterJob run(QString op,QJsonObject fields);
 QString signature()const;QString fingerprint()const;QString journalPath()const;void persist();void loadJournal();void evaluate();
};
