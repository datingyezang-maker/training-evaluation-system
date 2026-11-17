/* 可选的本机Chrome检查：需先启动服务及独立headless Chrome调试端口。
   密码从UI_REVIEW_PASSWORD读取，不写文件。截图只保存target目录。 */
const fs=require('node:fs');
(async()=>{
 const tabs=await(await fetch('http://127.0.0.1:9239/json')).json();const socket=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);await new Promise(resolve=>socket.onopen=resolve);let id=0;const waiting=new Map();socket.onmessage=event=>{const m=JSON.parse(event.data);if(waiting.has(m.id)){const {resolve,reject}=waiting.get(m.id);waiting.delete(m.id);m.error?reject(m.error):resolve(m.result);}};
 const call=(method,params={})=>new Promise((resolve,reject)=>{const key=++id;waiting.set(key,{resolve,reject});socket.send(JSON.stringify({id:key,method,params}));});
 const evaluate=expression=>call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});
 const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const size=async(width,height)=>call('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<600});
 const screenshot=async name=>{const img=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/'+name+'.png',Buffer.from(img.data,'base64'));};
 await size(1600,1000);await call('Page.navigate',{url:'http://127.0.0.1:18080/admin/'});await sleep(1300);await screenshot('ui-login');
 await evaluate(`(async()=>{const c=(await(await fetch('/api/auth/csrf')).json()).data;const r=await fetch('/api/auth/login',{method:'POST',headers:{[c.headerName]:c.token},body:new URLSearchParams({username:'admin',password:${JSON.stringify(process.env.UI_REVIEW_PASSWORD)}})});if(!r.ok)throw Error('review login failed');await workspace()})()`);await sleep(300);await screenshot('ui-overview');
 await evaluate("go('tasks')");await sleep(200);await screenshot('ui-tasks');
 await evaluate("(()=>{const more=[...document.querySelectorAll('#rows .row-actions button')].find(b=>b.textContent.includes('更多'));if(more){more.click();const extend=[...document.querySelectorAll('#action-menu button')].find(b=>b.textContent==='延期');if(extend)extend.click();else detail('操作详情');}else detail('操作详情')})()");await sleep(200);await screenshot('ui-drawer');
 console.log(JSON.stringify((await evaluate(`({page:state.entity,drawerVisible:!document.getElementById('detail').hidden,documentWidth:document.documentElement.scrollWidth,viewport:innerWidth,error:document.getElementById('message').textContent})`)).result.value));
 await evaluate('closeDrawer()');await size(390,844);await sleep(400);await screenshot('ui-admin-mobile');
 await call('Page.navigate',{url:'http://127.0.0.1:18080/account/'});await sleep(700);await screenshot('ui-account-mobile');
 await evaluate("(async()=>{const c=(await(await fetch('/api/auth/csrf')).json()).data;await fetch('/api/auth/logout',{method:'POST',headers:{[c.headerName]:c.token}})})()");socket.close();
})().catch(error=>{console.error(error);process.exitCode=1;});
