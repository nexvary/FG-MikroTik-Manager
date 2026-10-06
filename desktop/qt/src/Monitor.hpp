#pragma once
#include "RouterClient.hpp"
#include <QVariantList>
#include <QElapsedTimer>
#include <QSystemTrayIcon>
#include <map>
#include <memory>
class QMenu;
struct MonitorHealth {
 qint64 lastSuccess=-1,lastAlert=-1;int failures=0;QString incident;
 bool stale(qint64 now)const{return lastSuccess<0||now-lastSuccess>90000;}
 QString sample(qint64 now,int cpu,bool success);
};
class Monitor:public QObject {
 Q_OBJECT
 Q_PROPERTY(QVariantList routers READ routers NOTIFY changed)
 Q_PROPERTY(QVariantList events READ events NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(bool background READ background WRITE setBackground NOTIFY changed)
 Q_PROPERTY(bool keepRunning READ keepRunning NOTIFY changed)
 Q_PROPERTY(bool trayAvailable READ trayAvailable CONSTANT)
 Q_PROPERTY(bool active READ active NOTIFY changed)
public:
 explicit Monitor(QObject *parent=nullptr);~Monitor();
 void setAuthorization(std::function<bool()> check){authorization=std::move(check);}
 QVariantList routers()const;QVariantList events()const{return alerts;}
 QString status()const{return message;}bool active()const{return !entries.empty();}
 bool background()const{return backgroundRequested;}bool trayAvailable()const{return QSystemTrayIcon::isSystemTrayAvailable();}bool keepRunning()const{return backgroundRequested&&active()&&trayAvailable();}
 void setBackground(bool value){backgroundRequested=value;emit changed();}
 Q_INVOKABLE void start(QVariantMap profile,QString password);
 Q_INVOKABLE void stop(QString id);Q_INVOKABLE void stopAll();
signals:void changed();void showWindow();
private:
 struct Entry {QVariantMap profile;std::unique_ptr<RouterClient> client;MonitorHealth health;QJsonObject snapshot;bool checking=false;int stage=0; qint64 next=0;QString error;};
 std::map<QString,std::shared_ptr<Entry>> entries;QVariantList alerts;QString message;
 QElapsedTimer clock;QTimer timer;QSystemTrayIcon tray;std::unique_ptr<QMenu> trayMenu;bool backgroundRequested=false;int running=0;
 std::function<bool()> authorization=[](){return true;};
 void tick();void poll(std::shared_ptr<Entry> entry);void read(std::shared_ptr<Entry> entry);void complete(std::shared_ptr<Entry> entry,bool success);
};
