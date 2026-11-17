/* 无 npm 依赖的页面逻辑冒烟测试；模拟 DOM 与网络，不替代真实浏览器/微信验收。 */
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
class Element {
  constructor(tag = 'div') { this.tagName = tag; this.children = []; this.hidden = false; this.textContent = '';this.style={};this.value='';this.dataset={};this.classList={add(){},remove(){},toggle(){},contains(){return false;}}; }
  append(...items) { for(const item of items){if(item&&typeof item==='object')item.parentElement=this;}this.children.push(...items);if(this.tagName.toLowerCase()==='select'&&!this.value&&this.children.length)this.value=this.children[0].value; }
  replaceChildren(...items) { this.children = items; }
  scrollIntoView() {}
  setAttribute(name,value){this[name]=value;}
  removeAttribute(name){delete this[name];}
  focus(){this.focused=true;}
  showModal() { this.open = true; }
  close() { this.open = false; }
  addEventListener() {}
  get firstChild() { return this.children[0]; }
  get lastChild() { return this.children[this.children.length-1]; }
  querySelectorAll(selector) {
    const matches=(element,part)=>{
      const checked=part.includes(':checked');part=part.replace(':checked','');
      if(part.includes(':not([type=file])')){if(element.type==='file')return false;part=part.replace(':not([type=file])','');}
      const attr=part.match(/\[([\w-]+)(?:=["']?([^\]"']+)["']?)?\]/);part=part.replace(/\[[^\]]+\]/,'');
      const cls=part.startsWith('.')?part.slice(1):null;
      return (!part||(cls?(element.className||'').split(' ').includes(cls):element.tagName?.toLowerCase()===part.toLowerCase()))&&(!attr||(attr[2]===undefined?element[attr[1]]!==undefined:String(element[attr[1]])===attr[2]))&&(!checked||element.checked);
    };
    const result=[];const visit=parent=>{for(const child of parent.children||[]){if(selector.split(',').some(part=>matches(child,part.trim())))result.push(child);visit(child);}};visit(this);return result;
  }
  reportValidity(){return !this.required||!!this.value;}
  checkValidity(){return this.reportValidity();}
  get selectedOptions(){return this.children.filter(child=>String(child.value)===String(this.value));}
  insertBefore(item,before){item.parentElement=this;const index=this.children.indexOf(before);this.children.splice(index<0?0:index,0,item);}
  remove(){if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(child=>child!==this);}
  querySelector(selector) {return this.querySelectorAll(selector)[0]||null;}
}
const template = { id: 1, name: '模板', teaching_label: '教学质量', content_label: '课程内容', organization_label: '组织安排', teaching_weight: 1, content_weight: 1, organization_weight: 1 };
template.dimensions=[{id:11,label:'教学质量',weight:1,average_score:4},{id:12,label:'课程内容',weight:1,average_score:4},{id:13,label:'组织安排',weight:1,average_score:4}];
const task = { id: 1, lesson_name: '课程', class_name: '班次', teacher_name: '老师', template_id: 1, token: 'task-token', template };
function environment(file, search = '') {
  const nodes = new Map(), calls = [];
  const form=new Element('form'),fields=new Element('div'),actions=new Element('div'),save=new Element('button'),cancel=new Element('button');actions.className='actions';save.type='submit';actions.append(cancel,save);form.append(fields,actions);nodes.set('edit-form',form);nodes.set('fields',fields);nodes.set('cancel',cancel);const editor=new Element('dialog');editor.append(form);nodes.set('editor',editor);
  const login=new Element('form');login.append(new Element('button'));nodes.set('login-form',login);
  const fixtures = {
    '/api/auth/me': { id: 1, username: 'admin', role: 'ADMIN' },
    '/api/auth/csrf': { headerName: 'X-CSRF-TOKEN', token: 'csrf' },
    '/api/business/classes': [{ id: 1, name: '班次', owner_id: 1, start_date: '2026-01-01', end_date: '2026-12-31' }],
    '/api/business/teachers': [{ id: 1, name: '老师' }],
    '/api/business/students': [{ id: 1, name: '学员', code: 'S01' }],
    '/api/business/lessons': [{ id: 1, name: '课程', class_id: 1, teacher_id: 1, taught_at: '2026-11-14T09:00:00' }],
    '/api/business/tasks': [task], '/api/business/templates': [template],
    '/api/business/users': [{ id: 1, username: 'admin', role: 'ADMIN', enabled: true }],
    '/api/admin/audit': [], '/api/business/statistics?group=lesson': [],
    '/api/business/classes/1/members': [{ id: 1, name: '学员', code: 'S01' }],
    '/api/business/classes/1/student-picker?page=1&keyword=':{total:2,page:1,pageSize:100,items:[{id:2,name:'王同学',code:'S02'},{id:3,name:'李同学',code:'S03'}]},
    '/api/business/classes/1/members/select':{added:2,skipped:0},
    '/api/business/tasks/1/codes': [{ name: '学员', code: 'S01', access_code: 'personal-code', submission_status: '未提交' }],
    '/api/business/tasks/1/report': { ...task, dimensions:template.dimensions, expected: 1, summary: { submitted: 1, average_score: 4 }, answers: [] },
    '/api/business/tasks/1/share': {url:'https://example.test/h5/?token=task-token'},
    '/api/business/lessons/1/readiness':{class_name:'班次',teacher_name:'老师',students:1,tasks:1},
    '/api/business/tasks/1/progress?status=all':[{code:'S01',name:'学员',submission_status:'未提交',source:null,created_at:null}],
    '/api/public/task?token=task-token': task,
    '/api/public/submit': 'receipt-001'
  };
  const context = vm.createContext({
    document: {addEventListener(){},body:{classList:{add(){},remove(){}}}, getElementById(id) { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); }, createElement: tag => new Element(tag),createElementNS:(_,tag)=>new Element(tag), createTextNode: text => ({ textContent: text }) },
    location: { search, hostname: 'localhost', reload() {},assign(path){this.assigned=path;} }, URLSearchParams, MutationObserver:class {observe(){}}, console, AbortController, setTimeout, clearTimeout, confirm: () => true,
    FormData: class { constructor(form) { this.values = form.mockData || []; } [Symbol.iterator]() { return this.values[Symbol.iterator](); } },
    fetch: async (path, options) => { calls.push({ path, options }); if (!(path in fixtures)) throw new Error('未预期的API: ' + path);const fixture=fixtures[path];if(fixture.__wait)await fixture.__wait;if(fixture.__abort){const e=new Error('timeout');e.name='AbortError';throw e;}return { ok:!fixture.__status,status:fixture.__status||200,json:async()=>({code:'OK',message:fixture.message,data:fixture}),text:async()=>JSON.stringify({code:'OK',message:fixture.message,data:fixture}) }; }
  });
  vm.runInContext(fs.readFileSync(file, 'utf8'), context, { filename: file });
  if(file.endsWith('admin.js')){vm.runInContext(fs.readFileSync('src/main/resources/static/assets/portal.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/workflow.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/identity-admin.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/account-admin.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/dimensions.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/workspace-ui.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/experience.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/student-picker.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/profile-permissions.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/interaction-safety.js','utf8'),context);vm.runInContext(fs.readFileSync('src/main/resources/static/assets/training-workspace.js','utf8'),context);}
  return { context, nodes, calls,fixtures };
}
(async () => {
  const admin = environment('src/main/resources/static/assets/admin.js');
  await vm.runInContext('workspace()', admin.context);
  const overviewStart=admin.calls.length;
  await vm.runInContext("state.entity='dashboard';load()",admin.context);
  assert.equal(admin.calls.slice(overviewStart).filter(c=>['/api/business/classes','/api/business/lessons','/api/business/tasks'].includes(c.path)).length,3,'概览数据每项只请求一次');
  const dashboardMetrics=admin.nodes.get('detail').querySelectorAll('.overview-metric');
  assert.equal(dashboardMetrics.length,4);await dashboardMetrics[0].onclick();assert.equal(vm.runInContext('state.entity',admin.context),'classes');
  await dashboardMetrics[1].onclick();assert.equal(vm.runInContext('state.entity',admin.context),'lessons');
  await dashboardMetrics[2].onclick();assert.equal(vm.runInContext('state.entity',admin.context),'tasks');assert.equal(admin.nodes.get('tools').querySelector('select').value,'进行中');
  await dashboardMetrics[3].onclick();assert.equal(vm.runInContext('state.entity',admin.context),'statistics');
  for (const entity of ['classes', 'teachers', 'students', 'lessons', 'tasks', 'templates', 'users', 'statistics', 'audit']) {
    await vm.runInContext(`state.entity='${entity}';load()`, admin.context);
    assert.equal(admin.nodes.get('heading').textContent,vm.runInContext(`modules['${entity}'].name`,admin.context));
    if(!['statistics','audit'].includes(entity))assert.ok(admin.nodes.get('create').textContent.includes(vm.runInContext(`'${entity}'==='users'?'账号':modules['${entity}'].name`,admin.context)),entity+' 新增按钮必须对应本模块');
    if (!['statistics', 'audit'].includes(entity)) await vm.runInContext('edit(state.data[0])', admin.context);
    if(['classes','lessons','tasks','users'].includes(entity)){
      const options=[];const visit=element=>{if(element.tagName==='option')options.push(element);for(const child of element.children||[])visit(child);};visit(admin.nodes.get('fields'));
      assert.ok(options.every(option=>!/[（(]\d+[)）]$/.test(option.textContent)),entity+' 下拉框不得拼接数据库编号');
    }
  }
  assert.equal(vm.runInContext("optionLabel('templates',{id:123,name:'模板'})",admin.context),'模板');
  assert.equal(vm.runInContext("optionLabel('students',{id:123,name:'学员',code:'00001'})",admin.context),'学员 · 学号 00001');
  await vm.runInContext("go('tasks')",admin.context);
  admin.fixtures['/api/business/lessons/1/readiness']={class_name:'班次',teacher_name:'老师',students:1,tasks:0};
  await vm.runInContext('edit()',admin.context);assert.equal(admin.nodes.get('edit-title').textContent,'发布评价任务');
  assert.equal(vm.runInContext('state.taskWizard.sections.length',admin.context),3);
  await vm.runInContext('state.taskWizard.next.onclick()',admin.context);await vm.runInContext('state.taskWizard.next.onclick()',admin.context);
  assert.equal(vm.runInContext('state.taskWizard.submit.hidden',admin.context),false);
  await vm.runInContext("go('teachers');edit()",admin.context);assert.equal(admin.nodes.get('edit-form').querySelectorAll('button').length,2,'切换编辑模块后清理向导按钮');
  // 模拟慢学员接口：切到评分模板后，旧响应不得覆盖标题、按钮、工具或表格。
  let releaseStudents;
  admin.fixtures['/api/business/students'].__wait=new Promise(resolve=>{releaseStudents=resolve;});
  const slowStudents=vm.runInContext("go('students')",admin.context);
  await new Promise(resolve=>setImmediate(resolve));
  await vm.runInContext("go('templates')",admin.context);
  assert.equal(admin.nodes.get('heading').textContent,'评分模板');
  assert.equal(admin.nodes.get('create').textContent,'＋ 新增评分模板');
  releaseStudents();await slowStudents;delete admin.fixtures['/api/business/students'].__wait;
  assert.equal(vm.runInContext('state.entity',admin.context),'templates');
  assert.equal(vm.runInContext('state.data[0].name',admin.context),'模板');
  assert.ok(!admin.nodes.get('head').children[0].children.some(n=>n.textContent==='学号'));
  assert.ok(!admin.nodes.get('tools').children.some(n=>n.type==='file'));
  assert.ok(!admin.nodes.get('rows').children.some(tr=>tr.lastChild.children.some(n=>n.textContent==='权限设置')));
  // 旧页面请求失败也不能清空或污染已经切换成功的新页面。
  let releaseFailure;admin.fixtures['/api/business/students'].__wait=new Promise(resolve=>{releaseFailure=resolve;});admin.fixtures['/api/business/students'].__status=500;
  const failedOldPage=vm.runInContext("go('students')",admin.context);await new Promise(resolve=>setImmediate(resolve));
  await vm.runInContext("go('teachers')",admin.context);releaseFailure();await failedOldPage;
  delete admin.fixtures['/api/business/students'].__wait;delete admin.fixtures['/api/business/students'].__status;
  assert.equal(admin.nodes.get('heading').textContent,'教师');assert.equal(admin.nodes.get('create').textContent,'＋ 新增教师');
  assert.ok(!admin.nodes.get('tools').children.some(n=>n.type==='file'));
  admin.fixtures['/api/business/students']=Array.from({length:12},(_,i)=>({id:101+i,code:'S'+i,name:'学员'+i}));
  await vm.runInContext("state.entity='students';load()",admin.context);
  assert.equal(admin.nodes.get('rows').children[0].children[0].textContent,'1');
  await admin.nodes.get('pagination').children[2].onclick();
  assert.equal(admin.nodes.get('rows').children[10].hidden,false);
  assert.equal(admin.nodes.get('rows').children[10].children[0].textContent,'11');
  assert.equal(vm.runInContext('state.data[0].id',admin.context),101);
  assert.ok(admin.nodes.get('rows').children[0].lastChild.children.some(b=>b.textContent==='权限设置'));
  admin.fixtures['/api/admin/profile-permissions/teachers/1']={account:{id:2,username:'13900000001',enabled:true},permissions:['CLASS_MANAGE']};
  await vm.runInContext("profilePermissions('teachers',{id:1,name:'教师'})",admin.context);
  const permissionForm=admin.nodes.get('detail').children.find(n=>n.tagName==='form');
  const permissionGroup=permissionForm.children[0],permissionInputs=permissionGroup.children.filter(n=>n.tagName==='label').map(n=>n.children[0]);
  assert.equal(permissionInputs.length,3);assert.equal(permissionInputs[0].checked,true);assert.equal(permissionInputs[1].checked,false);
  permissionInputs[1].checked=true;permissionGroup.querySelectorAll=()=>permissionInputs.filter(n=>n.checked);
  await permissionForm.onsubmit({preventDefault(){}});
  assert.deepEqual(JSON.parse(admin.calls.filter(c=>c.options?.method==='PUT').at(-1).options.body).permissions,['CLASS_MANAGE','LESSON_MANAGE']);
  const csrfCount=()=>admin.calls.filter(c=>c.path==='/api/auth/csrf').length;
  const reusedCsrf=csrfCount();await permissionForm.onsubmit({preventDefault(){}});assert.equal(csrfCount(),reusedCsrf,'同一会话的写请求应复用CSRF令牌');
  // 测试模拟成功登出后令牌必须重新获取，不复用旧会话的令牌。
  admin.fixtures['/api/auth/logout']={};await vm.runInContext("api('/api/auth/logout',{method:'POST'})",admin.context);
  await permissionForm.onsubmit({preventDefault(){}});assert.equal(csrfCount(),reusedCsrf+1);
  await vm.runInContext("state.user={role:'TEACHER',permissions:['CLASS_MANAGE']};state.entity='classes'",admin.context);
  assert.equal(vm.runInContext('canWrite()',admin.context),true);
  await vm.runInContext("state.entity='tasks'",admin.context);assert.equal(vm.runInContext('canWrite()',admin.context),false);
  await vm.runInContext("state.user={role:'ADMIN',username:'admin'}",admin.context);
  admin.fixtures['/api/business/tasks']=[{...task,status:'CLOSED'}];
  await vm.runInContext("state.entity='tasks';load()",admin.context);
  const taskActions=admin.nodes.get('rows').children[0].lastChild.children;
  assert.ok(taskActions.some(button=>button.textContent==='删除'));
  assert.ok(!taskActions.some(button=>button.textContent==='关闭'));
  assert.equal(vm.runInContext("statusOf({status:'OPEN',expected:1,submitted:1})",admin.context),'已完成');
  assert.notEqual(vm.runInContext("statusOf({status:'OPEN',expected:0,submitted:0})",admin.context),'已完成');
  for (const code of ['members({id:1,name:"班次"})', 'codes({id:1})', 'qr({id:1})', 'report({id:1,lesson_name:"课程"})', 'paper({id:1,template_id:1,lesson_name:"课程"})']) await vm.runInContext(code, admin.context);
  await vm.runInContext('members({id:1,name:"班次"})',admin.context);
  const picker=admin.nodes.get('detail'),grid=picker.children.find(n=>n.className==='student-picker-grid'),bar=picker.children.find(n=>n.className==='picker-selection'),controls=picker.children.find(n=>n.className==='picker-toolbar');
  assert.equal(grid.children.length,2);
  let checkbox=grid.children[0].children[2];checkbox.checked=true;checkbox.onchange();assert.equal(bar.children[1].textContent,'已选 1 人');
  const all=bar.children[0].children[0];all.checked=true;all.onchange();assert.equal(bar.children[1].textContent,'已选 2 人');
  controls.children[1].value='single';controls.children[1].onchange();assert.equal(bar.children[1].textContent,'已选 1 人');assert.equal(all.disabled,true);
  await bar.children[2].onclick();assert.equal(bar.children[1].textContent,'已选 0 人');
  for(const code of ['readiness({id:1,name:"课程"})','progress({id:1,lesson_name:"课程"})','previewTemplate({name:"预览",teaching_label:"教学",content_label:"内容",organization_label:"安排",teaching_weight:1,content_weight:1,organization_weight:1})','extendTask({id:1,lesson_name:"课程",closes_at:"2026-12-01T00:00"})'])await vm.runInContext(code,admin.context);
  for(const action of ['review','unlock','reset-password'])await vm.runInContext(`accountAction({id:2,username:'13800138000'},'${action}')`,admin.context);
  assert.equal(admin.nodes.get('message').textContent, '');
  const loginForm=admin.nodes.get('login-form');loginForm.elements={username:new Element('input'),password:new Element('input')};
  await loginForm.onsubmit({preventDefault(){},target:loginForm});assert.match(admin.nodes.get('login-error').textContent,/请输入登录账号/);
  loginForm.elements.username.value='admin';await loginForm.onsubmit({preventDefault(){},target:loginForm});assert.match(admin.nodes.get('login-error').textContent,/请输入登录密码/);
  loginForm.elements.password.value='wrong';admin.fixtures['/api/auth/login']={__status:401,message:'账号或密码错误'};await loginForm.onsubmit({preventDefault(){},target:loginForm});assert.match(admin.nodes.get('login-error').textContent,/账号或密码错误/);
  admin.fixtures['/api/test-timeout']={__abort:true};await assert.rejects(vm.runInContext("api('/api/test-timeout')",admin.context),/连接超时/);
  const safety=environment('src/main/resources/static/assets/admin.js');await vm.runInContext('workspace()',safety.context);
  await vm.runInContext("go('tasks')",safety.context);
  let releaseReport;
  safety.fixtures['/api/business/tasks/1/report'].__wait=new Promise(resolve=>{releaseReport=resolve;});
  const olderReport=vm.runInContext("report({id:1,lesson_name:'旧课程'})",safety.context).catch(e=>e.stale);
  await new Promise(resolve=>setImmediate(resolve));
  safety.fixtures['/api/business/tasks/2/report']={...safety.fixtures['/api/business/tasks/1/report'],__wait:null};
  await vm.runInContext("report({id:2,lesson_name:'新课程'})",safety.context);
  releaseReport();assert.equal(await olderReport,true);
  assert.equal(safety.nodes.get('detail').children[0].children[1].textContent,'新课程 · 评价结果');
  let releaseClosed;
  safety.fixtures['/api/business/tasks/1/report'].__wait=new Promise(resolve=>{releaseClosed=resolve;});
  const closedReport=vm.runInContext("report({id:1,lesson_name:'关闭的课程'})",safety.context).catch(e=>e.stale);
  await new Promise(resolve=>setImmediate(resolve));vm.runInContext('closeDrawer()',safety.context);releaseClosed();
  assert.equal(await closedReport,true);assert.equal(safety.nodes.get('detail').hidden,true);
  // 编辑弹窗的下拉请求返回晚时，不能覆盖后来打开的另一份编辑表单。
  delete safety.fixtures['/api/business/tasks/1/report'].__wait;
  await vm.runInContext("go('lessons')",safety.context);
  let releaseChoices;safety.fixtures['/api/business/classes'].__wait=new Promise(resolve=>{releaseChoices=resolve;});
  const olderEditor=vm.runInContext('edit({id:1})',safety.context).catch(e=>e.stale);
  await new Promise(resolve=>setImmediate(resolve));vm.runInContext("state.entity='teachers'",safety.context);
  await vm.runInContext("edit({id:2,name:'教师乙'})",safety.context);releaseChoices();assert.equal(await olderEditor,true);
  assert.equal(safety.nodes.get('fields').children.length,1);assert.equal(safety.nodes.get('edit-title').textContent,'编辑教师');
  delete safety.fixtures['/api/business/classes'].__wait;
  // 同一表单连续提交只能写一次；目标模块变化后禁止提交旧表单。
  await vm.runInContext("go('students');",safety.context);await vm.runInContext("edit({id:101,name:'学生'})",safety.context);
  const editor=safety.nodes.get('edit-form');editor.mockData=[['name','改名']];
  vm.runInContext("state.entity='teachers'",safety.context);const writesBefore=safety.calls.filter(c=>c.options?.method==='PUT').length;
  await editor.onsubmit({preventDefault(){},target:editor});assert.equal(safety.calls.filter(c=>c.options?.method==='PUT').length,writesBefore);
  vm.runInContext("state.entity='students'",safety.context);
  let releaseSave;safety.fixtures['/api/business/students/101']={__wait:new Promise(resolve=>{releaseSave=resolve;})};
  const saving=editor.onsubmit({preventDefault(){},target:editor});await new Promise(resolve=>setImmediate(resolve));
  await editor.onsubmit({preventDefault(){},target:editor});assert.equal(safety.calls.filter(c=>c.path==='/api/business/students/101'&&c.options.method==='PUT').length,1);
  releaseSave();await saving;
  const mobile = environment('src/main/resources/static/assets/h5.js', '?token=task-token');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(mobile.nodes.get('scores').children.length, 3);
  assert.equal(mobile.nodes.get('evaluation').hidden, false);
  const form = mobile.nodes.get('evaluation');
  form.mockData = [['access_code', 'personal-code'], ['score_11', '5'], ['score_12', '4'], ['score_13', '3'], ['comment', '反馈']];
  await form.onsubmit({ preventDefault() {}, target: form });
  assert.equal(form.hidden, true);
  assert.match(mobile.nodes.get('message').textContent, /receipt-001/);
  const submit = mobile.calls.find(call => call.path === '/api/public/submit');
  assert.equal(submit.options.headers['X-CSRF-TOKEN'], 'csrf');
  assert.equal(JSON.parse(submit.options.body).token, 'task-token');
  assert.equal(JSON.parse(submit.options.body).scores['11'],'5');
  await form.onsubmit({preventDefault(){},target:form});assert.equal(mobile.calls.filter(c=>c.path==='/api/public/submit').length,1,'提交成功后不得再次发送评价');
  const invalidMobile=environment('src/main/resources/static/assets/h5.js','?token=task-token');await new Promise(resolve=>setImmediate(resolve));
  invalidMobile.fixtures['/api/public/submit']={__status:400,message:'任务已完成'};
  const invalidForm=invalidMobile.nodes.get('evaluation');invalidForm.mockData=form.mockData;
  await invalidForm.onsubmit({preventDefault(){},target:invalidForm});assert.equal(invalidMobile.nodes.get('message').textContent,'任务已完成');
  const missing = environment('src/main/resources/static/assets/h5.js');
  await new Promise(resolve => setImmediate(resolve));
  assert.match(missing.nodes.get('course').textContent, /二维码/);
  const account=environment('src/main/resources/static/assets/account.js');await new Promise(resolve=>setImmediate(resolve));
  account.fixtures['/api/auth/me']={role:'STUDENT',display_name:'学员甲',username:'13800000001',workspace_allowed:false};
  account.fixtures['/api/portal/courses']=[{id:1,lesson_name:'待评价课',class_name:'一班',teacher_name:'老师',status:'OPEN',submission_status:'未提交',opens_at:'2000-01-01',closes_at:'2099-01-01',token:'token-one'},{id:2,lesson_name:'已评价课',class_name:'一班',teacher_name:'老师',status:'COMPLETED',submission_status:'已提交',receipt:'receipt-two'}];
  await vm.runInContext('personal()',account.context);assert.equal(account.nodes.get('portal-nav').children.length,3);assert.equal(account.nodes.get('courses').children.length,1);assert.equal(account.nodes.get('management-entry').hidden,true);
  await vm.runInContext("personalTab('history')",account.context);assert.equal(account.nodes.get('courses').children[0].querySelector('h3').textContent,'已评价课');
  account.nodes.get('portal-metrics').children[0].onclick();assert.equal(vm.runInContext('portal.tab',account.context),'pending');
  account.nodes.get('portal-metrics').children[1].onclick();assert.equal(vm.runInContext('portal.tab',account.context),'history');
  account.nodes.get('portal-metrics').children[2].onclick();assert.equal(account.nodes.get('courses').children.length,2);assert.equal(account.nodes.get('course-heading').textContent,'全部课程');
  account.fixtures['/api/auth/me']={role:'TEACHER',display_name:'教师甲',username:'13900000001',workspace_allowed:true};account.fixtures['/api/portal/courses']=[{id:1,lesson_name:'本人课程',class_name:'一班',expected:2,submitted:1,average_score:null}];
  account.fixtures['/api/portal/lessons/1/feedback']={lesson:{lesson_name:'本人课程',class_name:'一班'},released:false,dimensions:[]};
  await vm.runInContext('personal()',account.context);assert.equal(account.nodes.get('portal-nav').children.length,4);assert.equal(account.nodes.get('management-entry').hidden,false);
  await vm.runInContext("showFeedback({id:1,lesson_name:'本人课程'})",account.context);assert.equal(account.nodes.get('feedback-dialog').open,true);assert.ok(account.nodes.get('feedback-content').children.some(n=>n.textContent.includes('暂未开放')));
  assert.equal(account.nodes.get('teacher-create').disabled,true);
  account.fixtures['/api/auth/me'].permissions=['LESSON_MANAGE','TASK_MANAGE'];account.fixtures['/api/auth/me'].id=2;
  account.fixtures['/api/portal/teaching/options']={can_create_lesson:true,can_publish:true,classes:[{id:1,name:'负责班次',student_count:2}],lessons:[{id:1,name:'本人课程',class_name:'负责班次',student_count:2}],templates:[{id:1,name:'标准模板'}]};
  account.fixtures['/api/portal/teaching/lessons']={};account.fixtures['/api/portal/teaching/tasks']={task_id:5,url:'https://example.test/h5/?token=teacher-task'};
  await vm.runInContext('personal()',account.context);assert.equal(account.nodes.get('teacher-create').disabled,false);
  await vm.runInContext("openTeaching('lesson')",account.context);const teachingForm=account.nodes.get('teaching-content').querySelector('form');teachingForm.mockData=[['name','新增授课'],['class_id','1'],['taught_at','2026-11-15T09:00']];
  await teachingForm.onsubmit({preventDefault(){}});assert.equal(account.calls.filter(c=>c.path==='/api/portal/teaching/lessons'&&c.options.method==='POST').length,1);
  await vm.runInContext("openTeaching('task',1)",account.context);const publishingForm=account.nodes.get('teaching-content').querySelector('form');publishingForm.mockData=[['lesson_id','1'],['template_id','1'],['opens_at','2026-11-15T09:00'],['closes_at','2026-11-16T09:00']];
  await publishingForm.onsubmit({preventDefault(){}});assert.equal(account.nodes.get('teaching-content').querySelector('img').src,'/api/portal/teaching/tasks/5/qr');
  const resume=environment('src/main/resources/static/assets/account.js','?return='+encodeURIComponent('/h5/?token=course-token'));
  await new Promise(resolve=>setImmediate(resolve));resume.fixtures['/api/auth/me']={role:'STUDENT',username:'13800000001'};resume.fixtures['/api/portal/courses']=[];await vm.runInContext('personal()',resume.context);assert.equal(resume.context.location.assigned,'/h5/?token=course-token');
  const external=environment('src/main/resources/static/assets/account.js','?return='+encodeURIComponent('https://example.test/'));
  await new Promise(resolve=>setImmediate(resolve));external.fixtures['/api/auth/me']={role:'STUDENT',username:'13800000001'};external.fixtures['/api/portal/courses']=[];await vm.runInContext('personal()',external.context);assert.equal(external.context.location.assigned,undefined);
  console.log('PC模块渲染、编辑表单、名单/二维码/报表/补录入口、H5评分与缺失令牌处理：通过');
})().catch(error => { console.error(error); process.exitCode = 1; });
