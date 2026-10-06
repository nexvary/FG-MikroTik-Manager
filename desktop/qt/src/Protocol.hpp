#pragma once
#include <QUrl>
#include <QJsonValue>
#include <QJsonArray>
#include <QStringList>
namespace Protocol {
bool validOrigin(const QString &value);
QString csvCell(QString value);
QString jsonText(const QJsonValue &value);
QJsonValue redact(const QJsonValue &value);
QString menuForCommand(QString command);
QStringList menus();
QJsonArray modules();
}
