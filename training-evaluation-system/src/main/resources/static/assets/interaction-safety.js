/* 弹窗与编辑操作具有独立生命周期；同页切换记录、关闭窗口也会使旧读取失效。 */
'use strict';
state.viewToken={};
const closePreviousDrawer=closeDrawer;
closeDrawer=function(){state.viewToken={};closePreviousDrawer();};
$('drawer-overlay').onclick=closeDrawer;

// 包装最终生效的入口，不改变每个模块的业务 API、权限或保存事务。
function scopedView(action){return function(...args){closeDrawer();state.viewToken={};return action(...args);};}
members=scopedView(members);codes=scopedView(codes);qr=scopedView(qr);
report=scopedView(report);paper=scopedView(paper);readiness=scopedView(readiness);
progress=scopedView(progress);profilePermissions=scopedView(profilePermissions);
previewTemplate=scopedView(previewTemplate);extendTask=scopedView(extendTask);accountAction=scopedView(accountAction);

const openPreviousEditor=edit;
edit=async function(row={}){
 if(state.editorSaving)throw new Error('正在保存，请等待本次操作完成');
 closeDrawer();state.viewToken={};state.editEntity=state.entity;
 const viewToken=state.viewToken;
 await openPreviousEditor(row);
 if(viewToken!==state.viewToken){const error=new Error('编辑窗口已失效');error.stale=true;throw error;}
};
function dismissEditor(){state.viewToken={};$('editor').close();}
$('cancel').onclick=dismissEditor;$('close-editor').onclick=dismissEditor;
$('editor').addEventListener('cancel',()=>{state.viewToken={};});
const submitPreviousEditor=$('edit-form').onsubmit;
$('edit-form').onsubmit=async function(event){
 event.preventDefault();
 if(state.editorSaving)return;
 if(state.editEntity!==state.entity){$('edit-error').textContent='页面已切换，请关闭窗口后重新编辑';return;}
 state.editorSaving=true;
 try{await submitPreviousEditor(event);}finally{state.editorSaving=false;}
};

// ??????????????????????????????
let noticeTimer;
const notice= new MutationObserver(()=>{clearTimeout(noticeTimer);if($('message').textContent)noticeTimer=setTimeout(()=>{$('message').textContent='';},4000);});
notice.observe($('message'),{childList:true,characterData:true,subtree:true});
