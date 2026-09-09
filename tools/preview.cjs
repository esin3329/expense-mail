const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const allowed={'/':'app/Index.html','/setup':'tools/setup.html','/code/Code.gs':'app/Code.gs','/code/Index.html':'app/Index.html','/code/appsscript.json':'app/appsscript.json'};
http.createServer((req,res)=>{
  const url=new URL(req.url,'http://127.0.0.1');
  if(req.method!=='GET'||!allowed[url.pathname]){res.writeHead(404);res.end('Not found');return;}
  const mime=url.pathname.startsWith('/code/')?'text/plain; charset=utf-8':'text/html; charset=utf-8';
  res.writeHead(200,{'Content-Type':mime,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
  res.end(fs.readFileSync(path.join(root,allowed[url.pathname])));
}).listen(49187,'127.0.0.1',()=>console.log('Preview: http://127.0.0.1:49187 (no mail sending)'));
