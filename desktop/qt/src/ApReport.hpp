#pragma once
#include <QJsonArray>
#include <QString>
namespace ApReport { bool xlsx(QString path,const QJsonArray& summary,const QJsonArray& observations);bool pdf(QString path,const QJsonArray& summary,QString period); }
