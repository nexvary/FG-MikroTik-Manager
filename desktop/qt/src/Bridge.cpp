#include "Bridge.hpp"
#include "Business.hpp"
#include "Protocol.hpp"
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QTimer>
#include <QFile>
#include <QJsonDocument>
#include <QSet>
#include <algorithm>
#include <memory>
#include <QSettings>
#include <QUuid>
#include <QRegularExpression>
#include <QApplication>
#include <QClipboard>
Bridge::Bridge(QObject *p):QObject(p),routerClient(this) {
 connect(&routerClient,&RouterClient::changed,this,[this]{m_status=routerClient.status();emit changed();});
 connect(&routerClient,&RouterClient::discoveryReady,this,[this](QJsonArray rows){showRows(rows);});
 auto timer=new QTimer(this);timer->setInterval(1000);connect(timer,&QTimer::timeout,this,[this]{if(!token.isEmpty()&&!connected()) {reset();m_status="انتهت الجلسة • Session expired";emit changed();}});timer->start();
}
QStringList Bridge::menus()const{return Protocol::menus();}
void Bridge::reset(){serverTenant.clear();serverBranch.clear();serverRole.clear();token.clear();m_scope.clear();records={};matching={};m_rows.clear();m_columns.clear();expires={};emit changed();}
void Bridge::request(QString url,QByteArray method,QJsonObject body,QByteArray auth,std::function<void(QJsonValue)> done) {
 if(m_busy)return; m_busy=true;m_status="جارٍ التحميل • Loading";emit changed();
 QNetworkRequest req{QUrl(url)};req.setAttribute(QNetworkRequest::RedirectPolicyAttribute,QNetworkRequest::ManualRedirectPolicy);req.setTransferTimeout(60000);
 req.setHeader(QNetworkRequest::ContentTypeHeader,"application/json");if(!auth.isEmpty())req.setRawHeader("Authorization",auth);
 auto reply=method=="GET"?net.get(req):net.sendCustomRequest(req,method,QJsonDocument(body).toJson(QJsonDocument::Compact));
 auto bytes=std::make_shared<QByteArray>();
 connect(reply,&QNetworkReply::readyRead,this,[reply,bytes]{bytes->append(reply->readAll());if(bytes->size()>32*1024*1024)reply->abort();});
 auto timer=new QTimer(reply);timer->setSingleShot(true);connect(timer,&QTimer::timeout,reply,&QNetworkReply::abort);timer->start(60000);
 connect(reply,&QNetworkReply::finished,this,[this,reply,bytes,done]{
  bytes->append(reply->readAll());m_busy=false;int code=reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
  if(reply->error()!=QNetworkReply::NoError || code<200 || code>=300 || bytes->size()>32*1024*1024) {
   if(code==401||code==403){reset();routerAuth.clear();routerOrigin.clear();m_terminal.clear();}
   m_status="تعذر الاتصال أو رُفض الوصول • Request failed ("+QString::number(code)+")";
  }else if(code==204){m_status="تم • Done";done(QJsonObject{});}else{
   QJsonParseError error;auto doc=QJsonDocument::fromJson(*bytes,&error);
   if(error.error!=QJsonParseError::NoError){m_status="رد غير صالح • Invalid response";}else {m_status="تم التحديث • Updated";done(doc.isArray()?QJsonValue(doc.array()):QJsonValue(doc.object()));}
  }
  reply->deleteLater();emit changed();
 });
}
void Bridge::login(QString url,QString tenant,QString branch,QString user,QString password){
 if(m_busy)return;reset();if(!Protocol::validOrigin(url)||tenant.trimmed().isEmpty()||branch.trimmed().isEmpty()||user.trimmed().isEmpty()){m_status="راجع عنوان HTTPS والمعرفات • Check HTTPS and scope";emit changed();return;}
 origin=QUrl(url).toString(QUrl::RemovePath);request(origin+"/v1/login","POST",{{"username",user},{"password",password}},{},[this,tenant,branch](QJsonValue v){
  auto o=v.toObject();int ttl=o["expires_in"].toInt();auto t=o["token"].toString();if(t.size()<20||ttl<1||ttl>900){m_status="رد دخول غير صالح • Invalid session";return;}
  token=t;expires=QDateTime::currentDateTimeUtc().addSecs(ttl);
  api("identity","GET",{},[this,tenant,branch](QJsonValue id){auto o=id.toObject();if(o["tenant"].toString()!=tenant||o["branch"].toString()!=branch){reset();m_status="المؤسسة أو الفرع غير مطابق • Scope mismatch";return;}serverTenant=tenant;serverBranch=branch;serverRole=o["role"].toString();m_scope=tenant+" / "+branch+" • "+serverRole;});
 });
}
void Bridge::api(QString path,QByteArray method,QJsonObject body,std::function<void(QJsonValue)> done){if(!connected()){reset();m_status="سجّل الدخول • Login required";emit changed();return;}auto session=token;request(origin+"/v1/"+path,method,body,"Bearer "+token.toUtf8(),[this,session,done](QJsonValue v){if(!connected()||token!=session){reset();m_status="انتهت الجلسة • Session expired";return;}done(v);});}
void Bridge::logout(){if(m_busy)return;QString target=origin;QByteArray auth="Bearer "+token.toUtf8();bool live=connected();reset();routerAuth.clear();routerOrigin.clear();m_terminal.clear();if(live)request(target+"/v1/logout","POST",{},auth,[](QJsonValue){});}
void Bridge::showRows(QJsonArray data){m_columns.clear();m_rows.clear();for(auto v:data)for(auto k:v.toObject().keys())if(!m_columns.contains(k))m_columns.append(k);m_columns.sort();for(auto v:data){QVariantMap row;auto o=v.toObject();for(auto k:m_columns)row[k]=Protocol::jsonText(o[k]);m_rows.append(row);}emit changed();}
void Bridge::business(){m_rows.clear();records={};matching={};emit changed();api("business/sync","GET",{},[this](QJsonValue v){auto o=v.toObject();if(!o["records"].isArray()){m_status="رد أعمال غير صالح • Invalid business response";return;}records=o["records"].toArray();filter("subscribers","",0);});}
void Bridge::filter(QString table,QString query,int page){if(!connected()){reset();return;}matching={};QJsonArray result;for(auto v:records){auto o=v.toObject();if(o["table"]==table && (o["id"].toString()+QString::fromUtf8(QJsonDocument(o["body"].toObject()).toJson())).contains(query,Qt::CaseInsensitive)){auto body=o["body"].toObject();body["id"]=o["id"];matching.append(body);}}
 page=std::max(0,page);for(int i=page*50;i<matching.size()&&i<(page+1)*50;i++)result.append(matching[i]);showRows(result);m_status=QString("%1 نتيجة · %2 / %3 • %1 matches · %2 / %3").arg(matching.size()).arg(page+1).arg(std::max(1,(int(matching.size())+49)/50));emit changed();}
void Bridge::radius(bool sessions){api(sessions?"radius/sessions":"radius/users","GET",{},[this,sessions](QJsonValue v){showRows(v.toObject()[sessions?"sessions":"users"].toArray());});}
void Bridge::diagnose(){api("diagnostics","POST",{},[this](QJsonValue v){auto o=v.toObject();if(o["source"]!="server"||!o["checks"].isArray()){m_status="رد تشخيص غير صالح • Invalid diagnostic response";return;}showRows(o["checks"].toArray());});}
void Bridge::exportCsv(QUrl path){if(!connected()){reset();return;}QStringList cols;for(auto r:matching)for(auto k:r.toObject().keys())if(!cols.contains(k))cols.append(k);cols.sort();QStringList lines,cells;for(auto c:cols)cells.append(Protocol::csvCell(c));lines.append(cells.join(','));for(auto r:matching){cells.clear();for(auto c:cols)cells.append(Protocol::csvCell(Protocol::jsonText(r.toObject()[c])));lines.append(cells.join(','));}QFile file(path.toLocalFile());if(!file.open(QIODevice::WriteOnly)){m_status="تعذر حفظ الملف • Cannot save file";}else {QByteArray content="\xEF\xBB\xBF";content+=lines.join("\r\n").toUtf8()+"\r\n";m_status=file.write(content)==content.size()?"تم حفظ CSV • CSV saved":"فشل حفظ الملف • Save failed";}emit changed();}
void Bridge::router(QString url,QString user,QString password,QString menu){QUrl u(url);if(!Protocol::validOrigin(url)){m_status="راجع HTTPS • Check HTTPS origin";emit changed();return;}connectRouter(u.host(),u.port(443),user,password,"REST",menu);}
void Bridge::connectRouter(QString host,int port,QString user,QString password,QString protocol,QString menu){if(busy())return;routerClient.configure(host,port,user,password,protocol);command("/"+menu+" print");}
void Bridge::command(QString value){auto c=RouterCodec::parse(value);if(!c.valid()){m_status=c.error;emit changed();return;}if(c.risk!="READ"){previewCommand(value);return;}
 routerClient.command(value,[this](RouterReply reply){if(!reply.ok()){m_status=reply.error;emit changed();return;}auto v=Protocol::redact(reply.rows);showRows(v.toArray());m_terminal+="\n> print\n"+QString::fromUtf8(QJsonDocument(v.toArray()).toJson());if(m_terminal.size()>100000)m_terminal=m_terminal.right(100000);emit changed();});}
void Bridge::previewCommand(QString value){auto c=RouterCodec::parse(value);pendingCommand.clear();if(!c.valid()){m_preview=c.error;}else{pendingCommand=value;m_preview=c.risk+" /"+c.menu+"/"+c.action+"\n"+QString::fromUtf8(QJsonDocument(Protocol::redact(c.attributes).toObject()).toJson());}emit changed();}
void Bridge::executePreview(){if(busy()||pendingCommand.isEmpty())return;auto value=pendingCommand;pendingCommand.clear();m_preview.clear();routerClient.command(value,[this](RouterReply reply){m_status=reply.ok()?"تم تنفيذ الأمر • Command applied":reply.error;if(reply.uncertain)m_status+=" — تحقق من النتيجة قبل إعادة المحاولة • Verify outcome before retrying";m_terminal+="\n"+m_status+"\n";if(reply.ok()&&!reply.rows.isEmpty())showRows(Protocol::redact(reply.rows).toArray());emit changed();});emit changed();}
void Bridge::discoverRouters(){routerClient.discover();}
QVariantList Bridge::profiles()const{QSettings s;return s.value("routers/profiles").toList();}
void Bridge::saveRouterProfile(QString name,QString branch,QString host,int port,QString user,QString protocol){if(name.trimmed().isEmpty()||host.trimmed().isEmpty()||port<1||port>65535)return;auto list=profiles();list.append(QVariantMap{{"id",QUuid::createUuid().toString(QUuid::WithoutBraces)},{"name",name.trimmed()},{"branch",branch.trimmed()},{"host",host.trimmed()},{"port",port},{"user",user},{"protocol",protocol}});QSettings s;s.setValue("routers/profiles",list);emit changed();}
void Bridge::deleteRouterProfile(QString id){auto list=profiles();for(int i=list.size()-1;i>=0;i--)if(list[i].toMap()["id"].toString()==id)list.removeAt(i);QSettings s;s.setValue("routers/profiles",list);emit changed();}
QVariantMap Bridge::module(QString menu)const{for(auto item:Protocol::modules())if(item.toObject()["menu"]==menu)return item.toObject().toVariantMap();return {};}
void Bridge::admin(QString menu,QString action,QString id,QString json){auto info=module(menu);auto cap=action=="add"?"create":action=="set"?"edit":action=="remove"?"delete":action=="enable"||action=="disable"?"toggle":"";if(info.isEmpty()||QString(cap).isEmpty()||!info[cap].toBool()){m_status="الإجراء غير متاح لهذا القسم • Action unavailable for this module";emit changed();return;}QJsonParseError e;auto doc=QJsonDocument::fromJson(json.toUtf8(),&e);if(e.error!=QJsonParseError::NoError||!doc.isObject()){m_status="الحقول غير صالحة • Invalid fields";emit changed();return;}auto attrs=doc.object();for(auto v:attrs)if(!v.isString()){m_status="استخدم قيمًا نصية للحقول • Field values must be strings";emit changed();return;}if(!id.isEmpty())attrs[".id"]=id;QString text="/"+menu+" "+action;for(auto it=attrs.begin();it!=attrs.end();++it){auto val=it.value().toString();val.replace("\\","\\\\").replace("\"","\\\"");text+=" "+it.key()+"=\""+val+"\"";}previewCommand(text);}
void Bridge::clearTerminal(){m_terminal.clear();emit changed();}
void Bridge::smoke(){token="fixture-session";expires=QDateTime::currentDateTimeUtc().addSecs(900);m_scope="UI fixture • main";for(int i=1;i<=51;i++)records.append(QJsonObject{{"table","subscribers"},{"id",QString::number(i)},{"body",QJsonObject{{"name",QString("Customer %1").arg(i)},{"service","HotSpot"}}}});filter("subscribers","",0);}

void Bridge::clearView(){m_status.clear();m_rows.clear();m_columns.clear();emit changed();}

void Bridge::syncBusiness(bool joinEmpty){
 if(busy())return;if(!commerce||!commerce->allowed("BRANCHES")||serverRole!="owner"){m_status="مزامنة الأعمال تتطلب حساب المالك المحلي والخادم • Local and server owner permissions required";emit changed();return;}
 const auto syncOrigin=origin,tenant=serverTenant,branch=serverBranch;
 api("business/sync","GET",{},[this,joinEmpty,syncOrigin,tenant,branch](QJsonValue value){
  try{
   const auto remote=value.toObject();if(!remote["records"].isArray()||!remote["revision"].isDouble()||remote["revision"].toInteger(-1)<0)throw std::runtime_error("CLOUD_INVALID_RESPONSE");
   auto remoteRecords=remote["records"].toArray();if(commerce->scope()!=tenant+" / "+branch){if(!joinEmpty)throw std::runtime_error("CLOUD_SCOPE_MISMATCH");commerce->join(tenant,branch,remoteRecords);}
   const auto local=commerce->snapshot(),merged=commerce->combine(remoteRecords,syncOrigin);commerce->merge(merged,true,syncOrigin);
   api("business/sync","POST",{{"revision",remote["revision"]},{"device",commerce->device()},{"records",merged}},[this,local,merged,syncOrigin](QJsonValue response){
    try{if(response.toObject()["accepted"]!=true)throw std::runtime_error("CLOUD_NOT_CONFIRMED");if(commerce->snapshot()!=local)throw std::runtime_error("CLOUD_LOCAL_CHANGED");commerce->merge(merged,false,syncOrigin);records=merged;filter("subscribers","",0);m_status=QString("تمت مزامنة %1 سجل • Synchronized %1 records").arg(merged.size());}catch(const std::exception&e){m_status=e.what();}emit changed();
   });
  }catch(const std::exception&e){m_status=e.what();emit changed();}
 });
}

QVariantList Bridge::commandLibrary()const{QFile f(":/resources/command-library.json");if(!f.open(QIODevice::ReadOnly))return {};return QJsonDocument::fromJson(f.readAll()).array().toVariantList();}
QString Bridge::fillCommand(QString text,QVariantMap values){QRegularExpression pattern("\\{\\{([a-z]+)\\}\\}");auto matches=pattern.globalMatch(text);QList<QRegularExpressionMatch> found;while(matches.hasNext())found.append(matches.next());for(auto it=found.rbegin();it!=found.rend();++it){auto value=values[it->captured(1)].toString();if(value.trimmed().isEmpty()||value.contains('\n')||value.contains('\r')||value.contains(QChar(0))){m_status="املأ كل المتغيرات بقيم من سطر واحد • Fill every variable with one-line values";emit changed();return {};}value.replace("\\","\\\\").replace("\"","\\\"");text.replace(it->capturedStart(),it->capturedLength(),"\""+value+"\"");}auto command=RouterCodec::parse(text);if(!command.valid()){m_status=command.error;emit changed();return {};}return text;}
void Bridge::copyText(QString text){QApplication::clipboard()->setText(text);m_status="نُسخ النص • Text copied";emit changed();}
