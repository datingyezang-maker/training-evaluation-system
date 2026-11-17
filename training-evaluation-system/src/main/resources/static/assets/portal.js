/* 工作台体验层：复用既有鉴权与业务API，所有统计来自实际权限范围内的数据。 */
'use strict';
modules.dashboard={name:'工作概览',cols:{}};
modules.tasks.cols={id:'编号',lesson_name:'课程',class_name:'班次',status:'状态',completion:'评价进度',opens_at:'开放时间',closes_at:'截止时间'};
modules.classes.cols={id:'编号',name:'班次名称',start_date:'开始日期',end_date:'结束日期',owner_name:'负责人',student_count:'学员人数'};
state.entity='dashboard';
const labels={dashboard:'◈',classes:'▤',teachers:'♙',students:'♧',lessons:'▦',templates:'▧',tasks:'▣',statistics:'▥',users:'⚙',audit:'≡'};
const roleNames={ADMIN:'系统管理员',BUSINESS:'培训负责人',VIEWER:'只读用户'};
const baseLoad=load;
let requestVersion=0;
function statusOf(row){if(row.status==='REVOKED')return '已撤销';if(row.status==='COMPLETED'||(Number(row.expected)>0&&Number(row.submitted)>=Number(row.expected)))return '已完成';if(row.status==='CLOSED')return '已关闭';const now=Date.now();if(now<new Date(row.opens_at).getTime())return '待开放';if(now>new Date(row.closes_at).getTime())return '已截止';return '进行中';}
function display(value,key){if(value==null)return '—';if(key==='role')return roleNames[value]||value;if(key==='enabled')return value===true?'已启用':'已停用';if(/(_at|_date)$/.test(key)){const d=new Date(value);if(!Number.isNaN(d.getTime()))return d.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',...(!key.endsWith('_date')?{hour:'2-digit',minute:'2-digit'}:{})});}if(key==='average_score')return Number(value).toFixed(2);return String(value);}
function link(text,url){const a=node('a',text,'download');a.href=url;return a;}
async function go(entity){if(!modules[entity])throw new Error('功能不存在');state.entity=entity;await load();}
function shell(){document.body.classList.add('signed-in');$('identity').textContent=state.user.username+' · '+roleNames[state.user.role];$('nav').replaceChildren();const title=node('div','培训工作空间','nav-caption');$('nav').append(title);for(const key of ['dashboard','classes','lessons','tasks','statistics','students','teachers','templates','users','audit']){if(['users','audit'].includes(key)&&!isAdmin())continue;const b=button(labels[key]+'  '+modules[key].name,()=>go(key));b.className=state.entity===key?'nav-active':'';$('nav').append(b);}$('nav').append(node('div','培训评估 · 单位内部平台','nav-bottom'));}
function metricLink(card,action,label){card.classList.add('stat-link');card.setAttribute('role','button');card.setAttribute('tabindex','0');card.setAttribute('aria-label','查看'+label);card.title='点击查看'+label;const activate=()=>Promise.resolve().then(action).catch(showError);card.onclick=activate;card.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();activate();}};}
function metric(container,label,value,note,action){const c=node('article',undefined,'metric');c.append(node('p',label),node('strong',String(value)),node('small',note));if(action)metricLink(c,action,label);container.append(c);}
load=async function(){
 const version=++requestVersion;shell();$('detail').hidden=true;$('pagination').replaceChildren();
 if(state.entity==='dashboard'){
  $('list-panel').hidden=true;
  $('heading').textContent='工作概览';$('description').textContent='从培训准备到评价归档，在这里掌握进度。';$('create').hidden=true;$('tools').replaceChildren();$('head').replaceChildren();$('rows').replaceChildren();$('empty').hidden=true;
  const [classes,lessons,tasks]=await Promise.all(['classes','lessons','tasks'].map(e=>api('/api/business/'+e)));if(version!==requestVersion)return;
  state.overviewData={classes,lessons,tasks};
  return;
 }
 $('list-panel').hidden=false;
 // 列表序号是展示位置，不替换用于编辑、删除和关联的数据库主键。
 if(modules[state.entity].cols.id)modules[state.entity].cols.id='序号';
 await baseLoad();if(version!==requestVersion)return;
 if(state.entity==='students'&&isAdmin())$('tools').append(link('下载导入模板','/api/business/students/import-template'));
 $('create').textContent=state.entity==='tasks'?'＋ 发布评价任务':'＋ 新增'+(state.entity==='users'?'账号':modules[state.entity].name);
 const entity=state.entity,keys=Object.keys(modules[entity].cols),rows=Array.from($('rows').children);let keyword='',filter='',page=1;
 rows.forEach((tr,index)=>{const record=state.data[index];keys.forEach((key,i)=>{const cell=tr.children[i];cell.textContent=key==='id'?String(index+1):key==='completion'?(record.submitted||0)+' / '+(record.expected||0)+' 人':key==='status'?statusOf(record):display(record[key],key);if(key==='status'){cell.replaceChildren(node('span',statusOf(record),'status-pill '+(statusOf(record)==='进行中'?'running':'')));}});tr.searchText=keys.filter(key=>key!=='id').map(key=>key==='status'?statusOf(record):display(record[key],key)).join(' ').toLowerCase();});
 if(entity==='tasks'){const select=node('select');select.append(node('option','全部状态'));select.firstChild.value='';for(const value of ['待开放','进行中','已完成','已截止','已关闭','已撤销']){const o=node('option',value);o.value=value;select.append(o);}select.onchange=()=>{filter=select.value;page=1;paginate();};$('tools').append(select);}
 function paginate(){const matched=rows.filter((r,i)=>r.searchText.includes(keyword)&&(!filter||statusOf(state.data[i])===filter));rows.forEach(r=>r.hidden=true);const idColumn=keys.indexOf('id');if(idColumn>=0)matched.forEach((row,index)=>row.children[idColumn].textContent=String(index+1));const pages=Math.max(1,Math.ceil(matched.length/10));page=Math.min(page,pages);matched.slice((page-1)*10,page*10).forEach(r=>r.hidden=false);$('empty').hidden=matched.length!==0;$('pagination').replaceChildren(node('span','共 '+matched.length+' 条 · 第 '+page+' / '+pages+' 页'),button('上一页',()=>{page=Math.max(1,page-1);paginate();}),button('下一页',()=>{page=Math.min(pages,page+1);paginate();}));}
 const search=$('tools').querySelector('input:not([type=file])');if(search)search.oninput=()=>{keyword=search.value.trim().toLowerCase();page=1;paginate();};paginate();
};
const baseDetail=detail;
detail=function(title){const d=baseDetail(title);if(state.entity!=='dashboard'){const close=button('收起',()=>d.hidden=true);close.className='detail-close';d.append(close);}return d;};
members=async function(row){const current=await api('/api/business/classes/'+row.id+'/members'),d=detail(row.name+' · 班次学员');d.append(node('p','已关联 '+current.length+' 位学员。已发布评价任务的应评名单保持不变。'));
 for(const student of current){const item=node('div',undefined,'member-row');item.append(node('span',student.name),node('small',student.code));if(canWrite())item.append(button('移除',async()=>{if(confirm('移除 '+student.name+'？不会删除学员档案。')){await api('/api/business/classes/'+row.id+'/members/'+student.id,{method:'DELETE'});await members(row);}}));d.append(item);}
 if(canWrite()){const search=node('input');search.placeholder='搜索姓名或学号（至少2个字符）';const results=node('div');d.append(node('h3','添加学员'),search,button('搜索可添加学员',async()=>{const candidates=await api('/api/business/classes/'+row.id+'/candidates?keyword='+encodeURIComponent(search.value));results.replaceChildren();if(!candidates.length)results.append(node('p','没有可添加的匹配学员，请先在学员管理录入。'));for(const s of candidates){const item=node('div',undefined,'member-row');item.append(node('span',s.name+' · '+s.code),button('添加到班次',async()=>{await api('/api/business/classes/'+row.id+'/members/'+s.id,{method:'POST'});await members(row);}));results.append(item);}}),results);}
};
qr=async function(row){const share=await api('/api/business/tasks/'+row.id+'/share'),d=detail(row.lesson_name+' · 评价入口'),image=node('img');image.src='/api/business/tasks/'+row.id+'/qr';image.alt='课程评价二维码';image.width=240;const input=node('input');input.value=share.url;input.readOnly=true;d.append(image,node('p','展示本二维码供全班扫码。个人评价码需单独发放，请勿公开展示。'),input,button('复制评价链接',async()=>{try{await navigator.clipboard.writeText(share.url);$('message').textContent='评价链接已复制';}catch(e){input.select();$('message').textContent='请长按或使用 Ctrl+C 复制链接';}}),link('下载二维码',image.src));};
const baseEdit=edit;
edit=async function(row={}){const viewToken=state.viewToken;await baseEdit(row);ensureView(viewToken);if(state.entity==='tasks'&&!row.id){const inputs=$('fields').querySelectorAll('input[type=datetime-local]'),now=new Date();const local=d=>new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);inputs.forEach(input=>input.value=local(input.name==='closes_at'?new Date(now.getTime()+86400000):now));}$('edit-title').textContent=(row.id?'编辑':'新增')+(state.entity==='users'?'账号':modules[state.entity].name);};
const taskEdit=edit;
edit=async function(row={}){const viewToken=state.viewToken;await taskEdit(row);ensureView(viewToken);if(!row.id){const defaults=state.entity==='users'?{role:'BUSINESS'}:state.entity==='templates'?{teaching_label:'教学质量',content_label:'课程内容',organization_label:'组织安排',teaching_weight:1,content_weight:1,organization_weight:1}:{};for(const[key,value]of Object.entries(defaults)){const input=$('fields').querySelector('[name='+key+']');if(input)input.value=value;}}};
const originalReport=report;
report=async function(row){await originalReport(row);const result=await api('/api/business/tasks/'+row.id+'/report'),d=$('detail'),chart=node('div',undefined,'score-chart');chart.append(node('h3','各维度平均得分'));for(const key of ['teaching','content','organization']){const value=result.summary[key],line=node('div',undefined,'score-line'),track=node('div',undefined,'score-track'),fill=node('div',undefined,'score-fill');fill.style.width=(value===null?0:Number(value)/5*100)+'%';track.append(fill);line.append(node('span',result.template[key+'_label']),track,node('strong',value===null?'暂无':Number(value).toFixed(2)+' / 5'));chart.append(line);}d.append(chart);};
const originalTable=table;
table=function(container,rows,columns){originalTable(container,rows.map(row=>{const formatted={...row};for(const key of Object.keys(columns))formatted[key]=display(row[key],key);return formatted;}),columns);};
