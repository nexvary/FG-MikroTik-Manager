#pragma once
#include <QObject>
#include <QSqlDatabase>
#include <QJsonObject>
#include <QJsonArray>
#include <QVariantList>
#include <QElapsedTimer>
#include <QUrl>
#include <QSet>
#include <functional>
class Business:public QObject{
 Q_OBJECT
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(QString scope READ scope NOTIFY changed)
 Q_PROPERTY(QString role READ role NOTIFY changed)
 Q_PROPERTY(bool enrolled READ enrolled NOTIFY changed)
 Q_PROPERTY(QVariantList rows READ rows NOTIFY changed)
 Q_PROPERTY(QStringList tables READ tables CONSTANT)
public:
 explicit Business(QString file={},QObject*p=nullptr);~Business();
 QString status()const{return message;}QString scope()const{return organization+" / "+branch;}QString role()const;bool enrolled()const;
 QVariantList rows()const{return view;}QStringList tables()const;
 static qint64 money(QString text);static QSet<QString> permissions(QString role);
 Q_INVOKABLE bool allowed(QString permission)const;
 Q_INVOKABLE QString perform(QString operation,QJsonObject fields);
 Q_INVOKABLE void browse(QString table,QString query="",int page=0);
 Q_INVOKABLE void enroll(QString password);
 Q_INVOKABLE void login(QString username,QString password);
 Q_INVOKABLE void lock();
 Q_INVOKABLE void receipt(QString id,bool sale,QUrl file,QString paper="A4",bool arabic=true);
 Q_INVOKABLE void exportBackup(QUrl path,QString password);
 Q_INVOKABLE void restoreBackup(QUrl path,QString password);
 Q_INVOKABLE void exportCsv(QUrl path);
 QJsonArray snapshot();QJsonArray combine(QJsonArray remote,QString origin);
 void join(QString tenant,QString branch,QJsonArray remote);
 void merge(QJsonArray records,bool preview=false,QString origin={});
 QString device()const;
signals:void changed();
private:
 QSqlDatabase db;QString connection,message,organization,branch;QVariantList view;QJsonObject principal;QElapsedTimer clock;qint64 expiry=0;
 void guard(QString permission)const;QJsonObject session()const;
 class QSqlQuery sql(QString query,QVariantList args={})const;
 QJsonObject one(QString table,QString id)const;QJsonObject scoped(QString table,QString id)const;
 void insert(QString table,QJsonObject body,bool replay=true);
 QJsonObject base(QString id)const;void posting(QString id,QString sub,QString kind,qint64 amount,QString note,QString method="CASH",QString reference="",QString reversal={});
 void reversal(QString id,QString target,QString note);
 void audit(QString id,QString action,QString actor);
 void setScope();void validateReplica();
 QString apply(QString operation,QJsonObject fields);
};
