const fs=require('fs'),vm=require('vm'),assert=require('assert');
const elements=[], byId={};
const document={documentElement:{lang:'ar',dir:'rtl'},querySelectorAll:selector=>selector==='[data-ar]'?[]:elements,getElementById:id=>byId[id]||null};
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
