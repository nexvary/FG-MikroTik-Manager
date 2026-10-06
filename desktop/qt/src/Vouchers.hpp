#pragma once
#include <QObject>
#include <QJsonObject>
#include <QJsonArray>
#include <QVariantList>
#include <QImage>
#include <QUrl>
#include <QSet>
#include "RouterClient.hpp"
class QPainter;
class Vouchers:public QObject{
 Q_OBJECT
 Q_PROPERTY(QVariantList cards READ cards NOTIFY changed)
 Q_PROPERTY(QVariantList archive READ archive NOTIFY changed)
 Q_PROPERTY(int archivePage READ archivePage NOTIFY changed)
 Q_PROPERTY(int archiveCount READ archiveCount NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
 Q_PROPERTY(QVariantList profiles READ profiles NOTIFY changed)
 Q_PROPERTY(QVariantList servers READ servers NOTIFY changed)
public:
 explicit Vouchers(RouterClient *router,QObject *parent=nullptr,QString archiveDirectory={});
 QVariantList cards()const{return authorization()?batch["vouchers"].toArray().toVariantList():QVariantList{};} QVariantList archive()const;QString status()const{return message;}bool busy()const{return working;}
 int archivePage()const{return page;}int archiveCount()const{try{return authorization()?ids().size():0;}catch(...){return 0;}}
 Q_INVOKABLE void setArchivePage(int value);
 Q_INVOKABLE QString shareCard(int index,bool arabic=true);
 static QString shareText(QJsonObject card,bool arabic=true);
 Q_INVOKABLE void loadProfiles(QString mode);
 QVariantList profiles()const;QVariantList servers()const;
 Q_INVOKABLE QString previewCard(int index)const;
 Q_INVOKABLE void selectCard(int index);
 void setAuthorization(std::function<bool()> check){authorization=std::move(check);}
 static QJsonObject generateBatch(QJsonObject request);
 static QJsonObject portableBatch(QJsonObject batch);
 static QString quote(QString value);
 static QString script(const QJsonObject &batch);
 static QString qrPayload(const QJsonObject &voucher);
 static QImage qr(const QString &payload,int size=320);
 Q_INVOKABLE void generate(QJsonObject request);
 Q_INVOKABLE void activate();
 Q_INVOKABLE void openBatch(QString id);
 Q_INVOKABLE void exportFile(QUrl path,QString format,QString paper="A4",bool selectedOnly=false);
 Q_INVOKABLE void print(QString paper="A4",bool selectedOnly=false);
 Q_INVOKABLE void exportArchive(QUrl path,QString password);
 Q_INVOKABLE void importArchive(QUrl path,QString password);
signals:void changed();
private:
 std::function<bool()> authorization=[](){return true;};
 RouterClient *router;QJsonObject batch;QString batchId,message;bool working=false;QString directory;int page=0,selectedCard=0;QJsonArray ids()const;void commitIds(QJsonArray ids);
 void save();void provision(int index,QString menu,QSet<QString> existing);
 void expire(QJsonObject voucher,QString id,std::function<void(RouterReply)> done);
 QString catalogIdentity,catalogMode;QJsonArray profileCatalog,serverCatalog;
 static void paintCard(QPainter &p,QRectF rect,QJsonObject card);
 void render(class QPagedPaintDevice &device,QString paper,bool selectedOnly=false);
};
