#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QTimer>
#include "Bridge.hpp"
int main(int argc,char **argv){QGuiApplication app(argc,argv);app.setOrganizationName("FG Machines");app.setApplicationName("FG MTM");Bridge bridge;QQmlApplicationEngine engine;engine.rootContext()->setContextProperty("backend",&bridge);engine.load(QUrl("qrc:/qml/Main.qml"));if(engine.rootObjects().isEmpty())return 1;
 if(app.arguments().contains("--smoke-test")){bridge.smoke();QTimer::singleShot(1500,&app,[&]{auto w=qobject_cast<QQuickWindow*>(engine.rootObjects().first());if(!w||!w->grabWindow().save("FG-MTM-Qt.png")){app.exit(2);return;}app.exit(0);});}
 return app.exec();}
