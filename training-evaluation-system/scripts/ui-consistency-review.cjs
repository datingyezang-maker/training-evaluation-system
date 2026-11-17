/* 真实浏览器只读检查。先启动本地服务18080和隔离Chrome CDP9239；登录密码从环境变量读取。 */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 // 服务冷启动时先等待可访问，避免把连接拒绝页误判成前端脚本缺失。
 let ready=false;for(let attempt=0;attempt<60;attempt++){try{ready=(await fetch('http://127.0.0.1:18080/admin/')).ok;if(ready)break;}catch(error){}await new Promise(resolve=>setTimeout(resolve,200));}assert.equal(ready,true,'Review server must be ready');
 const tabs=await(await fetch('http://127.0.0.1:9239/json')).json();
 const socket=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise(resolve=>socket.onopen=resolve);
 let serial=0;const pending=new Map(),errors=[];
 socket.onmessage=event=>{const message=JSON.parse(event.data);if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails.text);if(pending.has(message.id)){const item=pending.get(message.id);pending.delete(message.id);message.error?item.reject(message.error):item.resolve(message.result);}};
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++serial;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const result=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};
 try{
  await call('Runtime.enable');await call('Page.enable');
  await call('Emulation.setDeviceMetricsOverride',{width:1600,height:1000,deviceScaleFactor:1,mobile:false});
  await call('Page.navigate',{url:'http://127.0.0.1:18080/admin/'});
  for(let i=0;i<30;i++){await new Promise(resolve=>setTimeout(resolve,200));if(await evaluate("typeof state!=='undefined'&&typeof dismissEditor==='function'"))break;}
  assert.equal(await evaluate("typeof dismissEditor"),'function');
  await evaluate(`(async()=>{const c=(await(await fetch('/api/auth/csrf')).json()).data;const r=await fetch('/api/auth/login',{method:'POST',headers:{[c.headerName]:c.token},body:new URLSearchParams({username:'admin',password:${JSON.stringify(process.env.UI_REVIEW_PASSWORD)}})});if(!r.ok)throw Error('Review login failed');await workspace();})()`);
  for(const entity of ['classes','lessons','tasks','statistics','students','teachers','templates','users','audit']){
   const result=await evaluate(`(async()=>{await go('${entity}');return {heading:$('heading').textContent,expected:modules[state.entity].name,columns:$('head').querySelectorAll('th').length,expectedColumns:Object.keys(modules[state.entity].cols).length+1,files:$('tools').querySelectorAll('input[type=file]').length,error:$('message').textContent};})()`);
   assert.equal(result.heading,result.expected,entity);assert.equal(result.columns,result.expectedColumns,entity);assert.equal(result.files,['students','users'].includes(entity)?1:0,entity);assert.equal(result.error,'',entity);
   if(!['statistics','audit','tasks'].includes(entity)){
    const editor=await evaluate("(async()=>{await edit();return {open:$('editor').open,title:$('edit-title').textContent,entity:state.editEntity,fields:$('fields').querySelectorAll('input,select,textarea').length};})()");
    assert.equal(editor.open,true,entity);assert.equal(editor.entity,entity);assert.ok(editor.fields>0,entity);await evaluate('dismissEditor()');
   }
  }
  const rapid=await evaluate("(async()=>{await Promise.all([go('students'),go('templates'),go('teachers'),go('tasks')]);return {entity:state.entity,heading:$('heading').textContent,button:$('create').textContent,error:$('message').textContent};})()");
  assert.equal(rapid.entity,'tasks');assert.equal(rapid.heading,'评价任务');assert.equal(rapid.button,'＋ 发布评价任务');assert.equal(rapid.error,'');
  const picker=await evaluate("(async()=>{await go('classes');if(!state.data.length)return 'no classes';await members(state.data[0]);const open=!$('detail').hidden;closeDrawer();return open&&$('detail').hidden;})()");
  assert.ok(picker===true||picker==='no classes');
  const classArea=await evaluate("(async()=>{if(!state.data.length)return true;await classWorkspace(state.data[0]);const tabs=$('detail').querySelectorAll('.workspace-tabs button').length;closeDrawer();return tabs===3;})()");assert.equal(classArea,true);
  const wizard=await evaluate("(async()=>{await go('tasks');await edit();const result=state.taskWizard.sections.length;state.taskWizard.showStep(2);dismissEditor();return result;})()");assert.equal(wizard,3);
  await evaluate("go('dashboard')");assert.equal(await evaluate("document.querySelectorAll('.operations-todos').length"),1);
  const screenshot=async name=>{const result=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync('target/'+name+'.png',Buffer.from(result.data,'base64'));};
  await screenshot('upgrade-admin');
  // 人员工作台视觉检查使用浏览器内的测试数据，不创建真实账号或改动业务数据。
  await call('Page.navigate',{url:'http://127.0.0.1:18080/account/'});
  for(let i=0;i<30;i++){await new Promise(resolve=>setTimeout(resolve,100));if(await evaluate("typeof personal==='function'&&typeof portal!=='undefined'"))break;}
  await evaluate(`(()=>{const original=request;window.previewIdentity={role:'TEACHER',id:2,permissions:['LESSON_MANAGE','TASK_MANAGE'],display_name:'张老师',username:'13900000001',workspace_allowed:true};window.previewCourses=[{id:1,lesson_name:'结构化表达与沟通',class_name:'综合能力提升班',taught_at:'2026-11-15T09:00:00',expected:80,submitted:64,average_score:4.65},{id:2,lesson_name:'公文写作实务',class_name:'岗位技能培训班',taught_at:'2026-11-16T09:00:00',expected:60,submitted:0,average_score:null}];request=async(path,options)=>path==='/api/auth/me'?window.previewIdentity:path==='/api/portal/courses'?window.previewCourses:path==='/api/portal/teaching/options'?{can_create_lesson:true,can_publish:true,classes:[{id:1,name:'Preview class',student_count:20}],lessons:[{id:1,name:'Preview lesson',class_name:'Preview class',student_count:20}],templates:[{id:1,name:'Standard template'}]}:original(path,options);})()`);
  await evaluate('personal()');assert.equal(await evaluate("el('portal-nav').children.length"),4);await screenshot('upgrade-teacher');
  assert.equal(await evaluate("el('teacher-create').disabled"),false);
  const teaching=await evaluate("(async()=>{await openTeaching('lesson');const inputs=el('teaching-content').querySelectorAll('input,select').length;closeTeaching();await openTeaching('task');const publishInputs=el('teaching-content').querySelectorAll('input,select').length;closeTeaching();return {inputs,publishInputs};})()");assert.equal(teaching.inputs,3);assert.equal(teaching.publishInputs,4);
  await call('Emulation.setDeviceMetricsOverride',{width:820,height:1100,deviceScaleFactor:1,mobile:false});assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true,'teacher tablet layout must not overflow');
  await call('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await evaluate(`(async()=>{window.previewIdentity={role:'STUDENT',display_name:'李同学',username:'13800000001',workspace_allowed:false};window.previewCourses=[{id:1,lesson_name:'结构化表达与沟通',class_name:'综合能力提升班',teacher_name:'张老师',status:'OPEN',opens_at:'2000-01-01',closes_at:'2099-01-01',token:'preview-token',submission_status:'未提交'},{id:2,lesson_name:'公文写作实务',class_name:'岗位技能培训班',teacher_name:'王老师',status:'COMPLETED',submission_status:'已提交',submitted_at:'2026-11-14T18:00:00',receipt:'preview-receipt'}];await personal();})()`);
  assert.equal(await evaluate("el('portal-nav').children.length"),3);assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);await screenshot('upgrade-student-mobile');
  await evaluate("personalTab('history')");assert.equal(await evaluate("el('courses').querySelector('h3').textContent"),'公文写作实务');
  assert.deepEqual(errors,[]);
  console.log(JSON.stringify({modules:9,editorChecks:6,rapidNavigation:'passed',drawerClose:'passed',classWorkspace:'passed',publishWizard:'passed',teacherPortal:'preview passed',studentMobile:'preview passed',browserExceptions:errors.length}));
 }finally{
  try{await evaluate("(async()=>{const c=(await(await fetch('/api/auth/csrf')).json()).data;await fetch('/api/auth/logout',{method:'POST',headers:{[c.headerName]:c.token}});})()");}finally{socket.close();}
 }
})().catch(error=>{console.error(error);process.exitCode=1;});
