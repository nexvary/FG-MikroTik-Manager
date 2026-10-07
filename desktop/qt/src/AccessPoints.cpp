#include "AccessPoints.hpp"
#include "ApReport.hpp"
#include <QRegularExpression>
#include <QSet>
#include <QSqlQuery>
#include <QSqlError>
#include <QJsonDocument>
#include <QStandardPaths>
#include <QDir>
#include <QUuid>
#include <QDateTime>
#include <QSaveFile>
namespace {
QString text(QJsonObject r,QString key){return r.value(key).toVariant().toString().trimmed();}
bool yes(QJsonObject r,QString key){return QStringList{"true","yes"}.contains(text(r,key));}
QStringList tokens(QString value){return value.toLower().split(QRegularExpression("[,; ]+"),Qt::SkipEmptyParts);}
QString first(QJsonObject row,QStringList keys){for(auto k:keys)if(!text(row,k).isEmpty())return text(row,k);return {};}
}
QStringList AccessPointEngine::menus(){return {"ip/neighbor","ip/arp","ip/dhcp-server/lease","interface/bridge/host","interface/ethernet","ip/hotspot/active","ip/hotspot/host"};}
QString AccessPointEngine::mac(QString s){s=s.trimmed().toUpper().replace('-',':');if(!QRegularExpression("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$").match(s).hasMatch()||s=="00:00:00:00:00:00"||(s.left(2).toInt(nullptr,16)&1))return {};return s;}
QJsonArray AccessPointEngine::discover(const QMap<QString,QJsonArray>& data,const QJsonObject& mappings){
 QMap<QString,QList<QPair<QString,QJsonObject>>> groups;QSet<QString> physical;
 for(auto value:data.value("interface/ethernet"))physical.insert(text(value.toObject(),"name"));
 for(auto source:menus().mid(0,4))for(auto value:data.value(source)){auto r=value.toObject();if(source=="interface/bridge/host"&&yes(r,"local"))continue;auto key=mac(first(r,{"active-mac-address","mac-address"}));if(!key.isEmpty())groups[key].append({source,r});}
 QJsonArray result;
 for(auto it=groups.begin();it!=groups.end();++it){QSet<QString> enabled,capable,ports,bridges,sources,protocols;bool platform=false;QJsonObject row;
  for(auto evidence:it.value()){auto source=evidence.first;auto r=evidence.second;sources.insert(source);
   for(auto pair:QList<QPair<QString,QStringList>>{{"name",{"identity","host-name","name"}},{"hostname",{"host-name"}},{"ip",{"active-address","address","ip-address"}},{"platform",{"platform"}}})if(text(row,pair.first).isEmpty())row[pair.first]=first(r,pair.second);
   if(source=="ip/neighbor"){for(auto t:tokens(text(r,"system-caps-enabled")))enabled.insert(t);for(auto t:tokens(text(r,"system-caps")))capable.insert(t);platform|=!text(r,"platform").isEmpty();for(auto p:text(r,"interface").split(','))if(physical.contains(p.trimmed()))ports.insert(p.trimmed());if(!text(r,"discovered-by").isEmpty())protocols.insert(text(r,"discovered-by"));}
   if(source=="interface/bridge/host"){auto p=text(r,"on-interface");if(physical.contains(p))ports.insert(p);if(!text(r,"bridge").isEmpty())bridges.insert(text(r,"bridge"));}
  }
  auto mapping=mappings[it.key()].toObject();bool manual=text(mapping,"confirmed")=="true",confirmed=false,likely=false;
  for(auto token:{"wlan-access-point","wlan","access-point"}){confirmed|=enabled.contains(token);likely|=capable.contains(token);}likely&=platform;
  row["mac"]=it.key();if(text(row,"name").isEmpty())row["name"]=it.key();row["classification"]=manual||confirmed?"Confirmed AP":likely?"Likely AP":enabled.contains("station-only")?"Other client/device":"Unknown network device";
  row["reason"]=manual?"User verified":confirmed?"LLDP enabled WLAN capability":likely?"LLDP WLAN capability and platform":"Insufficient AP evidence";
  row["port"]=ports.size()==1?*ports.begin():QString{};row["bridge"]=bridges.size()==1?*bridges.begin():QString{};auto sourceList=sources.values();sourceList.sort();row["sources"]=sourceList.join(", ");row["protocols"]=protocols.values().join(", ");row["shop"]=text(mapping,"shop");row["mode"]=text(mapping,"mode").isEmpty()?"Unknown":text(mapping,"mode");row["portConfidence"]=ports.size()==1?"Derived":"Unknown";row["directConnection"]="Unknown";row["state"]="Observed";result.append(row);
 }return result;
}
QJsonArray AccessPointEngine::correlate(const QMap<QString,QJsonArray>& data,const QJsonArray& devices){QJsonArray result;
 for(auto value:data.value("ip/hotspot/active")){auto session=value.toObject();auto client=mac(text(session,"mac-address"));QSet<QString> paths;QList<QJsonObject> candidates;
  for(auto h:data.value("interface/bridge/host")){auto host=h.toObject();if(!yes(host,"local")&&mac(text(host,"mac-address"))==client&&!text(host,"on-interface").isEmpty())paths.insert(text(host,"on-interface"));}
  if(paths.size()==1)for(auto d:devices){auto ap=d.toObject();if(text(ap,"classification")=="Confirmed AP"&&text(ap,"port")==*paths.begin())candidates.append(ap);}
  QJsonObject ap;if(candidates.size()==1&&text(candidates[0],"mode")!="NAT"&&text(candidates[0],"mac")!=client)ap=candidates[0];
  result.append(QJsonObject{{"id",text(session,".id")},{"account",text(session,"user")},{"client",client},{"ap",text(ap,"mac")},{"confidence",ap.isEmpty()?"Unknown":"Inferred"},{"upload",text(session,"bytes-in")},{"download",text(session,"bytes-out")},{"uptime",text(session,"uptime")},{"server",text(session,"server")}});
 }return result;
}
QByteArray AccessPointEngine::csv(const QJsonArray& rows){QSet<QString> keys;for(auto v:rows)for(auto k:v.toObject().keys())keys.insert(k);auto columns=keys.values();columns.sort();
 auto cell=[](QString s){if(QRegularExpression("^[=+@-]").match(s.trimmed()).hasMatch()||s.startsWith('\t')||s.startsWith('\r'))s.prepend('\'');return '"'+s.replace("\"","\"\"")+'"';};
 QString output=QChar(0xfeff);QStringList header;for(auto c:columns)header.append(cell(c));output+=header.join(',')+"\r\n";for(auto v:rows){QStringList fields;for(auto c:columns)fields.append(cell(text(v.toObject(),c)));output+=fields.join(',')+"\r\n";}return output.toUtf8();
}
AccessPoints::AccessPoints(RouterClient* t,QObject*p):QObject(p),client(t){connection="ap-"+QUuid::createUuid().toString();open();}
AccessPoints::~AccessPoints(){db.close();db=QSqlDatabase();QSqlDatabase::removeDatabase(connection);}
bool AccessPoints::open(){auto dir=QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);QDir().mkpath(dir);db=QSqlDatabase::addDatabase("QSQLITE",connection);db.setDatabaseName(dir+"/access-points.sqlite");if(!db.open()){message="Cannot open access point history";return false;}QSqlQuery q(db);return q.exec("CREATE TABLE IF NOT EXISTS mappings(scope TEXT,mac TEXT,body TEXT NOT NULL,PRIMARY KEY(scope,mac))")&&q.exec("CREATE TABLE IF NOT EXISTS observations(scope TEXT,at INTEGER,kind TEXT,identity TEXT,body TEXT NOT NULL,PRIMARY KEY(scope,at,kind,identity))")&&q.exec("CREATE INDEX IF NOT EXISTS ap_period ON observations(scope,at)");}
void AccessPoints::setScope(QString value){if(scopePrefix==value)return;scopePrefix=value;++generation;loading=false;scope.clear();rows={};reportRows={};exportRows={};data.clear();cache.invalidate();emit changed();}
int AccessPoints::confirmedCount()const{int n=0;for(auto v:rows)if(text(v.toObject(),"classification")=="Confirmed AP")++n;return n;}
QJsonObject AccessPoints::mappings(){QJsonObject result;QSqlQuery q(db);q.prepare("SELECT mac,body FROM mappings WHERE scope=?");q.addBindValue(scope);if(q.exec())while(q.next())result[q.value(0).toString()]=QJsonDocument::fromJson(q.value(1).toByteArray()).object();return result;}
void AccessPoints::refresh(){if(loading)return;if(!client->authorized()||!client->connected()){message="اتصل بالراوتر أولًا • Connect to a router first";emit changed();return;}auto key=scopePrefix+"|"+client->identityKey();if(scope!=key){scope=key;data.clear();rows={};reportRows={};exportRows={};cache.invalidate();}if(cache.isValid()&&cache.elapsed()<30000){rebuild();message="نتائج محفوظة مؤقتًا • Cached for 30 seconds";emit changed();return;}if(client->busy())return;loading=true;stage=0;warnings.clear();data.clear();message="قراءة مصادر الشبكة • Reading network evidence";emit changed();read(++generation);}
void AccessPoints::read(quint64 token){auto menu=AccessPointEngine::menus()[stage];client->read(menu,[this,token,menu](RouterReply reply){if(token!=generation)return;if(scope!=scopePrefix+"|"+client->identityKey()){loading=false;data.clear();rows={};message="Router changed; refresh again";emit changed();return;}if(reply.ok())data[menu]=reply.rows;else warnings.append(menu);if(++stage<AccessPointEngine::menus().size())read(token);else complete();});}
void AccessPoints::rebuild(){rows=AccessPointEngine::discover(data,mappings());auto sessions=AccessPointEngine::correlate(data,rows);QJsonArray next;for(auto v:rows){auto r=v.toObject();int count=0;for(auto s:sessions)if(text(s.toObject(),"ap")==text(r,"mac"))++count;r["active"]=data.contains("ip/hotspot/active")?QString::number(count):"N/A";next.append(r);}rows=next;}
void AccessPoints::complete(){loading=false;bool any=false;for(auto source:AccessPointEngine::menus().mid(0,4))any|=data.contains(source);if(!any){message="تعذر اكتشاف نقاط الوصول • Discovery unavailable";emit changed();return;}cache.start();rebuild();const auto at=QDateTime::currentMSecsSinceEpoch();bool ok=db.transaction();
 auto insert=[&](QString kind,QString id,QJsonObject row){QSqlQuery q(db);q.prepare("INSERT OR IGNORE INTO observations VALUES (?,?,?,?,?)");q.addBindValue(scope);q.addBindValue(at);q.addBindValue(kind);q.addBindValue(id);q.addBindValue(QString::fromUtf8(QJsonDocument(row).toJson(QJsonDocument::Compact)));ok=q.exec()&&ok;};
 for(auto v:rows)insert("device",text(v.toObject(),"mac"),v.toObject());int i=0;for(auto v:AccessPointEngine::correlate(data,rows)){auto r=v.toObject();insert("session",text(r,"id").isEmpty()?QString::number(i++):text(r,"id"),r);}insert("coverage","poll",{{"warnings",warnings.join(", ")}});
 QSqlQuery q(db);q.prepare("DELETE FROM observations WHERE scope=? AND at<?");q.addBindValue(scope);q.addBindValue(at-90LL*86400000);ok=q.exec()&&ok;if(ok)ok=db.commit();else db.rollback();message=!ok?"تعذر حفظ التاريخ • History could not be saved":warnings.isEmpty()?"اكتمل الرصد • Observation saved":"مصادر غير متاحة • Unavailable sources: "+warnings.join(", ");emit changed();}
void AccessPoints::saveMapping(QString mac,QString shop,bool confirmed,QString mode){if(!client->authorized()||loading||scope!=scopePrefix+"|"+client->identityKey())return;if(AccessPointEngine::mac(mac)!=mac||shop.size()>120||!QStringList{"Unknown","Bridge","NAT"}.contains(mode))return;QSqlQuery q(db);q.prepare("INSERT OR REPLACE INTO mappings VALUES (?,?,?)");q.addBindValue(scope);q.addBindValue(mac);q.addBindValue(QString::fromUtf8(QJsonDocument(QJsonObject{{"shop",shop.trimmed()},{"confirmed",confirmed?"true":"false"},{"mode",mode}}).toJson(QJsonDocument::Compact)));message=q.exec()?"تم حفظ التسمية • Mapping saved":"Mapping could not be saved";rebuild();emit changed();}
void AccessPoints::report(QString from,QString through){reportPeriod=from+" — "+through;if(!client->authorized())return;auto start=QDate::fromString(from,Qt::ISODate),end=QDate::fromString(through,Qt::ISODate);reportRows={};exportRows={};if(!start.isValid()||!end.isValid()||end<start){message="راجع الفترة • Check dates";emit changed();return;}
 QSqlQuery q(db);q.prepare("SELECT at,kind,body FROM observations WHERE scope=? AND at>=? AND at<? ORDER BY at");q.addBindValue(scope);q.addBindValue(start.startOfDay().toMSecsSinceEpoch());q.addBindValue(end.addDays(1).startOfDay().toMSecsSinceEpoch());if(q.exec())while(q.next()){auto r=QJsonDocument::fromJson(q.value(2).toByteArray()).object();r["at"]=q.value(0).toString();r["kind"]=q.value(1).toString();exportRows.append(r);}
 for(auto value:rows){auto ap=value.toObject();if(!QStringList{"Confirmed AP","Likely AP"}.contains(text(ap,"classification")))continue;QSet<QString> clients,accounts;int samples=0;for(auto v:exportRows){auto s=v.toObject();if(text(s,"kind")!="session"||text(s,"ap")!=text(ap,"mac"))continue;++samples;if(!text(s,"client").isEmpty())clients.insert(text(s,"client"));if(!text(s,"account").isEmpty())accounts.insert(text(s,"account"));}ap["observedClients"]=clients.size();ap["observedAccounts"]=accounts.size();ap["sessionSamples"]=samples;ap["sales"]="N/A";ap["cards"]="N/A";ap["traffic"]="N/A";ap["quality"]="Inferred • sampled";reportRows.append(ap);}
 message="عينات محلية فقط وليست سجل جلسات كاملًا • Local samples, not a complete session ledger";emit changed();}
void AccessPoints::exportCsv(QUrl url){if(!client->authorized()||!url.isLocalFile())return;QSaveFile file(url.toLocalFile());auto bytes=AccessPointEngine::csv(exportRows);bool ok=file.open(QIODevice::WriteOnly)&&file.write(bytes)==bytes.size()&&file.commit();message=ok?"تم حفظ CSV • CSV saved":"CSV could not be saved";emit changed();}
void AccessPoints::fixture(){if(!QStandardPaths::isTestModeEnabled())return;data["interface/ethernet"]=QJsonArray{QJsonObject{{"name","ether2"}},QJsonObject{{"name","ether3"}},QJsonObject{{"name","ether4"}}};for(int i=1;i<=3;++i){auto mac=QString("02:10:20:30:40:0%1").arg(i);data["ip/neighbor"].append(QJsonObject{{"identity",QString("المحل %1 • Shop %1").arg(i)},{"mac-address",mac},{"address",QString("192.168.1.%1").arg(i+10)},{"interface",QString("ether%1").arg(i+1)},{"system-caps-enabled","wlan-access-point"},{"discovered-by","lldp"}});}rebuild();message="بيانات اختبار مصطنعة • Synthetic fixture";emit changed();}

void AccessPoints::exportReport(QUrl file,QString format){if(!client->authorized()||!file.isLocalFile())return;if(format=="CSV"){exportCsv(file);return;}bool ok=format=="PDF"?ApReport::pdf(file.toLocalFile(),reportRows,reportPeriod):format=="XLSX"?ApReport::xlsx(file.toLocalFile(),reportRows,exportRows):false;message=ok?"تم حفظ التقرير • Report saved":"Report could not be saved";emit changed();}
