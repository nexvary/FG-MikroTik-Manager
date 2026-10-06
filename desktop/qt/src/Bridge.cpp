#include "Bridge.hpp"
#include "Protocol.hpp"
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QTimer>
#include <QFile>
#include <QJsonDocument>
#include <QSet>
#include <algorithm>
#include <memory>
Bridge::Bridge(QObject *p):QObject(p) {
 auto timer=new QTimer(this);timer->setInterval(1000);connect(timer,&QTimer::timeout,this,[this]{if(!token.isEmpty()&&!connected()) {reset();m_status="انتهت الجلسة • Session expired";emit changed();}});timer->start();
}
QStringList Bridge::menus()const{return Protocol::menus();}
void Bridge::reset(){token.clear();m_scope.clear();records={};matching={};m_rows.clear();m_columns.clear();expires={};emit changed();}
void Bridge::request(QString url,QByteArray method,QJsonObject body,QByteArray auth,std::function<void(QJsonValue)> done) {
 if(m_busy)return; m_busy=true;m_status="جارٍ التحميل • Loading";emit changed();
 QNetworkRequest req{QUrl(url)};req.setAttribute(QNetworkRequest::RedirectPolicyAttribute,QNetworkRequest::ManualRedirectPolicy);req.setTransferTimeout(20000);
 req.setHeader(QNetworkRequest::ContentTypeHeader,"application/json");if(!auth.isEmpty())req.setRawHeader("Authorization",auth);
 auto reply=method=="GET"?net.get(req):net.sendCustomRequest(req,method,QJsonDocument(body).toJson(QJsonDocument::Compact));
 auto bytes=std::make_shared<QByteArray>();
 connect(reply,&QNetworkReply::readyRead,this,[reply,bytes]{bytes->append(reply->readAll());if(bytes->size()>32*1024*1024)reply->abort();});
 auto timer=new QTimer(reply);timer->setSingleShot(true);connect(timer,&QTimer::timeout,reply,&QNetworkReply::abort);timer->start(20000);
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
  api("identity","GET",{},[this,tenant,branch](QJsonValue id){auto o=id.toObject();if(o["tenant"].toString()!=tenant||o["branch"].toString()!=branch){reset();m_status="المؤسسة أو الفرع غير مطابق • Scope mismatch";return;}m_scope=tenant+" / "+branch+" • "+o["role"].toString();});
 });
}
void Bridge::api(QString path,QByteArray method,QJsonObject body,std::function<void(QJsonValue)> done){if(!connected()){reset();m_status="سجّل الدخول • Login required";emit changed();return;}auto session=token;request(origin+"/v1/"+path,method,body,"Bearer "+token.toUtf8(),[this,session,done](QJsonValue v){if(!connected()||token!=session){reset();m_status="انتهت الجلسة • Session expired";return;}done(v);});}
void Bridge::logout(){if(m_busy)return;QString target=origin;QByteArray auth="Bearer "+token.toUtf8();bool live=connected();reset();routerAuth.clear();routerOrigin.clear();m_terminal.clear();if(live)request(target+"/v1/logout","POST",{},auth,[](QJsonValue){});}
void Bridge::showRows(QJsonArray data){m_columns.clear();m_rows.clear();for(auto v:data)for(auto k:v.toObject().keys())if(!m_columns.contains(k))m_columns.append(k);m_columns.sort();for(auto v:data){QVariantMap row;auto o=v.toObject();for(auto k:m_columns)row[k]=Protocol::jsonText(o[k]);m_rows.append(row);}emit changed();}
void Bridge::business(){m_rows.clear();records={};matching={};emit changed();api("business/sync","GET",{},[this](QJsonValue v){auto o=v.toObject();if(!o["records"].isArray()){m_status="رد أعمال غير صالح • Invalid business response";return;}records=o["records"].toArray();filter("subscribers","",0);});}
void Bridge::filter(QString table,QString query,int page){if(!connected()){reset();return;}matching={};QJsonArray result;for(auto v:records){auto o=v.toObject();if(o["table"]==table && (o["id"].toString()+QString::fromUtf8(QJsonDocument(o["body"].toObject()).toJson())).contains(query,Qt::CaseInsensitive)){auto body=o["body"].toObject();body["id"]=o["id"];matching.append(body);}}
 page=std::max(0,page);for(int i=page*50;i<matching.size()&&i<(page+1)*50;i++)result.append(matching[i]);showRows(result);m_status=QString("%1 نتيجة • matches · %2 / %3").arg(matching.size()).arg(page+1).arg(std::max(1,(int(matching.size())+49)/50));emit changed();}
void Bridge::radius(bool sessions){api(sessions?"radius/sessions":"radius/users","GET",{},[this,sessions](QJsonValue v){showRows(v.toObject()[sessions?"sessions":"users"].toArray());});}
void Bridge::diagnose(){api("diagnostics","POST",{},[this](QJsonValue v){auto o=v.toObject();if(o["source"]!="server"||!o["checks"].isArray()){m_status="رد تشخيص غير صالح • Invalid diagnostic response";return;}showRows(o["checks"].toArray());});}
void Bridge::exportCsv(QUrl path){if(!connected()){reset();return;}QStringList cols;for(auto r:matching)for(auto k:r.toObject().keys())if(!cols.contains(k))cols.append(k);cols.sort();QStringList lines,cells;for(auto c:cols)cells.append(Protocol::csvCell(c));lines.append(cells.join(','));for(auto r:matching){cells.clear();for(auto c:cols)cells.append(Protocol::csvCell(Protocol::jsonText(r.toObject()[c])));lines.append(cells.join(','));}QFile file(path.toLocalFile());if(!file.open(QIODevice::WriteOnly)){m_status="تعذر حفظ الملف • Cannot save file";}else {QByteArray content="\xEF\xBB\xBF";content+=lines.join("\r\n").toUtf8()+"\r\n";m_status=file.write(content)==content.size()?"تم حفظ CSV • CSV saved":"فشل حفظ الملف • Save failed";}emit changed();}
void Bridge::router(QString url,QString user,QString password,QString menu){if(!Protocol::validOrigin(url)||user.isEmpty()||user.contains(':')||!menus().contains(menu)){m_status="راجع HTTPS وحساب الراوتر • Check router settings";emit changed();return;}routerOrigin=QUrl(url).toString(QUrl::RemovePath);routerAuth=QString::fromLatin1((user+":"+password).toUtf8().toBase64());command("/"+menu+" print");}
void Bridge::command(QString value){auto menu=Protocol::menuForCommand(value);if(menu.isEmpty()||routerAuth.isEmpty()){m_status="أمر قراءة print معتمد واتصال الراوتر مطلوب • Supported print command and router connection required";emit changed();return;}request(routerOrigin+"/rest/"+menu,"GET",{},"Basic "+routerAuth.toUtf8(),[this,value](QJsonValue v){v=Protocol::redact(v);showRows(v.isArray()?v.toArray():QJsonArray{v});m_terminal+=(m_terminal.isEmpty()?"":"\n")+"> "+value+"\n"+QString::fromUtf8((v.isArray()?QJsonDocument(v.toArray()):QJsonDocument(v.toObject())).toJson());if(m_terminal.size()>100000)m_terminal=m_terminal.right(100000);});}
void Bridge::clearTerminal(){m_terminal.clear();emit changed();}
void Bridge::smoke(){token="fixture-session";expires=QDateTime::currentDateTimeUtc().addSecs(900);m_scope="UI fixture • main";for(int i=1;i<=51;i++)records.append(QJsonObject{{"table","subscribers"},{"id",QString::number(i)},{"body",QJsonObject{{"name",QString("Customer %1").arg(i)},{"service","HotSpot"}}}});filter("subscribers","",0);}

void Bridge::clearView(){m_rows.clear();m_columns.clear();emit changed();}
