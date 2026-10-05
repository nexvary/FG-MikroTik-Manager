import test from 'node:test';
import assert from 'node:assert/strict';
import {money,bytes,userStatus,filterUsers} from '../fg_server/web/panel.js';
test('integer minor units preserve signs, fractions and reject unsafe values',()=>{
 assert.equal(money(-105),'-1.05');assert.equal(money(1),'0.01');assert.equal(money(0),'0.00');assert.equal(money(999999999999),'9999999999.99');assert.equal(money(Number.MAX_SAFE_INTEGER+1),'—');assert.equal(money('123'),'—');assert.equal(money(1.5),'—');
});
test('counters sum using integers and never invent zero for missing data',()=>{
 assert.equal(bytes(100,200),'300 B');assert.equal(bytes(undefined),'—');assert.equal(bytes(Number.MAX_SAFE_INTEGER,Number.MAX_SAFE_INTEGER),'18014398509481982 B');
});
test('RADIUS status and active/expired/disabled/search filters remain independent',()=>{
 const now=Date.parse('2026-10-05T00:00:00Z');const rows=[{username:'Family',enabled:true,expires:'2026-10-06T00:00:00Z',active_sessions:1},{username:'OLD',enabled:true,expires:'2026-10-04T00:00:00Z',active_sessions:0},{username:'Disabled',enabled:false,expires:'2026-10-06T00:00:00Z',active_sessions:0}];
 assert.equal(userStatus(rows[0],now),'enabled');assert.equal(userStatus(rows[1],now),'expired');assert.equal(userStatus(rows[2],now),'disabled');assert.deepEqual(filterUsers(rows,'fam','active',now),[rows[0]]);assert.deepEqual(filterUsers(rows,'old','expired',now),[rows[1]]);assert.deepEqual(filterUsers(rows,'','disabled',now),[rows[2]]);assert.deepEqual(filterUsers(rows,'old','active',now),[]);
});
test('CSV quotes cells, preserves UTF-8 and neutralizes spreadsheet formulas',async()=>{
 const {toCsv}=await import('../fg_server/web/panel.js');
 assert.equal(toCsv(['الاسم','Value'],[['أحمد','a,"b"'],[' =HYPERLINK("x")',null],['\t@SUM(1)','safe']]),'\uFEFF"الاسم","Value"\r\n"أحمد","a,""b"""\r\n"\' =HYPERLINK(""x"")",""\r\n"\'\t@SUM(1)","safe"');
});
test('summaries use exclusive user states and exact business record types',async()=>{
 const {summarizeUsers,countRecords,businessTables}=await import('../fg_server/web/panel.js');
 const now=Date.parse('2026-10-05');
 assert.deepEqual(summarizeUsers([{enabled:true,expires:'2026-10-06'},{enabled:true,expires:'2026-10-04'},{enabled:false,expires:'2026-10-04'}],now),{enabled:1,expired:1,disabled:1});
 assert.deepEqual(countRecords([{table:'sales'},{table:'sales'},{table:'sale_voids'}]),{sales:2,sale_voids:1});
 assert.equal(businessTables.length,17);assert.equal(new Set(businessTables).size,17);
});
