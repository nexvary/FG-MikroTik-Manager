#include <QtTest>
#include <QSet>
#include <QFile>
#include "Vouchers.hpp"
#include "Vault.hpp"
class VoucherTests:public QObject{Q_OBJECT
private slots:
 void generation(){auto batch=Vouchers::generateBatch({{"quantity",5000},{"usernameLength",8},{"passwordLength",10},{"passwordMode","RANDOM"},{"profile","default"},{"mode","HOTSPOT"},{"durationValue",2},{"durationUnit","HOURS"},{"limitBytesTotal","104857600"}});auto cards=batch["vouchers"].toArray();QCOMPARE(cards.size(),5000);QSet<QString> names;for(auto c:cards){auto o=c.toObject();names.insert(o["username"].toString());QCOMPARE(o["password"].toString().size(),10);QCOMPARE(o["limitUptime"].toString(),QString("2h"));QCOMPARE(o["limitBytesTotal"].toInteger(),qint64(104857600));}QCOMPARE(names.size(),5000);QVERIFY_EXCEPTION_THROWN(Vouchers::generateBatch({{"quantity",5001},{"mode","OFFLINE"}}),std::runtime_error);QVERIFY_EXCEPTION_THROWN(Vouchers::generateBatch({{"quantity",1},{"profile",""},{"mode","HOTSPOT"}}),std::runtime_error);}
 void scriptEscapes(){auto batch=Vouchers::generateBatch({{"quantity",1},{"profile","x\"; /system reboot"},{"mode","HOTSPOT"},{"prefix","$"}});auto script=Vouchers::script(batch);QVERIFY(script.contains("profile=\"x\\\"; /system reboot\""));QVERIFY(script.contains("name=\"\\$"));QVERIFY(!script.contains('\r'));auto offline=Vouchers::generateBatch({{"quantity",1},{"mode","OFFLINE"}});QVERIFY(Vouchers::script(offline).isEmpty());}
 void qrOutput(){auto batch=Vouchers::generateBatch({{"quantity",1},{"mode","OFFLINE"}});auto card=batch["vouchers"].toArray().first().toObject();auto payload=Vouchers::qrPayload(card);QVERIFY(payload.contains("NOT activated"));auto image=Vouchers::qr(payload);QVERIFY(!image.isNull());QVERIFY(image.save("voucher-qr-test.png"));QFile file("voucher-qr-test.txt");QVERIFY(file.open(QIODevice::WriteOnly));file.write(payload.toUtf8());}
#ifdef Q_OS_WIN
 void portableBackup(){QFile file(":/tests/backup-vector.hex");QVERIFY(file.open(QIODevice::ReadOnly));auto fixture=QByteArray::fromHex(file.readAll());auto clear=Vault::decrypt(fixture,QString::fromUtf8("Arabic-كلمة-123"));QCOMPARE(clear,QByteArray("{\"format\":\"FG-MTM-vouchers\",\"schema\":1,\"batches\":[]}"));auto cipher=Vault::encrypt(clear,"long-password-123");QCOMPARE(Vault::decrypt(cipher,"long-password-123"),clear);QVERIFY_EXCEPTION_THROWN(Vault::decrypt(cipher,"wrong-password-123"),std::runtime_error);cipher[40]=char(cipher[40]^1);QVERIFY_EXCEPTION_THROWN(Vault::decrypt(cipher,"long-password-123"),std::runtime_error);QCOMPARE(Vault::unprotect(Vault::protect(clear)),clear);}
#endif
};
QTEST_MAIN(VoucherTests)
#include "test_vouchers.moc"
