'use strict';
let studentSession=false,submitting=false,submitted=false;
const $=id=>document.getElementById(id),token=new URLSearchParams(location.search).get('token');
async function api(path,options={}){
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
 const headers={Accept:'application/json',...options.headers};
 // 仅临时 ngrok 联调使用官方客户端跳过提示头，生产同源入口无需此头。
 if(/\.ngrok-free\.(dev|app)$/.test(location.hostname||''))headers['ngrok-skip-browser-warning']='1';
 try{
  const r=await fetch(path,{...options,headers,credentials:'same-origin',signal:controller.signal});
  const content=await r.text();let b;
  try{b=JSON.parse(content);}catch(error){throw new Error('服务器返回了非数据页面，请检查内网穿透是否正常，或在浏览器中打开后重试');}
  if(!r.ok){const error=new Error(b.message||'请求失败（'+r.status+'）');error.status=r.status;throw error;}return b.data;
 }catch(error){if(error.name==='AbortError')throw new Error('读取超时，请检查网络及内网穿透连接后重新打开');throw error;}
 finally{clearTimeout(timer);}
}
if(token)$('h5-account-link').href='/account/?return='+encodeURIComponent('/h5/?token='+token);
async function initialize(){if(!token)throw new Error('请通过课程二维码进入');const task=await api('/api/public/task?token='+encodeURIComponent(token));$('course').replaceChildren();$('scores').replaceChildren();for(const key of ['class_name','lesson_name','teacher_name']){const p=document.createElement('p');p.textContent=task[key];$('course').append(p);}for(const dimension of (task.template.dimensions||[{id:'teaching',label:task.template.teaching_label,weight:task.template.teaching_weight},{id:'content',label:task.template.content_label,weight:task.template.content_weight},{id:'organization',label:task.template.organization_label,weight:task.template.organization_weight}])){const field=document.createElement('fieldset'),legend=document.createElement('legend');legend.textContent=dimension.label+'（权重 '+dimension.weight+'）';field.append(legend);for(let value=1;value<=5;value++){const label=document.createElement('label'),input=document.createElement('input');input.type='radio';input.name='score_'+dimension.id;input.value=value;input.required=true;label.append(input,document.createTextNode(value+'分'));field.append(label);}$('scores').append(field);}try{const identity=await api('/api/auth/me');if(identity.role==='STUDENT'){studentSession=true;const codeInput=$('evaluation').elements.access_code;codeInput.required=false;codeInput.parentElement.hidden=true;}}catch(error){} $('evaluation').hidden=false;updateRatingProgress();}
$('evaluation').onsubmit=async event=>{event.preventDefault();if(submitting||submitted)return;submitting=true;$('submit').disabled=true;$('message').textContent='';try{const csrf=await api('/api/auth/csrf'),data=Object.fromEntries(new FormData(event.target));data.token=token;data.scores={};for(const[key,value]of Object.entries(data)){if(key.startsWith('score_')){data.scores[key.slice(6)]=value;delete data[key];}}if(!Object.keys(data.scores).length)delete data.scores;const receipt=await api(studentSession?'/api/portal/evaluate':'/api/public/submit',{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(data)});submitted=true;$('evaluation').hidden=true;$('course').textContent='提交成功，感谢你的反馈。';const back=document.createElement('a');back.href='/account/';back.textContent='返回我的课程 →';back.className='download';$('course').append(back);$('message').textContent='回执：'+receipt;}catch(error){$('message').textContent=error.message+(!error.status||error.status>=500?'；网络中断可原样重试，不会重复计分。':'');}finally{submitting=false;$('submit').disabled=submitted;}};
initialize().catch(error=>{$('course').textContent=error.message;});

function updateRatingProgress(){const total=$('scores').querySelectorAll('fieldset').length,answered=$('scores').querySelectorAll('input:checked').length;$('rating-progress').textContent='已完成 '+answered+' / '+total+' 项评分';}
$('scores').onchange=updateRatingProgress;
