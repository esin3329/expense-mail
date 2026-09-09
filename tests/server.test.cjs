const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1cAAAAASUVORK5CYII=';
function fixture(changes={}) {
  return {requestId:'12345678-1234-4234-8234-123456789abc',month:'2026-08',recipient:'finance@example.com',subject:'8월 출장비 증빙자료',body:'8월 출장비 증빙자료입니다.',files:[{name:'교통비.png',mimeType:'image/png',base64:png}],...changes};
}
function runtime({failSend=false,failDraft=false,active='owner@gmail.com'}={}) {
  const values = new Map(); const outbound=[]; const drafts=[];
  const props={getProperty:k=>values.get(k)||null,setProperty:(k,v)=>values.set(k,v),getProperties:()=>Object.fromEntries(values),deleteProperty:k=>values.delete(k)};
  const c=vm.createContext({
    Session:{getEffectiveUser:()=>({getEmail:()=> 'owner@gmail.com'}),getActiveUser:()=>({getEmail:()=>active})},
    PropertiesService:{getUserProperties:()=>props},
    LockService:{getScriptLock:()=>({tryLock:()=>true,releaseLock(){}})},
    Utilities:{getUuid:()=>crypto.randomUUID(),Charset:{UTF_8:'UTF-8'},DigestAlgorithm:{SHA_256:'SHA-256'},
      base64Encode:x=>Buffer.from(x).toString('base64'),base64EncodeWebSafe:x=>Buffer.from(x).toString('base64url'),base64Decode:x=>Array.from(Buffer.from(x,'base64')),
      computeDigest:(_,x)=>Array.from(crypto.createHash('sha256').update(x).digest())},
    Gmail:{Users:{Messages:{send:(message,user)=>{outbound.push({message,user});if(failSend)throw Error('timeout');return {id:'abc123',threadId:'def456',labelIds:['SENT']};}},Drafts:{create:(request,user)=>{drafts.push({request,user});if(failDraft)throw Error('timeout');return {id:'draft123',message:{id:'draft-message123'}};}}}}
  });
  const source=fs.existsSync('app/Code.gs')?fs.readFileSync('app/Code.gs','utf8'):'';
  vm.runInContext(source,c);
  return {c,outbound,drafts,values};
}
test('sends original attachment bytes and UTF-8 Korean content to exactly one recipient',()=>{
  const {c,outbound}=runtime(); assert.equal(typeof c.sendExpense,'function');
  const r=c.sendExpense(fixture()); assert.equal(r.messageId,'abc123'); assert.equal(outbound.length,1);
  assert.equal(outbound[0].user,'me');
  const mime=Buffer.from(outbound[0].message.raw,'base64url').toString('utf8');
  assert.match(mime,/To: finance@example.com\r\n/);
  const subject=mime.match(/Subject: =\?UTF-8\?B\?([^?]+)\?=/)[1];
  assert.equal(Buffer.from(subject,'base64').toString(),'8월 출장비 증빙자료');
  assert.ok(mime.includes(Buffer.from('8월 출장비 증빙자료입니다.').toString('base64')));
  const attachment=mime.split('Content-Disposition: attachment;')[1].split('\r\n\r\n')[1].split('\r\n--')[0].replace(/\r\n/g,'');
  assert.deepEqual(Buffer.from(attachment,'base64'),Buffer.from(png,'base64'));
});
test('blocks invalid input before any outbound mail',()=>{
  const {c,outbound}=runtime(); assert.equal(typeof c.sendExpense,'function');
  for(const change of [{recipient:'a@b.com\r\nBcc: x@y.com'},{recipient:'a@b.com,c@d.com'},{subject:'x\r\nBcc:x@y.com'},{month:'2026-13'},{files:[]},{files:[{name:'x.png',mimeType:'image/png',base64:'%%%%'}]},{files:[{name:'x.png',mimeType:'image/png',base64:Buffer.from('not an image').toString('base64')}]}]) {
    assert.throws(()=>c.sendExpense(fixture(change)));
  }
  assert.equal(outbound.length,0);
});
test('rejects excessive total attachment bytes before sending',()=>{
  const {c,outbound}=runtime(); assert.equal(typeof c.sendExpense,'function');
  const b=Buffer.alloc(18000001);Buffer.from(png,'base64').copy(b);
  assert.throws(()=>c.sendExpense(fixture({files:[{name:'large.png',mimeType:'image/png',base64:b.toString('base64')}]})),/18/);
  assert.equal(outbound.length,0);
});
test('accepts a normal multi-megabyte photo without exhausting regex stack',()=>{
  const {c,outbound}=runtime();
  const b=Buffer.alloc(2000000);Buffer.from(png,'base64').copy(b);
  const result=c.sendExpense(fixture({files:[{name:'photo.png',mimeType:'image/png',base64:b.toString('base64')}]}));
  assert.equal(result.count,1);assert.equal(outbound.length,1);
});
test('replaying an acknowledged request returns its result without sending again',()=>{
  const {c,outbound}=runtime(); assert.equal(typeof c.sendExpense,'function');
  c.sendExpense(fixture());assert.equal(c.sendExpense(fixture()).messageId,'abc123');assert.equal(outbound.length,1);
  assert.throws(()=>c.sendExpense(fixture({subject:'changed'})));assert.equal(outbound.length,1);
});
test('an ambiguous Gmail result is not silently retried',()=>{
  const {c,outbound}=runtime({failSend:true});assert.equal(typeof c.sendExpense,'function');
  assert.throws(()=>c.sendExpense(fixture()),/확인/);
  assert.throws(()=>c.sendExpense(fixture()),/확인/);assert.equal(outbound.length,1);
});
test('rejects a caller other than the account executing this private app',()=>{
  const {c,outbound}=runtime({active:'other@gmail.com'});assert.equal(typeof c.sendExpense,'function');
  assert.throws(()=>c.sendExpense(fixture()),/본인/);assert.equal(outbound.length,0);
});
test('saves and restores the validated recipient',()=>{
  const {c}=runtime();assert.equal(typeof c.saveRecipient,'function');
  c.saveRecipient(' finance@example.com ');assert.equal(c.getBootstrap().recipient,'finance@example.com');
  assert.throws(()=>c.saveRecipient('bad'));assert.equal(c.getBootstrap().recipient,'finance@example.com');
});
test('creates an unsent Gmail draft with the original attachment bytes',()=>{
  const {c,outbound,drafts}=runtime(); assert.equal(typeof c.createExpenseDraft,'function');
  const result=c.createExpenseDraft(fixture({recipient:''}));
  assert.equal(result.draftId,'draft123'); assert.equal(result.recipient,'');
  assert.equal(outbound.length,0); assert.equal(drafts.length,1); assert.equal(drafts[0].user,'me');
  const mime=Buffer.from(drafts[0].request.message.raw,'base64url').toString('utf8');
  assert.doesNotMatch(mime,/^To:/m);
  assert.match(mime,/Subject: =\?UTF-8\?B\?/);
  const attachment=mime.split('Content-Disposition: attachment;')[1].split('\r\n\r\n')[1].split('\r\n--')[0].replace(/\r\n/g,'');
  assert.deepEqual(Buffer.from(attachment,'base64'),Buffer.from(png,'base64'));
});
test('replaying an acknowledged draft request does not create another draft',()=>{
  const {c,drafts}=runtime(); const p=fixture({recipient:''});
  assert.equal(c.createExpenseDraft(p).draftId,'draft123');
  assert.equal(c.createExpenseDraft(p).draftId,'draft123'); assert.equal(drafts.length,1);
});
test('an ambiguous draft result is not silently retried',()=>{
  const {c,drafts}=runtime({failDraft:true}); assert.equal(typeof c.createExpenseDraft,'function');
  assert.throws(()=>c.createExpenseDraft(fixture({recipient:''})),/확인/);
  assert.throws(()=>c.createExpenseDraft(fixture({recipient:''})),/확인/); assert.equal(drafts.length,1);
});
