#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QTimer>
#include <QIcon>
#include <QDir>
#include <memory>
#include "Bridge.hpp"
int main(int argc,char **argv){QGuiApplication app(argc,argv);app.setOrganizationName("FG Machines");app.setApplicationName("FG MTM");app.setWindowIcon(QIcon("qrc:/packaging/fg-mtm.png"));Bridge bridge;QQmlApplicationEngine engine;engine.rootContext()->setContextProperty("backend",&bridge);engine.load(QUrl("qrc:/qml/Main.qml"));if(engine.rootObjects().isEmpty())return 1;
 if(app.arguments().contains("--smoke-test")){
  bridge.smoke();auto w=qobject_cast<QQuickWindow*>(engine.rootObjects().first());
  if(!w)return 1;QDir().mkpath("qt-proof");
  auto index=std::make_shared<int>(0);auto timer=new QTimer(&app);timer->setInterval(250);
  QObject::connect(timer,&QTimer::timeout,&app,[&,w,index,timer]{
   const QStringList pages{"home","settings","business","radius","router","diagnostics"};
   if(*index>=12){timer->stop();app.exit(0);return;}
   const bool ar=*index>=6;auto page=pages[*index%6];w->setProperty("arabic",ar);w->setProperty("page",page);bridge.clearView();if(page=="business")bridge.filter("subscribers","",0);
   QTimer::singleShot(120,&app,[&,w,page,ar]{if(!w->grabWindow().save("qt-proof/"+page+(ar?"-ar.png":"-en.png")))app.exit(2);});
   ++*index;
  });timer->start();
 }
 return app.exec();}
