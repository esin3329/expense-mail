const fs=require('node:fs');const vm=require('node:vm');
new vm.Script(fs.readFileSync('app/Code.gs','utf8'),{filename:'Code.gs'});
const html=fs.readFileSync('app/Index.html','utf8');
for(const [,js] of html.matchAll(/<script>([\s\S]*?)<\/script>/g))new vm.Script(js,{filename:'Index.html'});
JSON.parse(fs.readFileSync('app/appsscript.json','utf8'));
console.log('Server, browser JavaScript and manifest syntax OK');
