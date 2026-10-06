#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QJsonArray>
#include <QJsonObject>
#include <QVariantList>
#include <QDateTime>
#include <functional>
class Bridge : public QObject {
 Q_OBJECT
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(bool connected READ connected NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(QString scope READ scope NOTIFY changed)
 Q_PROPERTY(QString terminal READ terminal NOTIFY changed)
 Q_PROPERTY(QVariantList rows READ rows NOTIFY changed)
 Q_PROPERTY(QStringList columns READ columns NOTIFY changed)
 Q_PROPERTY(QStringList menus READ menus CONSTANT)
public:
 explicit Bridge(QObject *parent=nullptr);
 bool busy()const{return m_busy;} bool connected()const{return !token.isEmpty() && QDateTime::currentDateTimeUtc()<expires;}
 QString status()const{return m_status;} QString scope()const{return m_scope;} QString terminal()const{return m_terminal;}
 QVariantList rows()const{return m_rows;} QStringList columns()const{return m_columns;} QStringList menus()const;
 Q_INVOKABLE void login(QString url,QString tenant,QString branch,QString user,QString password);
 Q_INVOKABLE void logout();
 Q_INVOKABLE void business();
 Q_INVOKABLE void filter(QString table,QString query,int page);
 Q_INVOKABLE void radius(bool sessions);
 Q_INVOKABLE void diagnose();
 Q_INVOKABLE void exportCsv(QUrl path);
 Q_INVOKABLE void router(QString url,QString user,QString password,QString menu);
 Q_INVOKABLE void command(QString value);
 Q_INVOKABLE void clearTerminal();
 Q_INVOKABLE void clearView();
 Q_INVOKABLE void smoke();
signals: void changed();
private:
 QNetworkAccessManager net; bool m_busy=false; QString token,origin,m_status,m_scope,m_terminal,routerOrigin,routerAuth;
 QDateTime expires; QJsonArray records,matching; QVariantList m_rows; QStringList m_columns;
 void reset(); void showRows(QJsonArray data);
 void request(QString url,QByteArray method,QJsonObject body,QByteArray auth,std::function<void(QJsonValue)> done);
 void api(QString path,QByteArray method,QJsonObject body,std::function<void(QJsonValue)> done);
};
