/** Personal, owner-only Apps Script web app. Never expose as an anonymous app. */
function doGet() {
  assertOwner_();
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('출장비 보내기').addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

function assertOwner_() {
  const effective = Session.getEffectiveUser().getEmail();
  const active = Session.getActiveUser().getEmail();
  if (!effective || !active || effective !== active) throw new Error('본인 계정으로 로그인하고 접근 권한을 본인만으로 설정해 주세요.');
  return effective;
}

function getBootstrap() {
  const email = assertOwner_();
  return {email: email, recipient: PropertiesService.getUserProperties().getProperty('recipient') || ''};
}

function recipient_(value) {
  const email = typeof value === 'string' ? value.trim() : '';
  if (email.length > 254 || !/^[a-zA-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?)+$/.test(email)) {
    throw new Error('받는 이메일 주소 한 개를 정확히 입력해 주세요.');
  }
  return email;
}

function saveRecipient(email) {
  assertOwner_();
  const recipient = recipient_(email);
  PropertiesService.getUserProperties().setProperty('recipient', recipient);
  return {recipient: recipient};
}

function validateExpense_(p, options) {
  if (!p || typeof p !== 'object') throw new Error('메일 내용을 확인해 주세요.');
  if (typeof p.requestId !== 'string' || !/^[a-f0-9-]{36}$/i.test(p.requestId)) throw new Error('요청 번호가 올바르지 않습니다.');
  if (typeof p.month !== 'string' || !/^20\d{2}-(0[1-9]|1[0-2])$/.test(p.month)) throw new Error('정산 월을 확인해 주세요.');
  const allowBlankRecipient = !!(options && options.allowBlankRecipient);
  const recipientValue = typeof p.recipient === 'string' ? p.recipient.trim() : '';
  const recipient = allowBlankRecipient && !recipientValue ? '' : recipient_(recipientValue);
  if (typeof p.subject !== 'string' || !p.subject.trim() || p.subject.length > 150 || /[\r\n\x00-\x1f\x7f]/.test(p.subject)) throw new Error('제목은 줄바꿈 없이 150자 이내로 입력해 주세요.');
  if (typeof p.body !== 'string' || !p.body.trim() || p.body.length > 5000) throw new Error('본문은 1~5,000자로 입력해 주세요.');
  if (!Array.isArray(p.files) || !p.files.length || p.files.length > 30) throw new Error('사진을 1~30장 첨부해 주세요.');
  let total = 0;
  const files = p.files.map(function(f) {
    if (!f || !['image/jpeg','image/png'].includes(f.mimeType)) throw new Error('JPG 또는 PNG 사진만 첨부할 수 있습니다.');
    if (typeof f.name !== 'string' || !f.name.trim() || f.name.length > 180 || /[\r\n\x00-\x1f\x7f]/.test(f.name)) throw new Error('사진 파일 이름을 확인해 주세요.');
    if (typeof f.base64 !== 'string' || f.base64.length > 24000000) throw new Error('사진은 전체 18 MB까지 첨부할 수 있습니다.');
    if (!f.base64 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(f.base64)) throw new Error('사진 데이터를 읽을 수 없습니다. 다시 선택해 주세요.');
    const bytes = Utilities.base64Decode(f.base64);
    total += bytes.length;
    if (total > 18000000) throw new Error('사진은 전체 18 MB까지 첨부할 수 있습니다.');
    const header = bytes.slice(0,8).map(function(b) { return (b+256)%256; });
    const valid = f.mimeType === 'image/png' ? header.join(',') === '137,80,78,71,13,10,26,10' : header[0]===255 && header[1]===216 && header[2]===255;
    if (!valid) throw new Error('사진 형식이 올바르지 않습니다. JPG 또는 PNG 원본을 선택해 주세요.');
    return {name:f.name, mimeType:f.mimeType, base64:f.base64};
  });
  return {requestId:p.requestId,month:p.month,recipient:recipient,subject:p.subject.trim(),body:p.body,files:files};
}

function wrapBase64_(s) { return s.match(/.{1,76}/g).join('\r\n'); }
function encodedHeader_(s) {
  // Keep RFC 2047 encoded words short, without splitting a Unicode code point.
  const chunks = Array.from(s).reduce(function(a,c) {
    if (!a.length || Array.from(a[a.length-1]).length >= 12) a.push('');
    a[a.length-1] += c; return a;
  },[]);
  return chunks.map(function(c) { return '=?UTF-8?B?' + Utilities.base64Encode(c,Utilities.Charset.UTF_8) + '?='; }).join('\r\n ');
}
function buildMime_(p, sender) {
  const boundary = 'expense_' + Utilities.getUuid();
  const lines = ['From: '+sender];
  if (p.recipient) lines.push('To: '+p.recipient);
  lines.push('Subject: '+encodedHeader_(p.subject),'MIME-Version: 1.0','Content-Type: multipart/mixed; boundary="'+boundary+'"','','--'+boundary,'Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',wrapBase64_(Utilities.base64Encode(p.body,Utilities.Charset.UTF_8)));
  p.files.forEach(function(f,i) {
    const extension = f.mimeType==='image/png' ? 'png' : 'jpg';
    const name = encodeURIComponent(f.name).replace(/['()*]/g,function(c) {return '%'+c.charCodeAt(0).toString(16).toUpperCase();});
    lines.push('--'+boundary,'Content-Type: '+f.mimeType,'Content-Disposition: attachment; filename="evidence-'+(i+1)+'.'+extension+'";', " filename*=UTF-8''"+name,'Content-Transfer-Encoding: base64','',wrapBase64_(f.base64));
  });
  lines.push('--'+boundary+'--','');
  return lines.join('\r\n');
}

function sendExpense(payload) {
  const sender = assertOwner_();
  const p = validateExpense_(payload);
  const hash = Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,JSON.stringify(p),Utilities.Charset.UTF_8));
  const raw = Utilities.base64EncodeWebSafe(buildMime_(p,sender),Utilities.Charset.UTF_8);
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) throw new Error('다른 발송이 진행 중입니다. 잠시 후 다시 확인해 주세요.');
  try {
    const props = PropertiesService.getUserProperties();
    const key = 'request_'+p.requestId;
    const old = props.getProperty(key);
    if (old) {
      const saved = JSON.parse(old);
      if (saved.hash !== hash) throw new Error('이 요청의 내용이 변경되었습니다. 보낸편지함을 확인한 후 새로 작성해 주세요.');
      if (saved.result) return saved.result;
      throw new Error('발송 결과를 확인할 수 없습니다. Gmail 보낸편지함을 확인해 주세요. 자동으로 다시 보내지 않습니다.');
    }
    // Save before calling Gmail. If the response is lost, leave pending and fail closed.
    props.setProperty(key,JSON.stringify({hash:hash,created:Date.now()}));
    let sent;
    try { sent = Gmail.Users.Messages.send({raw:raw},'me'); }
    catch(e) { throw new Error('Gmail 발송 결과를 확인할 수 없습니다. 보낸편지함과 Google 권한을 확인해 주세요. 자동으로 다시 보내지 않습니다.'); }
    if (!sent || !sent.id) throw new Error('Gmail 발송 결과를 확인할 수 없습니다. 보낸편지함을 확인해 주세요.');
    const result = {messageId:sent.id,recipient:p.recipient,subject:p.subject,count:p.files.length};
    props.setProperty(key,JSON.stringify({hash:hash,created:Date.now(),result:result}));
    return result;
  } finally { lock.releaseLock(); }
}

function createExpenseDraft(payload) {
  const sender = assertOwner_();
  const p = validateExpense_(payload, {allowBlankRecipient: true});
  const hash = Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,JSON.stringify(p),Utilities.Charset.UTF_8));
  const raw = Utilities.base64EncodeWebSafe(buildMime_(p,sender),Utilities.Charset.UTF_8);
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) throw new Error('다른 Gmail 작업이 진행 중입니다. 잠시 후 다시 확인해 주세요.');
  try {
    const props = PropertiesService.getUserProperties();
    const key = 'draft_'+p.requestId;
    const old = props.getProperty(key);
    if (old) {
      const saved = JSON.parse(old);
      if (saved.hash !== hash) throw new Error('이 요청의 내용이 변경되었습니다. 새 테스트 초안을 작성해 주세요.');
      if (saved.result) return saved.result;
      throw new Error('초안 생성 결과를 확인할 수 없습니다. Gmail 임시보관함을 확인해 주세요. 자동으로 다시 만들지 않습니다.');
    }
    props.setProperty(key,JSON.stringify({hash:hash,created:Date.now()}));
    let draft;
    try { draft = Gmail.Users.Drafts.create({message:{raw:raw}},'me'); }
    catch(e) { throw new Error('Gmail 초안 생성 결과를 확인할 수 없습니다. Gmail 임시보관함과 Google 권한을 확인해 주세요. 자동으로 다시 만들지 않습니다.'); }
    if (!draft || !draft.id) throw new Error('Gmail 초안 생성 결과를 확인할 수 없습니다. 임시보관함을 확인해 주세요.');
    const result = {draftId:draft.id,recipient:p.recipient,subject:p.subject,count:p.files.length};
    props.setProperty(key,JSON.stringify({hash:hash,created:Date.now(),result:result}));
    return result;
  } finally { lock.releaseLock(); }
}
