/* 个人工作台：按身份组织导航，所有人员和课程范围由后端核验。 */
'use strict';
const el=id=>document.getElementById(id),portal={user:null,courses:[],tab:'',version:0,feedback:0,teaching:0};
async function request(path,options={}){
 const headers={Accept:'application/json',...options.headers};if(/\.ngrok-free\.(dev|app)$/.test(location.hostname))headers['ngrok-skip-browser-warning']='1';
 if(options.method&&options.method!=='GET'){const csrf=await request('/api/auth/csrf');headers[csrf.headerName]=csrf.token;}
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
 try{const response=await fetch(path,{...options,headers,signal:controller.signal,credentials:'same-origin'});let body;try{body=await response.json();}catch(error){throw new Error('服务返回异常，请检查网络后重试');}if(!response.ok){const error=new Error(body.message||'请求失败');error.status=response.status;throw error;}return body.data;
 }catch(error){if(error.name==='AbortError')throw new Error('连接超时，请检查网络后重试');throw error;}finally{clearTimeout(timer);}
}
function post(path,data){return request(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});}
function message(error){el('message').textContent=error.message||error;}
function element(tag,text,cls){const item=document.createElement(tag);if(text!==undefined)item.textContent=text;if(cls)item.className=cls;return item;}
function dateLabel(value){if(!value)return '未设置';const date=new Date(value);return Number.isNaN(date.getTime())?'未设置':date.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'});}
function courseStatus(course){if(course.submission_status==='已提交')return '已评价';if(course.status==='REVOKED')return '已撤销';if(course.status==='COMPLETED')return '已完成';if(course.status==='CLOSED')return '已关闭';if(Date.now()<new Date(course.opens_at).getTime())return '待开放';if(Date.now()>new Date(course.closes_at).getTime())return '已截止';return '待评价';}
function personalTab(tab){portal.tab=tab;closeFeedback();closeTeaching();renderPersonal();}
function renderPersonal(){
 const teacher=portal.user.role==='TEACHER',mine=portal.tab==='mine';el('teaching-actions').hidden=!teacher||mine||portal.tab==='feedback';if(teacher){el('teacher-create').disabled=!teacherPermission('LESSON_MANAGE');el('teacher-publish').disabled=!teacherPermission('TASK_MANAGE');el('teaching-hint').textContent=teacherPermission('LESSON_MANAGE')&&teacherPermission('TASK_MANAGE')?'直接新增本人授课或发布评价，仅限本人负责的班次。':'若按钮不可用，请联系管理员授予“授课安排”、“评价任务”权限并指定负责班次。';}el('course-panel').hidden=mine;el('portal-metrics').hidden=mine;el('account-panel').hidden=!mine;
 for(const button of el('portal-nav').children)button.setAttribute('aria-current',button.dataset.tab===portal.tab?'page':'false');
 el('course-heading').textContent=teacher?({overview:'我的工作台',courses:'我的授课',feedback:'评价反馈'}[portal.tab]||'我的授课'):(portal.tab==='all'?'全部课程':portal.tab==='history'?'评价记录':'待评价课程');
 el('course-description').textContent=teacher?'仅展示本人授课；评价结束后开放汇总反馈。':(portal.tab==='history'?'查看评价状态和提交回执，已提交内容不可修改。':'选择待评价课程完成反馈，已评价课程自动归入记录。');if(mine)return;
 const keyword=el('course-search').value.trim().toLowerCase();let courses=portal.courses.filter(c=>[c.lesson_name,c.class_name,c.teacher_name].filter(Boolean).join(' ').toLowerCase().includes(keyword));
 if(!teacher&&portal.tab!=='all')courses=courses.filter(c=>portal.tab==='history'?!['待评价','待开放'].includes(courseStatus(c)):['待评价','待开放'].includes(courseStatus(c)));
 if(teacher&&portal.tab==='feedback')courses=courses.filter(c=>c.average_score!=null);
 if(teacher&&portal.tab==='overview')courses=courses.slice(0,6);el('courses').replaceChildren();
 if(!courses.length){const empty=element('div',undefined,'portal-empty');empty.append(element('span','✓','empty-symbol'),element('h3',keyword?'没有匹配的课程':teacher?'暂时没有授课安排':portal.tab==='history'?'暂无评价记录':'当前没有待评价课程'),element('p',keyword?'尝试其他课程名称或班次。':teacher?'可点击新增授课；没有可选班次时请联系管理员分配。':'课程发布并关联到你后，会出现在这里。'));el('courses').append(empty);return;}
 for(const course of courses){const card=element('article',undefined,'portal-course'),top=element('div',undefined,'course-top'),status=teacher?(course.submitted?'已收到反馈':'等待反馈'):courseStatus(course);
  top.append(element('span',course.class_name,'course-class'),element('span',status,'course-badge '+(status==='待评价'?'is-pending':'')));card.append(top,element('h3',course.lesson_name),element('p',teacher?'授课时间 · '+dateLabel(course.taught_at):'授课教师 · '+course.teacher_name));
  if(teacher){const expected=Number(course.expected||0),submitted=Number(course.submitted||0),track=element('div',undefined,'portal-progress'),fill=element('span');fill.style.width=(expected?Math.min(100,submitted/expected*100):0)+'%';track.append(fill);card.append(element('p','评价进度 '+submitted+' / '+expected+' 人次'),track,element('p','已开放反馈均分 · '+(course.average_score==null?'暂未开放':Number(course.average_score).toFixed(2))));const button=element('button','查看课程反馈');button.type='button';button.onclick=()=>showFeedback(course).catch(message);card.append(button);const publish=element('button',Number(course.task_count)>0?'已发布评价':'发布评价');publish.type='button';publish.disabled=!teacherPermission('TASK_MANAGE')||Number(course.task_count)>0||String(course.owner_id)!==String(portal.user.id);publish.onclick=()=>openTeaching('task',course.id);card.append(publish);
  }else{card.append(element('p',status==='已评价'?'提交时间 · '+dateLabel(course.submitted_at):'截止时间 · '+dateLabel(course.closes_at)));if(status==='待评价'){const link=element('a','开始评价 →','portal-primary');link.href='/h5/?token='+encodeURIComponent(course.token);card.append(link);}else if(status==='待开放')card.append(element('span','开放时间 · '+dateLabel(course.opens_at),'course-note'));else if(course.receipt)card.append(element('small','回执：'+course.receipt,'course-receipt'));}el('courses').append(card);
 }
}
async function personal(){
 const version=++portal.version,user=await request('/api/auth/me');if(version!==portal.version)return;if(!['STUDENT','TEACHER'].includes(user.role)){message('当前为管理账号，请前往管理工作台。');return;}
 const courses=await request('/api/portal/courses');if(version!==portal.version)return;el('message').textContent='';portal.user=user;portal.courses=courses;const teacher=user.role==='TEACHER';portal.tab=teacher?'overview':'pending';
 // 回跳仅允许本站扫码路径，不能通过参数指定外部站点。
 const next=new URLSearchParams(location.search).get('return');if(!teacher&&next&&/^\/h5\/\?token=[a-zA-Z0-9_-]{1,64}$/.test(next)){location.assign(next);return;}
 el('auth').hidden=true;el('profile').hidden=false;el('portal-nav').hidden=false;document.body.classList.add('portal-ready');el('portal-role').textContent=teacher?'教师工作台':'学员工作台';el('portal-kicker').textContent=teacher?'TEACHING WORKSPACE':'LEARNING WORKSPACE';el('name').textContent='你好，'+(user.display_name||user.username);el('hint').textContent=teacher?'关注每一次授课，了解每一份反馈。':'你的每一份评价，都让下一堂课更好。';el('management-entry').hidden=!user.workspace_allowed;el('account-summary').textContent=(teacher?'教师':'学员')+' · '+(user.phone||user.username);
 el('portal-nav').replaceChildren();for(const[key,label]of teacher?[['overview','工作概览'],['courses','我的授课'],['feedback','评价反馈'],['mine','我的账号']]:[['pending','待评价'],['history','评价记录'],['mine','我的']]){const button=element('button',label);button.type='button';button.dataset.tab=key;button.onclick=()=>personalTab(key);el('portal-nav').append(button);}
 el('portal-metrics').replaceChildren();const metrics=teacher?[['我的课程',courses.length],['收到评价',courses.reduce((sum,c)=>sum+Number(c.submitted||0),0)],['已有汇总',courses.filter(c=>c.average_score!=null).length]]:[['待评价',courses.filter(c=>courseStatus(c)==='待评价').length],['已评价',courses.filter(c=>courseStatus(c)==='已评价').length],['参加课程',courses.length]];for(const[label,value]of metrics){const metric=element('article');metric.append(element('span',label),element('strong',String(value)));const target=teacher?(label==='已有汇总'?'feedback':'courses'):(label==='待评价'?'pending':label==='已评价'?'history':'all');metric.className='stat-link';metric.setAttribute('role','button');metric.setAttribute('tabindex','0');metric.setAttribute('aria-label','查看'+label);metric.title='点击查看'+label;metric.onclick=()=>{el('course-search').value='';personalTab(target);};metric.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();metric.onclick();}};el('portal-metrics').append(metric);}renderPersonal();
}
function closeFeedback(){portal.feedback++;el('feedback-dialog').close();}
el('feedback-close').onclick=closeFeedback;el('feedback-dialog').addEventListener('cancel',()=>{portal.feedback++;});
async function showFeedback(course){const version=++portal.feedback,d=el('feedback-content');d.replaceChildren(element('h2',course.lesson_name),element('p','正在读取课程反馈…'));el('feedback-dialog').showModal();
 try{const result=await request('/api/portal/lessons/'+course.id+'/feedback');if(version!==portal.feedback)return;d.replaceChildren(element('p',result.lesson.class_name,'portal-eyebrow'),element('h2',result.lesson.lesson_name));if(!result.released){d.append(element('p','评价尚未结束，汇总反馈暂未开放。'));return;}
  const summary=result.summary;d.append(element('p','已开放任务 · 已评 '+summary.submitted+' / 应评 '+summary.expected+' 人次'),element('strong',summary.average_score==null?'暂无有效评分':Number(summary.average_score).toFixed(2)+' / 5','feedback-score'));for(const dimension of result.dimensions){const row=element('div',undefined,'feedback-dimension'),track=element('div',undefined,'portal-progress'),fill=element('span');fill.style.width=(dimension.average_score==null?0:Number(dimension.average_score)/5*100)+'%';track.append(fill);row.append(element('span',dimension.label),track,element('strong',dimension.average_score==null?'暂无':Number(dimension.average_score).toFixed(2)));d.append(row);}
 }catch(error){if(version===portal.feedback)d.replaceChildren(element('h2','暂时无法读取反馈'),element('p',error.message));}
}
el('course-search').oninput=renderPersonal;
function teacherPermission(code){return (portal.user?.permissions||[]).includes(code);}
function closeTeaching(){portal.teaching++;el('teaching-dialog').close();}
el('teaching-close').onclick=closeTeaching;el('teaching-dialog').addEventListener('cancel',()=>{portal.teaching++;});
el('teacher-create').onclick=()=>openTeaching('lesson');el('teacher-publish').onclick=()=>openTeaching('task');
function teachingField(form,title,name,type,choices,value){
 const label=element('label',title),input=element(type==='select'?'select':'input');input.name=name;input.required=true;
 if(type==='select')for(const choice of choices){const option=element('option',choice.label);option.value=choice.id;input.append(option);}else{input.type=type;if(type==='text')input.maxLength=120;}
 if(value!==undefined)input.value=value;label.append(input);form.append(label);return input;
}
async function openTeaching(kind,lessonId){
 if(!teacherPermission(kind==='lesson'?'LESSON_MANAGE':'TASK_MANAGE')){message('请联系管理员授予对应的授课管理权限');return;}
 closeFeedback();const version=++portal.teaching,d=el('teaching-content');d.replaceChildren(element('h2',kind==='lesson'?'新增本人授课':'发布课程评价'),element('p','正在读取可用班次与课程…'));el('teaching-dialog').showModal();
 try{
  const options=await request('/api/portal/teaching/options');if(version!==portal.teaching)return;
  if(!(kind==='lesson'?options.can_create_lesson:options.can_publish))throw new Error('权限已变更，请重新登录或联系管理员');
  d.replaceChildren(element('h2',kind==='lesson'?'新增本人授课':'发布课程评价'));
  if(!(kind==='lesson'?options.classes:options.lessons).length){d.append(element('p',kind==='lesson'?'尚未分配负责班次，请联系管理员在班次管理中将你设为负责人。':'没有可以发布的本人授课，请先新增授课；已发布的课程不能重复发布。'));if(kind==='task'&&teacherPermission('LESSON_MANAGE')){const create=element('button','新增授课');create.type='button';create.onclick=()=>openTeaching('lesson');d.append(create);}return;}
  const form=element('form'),error=element('p',undefined,'form-error');error.setAttribute('role','alert');
  const local=date=>new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16),now=new Date();
  if(kind==='lesson'){
   teachingField(form,'课程名称','name','text');teachingField(form,'所属班次','class_id','select',options.classes.map(c=>({id:c.id,label:c.name+' · '+c.student_count+' 位学员'})));
   teachingField(form,'授课时间','taught_at','datetime-local',null,local(now));d.append(element('p','授课教师自动绑定为本人。发布评价前，请确认班次已有学员。'));
  }else{
   const lesson=teachingField(form,'本人授课','lesson_id','select',options.lessons.map(l=>({id:l.id,label:l.name+' · '+l.class_name+' · '+l.student_count+' 位学员'})),lessonId);
   teachingField(form,'评分模板','template_id','select',options.templates.map(t=>({id:t.id,label:t.name})));
   teachingField(form,'开放时间','opens_at','datetime-local',null,local(now));teachingField(form,'截止时间','closes_at','datetime-local',null,local(new Date(now.getTime()+86400000)));
   const preview=element('div',undefined,'publish-preview');form.append(preview);
   function update(){const selected=options.lessons.find(l=>String(l.id)===String(lesson.value));preview.textContent=selected?'应评名单 '+selected.student_count+' 人，发布后锁定名单及评分模板。':'';}
   lesson.onchange=update;update();
   if(!options.templates.length){d.append(element('p','尚无可用评分模板，请联系管理员维护。'));return;}
  }
  const submit=element('button',kind==='lesson'?'保存授课':'确认发布并生成二维码');submit.type='submit';form.append(error,submit);d.append(form);
  if(kind==='task'){
   const select=form.querySelector('[name=lesson_id]'),previous=select.onchange;
   select.onchange=()=>{previous();const selected=options.lessons.find(l=>String(l.id)===String(select.value));submit.disabled=!selected||!Number(selected.student_count);error.textContent=submit.disabled?'该班次尚无学员，请先联系管理员维护名单。':'';};select.onchange();
  }
  form.onsubmit=async event=>{
   event.preventDefault();if(submit.disabled)return;submit.disabled=true;error.textContent='';
   try{const data=Object.fromEntries(new FormData(form));if(kind==='task'&&new Date(data.closes_at)<=new Date(data.opens_at))throw new Error('截止时间必须晚于开放时间');
    const result=await post('/api/portal/teaching/'+(kind==='lesson'?'lessons':'tasks'),data);if(version!==portal.teaching)return;
    await personal();if(version!==portal.teaching)return;personalTabAfterSave();
    if(kind==='lesson'){closeTeaching();message('授课已保存，可在课程卡片上发布评价。');}
    else{
     d.replaceChildren(element('h2','评价已发布'),element('p','学员扫码后登录本人账号完成评分。二维码中的地址需能被学员手机访问。'));
     const image=element('img');image.src='/api/portal/teaching/tasks/'+result.task_id+'/qr';image.alt='课程评价二维码';image.width=240;image.height=240;
     const link=element('input');link.value=result.url;link.readOnly=true;link.setAttribute('aria-label','评价链接');
     const copy=element('button','复制评价链接');copy.type='button';copy.onclick=async()=>{try{await navigator.clipboard.writeText(result.url);message('评价链接已复制');}catch(e){link.select();message('请复制输入框中的评价链接');}};
     const download=element('a','下载二维码');download.href=image.src;download.download='evaluation-qr.png';d.append(image,link,copy,download);
    }
   }catch(e){if(version===portal.teaching)error.textContent=e.message;}finally{submit.disabled=false;}
  };
 }catch(error){if(version===portal.teaching)d.replaceChildren(element('h2','暂时无法操作'),element('p',error.message));}
}
function personalTabAfterSave(){portal.tab='courses';renderPersonal();}
let captchaUrl,captchaLoading=false;
async function refresh(){if(captchaLoading)return;captchaLoading=true;try{const headers={};if(/\.ngrok-free\.(dev|app)$/.test(location.hostname))headers['ngrok-skip-browser-warning']='1';const response=await fetch('/api/auth/captcha?t='+Date.now(),{headers,credentials:'same-origin'});if(!response.ok||!(response.headers.get('Content-Type')||'').includes('image/png'))throw new Error('验证码加载失败，请稍后点击刷新');const image=await response.blob();if(captchaUrl)URL.revokeObjectURL(captchaUrl);captchaUrl=URL.createObjectURL(image);el('captcha').src=captchaUrl;}catch(error){message(error);}finally{captchaLoading=false;}}
el('captcha').onclick=refresh;
function authTab(register){el('registration').hidden=!register;el('login-section').hidden=register;el('show-login').setAttribute('aria-pressed',String(!register));el('show-register').setAttribute('aria-pressed',String(register));if(register)refresh();}
el('show-login').onclick=()=>authTab(false);el('show-register').onclick=()=>authTab(true);
el('login').onsubmit=async event=>{event.preventDefault();const form=event.target,button=form.querySelector('button'),errorBox=el('account-login-error');if(button.disabled)return;errorBox.textContent='';const phone=form.elements.username,password=form.elements.password;phone.removeAttribute('aria-invalid');password.removeAttribute('aria-invalid');if(!/^1[3-9][0-9]{9}$/.test(phone.value.trim())){errorBox.textContent='请输入有效的11位手机号';phone.setAttribute('aria-invalid','true');phone.focus();return;}if(!password.value){errorBox.textContent='请输入登录密码';password.setAttribute('aria-invalid','true');password.focus();return;}phone.value=phone.value.trim();button.disabled=true;try{await request('/api/auth/login',{method:'POST',body:new URLSearchParams(new FormData(form))});password.value='';await personal();}catch(error){errorBox.textContent=error.message;}finally{button.disabled=false;}};
el('register').onsubmit=async event=>{event.preventDefault();const button=event.target.querySelector('button');if(button.disabled)return;button.disabled=true;try{const data=Object.fromEntries(new FormData(event.target));if(data.password!==data.confirm_password)throw new Error('两次密码不一致');message(await post('/api/auth/register',data));event.target.reset();authTab(false);}catch(error){message(error);}finally{button.disabled=false;refresh();}};
el('exit').onclick=()=>{portal.version++;closeFeedback();post('/api/auth/logout',{}).then(()=>location.reload()).catch(message);};
el('change').onsubmit=async event=>{event.preventDefault();const button=event.target.querySelector('button');if(button.disabled)return;button.disabled=true;try{await post('/api/auth/password',Object.fromEntries(new FormData(event.target)));location.reload();}catch(error){message(error);}finally{button.disabled=false;}};
personal().catch(error=>{if(portal.version===1&&error.status!==401)message(error);});
