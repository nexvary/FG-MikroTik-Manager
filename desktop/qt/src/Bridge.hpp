#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QJsonArray>
#include <QJsonObject>
#include <QVariantList>
#include <QDateTime>
#include <functional>
#include "RouterClient.hpp"
class Business;
class Bridge : public QObject {
 Q_OBJECT
 Q_PROPERTY(QVariantList commandLibrary READ commandLibrary CONSTANT)
 Q_PROPERTY(bool routerConnected READ routerConnected NOTIFY changed)
 Q_PROPERTY(QString preview READ preview NOTIFY changed)
 Q_PROPERTY(QVariantList profiles READ profiles NOTIFY changed)
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
 bool busy()const{return m_busy||workflowBusy||routerClient.busy();}
 bool routerConnected()const{return routerClient.connected();} QString preview()const{return m_preview;} QVariantList profiles()const; bool connected()const{return !token.isEmpty() && QDateTime::currentDateTimeUtc()<expires;}
 QString status()const{return m_status;} QString scope()const{return m_scope;} QString terminal()const{return m_terminal;}
 QVariantList rows()const{return m_rows;} QStringList columns()const{return m_columns;} QStringList menus()const;
 QVariantList commandLibrary()const;
 Q_INVOKABLE QString fillCommand(QString text,QVariantMap values);
 Q_INVOKABLE void copyText(QString text);
 void setWorkflowBusy(bool value){workflowBusy=value;emit changed();}
 void setCommerce(Business *value){commerce=value;}
 RouterClient *routerTransport(){return &routerClient;}
 Q_INVOKABLE void login(QString url,QString tenant,QString branch,QString user,QString password);
 Q_INVOKABLE void logout();
 Q_INVOKABLE void business();
 Q_INVOKABLE void syncBusiness(bool joinEmpty=false);
 Q_INVOKABLE void filter(QString table,QString query,int page);
 Q_INVOKABLE void radius(bool sessions);
 Q_INVOKABLE void diagnose();
 Q_INVOKABLE void exportCsv(QUrl path);
 Q_INVOKABLE void router(QString url,QString user,QString password,QString menu);
 Q_INVOKABLE void command(QString value);
 Q_INVOKABLE void connectRouter(QString host,int port,QString user,QString password,QString protocol,QString menu);
 Q_INVOKABLE void previewCommand(QString value);
 Q_INVOKABLE void executePreview();
 Q_INVOKABLE void discoverRouters();
 Q_INVOKABLE void saveRouterProfile(QString name,QString branch,QString host,int port,QString user,QString protocol);
 Q_INVOKABLE void deleteRouterProfile(QString id);
 Q_INVOKABLE void admin(QString menu,QString action,QString id,QString json);
 Q_INVOKABLE void clearTerminal();
 Q_INVOKABLE void clearView();
 Q_INVOKABLE void smoke();
signals: void changed();
private:
 Business *commerce=nullptr;QString serverTenant,serverBranch,serverRole;
 RouterClient routerClient; QString m_preview,pendingCommand;
 QNetworkAccessManager net; bool m_busy=false,workflowBusy=false; QString token,origin,m_status,m_scope,m_terminal,routerOrigin,routerAuth;
 QDateTime expires; QJsonArray records,matching; QVariantList m_rows; QStringList m_columns;
 void reset(); void showRows(QJsonArray data);
 void request(QString url,QByteArray method,QJsonObject body,QByteArray auth,std::function<void(QJsonValue)> done);
 void api(QString path,QByteArray method,QJsonObject body,std::function<void(QJsonValue)> done);
};
