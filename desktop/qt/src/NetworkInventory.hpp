#pragma once
#include <QJsonArray>
#include <QJsonObject>
#include <QHostAddress>
#include <QRegularExpression>
#include <QMap>

namespace NetworkInventory {
// Passive evidence only: DHCP/ARP entries are not proof of current reachability.
inline QJsonArray combine(const QJsonArray &neighbors,const QJsonArray &leases,const QJsonArray &arp) {
 QMap<QString,QJsonObject> devices;
 auto add=[&](QJsonObject input,QString source) {
  QString host=input.value("active-address").toString();if(host.isEmpty())host=input.value("address").toString();if(host.isEmpty())host=input.value("host").toString();
  QHostAddress ip(host);if(ip.protocol()!=QAbstractSocket::IPv4Protocol||ip.isNull()||ip.isMulticast()||host=="255.255.255.255")host.clear();
  QString mac=input.value("active-mac-address").toString();if(mac.isEmpty())mac=input.value("mac-address").toString();if(mac.isEmpty())mac=input.value("mac").toString();mac=mac.trimmed().toUpper();
  if(!QRegularExpression("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").match(mac).hasMatch()||mac=="00:00:00:00:00:00")mac.clear();
  if(host.isEmpty()&&mac.isEmpty())return;
  const auto key=mac.isEmpty()?"ip:"+host:"mac:"+mac;
  auto row=devices.value(key);row["mac"]=mac;if(!host.isEmpty())row["host"]=host;
  QString name=input.value("identity").toString();if(name.isEmpty())name=input.value("host-name").toString();if(name.isEmpty())name=input.value("name").toString();if(!name.isEmpty())row["name"]=name;
  auto interface=input.value("interface").toString();if(!interface.isEmpty())row["interface"]=interface;
  auto platform=input.value("platform").toString();if(!platform.isEmpty())row["platform"]=platform;
  if(source=="Neighbor"&&platform.contains("MikroTik",Qt::CaseInsensitive))row["management"]="RouterOS advertised";
  if(!row.contains("management"))row["management"]="Unverified device";
  auto sources=row.value("source").toString().split(" / ",Qt::SkipEmptyParts);if(!sources.contains(source))sources.append(source);row["source"]=sources.join(" / ");
  row["evidence"]="Seen in router tables; availability not tested";devices[key]=row;
 };
 for(auto v:neighbors)add(v.toObject(),"Neighbor");for(auto v:leases)add(v.toObject(),"DHCP");for(auto v:arp)add(v.toObject(),"ARP");
 // An IP-only ARP record can enrich one MAC record, but never join conflicting MACs.
 const auto keys=devices.keys();for(auto key:keys)if(key.startsWith("ip:")) {
  auto row=devices.value(key);QString target;int count=0;for(auto it=devices.cbegin();it!=devices.cend();++it)if(it.key().startsWith("mac:")&&it.value().value("host")==row.value("host")){target=it.key();count++;}
  if(count==1){auto matched=devices.value(target);auto sources=matched.value("source").toString().split(" / ");for(auto source:row.value("source").toString().split(" / "))if(!sources.contains(source))sources.append(source);matched["source"]=sources.join(" / ");devices[target]=matched;devices.remove(key);}
 }
 QJsonArray result;for(auto row:devices){if(!row.contains("name"))row["name"]="Unnamed device";result.append(row);}return result;
}
}
