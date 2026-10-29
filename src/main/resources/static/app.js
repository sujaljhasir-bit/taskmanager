'use strict';
const $=id=>document.getElementById(id);
const paths={grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',check:'<rect x="3" y="3" width="18" height="18" rx="4"/><path d="m7 12 3 3 7-7"/>',calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18"/>',users:'<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3m1-16a3 3 0 0 1 0 6m3 10v-3a6 6 0 0 0-2-4"/>',logout:'<path d="M10 4H4v16h6m5-12 4 4-4 4m-7-4h11"/>',plus:'<path d="M12 5v14M5 12h14"/>',search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',list:'<path d="M8 6h13M8 12h13M8 18h13M3 6h.1M3 12h.1M3 18h.1"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'};
const icon=n=>'<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">'+(paths[n]||paths.grid)+'</svg>';
document.querySelectorAll('[data-icon]').forEach(e=>e.innerHTML=icon(e.dataset.icon));
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const initials=n=>n.trim().split(/\s+/).slice(0,2).map(p=>p[0]).join('').toUpperCase();
const labels={TODO:'To do',IN_PROGRESS:'In progress',DONE:'Done'};
const localDate=(d=new Date())=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
const prettyDate=d=>new Date(d+'T12:00:00').toLocaleDateString(undefined,{month:'short',day:'numeric'});
let token=sessionStorage.getItem('metro-token'),user=null,tasks=[],people=[],registerMode=false,view='board',layout='board',editing=null;
let socket=null,retryTimer=null,toastTimer=null,retryDelay=1500,loadSerial=0,connectionSerial=0,ready=false;
function toast(s){$('toast').textContent=s;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4200);}
function connection(s,online=false){$('connection').classList.toggle('online',online);$('connection').querySelector('span').textContent=s;}
async function api(url,options={}){
 let r;try{r=await fetch('/api'+url,{...options,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...options.headers}});}catch{throw new Error('Cannot reach the server. Check your connection and try again.');}
 const data=r.status===204?null:await r.json().catch(()=>null);
 if(!r.ok){if(r.status===401&&user)logout('Your session ended. Please sign in again.');const error=new Error(data?.fields?Object.entries(data.fields).map(([k,v])=>k+': '+v).join('\n'):data?.message||'Request failed. Please try again.');error.status=r.status;throw error;}return data;
}
function logout(message){
 token=null;user=null;ready=false;tasks=[];people=[];loadSerial++;connectionSerial++;
 sessionStorage.removeItem('metro-token');clearTimeout(retryTimer);if(socket)socket.close();socket=null;
 document.querySelectorAll('dialog[open]').forEach(d=>d.close());$('workspace').hidden=true;$('auth').hidden=false;$('auth-error').textContent=message||'';$('auth-password').value='';
}
$('auth-toggle').onclick=()=>{
 registerMode=!registerMode;$('name-field').hidden=!registerMode;$('auth-name').required=registerMode;
 $('auth-title').textContent=registerMode?'Make yourself at home.':'Welcome back.';$('auth-subtitle').textContent=registerMode?'Create your account. Make space for great work.':'Sign in and pick up where you left off.';
 $('auth-submit').innerHTML=registerMode?'Create account <span>↗</span>':'Sign in <span>↗</span>';$('auth-switch-label').textContent=registerMode?'Already part of the city?':'New around here?';$('auth-toggle').textContent=registerMode?'Sign in':'Create an account';
 $('password-hint').hidden=!registerMode;$('auth-password').minLength=registerMode?8:1;$('auth-password').autocomplete=registerMode?'new-password':'current-password';$('auth-error').textContent='';
};
$('auth-form').onsubmit=async e=>{
 e.preventDefault();$('auth-submit').disabled=true;$('auth-error').textContent='';
 try{const body={email:$('auth-email').value.trim(),password:$('auth-password').value};if(registerMode)body.name=$('auth-name').value.trim();
 const result=await api('/auth/'+(registerMode?'register':'login'),{method:'POST',body:JSON.stringify(body)});token=result.token;user=result.user;sessionStorage.setItem('metro-token',token);$('auth-password').value='';await enterWorkspace();}
 catch(error){$('auth-error').textContent=error.message;}finally{$('auth-submit').disabled=false;}
};
async function enterWorkspace(){
 $('auth').hidden=true;$('workspace').hidden=false;$('user-name').textContent=user.name;$('user-role').textContent=user.role==='ADMIN'?'Administrator':'Workspace member';$('user-avatar').textContent=initials(user.name);
 $('today').textContent=new Date().toLocaleDateString(undefined,{month:'short',day:'numeric',year:'numeric'});$('task-board').innerHTML='<div class="loading">Opening your workspace…</div>';ready=false;setView('board');await refresh();connect();
}
async function refresh(){
 const serial=++loadSerial;
 try{const [a,b]=await Promise.all([api('/tasks'),api('/users')]);if(serial!==loadSerial||!user)return;tasks=a;people=b;ready=true;$('board-error').textContent='';render();}
 catch(error){if(user){$('board-error').textContent=error.message;if(!ready){$('task-board').innerHTML='<div class="loading">Unable to load tasks. <button id="retry-load" class="text-button">Refresh</button></div>';$('retry-load').onclick=refresh;}}}
}
async function connect(){
 if(!user)return;const serial=++connectionSerial;clearTimeout(retryTimer);connection('Connecting');
 try{const result=await api('/realtime/ticket',{method:'POST'});if(serial!==connectionSerial||!user)return;
 const ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws?ticket='+encodeURIComponent(result.ticket));socket=ws;
 ws.onmessage=e=>{if(serial!==connectionSerial)return;let event;try{event=JSON.parse(e.data);}catch{return;}
 if(event.type==='READY'){connection('Live updates',true);retryDelay=1500;refresh();}if(event.type==='TASKS_CHANGED')refresh();};
 ws.onclose=()=>{if(serial===connectionSerial&&user){connection('Reconnecting');retryTimer=setTimeout(connect,retryDelay);retryDelay=Math.min(30000,retryDelay*2);}};ws.onerror=()=>ws.close();
 }catch{if(serial===connectionSerial&&user){connection('Reconnecting');retryTimer=setTimeout(connect,retryDelay);retryDelay=Math.min(30000,retryDelay*2);}}
}
$('logout').onclick=()=>logout();
function setView(next){
 view=next;document.querySelectorAll('[data-view]').forEach(e=>e.classList.toggle('active',e.dataset.view===view));
 const titles={board:['Overview','A good day to make progress.','Big ideas, small steps. Keep your team moving forward.'],mine:['Assigned to me','Your work. Your next move.','A little focus goes a long way. Here’s what’s on your plate.'],upcoming:['Upcoming','Stay a step ahead.','Unfinished tasks due in the next seven days, plus anything overdue.'],team:['People','Good work starts with people.','Find your teammates. Turn individual effort into shared progress.']};
 $('breadcrumb-view').textContent=titles[view][0];$('page-title').textContent=titles[view][1];$('page-description').textContent=titles[view][2];$('overview-banner').hidden=view!=='board';$('stats').hidden=view==='team';$('task-section').hidden=view==='team';$('people-section').hidden=view!=='team';$('board-heading').textContent=view==='mine'?'Assigned to you':view==='upcoming'?'Coming up next':'Your task board';if(ready){render();if(view==='team')refresh();}
}
document.querySelectorAll('[data-view]').forEach(e=>e.onclick=()=>setView(e.dataset.view));$('people-link').onclick=()=>setView('team');$('refresh-people').onclick=refresh;
function visibleTasks(){
 let result=tasks;if(view==='mine')result=result.filter(t=>t.assigneeId===user.id);
 if(view==='upcoming'){const next=new Date();next.setDate(next.getDate()+7);result=result.filter(t=>t.status!=='DONE'&&t.dueDate&&t.dueDate<=localDate(next));}
 const q=$('search').value.toLowerCase().trim(),p=$('priority-filter').value,s=$('status-filter').value;return result.filter(t=>(!q||(t.title+' '+t.description).toLowerCase().includes(q))&&(!p||t.priority===p)&&(!s||t.status===s));
}
function card(t){
 const a=people.find(p=>p.id===t.assigneeId),late=t.dueDate&&t.dueDate<localDate()&&t.status!=='DONE';
 return '<article class="task-card" data-task="'+t.id+'" tabindex="0" role="button" aria-label="Edit task: '+esc(t.title)+'"><div class="card-top"><span class="priority '+t.priority.toLowerCase()+'">'+({HIGH:'↑ High priority',MEDIUM:'− Medium',LOW:'↓ Low priority'}[t.priority])+'</span><span class="task-id">MT-'+String(t.id).padStart(3,'0')+'</span></div><div class="card-content"><h3>'+esc(t.title)+'</h3><p class="task-description">'+esc(t.description||'Add a little context to this task.')+'</p></div><div class="card-meta"><span class="due-date '+(late?'overdue':'')+'">'+icon('calendar')+(t.dueDate?prettyDate(t.dueDate)+(late?' · Overdue':''):'No due date')+'</span><span class="card-person"><span>'+esc(a?.name||'Unassigned')+'</span><span class="avatar">'+esc(a?initials(a.name):'—')+'</span></span></div></article>';
}
function render(){
 if(!user)return;$('nav-total').textContent=tasks.length;const n=tasks.length,c=Object.fromEntries(Object.keys(labels).map(k=>[k,tasks.filter(t=>t.status===k).length]));
 const stats=[['Total tasks',n,'In your workspace','grid',100],['To do',c.TODO,'Ready when you are','list',n?c.TODO/n*100:0],['In progress',c.IN_PROGRESS,'Making things happen','clock',n?c.IN_PROGRESS/n*100:0],['Completed',c.DONE,n?Math.round(c.DONE/n*100)+'% of all tasks':'Every step counts','check',n?c.DONE/n*100:0]];
 $('stats').innerHTML=stats.map(([t,v,n,s])=>'<article class="stat"><div class="stat-top"><span>'+t+'</span><span class="stat-icon">'+icon(s)+'</span></div><div class="stat-bottom"><strong class="stat-value">'+String(v).padStart(2,'0')+'</strong><span class="stat-note">'+n+'</span></div><div class="stat-line"><i></i></div></article>').join('');
 $('stats').querySelectorAll('.stat-line i').forEach((e,i)=>{e.style.width=stats[i][4]+'%';e.style.background=['#a69ac5','#d1ad6b','#91a6c0','#85ab94'][i];});
 const visible=visibleTasks();$('task-count').textContent=visible.length+' task'+(visible.length===1?'':'s');$('clear-filters').hidden=!($('search').value||$('priority-filter').value||$('status-filter').value);$('task-board').classList.toggle('list-board',layout==='list');
 $('task-board').innerHTML=Object.entries(labels).map(([s,label])=>{const group=visible.filter(t=>t.status===s);return '<section class="column '+(s==='IN_PROGRESS'?'in-progress':s==='DONE'?'done':'')+'"><div class="column-heading"><i class="status-dot"></i>'+label+'<span class="column-count">'+group.length+'</span><button class="icon-button" data-add="'+s+'" aria-label="Add '+label.toLowerCase()+' task">+</button></div><div class="column-cards">'+(group.map(card).join('')||'<div class="empty-column"><span>○</span>'+(s==='DONE'?'Good things take a few steps.':'A little room for your next idea.')+'</div>')+'</div></section>';}).join('');
 $('task-board').querySelectorAll('[data-task]').forEach(e=>{e.onclick=()=>openTask(Number(e.dataset.task));e.onkeydown=ev=>{if(ev.key==='Enter'||ev.key===' '){ev.preventDefault();openTask(Number(e.dataset.task));}};});
 $('task-board').querySelectorAll('[data-add]').forEach(e=>e.onclick=()=>openTask(null,e.dataset.add));$('first-steps').hidden=tasks.length!==0||view!=='board';
 $('people-grid').innerHTML=people.map(p=>'<article class="person-card"><span class="avatar">'+esc(initials(p.name))+'</span><h3>'+esc(p.name)+(p.id===user.id?' <span class="muted">(you)</span>':'')+'</h3><p>'+esc(p.email)+'</p><small>'+esc(p.role==='ADMIN'?'Administrator':'Workspace member')+'</small></article>').join('');
}
['search','priority-filter','status-filter'].forEach(id=>$(id).addEventListener(id==='search'?'input':'change',render));$('clear-filters').onclick=()=>{$('search').value='';$('priority-filter').value='';$('status-filter').value='';render();};
function setLayout(next){layout=next;['board','list'].forEach(v=>{$(v+'-mode').classList.toggle('selected',next===v);$(v+'-mode').setAttribute('aria-pressed',next===v);});render();}
$('board-mode').onclick=()=>setLayout('board');$('list-mode').onclick=()=>setLayout('list');
async function openTask(id,status='TODO'){
 if(!user)return;$('task-error').textContent='';$('task-form').reset();$('task-owner-note').textContent='';
 try{const [users,t]=await Promise.all([api('/users'),id?api('/tasks/'+id):Promise.resolve(null)]);if(!user)return;people=users;editing=t;
 $('task-assignee').innerHTML='<option value="">Unassigned</option>'+people.map(p=>'<option value="'+p.id+'">'+esc(p.name)+(p.id===user.id?' (you)':'')+'</option>').join('');
 $('dialog-title').textContent=id?'The details make it happen.':'Create a task';$('save-task').textContent=id?'Save changes ↗':'Create task ↗';
 $('task-title').value=t?.title||'';$('task-description').value=t?.description||'';$('task-status').value=t?.status||status;$('task-priority').value=t?.priority||'MEDIUM';$('task-assignee').value=t?.assigneeId||'';$('task-due').value=t?.dueDate||'';
 const owner=!t||t.ownerId===user.id||user.role==='ADMIN';$('task-assignee').disabled=!owner;$('delete-task').hidden=!t||!owner;
 if(t)$('task-owner-note').textContent='Created by '+(people.find(p=>p.id===t.ownerId)?.name||'a teammate')+(!owner?' · Only the owner can reassign or delete.':'');
 if(!$('task-dialog').open)$('task-dialog').showModal();$('task-title').focus();
 }catch(error){toast(error.message);refresh();}
}
$('new-task').onclick=()=>openTask(null);$('close-dialog').onclick=$('cancel-dialog').onclick=()=>$('task-dialog').close();

/* ---- Save, delete, samples and session restore ---- */
$('task-form').onsubmit=async e=>{
 e.preventDefault();$('task-error').textContent='';
 const title=$('task-title').value.trim();
 if(!title){$('task-error').textContent='Title is required.';$('task-title').focus();return;}
 const body={title,description:$('task-description').value.trim(),status:$('task-status').value,priority:$('task-priority').value,dueDate:$('task-due').value||null,assigneeId:$('task-assignee').value?Number($('task-assignee').value):null};
 const wasEditing=!!editing;$('save-task').disabled=true;
 try{
  if(wasEditing){body.version=editing.version;await api('/tasks/'+editing.id,{method:'PUT',body:JSON.stringify(body)});}
  else await api('/tasks',{method:'POST',body:JSON.stringify(body)});
  $('task-dialog').close();toast(wasEditing?'Task updated.':'Task created.');await refresh();
 }catch(error){$('task-error').textContent=error.message;if(error.status===409||error.status===404)refresh();}
 finally{$('save-task').disabled=false;}
};
$('delete-task').onclick=()=>{
 if(!editing)return;$('confirm-error').textContent='';
 $('confirm-text').textContent='“'+editing.title+'” will be removed for everyone who can see it. This cannot be undone.';
 $('confirm-dialog').showModal();$('cancel-delete').focus();
};
$('cancel-delete').onclick=()=>$('confirm-dialog').close();
$('confirm-delete').onclick=async()=>{
 if(!editing)return;$('confirm-delete').disabled=true;
 try{await api('/tasks/'+editing.id,{method:'DELETE'});$('confirm-dialog').close();$('task-dialog').close();toast('Task deleted.');await refresh();}
 catch(error){$('confirm-error').textContent=error.message;if(error.status===404){refresh();}}
 finally{$('confirm-delete').disabled=false;}
};
$('first-task').onclick=()=>openTask(null);
$('sample-tasks').onclick=async()=>{
 $('sample-tasks').disabled=true;
 try{await api('/tasks/samples',{method:'POST'});toast('Added 4 sample tasks.');await refresh();}
 catch(error){toast(error.message);}finally{$('sample-tasks').disabled=false;}
};
['task-dialog','confirm-dialog'].forEach(id=>$(id).addEventListener('click',e=>{if(e.target===$(id))$(id).close();}));
(async()=>{
 if(!token)return;
 try{user=await api('/auth/me');await enterWorkspace();}
 catch(error){if(error.status===401){token=null;user=null;sessionStorage.removeItem('metro-token');}else{$('auth-error').textContent=error.message;}}
})();
