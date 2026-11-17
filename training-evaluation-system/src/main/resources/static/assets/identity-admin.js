'use strict';
roleNames.STUDENT='学员';roleNames.TEACHER='教师';
modules.students.fields=[['name','姓名']];
modules.students.desc='新增学员自动生成学号：00001、00002……；删除后不回收。Excel 第一列学号（留空自动生成），第二列姓名，首行为表头。';
modules.users.cols={id:'编号',username:'登录账号',phone:'手机号',display_name:'姓名',role:'权限角色',enabled:'启用'};
modules.users.desc='学员、教师以手机号登录；教师自注册默认为停用，核对身份及档案后编辑启用。手机号全库唯一。导入文件含初始密码，请妥善保管。';
modules.users.fields=[['username','登录账号（填写手机号后自动使用手机号）'],['phone','手机号','tel'],['display_name','姓名'],['password','密码（至少12位，编辑留空不变）','password'],['role','权限角色','roles'],['enabled','启用','boolean'],['student_id','绑定已有学员（留空则创建档案）','students'],['teacher_id','绑定已有教师（留空则创建档案）','teachers']];
const identityLoad=load;
load=async function(){const token=state.loadToken,loadedEntity=state.entity;await identityLoad();if(state.loadToken!==token||state.entity!==loadedEntity)return;if(state.entity==='users'&&isAdmin()){const file=node('input');file.type='file';file.accept='.xlsx';$('tools').append(link('账号导入模板','/api/business/accounts/template'),file,button('批量导入账号',async()=>{const context=captureUI();if(!file.files.length)throw new Error('请选择账号xlsx文件');const body=new FormData();body.append('file',file.files[0]);const count=await api('/api/business/accounts/import',{method:'POST',body});await refreshUI(context,'成功导入 '+count+' 个账号');}));}};
const identityEdit=edit;
edit=async function(row={}){const viewToken=state.viewToken;await identityEdit(row);ensureView(viewToken);if(state.entity!=='users')return;const field=name=>$('fields').querySelector('[name='+name+']');for(const name of ['student_id','teacher_id']){const select=field(name),option=node('option','不绑定已有档案（自动创建）');option.value='';select.insertBefore(option,select.firstChild);select.required=false;select.value=row[name]==null?'':row[name];}
 const phone=field('phone'),username=field('username'),role=field('role'),displayName=field('display_name');
 if(!row.id&&row.role)role.value=row.role;
 function update(){const mobile=['STUDENT','TEACHER'].includes(role.value);phone.required=mobile;displayName.required=mobile||!!phone.value;field('student_id').parentElement.hidden=role.value!=='STUDENT';field('teacher_id').parentElement.hidden=role.value!=='TEACHER';if(phone.value){username.value=phone.value;username.readOnly=true;}else username.readOnly=false;phone.pattern='1[3-9][0-9]{9}';phone.maxLength=11;}
 phone.oninput=update;role.onchange=update;update();};
