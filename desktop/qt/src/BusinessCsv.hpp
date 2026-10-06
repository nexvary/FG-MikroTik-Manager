#pragma once
#include <QStringList>
#include <QList>
namespace BusinessCsv {
 QList<QStringList> parse(QString text);
 QString line(QStringList cells,QList<int> numeric={});
 QString stableId(QByteArray text);
}
