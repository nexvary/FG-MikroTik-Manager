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
#include <algorithm>
#include <limits>
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
  result.append(QJsonObject{{"id",text(session,".id")},{"account",text(session,"user")},{"client",client},{"ap",text(ap,"mac")},{"confidence",ap.isEmpty()?"Unknown":"Inferred"},{"upload",text(session,"bytes-in")},{"download",text(session,"bytes-out")},{"uptime",text(session,"uptime")},{"server",text(session,"server")},{"profile",text(session,"profile")}});
 }return result;
}
QJsonArray AccessPointEngine::summary(const QJsonArray& records,const QJsonArray& devices){
 auto durationSeconds=[](QString value)->qint64{value=value.trimmed().toLower();if(value.isEmpty())return 0;QRegularExpression rx("(\\d+)([wdhms])");auto matches=rx.globalMatch(value);qint64 total=0;int covered=0;while(matches.hasNext()){auto m=matches.next();bool ok=false;auto n=m.captured(1).toLongLong(&ok);if(!ok)return 0;covered+=m.capturedLength();qint64 factor=m.captured(2)=="w"?604800:m.captured(2)=="d"?86400:m.captured(2)=="h"?3600:m.captured(2)=="m"?60:1;if(n>std::numeric_limits<qint64>::max()/factor||total>std::numeric_limits<qint64>::max()-n*factor)return 0;total+=n*factor;}return covered==value.size()?total:0;};
 auto bytes=[](QString value)->quint64{bool ok=false;auto n=value.toULongLong(&ok);return ok?n:0;};
 auto best=[](const QMap<QString,int>& counts){QString key;int score=-1;for(auto it=counts.cbegin();it!=counts.cend();++it)if(it.value()>score){key=it.key();score=it.value();}return key;};
 QList<QJsonObject> ranked;
 for(auto value:devices){auto ap=value.toObject();if(!QStringList{"Confirmed AP","Likely AP"}.contains(text(ap,"classification")))continue;QList<QJsonObject> samples;QSet<QString> clients,accounts;QMap<QString,int> hours,days;
  for(auto v:records){auto s=v.toObject();if(text(s,"kind")!="session"||text(s,"ap")!=text(ap,"mac"))continue;samples.append(s);if(!text(s,"client").isEmpty())clients.insert(text(s,"client"));if(!text(s,"account").isEmpty())accounts.insert(text(s,"account"));bool ok=false;auto at=text(s,"at").toLongLong(&ok);if(ok){auto dt=QDateTime::fromMSecsSinceEpoch(at);hours[dt.toString("HH:00")]++;days[dt.date().toString(Qt::ISODate)]++;}}
  QMap<QString,QList<QJsonObject>> sessions;int anonymous=0;for(auto s:samples){auto key=text(s,"id");if(key.isEmpty())key=text(s,"account")+"|"+text(s,"client")+"|"+text(s,"server");if(key=="||")key="anonymous-"+QString::number(anonymous++);sessions[key].append(s);}
  quint64 upload=0,download=0;bool overflow=false;qint64 durationTotal=0;QMap<QString,int> plans;
  for(auto group:sessions){quint64 maxUp=0,maxDown=0;qint64 maxDuration=0;QString plan;for(auto s:group){maxUp=std::max(maxUp,bytes(text(s,"upload")));maxDown=std::max(maxDown,bytes(text(s,"download")));maxDuration=std::max(maxDuration,durationSeconds(text(s,"uptime")));if(!text(s,"profile").isEmpty())plan=text(s,"profile");}
   auto add=[&](quint64& target,quint64 value){if(std::numeric_limits<quint64>::max()-target<value)overflow=true;else target+=value;};add(upload,maxUp);add(download,maxDown);if(durationTotal<=std::numeric_limits<qint64>::max()-maxDuration)durationTotal+=maxDuration;if(!plan.isEmpty())plans[plan]++;
  }
  bool totalOverflow=overflow||std::numeric_limits<quint64>::max()-upload<download;quint64 total=totalOverflow?0:upload+download;
  ap["observedClients"]=clients.size();ap["observedAccounts"]=accounts.size();ap["sessionSamples"]=samples.size();ap["observedSessions"]=sessions.size();ap["uploadBytes"]=overflow?"N/A":QString::number(upload);ap["downloadBytes"]=overflow?"N/A":QString::number(download);ap["totalTrafficBytes"]=totalOverflow?"N/A":QString::number(total);ap["averageTrafficPerClientBytes"]=clients.isEmpty()||totalOverflow?"N/A":QString::number(total/clients.size());ap["averageSessionSeconds"]=sessions.isEmpty()?"N/A":QString::number(durationTotal/sessions.size());ap["peakObservedHour"]=best(hours);ap["peakObservedDay"]=best(days);auto plan=best(plans);ap["topObservedPlan"]=plan.isEmpty()?"N/A":plan;ap["sales"]="N/A";ap["revenue"]="N/A";ap["cards"]="N/A";ap["accountEvidence"]="Observed HotSpot account IDs; not confirmed sold vouchers";ap["salesReason"]="No verified sales-to-session attribution";ap["quality"]="Inferred • sampled";ranked.append(ap);
 }
 std::sort(ranked.begin(),ranked.end(),[](const QJsonObject&a,const QJsonObject&b){auto aa=a["observedAccounts"].toInt(),ba=b["observedAccounts"].toInt();return aa==ba?a["observedClients"].toInt()>b["observedClients"].toInt():aa>ba;});QJsonArray result;for(int i=0;i<ranked.size();++i){auto row=ranked[i];row["rankByObservedAccounts"]=i+1;result.append(row);}return result;
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
 reportRows=AccessPointEngine::summary(exportRows,rows);
 message="مؤشرات العملاء والترافيك مستنتجة من عينات HotSpot؛ المبيعات والإيراد لا تُنسب بلا دليل • Client/traffic metrics are inferred from HotSpot samples; sales/revenue require verified attribution";emit changed();}
void AccessPoints::exportCsv(QUrl url){if(!client->authorized()||!url.isLocalFile())return;QSaveFile file(url.toLocalFile());auto bytes=AccessPointEngine::csv(exportRows);bool ok=file.open(QIODevice::WriteOnly)&&file.write(bytes)==bytes.size()&&file.commit();message=ok?"تم حفظ CSV • CSV saved":"CSV could not be saved";emit changed();}
void AccessPoints::fixture(){if(!QStandardPaths::isTestModeEnabled())return;data["interface/ethernet"]=QJsonArray{QJsonObject{{"name","ether2"}},QJsonObject{{"name","ether3"}},QJsonObject{{"name","ether4"}}};for(int i=1;i<=3;++i){auto mac=QString("02:10:20:30:40:0%1").arg(i);data["ip/neighbor"].append(QJsonObject{{"identity",QString("المحل %1 • Shop %1").arg(i)},{"mac-address",mac},{"address",QString("192.168.1.%1").arg(i+10)},{"interface",QString("ether%1").arg(i+1)},{"system-caps-enabled","wlan-access-point"},{"discovered-by","lldp"}});}rebuild();message="بيانات اختبار مصطنعة • Synthetic fixture";emit changed();}

void AccessPoints::exportReport(QUrl file,QString format){if(!client->authorized()||!file.isLocalFile())return;if(format=="CSV"){exportCsv(file);return;}bool ok=format=="PDF"?ApReport::pdf(file.toLocalFile(),reportRows,reportPeriod):format=="XLSX"?ApReport::xlsx(file.toLocalFile(),reportRows,exportRows):false;message=ok?"تم حفظ التقرير • Report saved":"Report could not be saved";emit changed();}
