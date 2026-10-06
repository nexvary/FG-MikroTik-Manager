#include "BusinessCsv.hpp"
#include <QCryptographicHash>
#include <QUuid>
#include <stdexcept>
namespace {void require(bool ok,const char *message){if(!ok)throw std::runtime_error(message);}}
QList<QStringList> BusinessCsv::parse(QString text){
 require(text.size()<=2000000,"FILE_TOO_LARGE");if(text.startsWith(QChar(0xfeff)))text.remove(0,1);
 QList<QStringList> rows;QStringList row;QString cell;bool quoted=false,closed=false;
 auto field=[&]{require(cell.size()<=2000,"INVALID_CSV");row.append(cell);cell.clear();closed=false;};
 auto record=[&]{field();rows.append(row);row.clear();require(rows.size()<=1001,"IMPORT_LIMIT");};
 for(int i=0;i<text.size();i++){auto c=text[i];if(quoted){if(c=='"'){if(i+1<text.size()&&text[i+1]=='"'){cell+='"';i++;}else{quoted=false;closed=true;}}else cell+=c;}else if(c=='"'){require(cell.isEmpty()&&!closed,"INVALID_CSV");quoted=true;}else if(c==',')field();else if(c=='\r'||c=='\n'){record();if(c=='\r'&&i+1<text.size()&&text[i+1]=='\n')i++;}else{require(!closed,"INVALID_CSV");cell+=c;}require(cell.size()<=2000&&row.size()<=20,"INVALID_CSV");}
 require(!quoted,"INVALID_CSV");if(!cell.isEmpty()||!row.isEmpty()||closed)record();return rows;
}
QString BusinessCsv::line(QStringList cells,QList<int> numeric){for(int i=0;i<cells.size();i++){auto value=cells[i],trim=value.trimmed();bool integer=numeric.contains(i)&&!value.isEmpty();int start=value.startsWith('-')?1:0;if(start==value.size())integer=false;for(int n=start;n<value.size();n++)if(value[n]<'0'||value[n]>'9')integer=false;if(!integer&&((!trim.isEmpty()&&QString("=+-@").contains(trim[0]))||value.startsWith('\t')||value.startsWith('\r')))value.prepend('\'');value.replace('"',"\"\"");cells[i]='"'+value+'"';}return cells.join(',')+"\r\n";}
QString BusinessCsv::stableId(QByteArray bytes){auto hash=QCryptographicHash::hash(bytes,QCryptographicHash::Md5);hash[6]=char((quint8(hash[6])&15)|0x30);hash[8]=char((quint8(hash[8])&63)|0x80);return QUuid::fromRfc4122(hash).toString(QUuid::WithoutBraces);}
