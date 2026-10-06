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
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
public:
 explicit Vouchers(RouterClient *router,QObject *parent=nullptr);
 QVariantList cards()const{return batch["vouchers"].toArray().toVariantList();} QVariantList archive()const;QString status()const{return message;}bool busy()const{return working;}
 static QJsonObject generateBatch(QJsonObject request);
 static QString quote(QString value);
 static QString script(const QJsonObject &batch);
 static QString qrPayload(const QJsonObject &voucher);
 static QImage qr(const QString &payload,int size=320);
 Q_INVOKABLE void generate(QJsonObject request);
 Q_INVOKABLE void activate();
 Q_INVOKABLE void openBatch(QString id);
 Q_INVOKABLE void exportFile(QUrl path,QString format,QString paper="A4");
 Q_INVOKABLE void print(QString paper="A4");
 Q_INVOKABLE void exportArchive(QUrl path,QString password);
 Q_INVOKABLE void importArchive(QUrl path,QString password);
signals:void changed();
private:
 RouterClient *router;QJsonObject batch;QString batchId,message;bool working=false;QString directory;
 void save();void provision(int index,QString menu,QSet<QString> existing);
 void expire(QJsonObject voucher,QString id,std::function<void(RouterReply)> done);
 static void paintCard(QPainter &p,QRectF rect,QJsonObject card);
 void render(class QPagedPaintDevice &device,QString paper);
};
