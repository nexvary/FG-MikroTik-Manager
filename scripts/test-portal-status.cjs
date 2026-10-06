const fs=require('fs'),vm=require('vm'),assert=require('assert');
const elements=[], byId={};
const document={documentElement:{lang:'ar',dir:'rtl'},querySelector:()=>null,querySelectorAll:selector=>selector==='[data-ar]'?[]:elements,getElementById:id=>byId[id]||null};
const storage={};const context={document,localStorage:{getItem:()=>null},sessionStorage:{getItem:k=>storage[k]||null},decodeURIComponent,Number,Date,console};
vm.createContext(context);vm.runInContext(fs.readFileSync('app/src/main/assets/hotspot/fg.js','utf8'),context);
assert.equal(context.fgBytes('524281093',true),'499.99 MB');assert.equal(context.fgBytes('0',true),'0 B');
assert.equal(context.fgBytes('---',true),'No total data limit');assert.equal(context.fgBytes('$(remain-bytes-total)',true),'Allowance unavailable');assert.equal(context.fgDuration('6h',false),'6 ساعة');
assert.equal(context.fgDuration('0s',true),'0 s');assert.equal(context.fgDuration('1d2h3m',true),'1 day 2 h 3 min');
function el(raw,classes){return {textContent:raw,dataset:{},hidden:true,previousElementSibling:{hidden:true},classList:{contains:x=>classes.includes(x)}}}
const quota=el('524281093',['bytes','quota']),optional=el('---',['bytes','optional-quota']);elements.push(quota,optional);
byId.username={textContent:'123456'};byId.profile=el('',[]);byId.expiry=el('',[]);
context.fgStatus();assert.equal(quota.textContent,'499.99 MB');assert(optional.hidden);assert(byId.profile.hidden);
storage.fgVoucher=JSON.stringify({u:'other',profile:'Wrong',expiry:1791000000000});context.fgStatus();assert(byId.profile.hidden);
storage.fgVoucher=JSON.stringify({u:'123456',profile:'<script>bad</script>',expiry:1791000000000});context.fgStatus();assert.equal(byId.profile.textContent,'<script>bad</script>');assert(!byId.profile.hidden);assert(!byId.expiry.hidden);
context.document.documentElement.lang='en';context.fgStatus();assert.equal(quota.textContent,'499.99 MB');
const zero=el('0',['bytes','optional-quota']);elements.push(zero);context.fgStatus();assert(!zero.hidden);assert.equal(zero.textContent,'0 B');
const html=fs.readFileSync('app/src/main/assets/hotspot/status.html','utf8');assert(!html.includes('غير متاح'));assert(html.includes('$(remain-bytes-total)'));assert(!html.includes('v.limit'));
console.log('Portal status: units, zero quotas, unlimited values, matching metadata, language and safe text passed');

// Exercise the actual login handlers: presentation changes must not change CHAP,
// separate passwords, secure-only transport or voucher QR prefill.
const login=fs.readFileSync('app/src/main/assets/hotspot/login.html','utf8');
for(const name of ['flogin.html','error.html'])assert.equal(fs.readFileSync('app/src/main/assets/hotspot/'+name,'utf8'),login);
for(const name of ['login.html','flogin.html','error.html','fg.css','fg.js'])assert.equal(fs.readFileSync('desktop/qt/resources/hotspot/'+name,'utf8'),fs.readFileSync('app/src/main/assets/hotspot/'+name,'utf8'));
function loginContext(chap,secure,hash=''){
  const hidden=new Set(['hide']);const fields={code:{value:''},same:{checked:true},password:{value:'',required:false},submittedPassword:{value:''},localError:{textContent:''},passwordBox:{classList:{toggle:(name,on)=>on?hidden.add(name):hidden.delete(name)}}};
  const c={document:{getElementById:id=>fields[id]},location:{protocol:secure?'https:':'http:',hash,pathname:'/login',search:''},history:{replaceState:()=>{}},sessionStorage:{setItem:()=>{}},URLSearchParams};
  vm.createContext(c);vm.runInContext(fs.readFileSync('app/src/main/assets/hotspot/md5.js','utf8'),c);
  const code=login.match(/<script>\n([\s\S]*?)<\/script>/)[1].replace("$(chap-id)",chap?'\\001':'').replace("$(chap-challenge)",chap?'challenge':'').replace("$(ssl-login)",secure?'yes':'no');
  vm.runInContext(code,c);return {c,fields,hidden};
}
let t=loginContext(true,false);t.fields.code.value='card123';assert(t.c.loginVoucher());assert.equal(t.fields.submittedPassword.value,require('crypto').createHash('md5').update('\x01card123challenge').digest('hex'));
t.fields.same.checked=false;t.c.showPassword();assert(t.fields.password.required);assert(!t.hidden.has('hide'));assert(!t.c.loginVoucher());t.fields.password.value='separate';assert(t.c.loginVoucher());assert.equal(t.fields.submittedPassword.value,require('crypto').createHash('md5').update('\x01separatechallenge').digest('hex'));
t=loginContext(false,true);t.fields.code.value='123456';assert(t.c.loginVoucher());assert.equal(t.fields.submittedPassword.value,'123456');
t=loginContext(false,false);t.fields.code.value='123456';assert(!t.c.loginVoucher());assert.equal(t.fields.submittedPassword.value,'');
t=loginContext(true,false,'#u=QR123&same=1');assert.equal(t.fields.code.value,'QR123');assert(t.fields.same.checked);assert(!t.fields.password.required);
console.log('Portal login: matching Android/Windows assets, CHAP, separate password, HTTPS, unsafe transport refusal and QR prefill passed');
