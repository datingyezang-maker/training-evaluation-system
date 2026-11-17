/* 本项目导航交互，不依赖任何第三方模板运行时。 */
'use strict';
const navigateWorkspace=go;
go=async function(entity){await navigateWorkspace(entity);document.body.classList.remove('nav-open');$('nav-toggle').setAttribute('aria-expanded','false');};
$('nav-toggle').onclick=function(){const mobile=window.matchMedia('(max-width:1000px)').matches,key=mobile?'nav-open':'nav-collapsed';document.body.classList.toggle(key);const expanded=mobile?document.body.classList.contains(key):!document.body.classList.contains(key);this.setAttribute('aria-expanded',String(expanded));};
$('module-search').oninput=function(){const target=$('module-results');target.replaceChildren();const query=this.value.trim();if(!query)return;const available=Object.keys(modules).filter(key=>!['users','audit'].includes(key)||isAdmin());for(const key of available){if(!modules[key].name.includes(query))continue;target.append(button(modules[key].name,async()=>{await go(key);this.value='';target.replaceChildren();}));}if(!target.children.length)target.append(node('p','没有匹配的功能'));};
