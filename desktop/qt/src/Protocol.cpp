#include <QFile>
#include <QJsonDocument>
#include <QJsonArray>
#include "Protocol.hpp"
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QRegularExpression>
bool Protocol::validOrigin(const QString &value) {
 const QUrl u(value,QUrl::StrictMode);
 return u.isValid() && u.scheme()=="https" && !u.host().isEmpty() && u.userInfo().isEmpty() && !u.hasQuery() && !u.hasFragment() && (u.path().isEmpty() || u.path()=="/");
}
QString Protocol::csvCell(QString value) {
 auto trimmed=value.trimmed();
 if ((!trimmed.isEmpty() && QString("=+-@").contains(trimmed.front())) || value.startsWith('\t') || value.startsWith('\r')) value.prepend('\'');
 value.replace('"',"\"\""); return '"'+value+'"';
}
QString Protocol::jsonText(const QJsonValue &value) {
 if(value.isString()) return value.toString();
 if(value.isUndefined()) return {};
 auto text=QJsonDocument(QJsonArray{value}).toJson(QJsonDocument::Compact);
 return QString::fromUtf8(text.mid(1,text.size()-2));
}
QJsonValue Protocol::redact(const QJsonValue &value) {
 if(value.isArray()) {QJsonArray a;for(auto v:value.toArray()) a.append(redact(v));return a;}
 if(value.isObject()) {QJsonObject o;const auto source=value.toObject();for(auto i=source.begin();i!=source.end();++i) {
  auto k=i.key().toLower();if(k.contains("password")||k.contains("secret")||k.contains("private-key")||k.contains("preshared-key")||k.contains("community"))continue;
  o.insert(i.key(),redact(i.value()));
 }return o;}return value;
}
QJsonArray Protocol::modules(){QFile file(":/resources/router-modules.json");if(!file.open(QIODevice::ReadOnly))return {};return QJsonDocument::fromJson(file.readAll()).array();}
QStringList Protocol::menus(){QStringList result;for(auto item:modules())result.append(item.toObject()["menu"].toString());return result;}
QString Protocol::menuForCommand(QString command) {
 command=command.trimmed();if(!command.endsWith(" print"))return {};
 command.chop(6);if(command.startsWith('/'))command.remove(0,1);
 command.replace(QRegularExpression("\\s+"),"/");
 return menus().contains(command)?command:QString{};
}
