#include "Monitor.hpp"
#include <QIcon>
#include <QDateTime>
#include <QMenu>
#include <QCoreApplication>
#include <algorithm>
QString MonitorHealth::sample(qint64 now,int cpu,bool success){
 failures=success?0:failures+1;QString next=failures>=2?QString("UNREACHABLE"):!success?incident:cpu>=85?QString("HIGH_CPU"):QString{};
 const bool changed=next!=incident,repeat=!next.isEmpty()&&lastAlert>=0&&now-lastAlert>=300000;
 QString event=changed&&!next.isEmpty()?next:changed&&!incident.isEmpty()?"RESOLVED:"+incident:repeat?next:QString{};
 if(success)lastSuccess=now;incident=next;if(!event.isEmpty())lastAlert=now;return event;
}
Monitor::Monitor(QObject*p):QObject(p),tray(QIcon("qrc:/packaging/fg-mtm.png"),this){
 clock.start();timer.setInterval(1000);connect(&timer,&QTimer::timeout,this,&Monitor::tick);timer.start();
 trayMenu=std::make_unique<QMenu>();trayMenu->addAction("فتح FG MTM / Open FG MTM",this,[this]{emit showWindow();});trayMenu->addAction("إيقاف المراقبة / Stop monitoring",this,&Monitor::stopAll);trayMenu->addAction("خروج / Quit",QCoreApplication::instance(),&QCoreApplication::quit);tray.setContextMenu(trayMenu.get());
 tray.setToolTip("FG MTM • Network monitor");connect(&tray,&QSystemTrayIcon::activated,this,[this](QSystemTrayIcon::ActivationReason r){if(r==QSystemTrayIcon::Trigger||r==QSystemTrayIcon::DoubleClick)emit showWindow();});
}
Monitor::~Monitor(){timer.stop();stopAll();}
QVariantList Monitor::routers()const{QVariantList result;for(const auto &[id,e]:entries){auto row=e->profile;row["checking"]=e->checking;row["stale"]=e->health.stale(clock.elapsed());row["incident"]=e->health.incident;row["failures"]=e->health.failures;row["error"]=e->error;row["snapshot"]=e->snapshot.toVariantMap();result.append(row);}return result;}
void Monitor::start(QVariantMap profile,QString password){
 if(!authorization()){message="صلاحية إدارة الراوتر مطلوبة • Router permission required";emit changed();return;}
 const auto id=profile["id"].toString();if(id.isEmpty()||profile["host"].toString().isEmpty()||password.isEmpty()){message="اختر راوترًا وأدخل كلمة المرور • Select a router and enter its password";emit changed();return;}
 if(entries.count(id)){message="هذا الراوتر مراقب بالفعل • Router already monitored";emit changed();return;}
 if(entries.size()>=4){message="الحد أربعة راوترات • Maximum four monitored routers";emit changed();return;}
 auto e=std::make_shared<Entry>();e->profile=profile;e->profile.remove("password");e->client=std::make_unique<RouterClient>();e->client->setAuthorization([this]{return authorization();});
 e->client->configure(profile["host"].toString(),profile["port"].toInt(),profile["user"].toString(),password,profile["protocol"].toString());password.fill(QChar(0));entries[id]=e;
 if(QSystemTrayIcon::isSystemTrayAvailable())tray.show();message="بدأت المراقبة • Monitoring started";emit changed();tick();
}
void Monitor::stop(QString id){auto it=entries.find(id);if(it==entries.end())return;auto e=it->second;entries.erase(it);e->client->close();if(entries.empty()){tray.hide();emit showWindow();}emit changed();}
void Monitor::stopAll(){while(!entries.empty())stop(entries.begin()->first);message="توقفت المراقبة • Monitoring stopped";emit changed();}
void Monitor::tick(){if(!entries.empty()&&!authorization()){stopAll();message="انتهت صلاحية المراقبة • Monitoring authorization expired";emit changed();return;}QList<std::shared_ptr<Entry>> ready;for(auto &[id,e]:entries)if(!e->checking&&e->next<=clock.elapsed())ready.append(e);for(auto e:ready){if(running>=2)break;poll(e);}emit changed();}
void Monitor::poll(std::shared_ptr<Entry> e){e->checking=true;e->stage=0;e->snapshot={};e->error.clear();running++;read(e);}
void Monitor::read(std::shared_ptr<Entry> e){static const QStringList menus{"system/identity","system/resource","interface"};e->client->read(menus[e->stage],[this,e](RouterReply reply){
 if(!entries.count(e->profile["id"].toString())){complete(e,false);return;}
 if(!reply.ok()){e->error=reply.error;complete(e,false);return;}
 if(e->stage==0){if(reply.rows.isEmpty()){e->error="IDENTITY_MISSING";complete(e,false);return;}e->snapshot["identity"]=reply.rows[0].toObject()["name"];}
 if(e->stage==1){if(reply.rows.isEmpty()){e->error="RESOURCE_MISSING";complete(e,false);return;}e->snapshot["resource"]=reply.rows[0];}
 if(e->stage==2)e->snapshot["interfaces"]=reply.rows;
 if(++e->stage<3)read(e);else complete(e,true);
 });}
void Monitor::complete(std::shared_ptr<Entry> e,bool success){if(!e->checking)return;e->checking=false;running=std::max(0,running-1);if(!entries.count(e->profile["id"].toString()))return;e->next=clock.elapsed()+30000;bool cpuOk=false;int cpu=e->snapshot["resource"].toObject()["cpu-load"].toVariant().toString().toInt(&cpuOk);auto event=e->health.sample(clock.elapsed(),cpuOk?cpu:-1,success);if(!event.isEmpty()){QVariantMap item{{"router",e->profile["name"]},{"event",event},{"time",QDateTime::currentDateTime().toString(Qt::ISODate)}};alerts.prepend(item);while(alerts.size()>100)alerts.removeLast();tray.showMessage("FG MTM • "+e->profile["name"].toString(),event,event.startsWith("RESOLVED")?QSystemTrayIcon::Information:QSystemTrayIcon::Warning,10000);}emit changed();}
