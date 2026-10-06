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
#include <memory>
#include "Bridge.hpp"
int main(int argc,char **argv){QApplication app(argc,argv);app.setOrganizationName("FG Machines");app.setApplicationName("FG MTM");app.setWindowIcon(QIcon("qrc:/packaging/fg-mtm.png"));Business commerce(app.arguments().contains("--smoke-test")?":memory:":QString{});Bridge bridge;bridge.setCommerce(&commerce);Monitor monitor;monitor.setAuthorization([&]{return commerce.allowed("ROUTER");});Vouchers vouchers(bridge.routerTransport());bridge.routerTransport()->setAuthorization([&]{return commerce.allowed("ROUTER");});vouchers.setAuthorization([&]{return commerce.allowed("VOUCHERS");});QObject::connect(&commerce,&Business::changed,&vouchers,&Vouchers::changed);RouterTools routerTools(bridge.routerTransport());NetworkBilling networkBilling(bridge.routerTransport(),&commerce);auto workflow=[&]{bridge.setWorkflowBusy(routerTools.busy()||networkBilling.busy());};QObject::connect(&routerTools,&RouterTools::changed,&bridge,workflow);QObject::connect(&networkBilling,&NetworkBilling::changed,&bridge,workflow);QQmlApplicationEngine engine;engine.rootContext()->setContextProperty("networkBilling",&networkBilling);engine.rootContext()->setContextProperty("monitor",&monitor);engine.rootContext()->setContextProperty("routerTools",&routerTools);engine.rootContext()->setContextProperty("vouchers",&vouchers);engine.rootContext()->setContextProperty("commerce",&commerce);engine.rootContext()->setContextProperty("backend",&bridge);engine.load(QUrl("qrc:/qml/Main.qml"));if(engine.rootObjects().isEmpty())return 1;auto mainWindow=qobject_cast<QQuickWindow*>(engine.rootObjects().first());QObject::connect(&monitor,&Monitor::showWindow,mainWindow,[mainWindow]{mainWindow->show();mainWindow->raise();mainWindow->requestActivate();});
 if(app.arguments().contains("--smoke-test")){
  bridge.smoke();if(bridge.rows().size()!=50)return 3;bridge.filter("subscribers","Customer 51",0);if(bridge.rows().size()!=1)return 4;bridge.filter("subscribers","",1);if(bridge.rows().size()!=1)return 5;bridge.filter("subscribers","",0);auto w=qobject_cast<QQuickWindow*>(engine.rootObjects().first());
  if(!w)return 1;QDir().mkpath("qt-proof");
  auto index=std::make_shared<int>(0);auto step=std::make_shared<std::function<void()>>();
  *step=[&,w,index,step]{
   const QStringList pages{"home","settings","business","radius","router","diagnostics","commerce","vouchers","tools","monitor","transfer","billing"};
   if(*index>=pages.size()*2+1){app.exit(0);return;}
   const bool terminal=*index==pages.size()*2;const bool ar=*index>=pages.size();auto page=terminal?QString("terminal"):pages[*index%pages.size()];
   if(terminal){if(!QMetaObject::invokeMethod(w,"showTerminal")){app.exit(7);return;}}
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
