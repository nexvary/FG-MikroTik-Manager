#include "Vault.hpp"
#include <stdexcept>
#ifdef Q_OS_WIN
#define NOMINMAX
#include <windows.h>
#include <wincrypt.h>
#include <bcrypt.h>
namespace {
void require(bool ok,const char *why){if(!ok)throw std::runtime_error(why);}
struct Alg{BCRYPT_ALG_HANDLE h=nullptr;~Alg(){if(h)BCryptCloseAlgorithmProvider(h,0);}};
struct Key{BCRYPT_KEY_HANDLE h=nullptr;~Key(){if(h)BCryptDestroyKey(h);}};
QByteArray random(int n){QByteArray b(n,'\0');require(BCryptGenRandom(nullptr,reinterpret_cast<PUCHAR>(b.data()),n,BCRYPT_USE_SYSTEM_PREFERRED_RNG)>=0,"RANDOM_FAILED");return b;}
QByteArray crypt(const QByteArray &bytes,const QString &password,bool encrypt){
 require(encrypt?password.size()>=12:!password.isEmpty(),"PASSWORD_SHORT");require(encrypt?bytes.size()<=20*1024*1024:bytes.size()>=52&&bytes.size()<=20*1024*1024+52&&bytes.startsWith("FGMTMB02"),"INVALID_BACKUP");
 QByteArray header=encrypt?QByteArray("FGMTMB02")+random(16)+random(12):bytes.left(36),salt=header.mid(8,16),iv=header.mid(24,12),tag=encrypt?QByteArray(16,'\0'):bytes.right(16),input=encrypt?bytes:bytes.mid(36,bytes.size()-52);
 Alg hash;require(BCryptOpenAlgorithmProvider(&hash.h,BCRYPT_SHA256_ALGORITHM,nullptr,BCRYPT_ALG_HANDLE_HMAC_FLAG)>=0,"KDF_PROVIDER");QByteArray pass=password.toUtf8(),derived(32,'\0');auto status=BCryptDeriveKeyPBKDF2(hash.h,reinterpret_cast<PUCHAR>(pass.data()),ULONG(pass.size()),reinterpret_cast<PUCHAR>(salt.data()),ULONG(salt.size()),210000,reinterpret_cast<PUCHAR>(derived.data()),32,0);SecureZeroMemory(pass.data(),pass.size());require(status>=0,"KDF_FAILED");
 Alg aes;require(BCryptOpenAlgorithmProvider(&aes.h,BCRYPT_AES_ALGORITHM,nullptr,0)>=0,"AES_PROVIDER");require(BCryptSetProperty(aes.h,BCRYPT_CHAINING_MODE,reinterpret_cast<PUCHAR>(const_cast<wchar_t*>(BCRYPT_CHAIN_MODE_GCM)),sizeof(BCRYPT_CHAIN_MODE_GCM),0)>=0,"AES_MODE");Key key;status=BCryptGenerateSymmetricKey(aes.h,&key.h,nullptr,0,reinterpret_cast<PUCHAR>(derived.data()),32,0);SecureZeroMemory(derived.data(),derived.size());require(status>=0,"AES_KEY");
 BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO info;BCRYPT_INIT_AUTH_MODE_INFO(info);info.pbNonce=reinterpret_cast<PUCHAR>(iv.data());info.cbNonce=12;info.pbAuthData=reinterpret_cast<PUCHAR>(header.data());info.cbAuthData=36;info.pbTag=reinterpret_cast<PUCHAR>(tag.data());info.cbTag=16;
 QByteArray output(input.size(),'\0');ULONG written=0;
 status=encrypt?BCryptEncrypt(key.h,reinterpret_cast<PUCHAR>(input.data()),ULONG(input.size()),&info,nullptr,0,reinterpret_cast<PUCHAR>(output.data()),ULONG(output.size()),&written,0):BCryptDecrypt(key.h,reinterpret_cast<PUCHAR>(input.data()),ULONG(input.size()),&info,nullptr,0,reinterpret_cast<PUCHAR>(output.data()),ULONG(output.size()),&written,0);
 if(status<0){SecureZeroMemory(output.data(),output.size());throw std::runtime_error("BACKUP_AUTHENTICATION_FAILED");}output.resize(written);return encrypt?header+output+tag:output;
}
}
QByteArray Vault::protect(const QByteArray &clear){DATA_BLOB in{DWORD(clear.size()),reinterpret_cast<BYTE*>(const_cast<char*>(clear.data()))},out{};require(CryptProtectData(&in,L"FG MTM",nullptr,nullptr,nullptr,CRYPTPROTECT_UI_FORBIDDEN,&out),"VAULT_PROTECT_FAILED");QByteArray result(reinterpret_cast<char*>(out.pbData),out.cbData);SecureZeroMemory(out.pbData,out.cbData);LocalFree(out.pbData);return result;}
QByteArray Vault::unprotect(const QByteArray &cipher){DATA_BLOB in{DWORD(cipher.size()),reinterpret_cast<BYTE*>(const_cast<char*>(cipher.data()))},out{};require(CryptUnprotectData(&in,nullptr,nullptr,nullptr,nullptr,CRYPTPROTECT_UI_FORBIDDEN,&out),"VAULT_UNPROTECT_FAILED");QByteArray result(reinterpret_cast<char*>(out.pbData),out.cbData);SecureZeroMemory(out.pbData,out.cbData);LocalFree(out.pbData);return result;}
QByteArray Vault::encrypt(const QByteArray &clear,const QString &password){return crypt(clear,password,true);}
QByteArray Vault::decrypt(const QByteArray &cipher,const QString &password){return crypt(cipher,password,false);}
#else
QByteArray Vault::protect(const QByteArray &){throw std::runtime_error("Windows DPAPI required");}
QByteArray Vault::unprotect(const QByteArray &){throw std::runtime_error("Windows DPAPI required");}
QByteArray Vault::encrypt(const QByteArray &,const QString &){throw std::runtime_error("Windows CNG required");}
QByteArray Vault::decrypt(const QByteArray &,const QString &){throw std::runtime_error("Windows CNG required");}
#endif
