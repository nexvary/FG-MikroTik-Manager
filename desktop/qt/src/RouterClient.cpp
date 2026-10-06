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
#include <memory>
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
RouterClient::RouterClient(QObject*p):QObject(p){deadline.setSingleShot(true);connect(&deadline,&QTimer::timeout,this,[this]{fail("انتهت مهلة الراوتر • Router timeout");});
 connect(&socket,&QSslSocket::connected,this,[this]{if(protocol=="API")sendLogin();});connect(&socket,&QSslSocket::encrypted,this,&RouterClient::sendLogin);
 connect(&socket,&QSslSocket::readyRead,this,&RouterClient::receive);
 connect(&socket,&QSslSocket::errorOccurred,this,[this](QAbstractSocket::SocketError){if(active)fail("انقطع اتصال الراوتر • Router connection lost");});
 connect(&socket,&QSslSocket::sslErrors,this,[this](const QList<QSslError>&){fail("شهادة الراوتر غير موثوقة • Router certificate rejected");});
 connect(&socket,&QSslSocket::disconnected,this,[this]{authenticated=false;if(active)fail("انقطع اتصال الراوتر • Router connection lost");emit changed();});}
RouterClient::~RouterClient(){callback={};active=false;deadline.stop();socket.disconnect(this);socket.abort();net.disconnect(this);password.fill(QChar(0));}
void RouterClient::configure(QString h,int p,QString u,QString pw,QString proto){close();host=h.trimmed();port=p;username=u;password=pw;protocol=proto;message.clear();emit changed();}
void RouterClient::close(){++generation;bool running=active;auto cb=std::move(callback);callback={};active=false;authenticated=false;deadline.stop();socket.abort();input.clear();password.clear();if(running&&cb)cb({{},"أغلقت الجلسة • Session closed",wrote&&action!="print"});emit changed();}
void RouterClient::read(QString menu,Done done){execute(menu,"print",{},std::move(done));}
void RouterClient::execute(QString menu,QString operation,QJsonObject attrs,Done done){if(!authorization()){done({{},"ACCESS_DENIED"});return;}if(active){done({{},"الراوتر مشغول • Router busy"});return;}
 if(host.isEmpty()||username.isEmpty()||port<1||port>65535||!QStringList{"REST","API","API_SSL"}.contains(protocol)||!QRegularExpression("^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$").match(menu).hasMatch()) {done({{},"إعداد اتصال غير صالح • Invalid router settings"});return;}
 if(protocol!="REST"&&!(socket.state()==QAbstractSocket::ConnectedState&&authenticated))socket.abort();
 ++generation;active=true;wrote=false;loggingIn=false;path=menu;action=operation;attributes=attrs;rows={};trap.clear();callback=std::move(done);deadline.start(20000);message="جارٍ الاتصال • Connecting";emit changed();
 if(protocol=="REST"){
  QUrl url;url.setScheme("https");url.setHost(host);url.setPort(port);url.setPath("/rest/"+path+(action.isEmpty()||action=="add"||action=="print"&&attributes.isEmpty()?QString{}:"/"+action));
  QNetworkRequest req(url);req.setTransferTimeout(20000);req.setAttribute(QNetworkRequest::RedirectPolicyAttribute,QNetworkRequest::ManualRedirectPolicy);req.setHeader(QNetworkRequest::ContentTypeHeader,"application/json");req.setRawHeader("Authorization","Basic "+(username+":"+password).toUtf8().toBase64());
  auto reply=action=="print"&&attributes.isEmpty()?net.get(req):net.sendCustomRequest(req,action=="add"?"PUT":"POST",QJsonDocument(attributes).toJson(QJsonDocument::Compact));
  auto bytes=std::make_shared<QByteArray>();auto current=generation;wrote=action!="print";
  connect(reply,&QNetworkReply::readyRead,this,[reply,bytes]{*bytes+=reply->readAll();if(bytes->size()>32*1024*1024)reply->abort();});
  connect(&deadline,&QTimer::timeout,reply,&QNetworkReply::abort);
  connect(reply,&QNetworkReply::finished,this,[this,reply,bytes,current]{*bytes+=reply->readAll();int code=reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();reply->deleteLater();if(!active||current!=generation)return;
   if(reply->error()!=QNetworkReply::NoError||code<200||code>=300){finish({{},QString("رفض الراوتر أو فشل الاتصال (%1) • Router request failed (%1)").arg(code),code==0&&wrote});return;}
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
void RouterClient::finish(RouterReply result){++generation;deadline.stop();active=false;loggingIn=false;message=result.ok()?"تم • Done":result.error;auto cb=std::move(callback);callback={};emit changed();if(cb)cb(result);}
void RouterClient::fail(QString reason){if(!active)return;bool uncertain=wrote&&action!="print";active=false;authenticated=false;socket.abort();input.clear();finish({{},reason,uncertain});}
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
void RouterClient::discover(){auto udp=new QUdpSocket(this);if(!udp->bind(QHostAddress::AnyIPv4,5678,QUdpSocket::ShareAddress|QUdpSocket::ReuseAddressHint)){delete udp;message="تعذر بدء اكتشاف الراوتر • Discovery bind failed";emit changed();return;}
 neighbors={};connect(udp,&QUdpSocket::readyRead,this,[this,udp]{while(udp->hasPendingDatagrams()){if(udp->pendingDatagramSize()>65536){udp->receiveDatagram();continue;}QByteArray b(int(udp->pendingDatagramSize()),'\0');QHostAddress sender;udp->readDatagram(b.data(),b.size(),&sender);if(b.size()<4)continue;QJsonObject row{{"host",sender.toString()}};int pos=4;bool valid=true;while(pos+4<=b.size()){auto u=[&](int i){return quint8(b[i]);};int type=(u(pos)<<8)|u(pos+1),n=(u(pos+2)<<8)|u(pos+3);pos+=4;if(pos+n>b.size()){valid=false;break;}auto v=b.mid(pos,n);if(type!=1&&type!=17)while(v.endsWith(char(0)))v.chop(1);if(type==5)row["name"]=QString::fromUtf8(v);else if(type==7)row["version"]=QString::fromUtf8(v);else if(type==8)row["platform"]=QString::fromUtf8(v);else if(type==12)row["board"]=QString::fromUtf8(v);else if(type==16)row["interface"]=QString::fromUtf8(v);else if(type==17&&n==4)row["host"]=QHostAddress((quint32(quint8(v[0]))<<24)|(quint32(quint8(v[1]))<<16)|(quint32(quint8(v[2]))<<8)|quint8(v[3])).toString();else if(type==1&&n==6)row["mac"]=QString::fromLatin1(v.toHex(':')).toUpper();pos+=n;}if(!valid||pos!=b.size())continue;if(row["name"].toString().isEmpty())row["name"]="MikroTik";bool duplicate=false;for(auto v:neighbors)if(row.contains("mac")?v.toObject()["mac"]==row["mac"]:v.toObject()["host"]==row["host"]&&v.toObject()["name"]==row["name"]){duplicate=true;break;}if(!duplicate)neighbors.append(row);}});
 udp->writeDatagram(QByteArray(4,'\0'),QHostAddress::Broadcast,5678);
 for(auto iface:QNetworkInterface::allInterfaces())for(auto entry:iface.addressEntries())if(!entry.broadcast().isNull())udp->writeDatagram(QByteArray(4,'\0'),entry.broadcast(),5678);
 QTimer::singleShot(4000,udp,[this,udp]{udp->close();udp->deleteLater();emit discoveryReady(neighbors);message="اكتمل اكتشاف الشبكة المحلية • LAN discovery complete";emit changed();});}
