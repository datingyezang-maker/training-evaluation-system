/* 按培训工作流组织入口；页面由本项目实现，不依赖外部模板。 */
'use strict';
async function openModuleEditor(entity,row={}){await go(entity);if(state.entity!==entity)return;await edit(row);}
const operationsOverview=renderOverview;
renderOverview=async function(){
 await operationsOverview();buildingOverview=false;const context=captureUI(),data=state.overviewData;
 if(state.entity!=='dashboard')return;
 const panel=node('section',undefined,'operations-todos'),heading=node('div',undefined,'block-heading');heading.append(node('h2','需要关注'),node('span','按当前班次范围实时汇总','todo-caption'));panel.append(heading);
 const items=[];
 for(const c of data.classes.filter(c=>!Number(c.student_count)))items.push({title:c.name+' · 尚未关联学员',note:'补齐名单后才能发布课程评价',action:()=>members(c),label:'查看名单'});
 const published=new Set(data.tasks.map(t=>String(t.lesson_id)));
 for(const lesson of data.lessons.filter(l=>!published.has(String(l.id))))items.push({title:lesson.name+' · 尚未发布评价',note:lesson.class_name||'请检查授课及名单',action:()=>readiness(lesson),label:'发布准备'});
 for(const task of data.tasks.filter(t=>t.status==='OPEN'&&Number(t.submitted)<Number(t.expected)&&new Date(t.closes_at).getTime()-Date.now()<48*3600000))items.push({title:task.lesson_name+' · '+(new Date(task.closes_at)<new Date()?'已截止，待归档':'即将截止'),note:'已评 '+task.submitted+' / 应评 '+task.expected+' 人',action:()=>hasManagementPermission('TASK_MANAGE')?progress(task):report(task),label:'查看进度'});
 if(isAdmin()){
  const users=await api('/api/business/users');if(!currentUI(context))return;const pending=users.filter(u=>u.review_status==='PENDING').length;
  if(pending)items.unshift({title:pending+' 个账号等待审核',note:'核对教师身份及关联档案后启用',action:()=>go('users'),label:'审核账号'});
 }
 if(!currentUI(context))return;
 if(!items.length)panel.append(node('p','当前没有需要处理的事项，培训进度有序进行。','todo-empty'));
 for(const item of items.slice(0,6)){const line=node('div',undefined,'todo-line'),copy=node('div');copy.append(node('strong',item.title),node('small',item.note));line.append(node('span','·','todo-dot'),copy,button(item.label,item.action));panel.append(line);}
 $('detail').insertBefore(panel,$('detail').children[1]);
};
const trainingLoad=load;
load=async function(){const pending=trainingLoad(),token=state.loadToken;await pending;if(token!==state.loadToken||state.entity!=='classes')return;for(const[ index,tr]of Array.from($('rows').children).entries()){
 const row=state.data[index],cell=tr.lastChild;
 for(const action of Array.from(cell.children))if(action.textContent==='学员名单')action.remove();
 cell.insertBefore(button('班次详情',()=>classWorkspace(row)),cell.firstChild);
}};
async function classWorkspace(row){
 const result=await api('/api/business/classes/'+row.id+'/overview'),d=detail(row.name+' · 班次工作区'),c=result.training_class;
 d.append(node('p',display(c.start_date,'start_date')+' 至 '+display(c.end_date,'end_date')+' · 负责人 '+(c.owner_name||c.owner_account)));
 const stats=node('div',undefined,'metrics');metric(stats,'学员',result.students.length,'当前班次名单',()=>tab('students'));metric(stats,'授课',result.lessons.length,'已安排课程',()=>tab('lessons'));metric(stats,'评价任务',result.tasks.length,'未删除任务',()=>tab('tasks'));d.append(stats);
 const tabs=node('div',undefined,'workspace-tabs'),content=node('section',undefined,'class-workspace-content');d.append(tabs,content);
 function tab(key){
  content.replaceChildren();for(const b of tabs.children)b.setAttribute('aria-pressed',String(b.dataset.tab===key));
  if(key==='lessons'){
   if(hasManagementPermission('LESSON_MANAGE'))content.append(button('新增授课安排',()=>openModuleEditor('lessons',{class_id:row.id})));
   for(const lesson of result.lessons){const card=node('article',undefined,'class-workspace-card');card.append(node('h3',lesson.name),node('p',lesson.teacher_name+' · '+display(lesson.taught_at,'taught_at')),button('发布准备',()=>readiness(lesson)));content.append(card);}
   if(!result.lessons.length)content.append(node('p','暂无授课安排。'));
  }else if(key==='students'){
   if(hasManagementPermission('CLASS_MANAGE'))content.append(button('选择或移除学员',()=>members(row)));
   table(content,result.students,{code:'学号',name:'姓名'});
  }else{
   for(const task of result.tasks){const card=node('article',undefined,'class-workspace-card');card.append(node('h3',task.lesson_name),node('p',statusOf(task)+' · 已评 '+task.submitted+' / 应评 '+task.expected+' 人'),button('评价结果 / 导出',()=>report(task)));if(task.status==='OPEN'&&hasManagementPermission('TASK_MANAGE'))card.append(button('二维码',()=>qr(task)));content.append(card);}
   if(!result.tasks.length)content.append(node('p','暂无评价任务，请先完成授课安排与学员名单。'));
  }
 }
 for(const[key,label]of [['lessons','授课安排'],['students','学员名单'],['tasks','评价任务']]){const b=button(label,()=>tab(key));b.dataset.tab=key;tabs.append(b);}tab('lessons');
}
classWorkspace=scopedView(classWorkspace);

// 发布向导复用现有表单、名单检查、CSRF和服务端唯一发布规则。
const workflowEditor=edit;
edit=async function(row={}){
 state.taskWizard=null;await workflowEditor(row);
 if(state.entity!=='tasks'||row.id)return;
 $('edit-title').textContent='发布评价任务';
 const context=captureUI(),fields=$('fields'),labels=Array.from(fields.children).filter(n=>n.tagName.toLowerCase()==='label'),notice=fields.querySelector('.publish-check'),steps=node('div',undefined,'publish-steps'),sections=[node('section'),node('section'),node('section')];
 steps.setAttribute('aria-label','评价任务发布步骤');for(const[index,title]of ['选择授课','模板与时间','确认发布'].entries())steps.append(node('span',(index+1)+' '+title));
 sections[0].append(labels[0]);if(notice)sections[0].append(notice);for(const label of labels.slice(1))sections[1].append(label);
 const templateSelect=labels[1].querySelector('select'),templatePreview=node('div',undefined,'publish-review');
 function showTemplate(){const template=(templateSelect.choiceRecords||[]).find(t=>String(t.id)===String(templateSelect.value));templatePreview.replaceChildren(node('strong','评分维度预览'));if(template)for(const dimension of template.dimensions||[])templatePreview.append(node('p',dimension.label+' · 权重 '+dimension.weight+' · 1—5分'));}
 templateSelect.onchange=showTemplate;sections[1].append(templatePreview);showTemplate();
 const review=node('div',undefined,'publish-review');sections[2].append(node('h3','请确认本次评价安排'),review,node('p','发布后锁定模板与应评名单。每位学员仅可提交一次，收齐全部评价后自动完成。'));
 fields.replaceChildren(steps,...sections);
 const actions=$('edit-form').querySelector('.actions'),submit=actions.querySelector('button[type=submit]'),previous=button('上一步',()=>showStep(step-1)),next=button('下一步',()=>{if(validStep(step))showStep(step+1);});actions.insertBefore(previous,submit);actions.insertBefore(next,submit);
 let step=0;$('edit-form').noValidate=true;
 function validStep(index){for(const input of sections[index].querySelectorAll('input,select'))if(!input.checkValidity()){showStep(index);input.reportValidity();return false;}return true;}
 function showStep(index){step=Math.max(0,Math.min(2,index));sections.forEach((section,i)=>section.hidden=i!==step);Array.from(steps.children).forEach((item,i)=>item.setAttribute('aria-current',i===step?'step':'false'));previous.hidden=step===0;next.hidden=step===2;submit.hidden=step!==2;submit.textContent='确认发布';
  if(step===2){review.replaceChildren();for(const label of labels){const input=label.querySelector('input,select'),line=node('p');line.append(node('strong',({lesson_id:'授课',template_id:'评分模板',opens_at:'开放时间',closes_at:'截止时间'}[input.name]||input.name)+'：'),node('span',input.tagName.toLowerCase()==='select'?(input.selectedOptions[0]?.textContent||'未选择'):input.value.replace('T',' ')));review.append(line);}if(notice)review.append(node('p',notice.textContent));}
 }
 state.taskWizard={context,sections,previous,next,submit,showStep,validStep};showStep(0);
};
const publishSubmit=$('edit-form').onsubmit;
$('edit-form').onsubmit=async function(event){
 const wizard=state.taskWizard;
 if(wizard&&state.entity==='tasks'){
  event.preventDefault();if(!currentUI(wizard.context))return;
  for(let i=0;i<2;i++)if(!wizard.validStep(i)){wizard.showStep(i);return;}
  if(wizard.submit.disabled)return;
 }
 const lessonId=wizard?$('fields').querySelector('[name=lesson_id]').value:null;
 const result=await publishSubmit(event);
 if(wizard&&!$('editor').open&&!$('edit-error').textContent&&state.entity==='tasks'&&$('detail').hidden){
  const task=state.data.find(task=>String(task.lesson_id)===String(lessonId)&&task.status==='OPEN');
  if(task)await qr(task);
 }
 return result;
};
// 下次编辑时清理向导专用按钮，避免跨模块复用操作区。
const resetWizardEditor=edit;
edit=async function(row={}){if(state.taskWizard){state.taskWizard.previous.remove();state.taskWizard.next.remove();state.taskWizard.submit.hidden=false;state.taskWizard.submit.textContent='保存';}$('edit-form').noValidate=false;return resetWizardEditor(row);};
