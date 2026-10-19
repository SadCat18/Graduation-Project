const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(process.env.REF_DIST),backend=new URL(process.env.REF_BACKEND);
http.createServer((req,res)=>{
 if(req.url.startsWith('/api/')||req.url.startsWith('/actuator')){const proxy=http.request(new URL(req.url,backend),{method:req.method,headers:req.headers},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});proxy.on('error',()=>{res.writeHead(502);res.end()});req.pipe(proxy);return;}
 let file=path.resolve(root,'.'+decodeURIComponent(req.url.split('?')[0]));if(!file.startsWith(root+path.sep))file=path.join(root,'index.html');if(!fs.existsSync(file)||fs.statSync(file).isDirectory())file=path.join(root,'index.html');res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml'})[path.extname(file)]||'application/octet-stream');fs.createReadStream(file).pipe(res);
}).listen(4173,'127.0.0.1');
