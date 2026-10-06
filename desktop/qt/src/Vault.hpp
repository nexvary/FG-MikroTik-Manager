#pragma once
#include <QByteArray>
#include <QString>
namespace Vault {
 QByteArray derive(const QString &password,const QByteArray &salt);
 QByteArray protect(const QByteArray &clear);
 QByteArray unprotect(const QByteArray &cipher);
 QByteArray encrypt(const QByteArray &clear,const QString &password);
 QByteArray decrypt(const QByteArray &cipher,const QString &password);
}
