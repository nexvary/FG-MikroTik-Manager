#include <QApplication>
#include "Vouchers.hpp"
#include "Business.hpp"
#include "RouterTools.hpp"
#include "Monitor.hpp"
#include "NetworkBilling.hpp"
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QTimer>
#include <QIcon>
#include <QDir>
#include <QStandardPaths>
#include <QDate>
#include <memory>
#include "Bridge.hpp"
int main(int argc,char **argv){QApplication app(argc,argv);if(app.arguments().contains("--smoke-test"))QStandardPaths::setTestModeEnabled(true);app.setOrganizationName("FG Machines");app.setApplicationName("FG MTM");app.setWindowIcon(QIcon("qrc:/packaging/fg-mtm.png"));Business commerce(app.arguments().contains("--smoke-test")?":memory:":QString{});Bridge bridge;bridge.setCommerce(&commerce);Monitor monitor;monitor.setAuthorization([&]{return commerce.allowed("ROUTER");});Vouchers vouchers(bridge.routerTransport());bridge.routerTransport()->setAuthorization([&]{return commerce.allowed("ROUTER");});bridge.routerTransport()->setAudit([&](QString command){return commerce.beginRouterAttempt(command);},[&](QJsonObject attempt,QString status){commerce.finishRouterAttempt(attempt,status);});vouchers.setAuthorization([&]{return commerce.allowed("VOUCHERS");});QObject::connect(&commerce,&Business::changed,&vouchers,&Vouchers::changed);RouterTools routerTools(bridge.routerTransport());QObject::connect(&commerce,&Business::changed,&routerTools,&RouterTools::changed);NetworkBilling networkBilling(bridge.routerTransport(),&commerce);auto workflow=[&]{bridge.setWorkflowBusy(routerTools.busy()||networkBilling.busy()||vouchers.busy());};QObject::connect(&routerTools,&RouterTools::changed,&bridge,workflow);QObject::connect(&networkBilling,&NetworkBilling::changed,&bridge,workflow);QObject::connect(&vouchers,&Vouchers::changed,&bridge,workflow);QQmlApplicationEngine engine;engine.rootContext()->setContextProperty("networkBilling",&networkBilling);engine.rootContext()->setContextProperty("monitor",&monitor);engine.rootContext()->setContextProperty("routerTools",&routerTools);engine.rootContext()->setContextProperty("vouchers",&vouchers);engine.rootContext()->setContextProperty("commerce",&commerce);engine.rootContext()->setContextProperty("backend",&bridge);engine.load(QUrl("qrc:/qml/Main.qml"));if(engine.rootObjects().isEmpty())return 1;auto mainWindow=qobject_cast<QQuickWindow*>(engine.rootObjects().first());QObject::connect(&monitor,&Monitor::showWindow,mainWindow,[mainWindow]{mainWindow->show();mainWindow->raise();mainWindow->requestActivate();});
 if(app.arguments().contains("--smoke-test")){
  for(int i=1;i<=51;i++)commerce.perform("subscriber",{{"id",QString::number(i)},{"name",QString("مشترك تجريبي %1 • Demo customer %1").arg(i)},{"service","HOTSPOT"},{"account",QString("customer%1").arg(i)},{"currency","EGP"}});
  commerce.perform("plan",{{"id","basic"},{"name","الاشتراك الشهري • Monthly plan"},{"service","HOTSPOT"},{"currency","EGP"},{"price","150.00"},{"days",30}});
  commerce.perform("renew",{{"id","demo-invoice"},{"subscriber_id","1"},{"plan_id","basic"},{"paid","100.00"},{"method","INSTAPAY"},{"reference","DEMO-001"}});
  commerce.perform("expense",{{"id","demo-expense"},{"amount","25.00"},{"currency","EGP"},{"category","Maintenance"},{"note","مصروف تجريبي • Demo expense"}});
  commerce.browse("subscribers");if(commerce.rows().size()!=50)return 9;
  vouchers.generate({{"quantity",8},{"mode","OFFLINE"},{"usernameLength",8},{"durationValue",60},{"durationUnit","MINUTES"},{"branding",QJsonObject{{"networkName","FG Machines WiFi"},{"supportPhone","01234567890"},{"priceText","10 EGP"}}}});if(vouchers.cards().size()!=8)return 10;
  auto today=QDate::currentDate().toString("yyyy-MM-dd");commerce.report(today,today);
  bridge.smoke();if(bridge.rows().size()!=50)return 3;bridge.filter("subscribers","Customer 51",0);if(bridge.rows().size()!=1)return 4;bridge.filter("subscribers","",1);if(bridge.rows().size()!=1)return 5;bridge.filter("subscribers","",0);auto w=qobject_cast<QQuickWindow*>(engine.rootObjects().first());
  if(!w)return 1;
  if(!QMetaObject::invokeMethod(w,"showOnline")||w->property("page").toString()!="tools"||w->property("toolsTab").toInt()!=2||!w->property("onlineOnly").toBool())return 12;
  if(!QMetaObject::invokeMethod(w,"goBack")||w->property("page").toString()!="home")return 13;
  QDir().mkpath("qt-proof");
  auto index=std::make_shared<int>(0);auto step=std::make_shared<std::function<void()>>();
  *step=[&,w,index,step]{
   const QStringList pages{"home","settings","business","radius","router","diagnostics","commerce","vouchers","tools","monitor","transfer","billing","about"};
   if(*index>=pages.size()*2+4){app.exit(0);return;}
   const int extra=*index-pages.size()*2;const bool terminal=extra==0;const bool ar=*index>=pages.size();auto page=terminal?QString("terminal"):extra==1?QString("business-editor"):extra==2?QString("voucher-preview"):extra==3?QString("router-editor"):pages[*index%pages.size()];
   if(terminal){if(!QMetaObject::invokeMethod(w,"showTerminal")){app.exit(7);return;}}
   else if(extra>0){QMetaObject::invokeMethod(w,"closeReviewDialogs");w->setProperty("page",extra==1?"commerce":extra==2?"vouchers":"router");const char *method=extra==1?"showBusinessEditor":extra==2?"showVoucherPreview":"showRouterEditor";if(!QMetaObject::invokeMethod(w,method)){app.exit(11);return;}}
   else{w->setProperty("arabic",ar);w->setProperty("page",page);bridge.clearView();if(page=="business")bridge.filter("subscribers","",0);}
   QTimer::singleShot(800,&app,[&,w,index,step,page,ar,terminal]{
    bool saved=false;
    if(terminal){for(auto window:QGuiApplication::allWindows()){auto view=qobject_cast<QQuickWindow*>(window);if(view&&view!=w&&view->isVisible())saved=view->grabWindow().save("qt-proof/terminal-ar.png")||saved;}}
    else saved=w->grabWindow().save("qt-proof/"+page+(ar?"-ar.png":"-en.png"));
    if(!saved){app.exit(8);return;}
    ++*index;QTimer::singleShot(0,&app,*step);
   });
  };QTimer::singleShot(500,&app,*step);

 }
 return app.exec();}
