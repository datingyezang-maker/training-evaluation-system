/* 同源 API + 服务端权限；数据只作为文本渲染，不拼接 HTML。 */
'use strict';
const $=id=>document.getElementById(id),state={user:null,entity:'classes',data:[],editId:null};
// CSRF 令牌随会话复用；登录、退出、失效响应后重新获取，不缓存业务数据或权限。
let adminCsrfPromise=null;
async function api(path,options={}){
 const pageToken=(!options.method||options.method==='GET')&&(/^\/api\/business\//.test(path)||/^\/api\/admin\//.test(path))?state.loadToken:null;
 const viewToken=pageToken?state.viewToken:null;
 if(options.method&&options.method!=='GET'){if(!adminCsrfPromise)adminCsrfPromise=api('/api/auth/csrf').catch(error=>{adminCsrfPromise=null;throw error;});const token=await adminCsrfPromise;options.headers={...options.headers,[token.headerName]:token.token};}
 const headers={Accept:'application/json',...options.headers};if(/\.ngrok-free\.(dev|app)$/.test(location.hostname||''))headers['ngrok-skip-browser-warning']='1';
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
 try{const r=await fetch(path,{...options,headers,credentials:'same-origin',signal:controller.signal});let b;try{b=await r.json();}catch(error){throw new Error('服务响应异常，请检查服务和网络后重试');}if(!r.ok){if(r.status===401||r.status===403)adminCsrfPromise=null;const error=new Error(b.message||'请求失败');error.status=r.status;throw error;}if(['/api/auth/login','/api/auth/logout','/api/auth/password'].includes(path))adminCsrfPromise=null;if(pageToken&&(pageToken!==state.loadToken||(viewToken&&viewToken!==state.viewToken))){const stale=new Error('Stale page response');stale.stale=true;throw stale;}return b.data;}catch(error){if(pageToken&&(pageToken!==state.loadToken||(viewToken&&viewToken!==state.viewToken))){error.stale=true;throw error;}if(error.name==='AbortError')throw new Error('连接超时，请检查网络后重试');if(error instanceof TypeError)throw new Error('无法连接服务器，请检查服务是否启动');throw error;}finally{clearTimeout(timer);}
}
function send(path,method,data){return api(path,{method,headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});}
function node(tag,text,cls){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;}
function button(text,action){const b=node('button',text);b.type='button';if(/^(删除|撤销|移除|关闭)$/.test(text))b.classList.add('action-danger');b.onclick=async()=>{if(b.disabled)return;const context=captureUI();b.disabled=true;try{return await action();}catch(error){if(currentUI(context))showError(error);}finally{b.disabled=false;}};return b;}
function showError(e){if(!e.stale)$('message').textContent=e.message;}
function ensureView(token){if(token!==state.viewToken){const error=new Error('窗口已切换或关闭');error.stale=true;throw error;}}
// 选项文本使用业务信息；数据库主键仅保留在 option.value 中用于提交。
function optionLabel(type,item){
 const name=item.name||item.display_name||item.username||'';
 if(type==='lessons')return [name,item.class_name,item.teacher_name,item.taught_at?display(item.taught_at,'taught_at'):null].filter(Boolean).join(' · ');
 if(type==='classes')return [name,item.start_date?String(item.start_date).slice(0,10):null].filter(Boolean).join(' · ');
 if(type==='students')return [name,item.code?'学号 '+item.code:null].filter(Boolean).join(' · ');
 if(type==='users')return [name,item.username&&item.username!==name?item.username:null].filter(Boolean).join(' · ');
 return name;
}
function captureUI(){return {page:state.loadToken,view:state.viewToken};}
function currentUI(context){return context.page===state.loadToken&&context.view===state.viewToken;}
async function refreshUI(context,message){if(!currentUI(context))return;const pending=load(),token=state.loadToken;await pending;if(state.loadToken===token&&message)$('message').textContent=message;}
const modules={
templates:{name:'评分模板',desc:'固定三项1—5分，可设置名称及权重；发布后锁定，变更请新增版本。',cols:{id:'编号',name:'名称',teaching_label:'维度1',content_label:'维度2',organization_label:'维度3',teaching_weight:'权重1',content_weight:'权重2',organization_weight:'权重3'},fields:[['name','模板名称'],['teaching_label','维度1名称'],['content_label','维度2名称'],['organization_label','维度3名称'],['teaching_weight','权重1（1—100）','number'],['content_weight','权重2（1—100）','number'],['organization_weight','权重3（1—100）','number']]},
statistics:{name:'统计报表',desc:'按有效答卷加权汇总，排除已撤销任务；应评为各任务应评人次之和，未提交不算零分。',cols:{id:'编号',name:'名称',expected:'应评人次',submitted:'已评人次',average_score:'加权均分'}},
classes:{name:'班次',desc:'先建班次并关联学员，再录入授课。任务发布时锁定应评名单。',cols:{id:'编号',name:'名称',start_date:'开始',end_date:'结束',owner_id:'负责人编号'},fields:[['name','名称'],['start_date','开始','date'],['end_date','结束','date'],['owner_id','负责人','users']]},
teachers:{name:'教师',cols:{id:'编号',name:'姓名'},fields:[['name','姓名']]},
students:{name:'学员',desc:'Excel：xlsx，第一列学号、第二列姓名，首行为表头；单次最多5000行、2MB。',cols:{id:'编号',code:'学号',name:'姓名'},fields:[['code','学号'],['name','姓名']]},
lessons:{name:'授课安排',cols:{id:'编号',name:'课程',class_name:'班次',teacher_name:'教师',taught_at:'时间'},fields:[['name','课程名称'],['class_id','班次','classes'],['teacher_id','教师','teachers'],['taught_at','授课时间','datetime-local']]},
tasks:{name:'评价任务',desc:'三项1—5分按模板权重计分；发布后不可改写。个人评价码请私下单独发放。',cols:{id:'编号',lesson_name:'课程',class_name:'班次',status:'状态',opens_at:'开放',closes_at:'截止'},fields:[['lesson_id','授课','lessons'],['template_id','评分模板','templates'],['opens_at','开放时间','datetime-local'],['closes_at','截止时间','datetime-local']]},
users:{name:'用户权限',desc:'ADMIN 全部；BUSINESS 负责班次维护与报表；VIEWER 负责班次只读。',cols:{id:'编号',username:'账号',role:'角色',enabled:'启用'},fields:[['username','账号'],['password','密码（编辑留空不变）','password'],['role','角色','roles'],['enabled','启用','boolean']]},
audit:{name:'操作日志',cols:{actor:'操作者',action:'动作',path:'对象',http_status:'状态',created_at:'时间'}}};
const canWrite=()=>['ADMIN','BUSINESS'].includes(state.user.role)||(state.user.permissions||[]).includes({classes:'CLASS_MANAGE',lessons:'LESSON_MANAGE',tasks:'TASK_MANAGE'}[state.entity]),isAdmin=()=>state.user.role==='ADMIN';
function mayEdit(){return canWrite()&&(!['users','teachers','students','templates'].includes(state.entity)||isAdmin())&&!['audit','statistics'].includes(state.entity);}
async function load(){
 $('message').textContent='';$('detail').hidden=true;const entity=state.entity,token=state.loadToken,m=modules[entity];$('heading').textContent=m.name;$('description').textContent=m.desc||'';
 const data=await api(entity==='audit'?'/api/admin/audit':'/api/business/'+entity+(entity==='statistics'?'?group='+(state.group||'lesson'):''));if(state.loadToken!==token||state.entity!==entity)return;state.data=data;$('create').hidden=!mayEdit();$('tools').replaceChildren();
 if(entity==='statistics'){const select=node('select');for(const[value,label]of [['lesson','课程'],['class','班次'],['teacher','教师']]){const option=node('option',label);option.value=value;select.append(option);}select.value=state.group||'lesson';select.onchange=()=>{state.group=select.value;load().catch(showError);};const a=node('a','导出统计 Excel');a.href='/api/business/statistics/excel?group='+(state.group||'lesson');$('tools').append(select,a);}
 const search=node('input');search.placeholder='输入关键字筛选';search.oninput=()=>{for(const row of $('rows').children)row.hidden=!row.textContent.toLowerCase().includes(search.value.toLowerCase());};$('tools').append(search);
 if(entity==='students'&&isAdmin()){const input=node('input');input.type='file';input.accept='.xlsx';$('tools').append(input,button('导入 Excel',async()=>{const context=captureUI();if(!input.files.length)throw new Error('请选择文件');const body=new FormData();body.append('file',input.files[0]);const count=await api('/api/business/students/import',{method:'POST',body});await refreshUI(context,'已导入 '+count+' 位学员');}));}
 $('head').replaceChildren();const tr=node('tr');Object.values(m.cols).forEach(v=>tr.append(node('th',v)));tr.append(node('th','操作'));$('head').append(tr);$('rows').replaceChildren();$('empty').hidden=state.data.length>0;
 state.data.forEach(row=>{const tr=node('tr');Object.keys(m.cols).forEach(k=>tr.append(node('td',row[k]===null?'—':String(row[k]))));const a=node('td',undefined,'row-actions');
 if(mayEdit()&&state.entity!=='tasks'){a.append(button('编辑',()=>edit(row)));if(state.entity!=='users')a.append(button('删除',async()=>{const context=captureUI();if(confirm('确认删除？已被引用的记录不可删除。')){await api('/api/business/'+entity+'/'+row.id,{method:'DELETE'});await refreshUI(context);}}));}
 if(entity==='classes')a.append(button('学员名单',()=>members(row)));
 if(entity==='tasks'){a.append(button('结果 / 导出',()=>report(row)));if(canWrite())a.append(button('二维码',()=>qr(row)),button('评价码',()=>codes(row)),button('纸质补录',()=>paper(row)),button('关闭',()=>changeStatus(row,'CLOSED')),button('撤销',()=>changeStatus(row,'REVOKED')));}
 tr.append(a);$('rows').append(tr);});
}
async function edit(row={}){const viewToken=state.viewToken,entity=state.entity;state.editId=row.id||null;$('fields').replaceChildren();$('edit-error').textContent='';$('edit-title').textContent=(row.id?'编辑':'新增')+(state.entity==='users'?'账号':modules[state.entity].name);
 const choiceTypes=[...new Set(modules[state.entity].fields.filter(([key,,type])=>['users','classes','teachers','lessons','templates'].includes(type)&&(key!=='owner_id'||isAdmin())).map(field=>field[2]))],choiceData={};
 await Promise.all(choiceTypes.map(async type=>{choiceData[type]=await api('/api/business/'+type);}));ensureView(viewToken);
 for(const [key,label,type='text']of modules[state.entity].fields){if(key==='owner_id'&&!isAdmin())continue;const wrap=node('label',label);let input;
 if(['users','classes','teachers','lessons','templates','roles','boolean'].includes(type)){input=node('select');input.choiceRecords=choiceData[type]||[];let choices;if(type==='roles')choices=['ADMIN','BUSINESS','VIEWER','STUDENT','TEACHER'].map(v=>[v,roleNames[v]||v]);else if(type==='boolean')choices=[['true','启用'],['false','停用']];else choices=choiceData[type].map(v=>[v.id,optionLabel(type,v)]);choices.forEach(([value,text])=>{const opt=node('option',text);opt.value=value;input.append(opt);});}
 else{input=node('input');input.type=type;input.maxLength=type==='password'?72:120;}input.name=key;input.required=!(key==='password'&&row.id);
 if(row[key]!==undefined)input.value=type==='datetime-local'?String(row[key]).slice(0,16):String(row[key]).slice(0,type==='date'?10:9999);wrap.append(input);$('fields').append(wrap);}$('editor').showModal();}
$('edit-form').onsubmit=async e=>{e.preventDefault();const context=captureUI();const b=e.target.querySelector('button[type=submit]');b.disabled=true;try{await send('/api/business/'+state.editEntity+(state.editId?'/'+state.editId:''),state.editId?'PUT':'POST',Object.fromEntries(new FormData(e.target)));$('editor').close();await refreshUI(context);}catch(error){$('edit-error').textContent=error.message;}finally{b.disabled=false;}};
$('cancel').onclick=()=>$('editor').close();$('create').onclick=()=>edit().catch(showError);
function detail(title){$('detail').hidden=false;$('detail').replaceChildren(node('h2',title));$('detail').scrollIntoView({behavior:'smooth'});return $('detail');}
function table(container,rows,columns){const wrap=node('div',undefined,'table-scroll'),t=node('table'),h=node('tr');Object.values(columns).forEach(v=>h.append(node('th',v)));t.append(h);rows.forEach(row=>{const tr=node('tr');Object.keys(columns).forEach(k=>tr.append(node('td',row[k]===null?'—':String(row[k]))));t.append(tr);});wrap.append(t);container.append(wrap);}
async function members(row){const rows=await api('/api/business/classes/'+row.id+'/members'),d=detail(row.name+' · 学员名单');table(d,rows,{id:'编号',code:'学号',name:'姓名'});if(canWrite()){const input=node('input');input.type='number';input.min=1;input.placeholder='学员编号';d.append(input,button('添加学员',async()=>{await api('/api/business/classes/'+row.id+'/members/'+input.value,{method:'POST'});await members(row);}),button('移除学员',async()=>{await api('/api/business/classes/'+row.id+'/members/'+input.value,{method:'DELETE'});await members(row);}));d.append(node('p','管理员在学员模块录入后提供编号。已发布任务的名单快照不随此处变更。'));}}
async function changeStatus(row,status){const context=captureUI();if(confirm('确认'+(status==='CLOSED'?'关闭':'撤销')+'任务？')){await send('/api/business/tasks/'+row.id+'/status','POST',{status});await refreshUI(context);}}
async function codes(row){const rows=await api('/api/business/tasks/'+row.id+'/codes'),d=detail('个人评价码 · 请私下发放');table(d,rows,{code:'学号',name:'姓名',access_code:'评价码',submission_status:'状态'});const link=node('a','导出个人评价码 Excel');link.href='/api/business/tasks/'+row.id+'/codes/excel';d.append(link);}
async function qr(row){const d=detail('课程二维码'),img=node('img');img.src='/api/business/tasks/'+row.id+'/qr';img.alt='课程二维码';img.width=280;d.append(img,node('p','手机必须能访问 PUBLIC_BASE_URL 配置的地址。localhost 不能被手机访问。'));const a=node('a','下载二维码');a.href=img.src;a.download='task-'+row.id+'.png';d.append(a);}
async function report(row){const r=await api('/api/business/tasks/'+row.id+'/report'),d=detail(row.lesson_name+' · 结果'),s=r.summary;d.append(node('p','应评 '+r.expected+' 人 · 已评 '+s.submitted+' 人 · 参与率 '+(r.expected?(s.submitted/r.expected*100).toFixed(1):'0')+'% · 综合均分 '+(s.average_score===null?'暂无':Number(s.average_score).toFixed(2))));for(const[kind,label]of [['excel','Excel 明细'],['pdf','PDF 纸质评价表']]){const a=node('a',label,'download');a.href='/api/business/tasks/'+row.id+'/'+kind;a.target='_blank';a.rel='noopener';d.append(a);}table(d,r.answers,{teaching:r.template.teaching_label,content:r.template.content_label,organization:r.template.organization_label,comment:'意见',source:'来源',created_at:'时间'});}
async function paper(row){const templates=await api('/api/business/templates'),template=templates.find(t=>String(t.id)===String(row.template_id));const d=detail(row.lesson_name+' · 纸质补录'),form=node('form');for(const[key,label,type]of [['access_code','个人评价码','text'],['paper_reference','纸表编号','text'],['teaching',template.teaching_label+'（1—5）','number'],['content',template.content_label+'（1—5）','number'],['organization',template.organization_label+'（1—5）','number'],['comment','意见建议','text']]){const l=node('label',label),input=node('input');input.name=key;input.type=type;input.required=key!=='comment';if(type==='number'){input.min=1;input.max=5;}l.append(input);form.append(l);}const b=node('button','提交补录');form.append(b);form.onsubmit=async e=>{e.preventDefault();b.disabled=true;try{const data=Object.fromEntries(new FormData(form));data.token=row.token;const receipt=await send('/api/business/paper','POST',data);d.replaceChildren(node('h2','补录成功'),node('p','回执：'+receipt));}catch(error){showError(error);}finally{b.disabled=false;}};d.append(form);}
async function workspace(){state.user=await api('/api/auth/me');if(['STUDENT','TEACHER'].includes(state.user.role)&&!state.user.workspace_allowed){location.assign('/account/');return;}$('identity').textContent=state.user.username+' · '+state.user.role;$('login-panel').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;$('password').hidden=false;$('nav').replaceChildren();Object.entries(modules).forEach(([key,m])=>{if(['users','audit'].includes(key)&&!isAdmin())return;$('nav').append(button(m.name,async()=>{state.entity=key;await load();}));});await load();}
$('login-form').onsubmit=async e=>{
 e.preventDefault();const form=e.target,b=form.querySelector('button'),errorBox=$('login-error');errorBox.textContent='';
 const username=form.elements.username,password=form.elements.password;username.removeAttribute('aria-invalid');password.removeAttribute('aria-invalid');
 if(!username.value.trim()||!password.value){const missing=!username.value.trim()?username:password;missing.setAttribute('aria-invalid','true');errorBox.textContent=!username.value.trim()?'请输入登录账号':'请输入登录密码';missing.focus();return;}
 username.value=username.value.trim();b.disabled=true;b.setAttribute('aria-busy','true');
 try{await api('/api/auth/login',{method:'POST',body:new URLSearchParams(new FormData(form))});password.value='';await workspace();}
 catch(error){errorBox.textContent=error.message;if(error.status===401){password.setAttribute('aria-invalid','true');password.focus();}}
 finally{b.disabled=false;b.removeAttribute('aria-busy');}
};
$('logout').onclick=()=>api('/api/auth/logout',{method:'POST'}).then(()=>location.reload()).catch(showError);
$('password').onclick=()=>{const d=detail('修改密码'),form=node('form');for(const[name,label]of [['old_password','原密码'],['new_password','新密码（至少12位）']]){const l=node('label',label),input=node('input');input.type='password';input.name=name;input.required=true;l.append(input);form.append(l);}form.append(node('button','保存并重新登录'));form.onsubmit=async e=>{e.preventDefault();try{await send('/api/auth/password','POST',Object.fromEntries(new FormData(form)));location.reload();}catch(error){showError(error);}};d.append(form);};
workspace().catch(error=>{if(error.message!=='请先登录')showError(error);});
