#pragma once
#include "RouterClient.hpp"
#include <QSqlDatabase>
#include <QElapsedTimer>
#include <QUrl>
namespace AccessPointEngine {
 QString mac(QString value);
 QJsonArray discover(const QMap<QString,QJsonArray>& data,const QJsonObject& mappings={});
 QJsonArray correlate(const QMap<QString,QJsonArray>& data,const QJsonArray& devices);
 QJsonArray summary(const QJsonArray& records,const QJsonArray& devices);
 QStringList menus();
 QByteArray csv(const QJsonArray& rows);
}
class AccessPoints:public QObject {
 Q_OBJECT
 Q_PROPERTY(QVariantList devices READ devices NOTIFY changed)
 Q_PROPERTY(QVariantList summary READ summary NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(int confirmedCount READ confirmedCount NOTIFY changed)
public:
 explicit AccessPoints(RouterClient* transport,QObject* parent=nullptr);~AccessPoints();
 QVariantList devices()const{return rows.toVariantList();}QVariantList summary()const{return reportRows.toVariantList();}
 QString status()const{return message;}bool busy()const{return loading;}int confirmedCount()const;
 Q_INVOKABLE void refresh();
 Q_INVOKABLE void saveMapping(QString mac,QString shop,bool confirmed,QString mode);
 Q_INVOKABLE void report(QString from,QString through);
 Q_INVOKABLE void exportCsv(QUrl file);
 Q_INVOKABLE void exportReport(QUrl file,QString format);
 void setScope(QString value);
 void fixture();
signals:void changed();
private:
 RouterClient* client;QSqlDatabase db;QString connection,scope,scopePrefix,message,reportPeriod;QStringList warnings;QJsonArray rows,reportRows,exportRows;
 QMap<QString,QJsonArray> data;bool loading=false;int stage=0;QElapsedTimer cache;quint64 generation=0;
 bool open();void read(quint64 token);void complete();QJsonObject mappings();void rebuild();
};
