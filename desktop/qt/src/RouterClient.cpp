#include "RouterClient.hpp"
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QJsonDocument>
#include <QRegularExpression>
#include <QUdpSocket>
#include <QNetworkInterface>
#include <QNetworkDatagram>
#include <QElapsedTimer>
#include <QUrl>
#include <QTcpSocket>
#include <QProcess>
#include <QQueue>
#include <QSet>
#include <QHash>
#include <memory>
#include <utility>
#include <stdexcept>
#include <algorithm>
namespace RouterCodec {
QByteArray length(quint32 n){QByteArray b;auto put=[&](quint32 x){b.append(char(x&255));};if(n<0x80)put(n);else if(n<0x4000){put((n>>8)|0x80);put(n);}else if(n<0x200000){put((n>>16)|0xc0);put(n>>8);put(n);}else if(n<0x10000000){put((n>>24)|0xe0);put(n>>16);put(n>>8);put(n);}else{put(0xf0);put(n>>24);put(n>>16);put(n>>8);put(n);}return b;}
QByteArray sentence(const QStringList &words){QByteArray b;for(auto word:words){auto bytes=word.toUtf8();b+=length(quint32(bytes.size()));b+=bytes;}b.append('\0');return b;}
int takeSentence(QByteArray &buffer,QStringList &words){qsizetype pos=0;QStringList result;while(pos<buffer.size()){
 auto first=quint8(buffer[pos]);int count=1;quint32 n=0;
 if(first<0x80)n=first;else if(first<0xc0){count=2;n=first&0x3f;}else if(first<0xe0){count=3;n=first&0x1f;}else if(first<0xf0){count=4;n=first&0xf;}else if(first==0xf0){count=5;n=0;}else return -1;
 if(buffer.size()-pos<count)return 0;for(int i=1;i<count;i++)n=(n<<8)|quint8(buffer[pos+i]);pos+=count;
 if(n>64*1024*1024)return -1;if(n==0){buffer.remove(0,pos);words=result;return 1;}
 if(buffer.size()-pos<n)return 0;result.append(QString::fromUtf8(buffer.mid(pos,n)));pos+=n;if(result.size()>10000)return -1;
 }return 0;}
RouterCommand parse(const QString &line){RouterCommand c;QString token;QStringList words;QChar quote;bool escape=false;
 auto flush=[&]{if(!token.isEmpty()){words.append(token);token.clear();}};
 for(QChar ch:line.trimmed()) {if(escape){token+=ch;escape=false;continue;}if(ch=='\\'){escape=true;continue;}if(!quote.isNull()){if(ch==quote)quote={};else token+=ch;continue;}if(ch=='"'||ch=='\''){quote=ch;continue;}if(QString(";[]{}$").contains(ch)){c.error="أمر مركب غير مدعوم • Unsupported compound command";return c;}if(ch.isSpace())flush();else token+=ch;}
 flush();if(!quote.isNull()||escape){c.error="اقتباس غير مكتمل • Unclosed quote or escape";return c;}
 const QStringList actions{"print","add","set","remove","enable","disable","reboot","save","run","renew","release"};
 if(words.isEmpty()||!words.first().startsWith('/')){c.error="صيغة غير مدعومة • Unsupported command syntax";return c;}
 int index=-1;for(int i=1;i<words.size();i++)if(actions.contains(words[i].toLower())){index=i;break;}
 if(index<1){c.error="العملية غير مدعومة • Unsupported action";return c;}
 QStringList menus;for(int i=0;i<index;i++){auto s=words[i];while(s.startsWith('/'))s.remove(0,1);while(s.endsWith('/'))s.chop(1);menus.append(s);}c.menu=menus.join('/');c.action=words[index].toLower();
 if(!QRegularExpression("^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$").match(c.menu).hasMatch()){c.error="مسار غير صالح • Invalid menu";return c;}
 for(int i=index+1;i<words.size();i++){int split=words[i].indexOf('=');if(split>0){auto key=words[i].left(split);if(c.attributes.contains(key)){c.error="وسيط مكرر • Duplicate argument";return c;}c.attributes[key]=words[i].mid(split+1);}else if(split==0||!c.selector.isEmpty()||QStringList{"print","add","reboot","save","run"}.contains(c.action)){c.error="وسائط غير مدعومة • Unsupported arguments";return c;}else c.selector=words[i];}
 c.risk=c.action=="print"?"READ": "CHANGE";
 if(c.action!="print"&&(QStringList{"reboot","remove","disable","release"}.contains(c.action)||c.menu=="ip/service"||c.menu=="user"||c.menu.startsWith("interface")||c.menu=="ip/address"||c.menu=="ip/route"||c.menu.startsWith("system/reset")||c.menu.startsWith("system/routerboard")||c.menu.startsWith("partition")||c.menu.startsWith("disk")||c.menu.startsWith("ip/firewall")))c.risk="DANGEROUS";
 return c;
}
}
namespace RouterDiscoveryCodec {
QJsonObject parseMndp(const QByteArray &b,const QHostAddress &sender){
 if(b.size()<4||sender.protocol()!=QAbstractSocket::IPv4Protocol)return {};
 QJsonObject row{{"host",sender.toString()}};int pos=4;bool valid=true;
 while(pos+4<=b.size()){
  auto u=[&](int i){return quint8(b[i]);};int type=(u(pos)<<8)|u(pos+1),n=(u(pos+2)<<8)|u(pos+3);pos+=4;
  if(n<0||pos+n>b.size()){valid=false;break;}auto v=b.mid(pos,n);
  if(type!=1&&type!=17)while(v.endsWith(char(0)))v.chop(1);
  if(type==5)row["name"]=QString::fromUtf8(v);
  else if(type==7)row["version"]=QString::fromUtf8(v);
  else if(type==8)row["platform"]=QString::fromUtf8(v);
  else if(type==12)row["board"]=QString::fromUtf8(v);
  else if(type==16)row["interface"]=QString::fromUtf8(v);
  else if(type==17&&n==4)row["host"]=QHostAddress((quint32(quint8(v[0]))<<24)|(quint32(quint8(v[1]))<<16)|(quint32(quint8(v[2]))<<8)|quint8(v[3])).toString();
  else if(type==1&&n==6)row["mac"]=QString::fromLatin1(v.toHex(':')).toUpper();
  pos+=n;
 }
 if(!valid||pos!=b.size())return {};
 if(row["name"].toString().isEmpty())row["name"]="MikroTik";
 row["method"]="MNDP";row["verification"]="MNDP RouterOS";
 return row;
}
QStringList scanHosts(const QHostAddress &local,int prefixLength,int cap){
 bool ok=false;quint32 address=local.toIPv4Address(&ok);if(!ok||prefixLength<1||prefixLength>30||cap<1)return {};
 const int prefix=std::max(24,prefixLength);if(prefix>30)return {};
 const quint32 mask=prefix==0?0u:(0xffffffffu<<(32-prefix));const quint32 network=address&mask,broadcast=network|~mask;
 QStringList result;for(quint32 current=network+1;current<broadcast&&result.size()<cap;current++)if(current!=address)result.append(QHostAddress(current).toString());
 return result;
}
}
RouterClient::RouterClient(QObject*p):QObject(p){deadline.setSingleShot(true);connect(&deadline,&QTimer::timeout,this,[this]{fail("انتهت مهلة الراوتر • Router timeout",true);});
 connect(&socket,&QSslSocket::connected,this,[this]{if(protocol=="API")sendLogin();});connect(&socket,&QSslSocket::encrypted,this,&RouterClient::sendLogin);
 connect(&socket,&QSslSocket::readyRead,this,&RouterClient::receive);
 connect(&socket,&QSslSocket::errorOccurred,this,[this](QAbstractSocket::SocketError){if(active)fail("انقطع اتصال الراوتر • Router connection lost",true);});
 connect(&socket,&QSslSocket::sslErrors,this,[this](const QList<QSslError>&){fail("شهادة الراوتر غير موثوقة • Router certificate rejected");});
 connect(&socket,&QSslSocket::disconnected,this,[this]{authenticated=false;if(active)fail("انقطع اتصال الراوتر • Router connection lost",true);emit changed();});}
RouterClient::~RouterClient(){cancelDiscovery();callback={};active=false;deadline.stop();socket.disconnect(this);socket.abort();net.disconnect(this);password.fill(QChar(0));}
void RouterClient::configure(QString h,int p,QString u,QString pw,QString proto){close();host=h.trimmed();port=p;username=u;password=pw;autoMode=proto=="AUTO";negotiated=false;selecting=false;protocol=autoMode?"API":proto=="REST_HTTPS"?"REST":proto;port=autoMode?8728:p;message.clear();emit changed();}
void RouterClient::cancelDiscovery(){if(!discovering&&!discoveryContext)return;++discoveryGeneration;discovering=false;if(discoveryContext){discoveryContext->deleteLater();discoveryContext=nullptr;}}
void RouterClient::close(){cancelDiscovery();auditAttempt={};++configVersion;++generation;selecting=false;negotiated=false;bool running=active||pendingRetry;pendingRetry=false;auto cb=std::move(callback);callback={};active=false;authenticated=false;deadline.stop();socket.abort();input.clear();password.clear();if(running&&cb)cb({{},"أغلقت الجلسة • Session closed",wrote&&action!="print"});emit changed();}
void RouterClient::read(QString menu,Done done){execute(menu,"print",{},std::move(done));}
void RouterClient::execute(QString menu,QString operation,QJsonObject attrs,Done done){if(!authorization()){done({{},"ACCESS_DENIED"});return;}if(active||pendingRetry){done({{},"الراوتر مشغول • Router busy"});return;}
 if(autoMode&&!negotiated&&!selecting){selecting=true;const auto version=configVersion;
  read("system/identity",[this,menu,operation,attrs,done,version](RouterReply apiProbe){
   if(version!=configVersion){done({{},"SESSION_CHANGED"});return;}
   if(apiProbe.ok()){negotiated=true;selecting=false;execute(menu,operation,attrs,done);return;}
   protocol="API_SSL";port=8729;
   read("system/identity",[this,menu,operation,attrs,done,version](RouterReply sslProbe){
    if(version!=configVersion){done({{},"SESSION_CHANGED"});return;}
    if(sslProbe.ok()){negotiated=true;selecting=false;execute(menu,operation,attrs,done);return;}
    protocol="REST";port=443;
    read("system/identity",[this,menu,operation,attrs,done,version](RouterReply restProbe){
     if(version!=configVersion){done({{},"SESSION_CHANGED"});return;}selecting=false;if(!restProbe.ok()){done(restProbe);return;}negotiated=true;execute(menu,operation,attrs,done);
    });
   });
  });return;
 }
 if(!retrying)readRetries=0;retrying=false;
 if(host.isEmpty()||username.isEmpty()||port<1||port>65535||!QStringList{"REST","REST_HTTP","API","API_SSL"}.contains(protocol)||!QRegularExpression("^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$").match(menu).hasMatch()) {done({{},"إعداد اتصال غير صالح • Invalid router settings"});return;}
 if(protocol!="REST"&&protocol!="REST_HTTP"&&!(socket.state()==QAbstractSocket::ConnectedState&&authenticated))socket.abort();
 auditAttempt={};if(auditBegin&&operation!="print"&&operation!="get"&&operation!="monitor"&&menu!="ping"&&menu!="traceroute"){try{auditAttempt=auditBegin(menu+(operation.isEmpty()?QString{}:"/"+operation));}catch(const std::exception&e){done({{},QString::fromUtf8(e.what())});return;}}
 ++generation;active=true;wrote=false;loggingIn=false;path=menu;action=operation;attributes=attrs;rows={};trap.clear();callback=std::move(done);deadline.start(20000);message="جارٍ الاتصال • Connecting";emit changed();
 if(protocol=="REST"||protocol=="REST_HTTP"){
  QUrl url;url.setScheme(protocol=="REST_HTTP"?"http":"https");url.setHost(host);url.setPort(port);url.setPath("/rest/"+path+(action.isEmpty()||action=="add"||action=="print"&&attributes.isEmpty()?QString{}:"/"+action));
  QNetworkRequest req(url);req.setTransferTimeout(20000);req.setAttribute(QNetworkRequest::RedirectPolicyAttribute,QNetworkRequest::ManualRedirectPolicy);req.setHeader(QNetworkRequest::ContentTypeHeader,"application/json");req.setRawHeader("Authorization","Basic "+(username+":"+password).toUtf8().toBase64());
  auto reply=action=="print"&&attributes.isEmpty()?net.get(req):net.sendCustomRequest(req,action=="add"?"PUT":"POST",QJsonDocument(attributes).toJson(QJsonDocument::Compact));
  auto bytes=std::make_shared<QByteArray>();auto current=generation;wrote=action!="print";
  connect(reply,&QNetworkReply::readyRead,this,[reply,bytes]{*bytes+=reply->readAll();if(bytes->size()>32*1024*1024)reply->abort();});
  connect(&deadline,&QTimer::timeout,reply,&QNetworkReply::abort);
  connect(reply,&QNetworkReply::finished,this,[this,reply,bytes,current]{*bytes+=reply->readAll();int code=reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();reply->deleteLater();if(!active||current!=generation)return;
   if(reply->error()!=QNetworkReply::NoError||code<200||code>=300){if(code==0&&action=="print"&&readRetries==0&&reply->error()!=QNetworkReply::SslHandshakeFailedError){wrote=true;fail("Router connection lost",true);return;}finish({{},QString("رفض الراوتر أو فشل الاتصال (%1) • Router request failed (%1)").arg(code),code==0&&wrote});return;}
   QJsonParseError e;auto doc=QJsonDocument::fromJson(*bytes,&e);if(!bytes->trimmed().isEmpty()&&e.error!=QJsonParseError::NoError){finish({{},"رد الراوتر غير صالح • Invalid router reply",wrote});return;}
   authenticated=true;finish({doc.isArray()?doc.array():doc.isObject()?QJsonArray{doc.object()}:QJsonArray{}});
  });return;
 }
 if(socket.state()==QAbstractSocket::ConnectedState&&authenticated){sendPending();return;}
 input.clear();authenticated=false;socket.abort();socket.setSocketOption(QAbstractSocket::LowDelayOption,1);if(protocol=="API_SSL"){socket.setProtocol(QSsl::TlsV1_2OrLater);socket.setPeerVerifyName(host);socket.connectToHostEncrypted(host,quint16(port));}else socket.connectToHost(host,quint16(port));
}
void RouterClient::sendLogin(){if(!active)return;loggingIn=true;socket.write(RouterCodec::sentence({"/login","=name="+username,"=password="+password}));}
void RouterClient::sendPending(){loggingIn=false;QStringList words{"/"+path+(action.isEmpty()?QString{}:"/"+action)};for(auto it=attributes.begin();it!=attributes.end();++it)words.append("="+it.key()+"="+it.value().toString());wrote=true;socket.write(RouterCodec::sentence(words));}
void RouterClient::receive(){input+=socket.readAll();if(input.size()>64*1024*1024){fail("رد كبير جدًا • Router reply exceeds limit");return;}while(active){QStringList words;int n=RouterCodec::takeSentence(input,words);if(n==0)return;if(n<0){fail("ترميز API غير صالح • Invalid API framing");return;}if(words.isEmpty())continue;QJsonObject attrs;for(int i=1;i<words.size();i++){auto w=words[i];int split=w.indexOf('=',1);if(w.startsWith('=')&&split>1)attrs[w.mid(1,split-1)]=w.mid(split+1);}
 auto kind=words.first();if(kind=="!trap")trap=attrs["message"].toString("RouterOS rejected request");else if(kind=="!fatal"){fail(attrs["message"].toString("RouterOS closed session"));return;}else if(kind=="!re"){rows.append(attrs);if(rows.size()>100000){fail("عدد سجلات كبير • Too many router records");return;}}else if(kind=="!done"){
  if(!trap.isEmpty()){auto error=trap;if(loggingIn){active=false;authenticated=false;socket.abort();}finish({{},error});return;}if(loggingIn){authenticated=true;rows={};sendPending();}else{if(rows.isEmpty()&&!attrs["ret"].toString().isEmpty())rows.append(QJsonObject{{".id",attrs["ret"]}});finish({rows});return;}
 }} }
void RouterClient::finish(RouterReply result){if(!auditAttempt.isEmpty()&&auditFinish){auto attempt=std::exchange(auditAttempt,QJsonObject{});try{auditFinish(attempt,result.ok()?"ACKNOWLEDGED":"REVIEW");}catch(const std::exception&e){result.error=QString::fromUtf8(e.what())+"; ROUTER_AUDIT_NEEDS_REVIEW";result.uncertain=true;}}++generation;deadline.stop();active=false;loggingIn=false;message=result.ok()?"تم • Done":result.error;auto cb=std::move(callback);callback={};emit changed();if(cb)cb(result);}
void RouterClient::fail(QString reason,bool io){if(!active)return;
 if(io&&action=="print"&&wrote&&readRetries==0){active=false;pendingRetry=true;authenticated=false;deadline.stop();++generation;auto current=generation;socket.abort();input.clear();readRetries++;QTimer::singleShot(0,this,[this,current]{if(!pendingRetry||generation!=current)return;pendingRetry=false;auto cb=std::move(callback);callback={};retrying=true;execute(path,action,attributes,std::move(cb));});return;}
bool uncertain=wrote&&action!="print";active=false;authenticated=false;socket.abort();input.clear();finish({{},reason,uncertain});}
void RouterClient::command(QString text,Done done){auto c=RouterCodec::parse(text);if(!c.valid()){done({{},c.error});return;}
 if(QStringList{"set","enable","disable","remove","renew","release"}.contains(c.action)){
  bool singleton=c.action=="set"&&QStringList{"system/identity","system/clock","system/ntp/client","ip/dns"}.contains(c.menu);
  if(!singleton){auto selector=c.selector;if(selector.isEmpty())selector=c.attributes[".id"].toString();if(selector.isEmpty())selector=c.attributes["numbers"].toString();if(selector.isEmpty()){done({{},"اختر العنصر • Target item required"});return;}
   c.attributes.remove("numbers");c.attributes.remove(".id");
   if(selector.startsWith('*')){c.attributes[".id"]=selector;execute(c.menu,c.action,c.attributes,std::move(done));return;}
   read(c.menu,[this,c,selector,done](RouterReply reply)mutable{if(!reply.ok()){done(reply);return;}QString id;int matches=0;for(auto value:reply.rows){auto row=value.toObject();for(auto key:QStringList{".id","name","number","address","user"})if(row[key].toString()==selector){matches++;id=row[".id"].toString();break;}}if(matches!=1||id.isEmpty()){done({{},"الهدف غير موجود أو غير محدد • Missing or ambiguous target"});return;}c.attributes[".id"]=id;execute(c.menu,c.action,c.attributes,done);});return;
  }
 }
 execute(c.menu,c.action,c.attributes,std::move(done));
}
void RouterClient::discover(QVariantList savedProfiles){
 if(discovering||active||pendingRetry)return;cancelDiscovery();discovering=true;neighbors={};discoveryDiagnosticsRows={};const auto current=++discoveryGeneration;
 auto context=new QObject(this);discoveryContext=context;
 struct AdapterInfo{QString name,ip,broadcast,subnet;quint32 address=0,mask=0;int prefix=0;};
 QList<AdapterInfo> adapters;
 auto candidateSource=std::make_shared<QHash<QString,QString>>(),candidateName=std::make_shared<QHash<QString,QString>>();
 auto extraPorts=std::make_shared<QHash<QString,QSet<quint16>>>();
 auto sourceRank=[](const QString&s){return s=="Saved Router"?4:s=="Gateway"?3:s=="ARP"?2:1;};
 auto addCandidate=[candidateSource,candidateName,extraPorts,sourceRank](QString host,QString source,QString name={},int port=0){
  host=host.trimmed();if(host.isEmpty()||host=="0.0.0.0"||host=="255.255.255.255")return;
  auto old=candidateSource->value(host);if(old.isEmpty()||sourceRank(source)>sourceRank(old))(*candidateSource)[host]=source;
  if(!name.trimmed().isEmpty())(*candidateName)[host]=name.trimmed();
  if(port>0&&port<=65535)(*extraPorts)[host].insert(quint16(port));
 };
 for(auto iface:QNetworkInterface::allInterfaces()){
  auto flags=iface.flags();if(!flags.testFlag(QNetworkInterface::IsUp)||!flags.testFlag(QNetworkInterface::IsRunning)||flags.testFlag(QNetworkInterface::IsLoopBack))continue;
  for(auto entry:iface.addressEntries()){
   if(entry.ip().protocol()!=QAbstractSocket::IPv4Protocol||entry.ip().isLoopback()||entry.ip().isMulticast())continue;
   bool ok=false;auto address=entry.ip().toIPv4Address(&ok);int prefix=entry.prefixLength();if(!ok||prefix<1||prefix>30)continue;
   quint32 mask=0xffffffffu<<(32-prefix);AdapterInfo info{iface.humanReadableName(),entry.ip().toString(),entry.broadcast().toString(),QHostAddress(address&mask).toString()+"/"+QString::number(prefix),address,mask,prefix};adapters.append(info);
   QJsonObject diag{{"adapter",info.name},{"ipv4",info.ip},{"subnet",info.subnet},{"broadcast",info.broadcast},{"udp5678","Starting"},{"mndp","Waiting"},{"scan","Pending"},{"firewall","Private/Domain installer rule"}};
   discoveryDiagnosticsRows.append(diag);
   for(auto host:RouterDiscoveryCodec::scanHosts(entry.ip(),prefix,254))addCandidate(host,"IP Scan");
  }
 }
 for(auto value:savedProfiles){auto p=value.toMap();addCandidate(p.value("host").toString(),"Saved Router",p.value("name").toString(),p.value("port").toInt());}
 auto adapterFor=[adapters](QString host){QHostAddress h(host);bool ok=false;auto a=h.toIPv4Address(&ok);if(!ok)return QString{};QStringList names;for(const auto&i:adapters)if((a&i.mask)==(i.address&i.mask)&&!names.contains(i.name))names.append(i.name);return names.join(" / ");};
 auto markDiagnostics=[this,current](const QString &adapter,const QString &field,const QString &value){
  if(current!=discoveryGeneration)return;for(int i=0;i<discoveryDiagnosticsRows.size();i++){auto row=discoveryDiagnosticsRows[i].toObject();if(adapter.isEmpty()||row["adapter"].toString()==adapter){row[field]=value;discoveryDiagnosticsRows[i]=row;}}emit changed();
 };
 auto publish=[this,current,adapterFor,markDiagnostics](QJsonObject row){
  if(current!=discoveryGeneration)return;QString host=row["host"].toString(),mac=row["mac"].toString().toUpper();if(host.isEmpty()&&mac.isEmpty())return;
  int found=-1;for(int i=0;i<neighbors.size();i++){auto old=neighbors[i].toObject();if(!mac.isEmpty()&&old["mac"].toString().toUpper()==mac){found=i;break;}if(mac.isEmpty()&&!host.isEmpty()&&old["host"].toString()==host){found=i;break;}}
  QJsonObject merged=found>=0?neighbors[found].toObject():QJsonObject{};
  auto methods=merged["method"].toString().split(" / ",Qt::SkipEmptyParts);for(auto m:row["method"].toString().split(" / ",Qt::SkipEmptyParts))if(!methods.contains(m))methods.append(m);
  for(auto it=row.begin();it!=row.end();++it)if(it.key()!="method"&&!it.value().isNull()&&!it.value().isUndefined()&&(!it.value().isString()||!it.value().toString().isEmpty())){
   if(it.key()=="name"&&!merged["name"].toString().isEmpty()&&it.value().toString().contains("candidate",Qt::CaseInsensitive))continue;
   if(it.key()=="verification"&&merged["verification"].toString()=="MNDP RouterOS")continue;
   merged[it.key()]=it.value();
  }
  merged["method"]=methods.join(" / ");if(!merged.contains("adapter")&&!host.isEmpty())merged["adapter"]=adapterFor(host);
  if(found>=0)neighbors[found]=merged;else neighbors.append(merged);
  if(methods.contains("MNDP"))markDiagnostics(merged["adapter"].toString(),"mndp","Found");
  emit discoveryReady(neighbors);message=QString("تم العثور على %1 جهاز • %1 discovery result(s)").arg(neighbors.size());emit changed();
 };
 message="جارٍ اكتشاف MikroTik عبر كل بطاقات الشبكة • Discovering MikroTik on all active adapters";emit changed();

 auto udp=new QUdpSocket(context);
 bool udpBound=udp->bind(QHostAddress::AnyIPv4,5678,QUdpSocket::ShareAddress|QUdpSocket::ReuseAddressHint);
 for(int i=0;i<discoveryDiagnosticsRows.size();i++){auto row=discoveryDiagnosticsRows[i].toObject();row["udp5678"]=udpBound?"Listening":("Bind failed: "+udp->errorString());discoveryDiagnosticsRows[i]=row;}
 if(udpBound){
  connect(udp,&QUdpSocket::readyRead,context,[this,current,udp,publish]{while(current==discoveryGeneration&&udp->hasPendingDatagrams()){
   if(udp->pendingDatagramSize()>65536){udp->receiveDatagram();continue;}QByteArray b(int(udp->pendingDatagramSize()),'\0');QHostAddress sender;udp->readDatagram(b.data(),b.size(),&sender);
   auto row=RouterDiscoveryCodec::parseMndp(b,sender);if(!row.isEmpty())publish(row);
  }});
  auto broadcast=[udp,adapters]{QByteArray probe(4,'\0');udp->writeDatagram(probe,QHostAddress::Broadcast,5678);for(const auto&i:adapters){QHostAddress b(i.broadcast);if(!b.isNull())udp->writeDatagram(probe,b,5678);}};
  broadcast();auto repeat=new QTimer(context);repeat->setInterval(3000);connect(repeat,&QTimer::timeout,context,broadcast);repeat->start();
 }else message="تعذر فتح UDP 5678؛ سيستمر الفحص البديل • UDP 5678 bind failed; fallback discovery will continue";
 emit changed();

#ifdef Q_OS_WIN
 auto parseProcess=[context,addCandidate](QString program,QStringList args,bool route){
  auto process=new QProcess(context);connect(process,&QProcess::finished,context,[process,addCandidate,route](int,QProcess::ExitStatus){
   auto text=QString::fromLocal8Bit(process->readAllStandardOutput());
   if(route){QRegularExpression re("^\\s*0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+\\d+\\s*$",QRegularExpression::MultilineOption);auto it=re.globalMatch(text);while(it.hasNext())addCandidate(it.next().captured(1),"Gateway");}
   else {QRegularExpression re("(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+([0-9A-Fa-f]{2}(?:-[0-9A-Fa-f]{2}){5})\\s+");auto it=re.globalMatch(text);while(it.hasNext())addCandidate(it.next().captured(1),"ARP");}
   process->deleteLater();
  });process->start(program,args);QTimer::singleShot(1800,process,[process]{if(process->state()!=QProcess::NotRunning)process->kill();});
 };
 parseProcess("route",{"print","-4"},true);parseProcess("arp",{"-a"},false);
#endif

 QTimer::singleShot(6000,context,[this,current,context,candidateSource,candidateName,extraPorts,adapterFor,publish,markDiagnostics]{
  if(current!=discoveryGeneration)return;markDiagnostics({},"scan",QString("Scanning %1 candidate(s)").arg(candidateSource->size()));
  auto tasks=std::make_shared<QQueue<QPair<QString,quint16>>>();
  auto hosts=candidateSource->keys();std::sort(hosts.begin(),hosts.end());for(const auto&host:hosts){QSet<quint16> ports{8291,8728,8729};ports.unite(extraPorts->value(host));auto list=ports.values();std::sort(list.begin(),list.end());for(auto port:list)tasks->enqueue({host,port});}
  auto inFlight=std::make_shared<int>(0);auto openPorts=std::make_shared<QHash<QString,QSet<quint16>>>();auto verified=std::make_shared<QSet<QString>>();
  auto publishCandidate=[candidateSource,candidateName,openPorts,verified,adapterFor,publish](const QString&host){
   auto ports=openPorts->value(host).values();std::sort(ports.begin(),ports.end());QStringList labels;for(auto p:ports)labels.append(QString::number(p));
   QJsonObject row{{"host",host},{"name",candidateName->value(host,"MikroTik candidate")},{"method",candidateSource->value(host,"IP Scan")},{"ports",labels.join(", ")},{"verification",verified->contains(host)?"RouterOS API":"Management port reachable"}};
   auto adapter=adapterFor(host);if(!adapter.isEmpty())row["adapter"]=adapter;publish(row);
  };
  auto launcher=new QTimer(context);launcher->setInterval(20);
  connect(launcher,&QTimer::timeout,context,[this,current,context,tasks,inFlight,openPorts,verified,publishCandidate,markDiagnostics,launcher]{
   if(current!=discoveryGeneration){launcher->stop();return;}
   while(*inFlight<64&&!tasks->isEmpty()){
    auto task=tasks->dequeue();auto host=task.first;auto port=task.second;(*inFlight)++;
    auto socket=new QTcpSocket(context);auto done=std::make_shared<bool>(false);auto connected=std::make_shared<bool>(false);auto buffer=std::make_shared<QByteArray>();
    auto finish=[socket,done,inFlight](){if(*done)return;*done=true;(*inFlight)--;socket->abort();socket->deleteLater();};
    connect(socket,&QTcpSocket::connected,context,[socket,host,port,connected,openPorts,publishCandidate,finish]{
     *connected=true;(*openPorts)[host].insert(port);
     if(port==8728){socket->write(RouterCodec::sentence({"/login","=name=__fg_discovery__","=password=__invalid__"}));return;}
     publishCandidate(host);finish();
    });
    connect(socket,&QTcpSocket::readyRead,context,[socket,host,port,buffer,verified,publishCandidate,finish]{
     if(port!=8728)return;*buffer+=socket->readAll();QStringList words;for(;;){int n=RouterCodec::takeSentence(*buffer,words);if(n<=0)break;if(!words.isEmpty()&&QStringList{"!done","!trap","!fatal"}.contains(words.first())){verified->insert(host);publishCandidate(host);finish();break;}}
    });
    connect(socket,&QTcpSocket::errorOccurred,context,[finish](QAbstractSocket::SocketError){finish();});
    socket->connectToHost(host,port);QTimer::singleShot(750,socket,[host,connected,publishCandidate,finish]{if(*connected)publishCandidate(host);finish();});
   }
   if(tasks->isEmpty()&&*inFlight==0){launcher->stop();markDiagnostics({},"scan","Complete");}
  });launcher->start();
 });

 QTimer::singleShot(38000,context,[this,current,context]{
  if(current!=discoveryGeneration)return;
  for(int i=0;i<discoveryDiagnosticsRows.size();i++){auto row=discoveryDiagnosticsRows[i].toObject();if(row["mndp"]=="Waiting")row["mndp"]="No advertisement received";if(row["scan"]!="Complete")row["scan"]="Stopped at discovery deadline";discoveryDiagnosticsRows[i]=row;}
  discovering=false;discoveryContext=nullptr;++discoveryGeneration;emit discoveryReady(neighbors);
  message=neighbors.isEmpty()?"لم يتم العثور على MikroTik؛ راجع التشخيص أو اتصل بعنوان IP يدويًا • No MikroTik found; review diagnostics or connect by IP":QString("اكتمل الاكتشاف: %1 جهاز • Discovery complete: %1 device(s)").arg(neighbors.size());
  emit changed();context->deleteLater();
 });
}
