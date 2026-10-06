#pragma once
#include "RouterJob.hpp"
#include "Business.hpp"
class NetworkBilling:public QObject {
 Q_OBJECT
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(QVariantList accounts READ accounts NOTIFY changed)
 Q_PROPERTY(QStringList hotspotProfiles READ hotspotProfiles NOTIFY changed)
 Q_PROPERTY(QStringList pppProfiles READ pppProfiles NOTIFY changed)
 Q_PROPERTY(QVariantMap target READ target NOTIFY changed)
public:
 NetworkBilling(RouterClient *router,Business *business,QObject *parent=nullptr);~NetworkBilling();
 bool busy()const{return working;}QString status()const{return message;}
 QVariantList accounts()const{return business->allowed("IMPORT")&&business->allowed("ROUTER")?catalog["accounts"].toArray().toVariantList():QVariantList{};}
 QStringList hotspotProfiles()const;QStringList pppProfiles()const;QVariantMap target()const{return proposed.toVariantMap();}
 Q_INVOKABLE void loadCatalog();Q_INVOKABLE void importSelected(QVariantList indexes,QString currency);
 Q_INVOKABLE void prepare(QString invoice,QString profile,QString allowanceMB);
 Q_INVOKABLE void apply(bool suspend=false);
 static QString expiryScript(QJsonObject target);
signals:void changed();
private:
 RouterClient *router;Business *business;bool working=false;QString message,fingerprint,label,catalogConnection,preparedConnection;
 QJsonObject catalog,proposed,accountRow;RouterJob job;std::shared_ptr<int> lifetime=std::make_shared<int>(0);
 RouterAwait call(QString menu,QString action="print",QJsonObject attributes={},bool required=true){return {router,lifetime,menu,action,attributes,{},required};}
 RouterJob identity();RouterJob account(QJsonObject target);RouterJob validate(QJsonObject target);RouterJob disconnectAccount(QJsonObject target);
 RouterJob catalogJob(QVariantList selection={},QString currency={});RouterJob prepareJob(QString invoice,QString profile,QString allowance);RouterJob applyJob(bool suspend);
};
