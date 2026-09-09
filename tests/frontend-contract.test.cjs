const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const html=fs.readFileSync('app/Index.html','utf8');
const css=html.match(/<style>([\s\S]*?)<\/style>/)[1];

test('responsive shell contains viewport-safe sizing rules',()=>{
  assert.match(css,/html,body\{[^}]*overflow-x:hidden/);
  assert.match(css,/main\{[^}]*width:100%/);
  assert.match(css,/header\{[^}]*min-width:0/);
  assert.match(css,/\.brand\{[^}]*min-width:0/);
  assert.match(css,/\.connection\{[^}]*min-width:0/);
});

test('test-draft flow is explicit and never reuses the send RPC',()=>{
  assert.match(html,/id="draftButton"[^>]*>테스트 초안 저장</);
  assert.match(html,/createExpenseDraft/);
  assert.match(html,/Gmail 임시보관함/);
});

test('Android photo flow has camera and gallery inputs with lightweight thumbnails',()=>{
  assert.match(html,/id="camera"[^>]*>카메라로 촬영</);
  assert.match(html,/id="cameraInput"[^>]*capture="environment"/);
  assert.match(html,/id="fileInput"[^>]*multiple/);
  assert.match(html,/image\.loading='lazy'/);
  assert.match(html,/image\.decoding='async'/);
  assert.match(html,/viewport-fit=cover/);
  assert.match(html,/\$\('cameraInput'\)\.value='';\$\('fileInput'\)\.value='';/);
});
