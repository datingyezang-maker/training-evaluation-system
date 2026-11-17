/* 人员身份与后台权限分离；所有授权请求由管理员接口再次验证。 */
'use strict';
function hasManagementPermission(code){return ['ADMIN','BUSINESS'].includes(state.user.role)||(state.user.permissions||[]).includes(code);}
const permissionLoad=load;
load=async function(){
 // 所有页面装饰层共用同一代加载标识；慢响应只能更新自己对应的那一页。
 const token=state.loadToken={},entity=state.entity;
 state.data=[];$('rows').replaceChildren();$('head').replaceChildren();$('tools').replaceChildren();$('pagination').replaceChildren();$('record-count').textContent='';$('empty').hidden=true;
 $('create').textContent=entity==='tasks'?'＋ 发布评价任务':'＋ 新增'+(entity==='users'?'账号':modules[entity].name);
 $('create').disabled=true;$('create').hidden=!mayEdit();
 try{await permissionLoad();}catch(error){if(state.loadToken===token)throw error;return;}finally{if(state.loadToken===token)$('create').disabled=false;}
 if(state.loadToken!==token||state.entity!==entity)return;
 if(!isAdmin()||!['students','teachers'].includes(state.entity))return;
 const kind=state.entity;
 Array.from($('rows').children).forEach((tr,index)=>{const person=state.data[index];tr.lastChild.append(button('权限设置',()=>profilePermissions(kind,person)));});
};
async function profilePermissions(kind,person){
 const viewToken=state.viewToken;
 const result=await api('/api/admin/profile-permissions/'+kind+'/'+person.id),d=detail(person.name+' · 功能权限');
 if(!result.account){
  d.append(node('p','此档案还没有绑定登录账号。请先创建手机号账号，再设置权限。'));
  d.append(button('创建登录账号',async()=>{await go('users');await edit({role:kind==='students'?'STUDENT':'TEACHER',display_name:person.name,[kind==='students'?'student_id':'teacher_id']:person.id,enabled:true});}));
  return;
 }
 d.append(node('p','登录账号：'+result.account.username+' · '+(result.account.enabled?'已启用':'已停用（授权不会自动启用账号）')));
 d.append(node('p','勾选后可进入管理工作空间，仅管理本人负责的班次。取消全部勾选后恢复仅使用个人中心。'));
 const form=node('form'),group=node('fieldset',undefined,'permission-options');group.append(node('legend','后台功能权限'));
 for(const[code,title,note]of [
  ['CLASS_MANAGE','班次管理','新增、编辑、删除本人负责的班次，以及选择、移除班次学员。'],
  ['LESSON_MANAGE','授课安排','在本人负责的班次内新增、编辑、删除授课安排。'],
  ['TASK_MANAGE','评价任务','在本人负责的班次内发布和管理评价任务、分发二维码及纸质补录。']
 ]){
  const label=node('label',undefined,'permission-option'),input=node('input'),words=node('span');
  input.type='checkbox';input.name='permission';input.value=code;input.checked=result.permissions.includes(code);
  words.append(node('strong',title),node('small',note));label.append(input,words);group.append(label);
 }
 const actions=node('div',undefined,'actions'),save=node('button','保存权限'),cancel=button('取消',closeDrawer),error=node('p',undefined,'form-error');
 save.type='submit';error.setAttribute('role','alert');actions.append(cancel,save);form.append(group,actions,error);
 form.onsubmit=async event=>{event.preventDefault();if(save.disabled)return;save.disabled=true;error.textContent='';try{
  const permissions=Array.from(group.querySelectorAll('input:checked')).map(input=>input.value);
  await send('/api/admin/profile-permissions/'+kind+'/'+person.id,'PUT',{permissions});if(viewToken!==state.viewToken)return;closeDrawer();$('message').textContent='权限已保存，下一次操作立即按新权限校验';
 }catch(e){error.textContent=e.message;}finally{save.disabled=false;}};
 d.append(form);
}
const permissionShell=shell;
shell=function(){permissionShell();if(['STUDENT','TEACHER'].includes(state.user.role)){const a=link('返回个人中心','/account/');a.classList.add('nav-link');$('nav').append(a);}};
