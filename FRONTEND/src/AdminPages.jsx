import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, ArchiveRestore, ArrowRight, Check, ChevronUp, KeyRound, Lock, Pencil, Plus, Save, ScrollText, Search, ShieldCheck, SlidersHorizontal, Trash2, Unlock, UserPlus, Warehouse, X } from 'lucide-react'
import { api } from './api'

const initials = (name = '') => name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'RC'

const ROLES={CLUB_MANAGER:'Quản lý câu lạc bộ',HEAD_TRAINER:'Huấn luyện viên trưởng',VETERINARIAN:'Bác sĩ thú y',STABLE_STAFF:'Nhân viên chuồng',HORSE_OWNER:'Chủ sở hữu ngựa'}
const MODULES={PROFILE:'Hồ sơ ngựa',TRAINING:'Huấn luyện',MEDICAL:'Y tế',STABLE_CARE:'Chăm sóc chuồng',RACING:'Giải đua',REPORTING:'Báo cáo'}
const money=(value)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(value||0)
const monthNow=()=>new Date().toISOString().slice(0,7)
const priorMonth=(month)=>{
  if(!month) return ''
  const [y,m]=month.split('-').map(Number)
  if(!y||!m||isNaN(y)||isNaN(m)) return ''
  const d=new Date(y,m-2,1)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`
}

export function AdminDashboard(){
  const [month,setMonth]=useState(monthNow());
  const [data,setData]=useState(null);
  const [stables,setStables]=useState([]);
  const [recentAudits,setRecentAudits]=useState([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('')
  const navigate = useNavigate();

  useEffect(()=>{
    if(!month) return
    setLoading(true); setError('');
    const prev = priorMonth(month);
    Promise.all([
      api(`/api/admin/dashboard?month=${month}${prev ? `&compareTo=${prev}` : ''}`),
      api('/api/stables').catch(()=>[]),
      api('/api/admin/audit-logs?size=5').catch(()=>[])
    ])
      .then(([dashData, stablesData, auditsData])=>{
        setData(dashData);
        setStables(Array.isArray(stablesData) ? stablesData : []);
        setRecentAudits(Array.isArray(auditsData) ? auditsData : []);
      })
      .catch(e=>setError(e.message))
      .finally(()=>setLoading(false))
  },[month])

  const current = data?.current || {};
  const previous = data?.previous || {};
  const stablesList = Array.isArray(stables) ? stables : [];
  const stablesCount = stablesList.length;
  const totalStalls = stablesList.reduce((acc, s) => acc + (s.stalls?.length || 0), 0);
  const occupiedStalls = stablesList.reduce((acc, s) => acc + (s.stalls?.filter(st => Boolean(st.currentAssignment))?.length || 0), 0);
  const vacantStalls = Math.max(0, totalStalls - occupiedStalls);
  const occupancyRate = totalStalls > 0 ? Math.round((occupiedStalls / totalStalls) * 100) : 0;

  const cards = data ? [
    ['Hồ sơ ngựa CLB', `${data.activeHorses ?? 0} con`, 'Đang nuôi dưỡng & hoạt động'],
    ['Tài khoản nhân sự', `${data.activeAccounts ?? 0} người`, '5 vai trò trong hệ thống'],
    ['Hạ tầng chuồng trại', `${totalStalls} ô chuồng`, `${stablesCount} khu · ${occupiedStalls} có ngựa`],
    ['Lợi nhuận ròng', money(current.net), `Thu: ${money(current.revenue)} · Chi: ${money(current.expense)}`]
  ] : []

  return <>
    <div className="page-heading">
      <div>
        <p className="eyebrow">DASHBOARD ĐIỀU HÀNH</p>
        <h1>Tổng quan câu lạc bộ</h1>
        <p>Báo cáo cấp cao về quy mô đàn ngựa, nhân sự, hạ tầng chuồng trại và tài chính theo tháng.</p>
      </div>
      <label className="month-picker">
        Tháng
        <input type="month" value={month} onChange={e=>setMonth(e.target.value)}/>
      </label>
    </div>
    {error&&<Notice text={error}/>}
    {loading ? (
      <section className="card simple-empty">Đang tải dữ liệu tổng quan điều hành…</section>
    ) : data?.current ? (
      <>
        <div className="admin-kpis">
          {cards.map(([label,currVal,subVal])=>(
            <div className="card comparison-card" key={label}>
              <span>{label}</span>
              <strong>{currVal}</strong>
              <small>{subVal}</small>
            </div>
          ))}
        </div>
        <div className="admin-dashboard-grid">
          <section className="card">
            <div className="admin-card-header-action">
              <div className="admin-section-head" style={{padding:0}}>
                <Warehouse size={18}/>
                <h3>Hiện trạng chuồng trại</h3>
              </div>
              <button type="button" onClick={()=>navigate('/admin/stables')}>
                Quản lý chuồng trại <ArrowRight size={14}/>
              </button>
            </div>
            <div className="occupancy-metric">
              <div>
                <div style={{display:'flex',justifyContent:'space-between',fontSize:'11px',marginBottom:'6px'}}>
                  <span style={{color:'var(--muted)'}}>Tỷ lệ lấp đầy ô chuồng</span>
                  <b style={{color:'#1e4a3b'}}>{occupancyRate}%</b>
                </div>
                <div className="occupancy-bar-track">
                  <div className="occupancy-bar-fill" style={{width:`${Math.min(100,occupancyRate)}%`}}/>
                </div>
              </div>
              <div className="occupancy-stats-row">
                <div className="occupancy-stat-box">
                  <b>{stablesCount}</b>
                  <span>Khu chuồng</span>
                </div>
                <div className="occupancy-stat-box">
                  <b style={{color:'#29734e'}}>{occupiedStalls}</b>
                  <span>Đang nuôi ngựa</span>
                </div>
                <div className="occupancy-stat-box">
                  <b style={{color:'#526b60'}}>{vacantStalls}</b>
                  <span>Ô chuồng trống</span>
                </div>
              </div>
            </div>
            {stablesList.length > 0 && (
              <div className="stable-quick-list">
                {stablesList.slice(0, 3).map(s => {
                  const sTotal = s.stalls?.length || 0;
                  const sOcc = s.stalls?.filter(st => Boolean(st.currentAssignment))?.length || 0;
                  return (
                    <div className="stable-quick-item" key={s.id}>
                      <div>
                        <b>{s.name}</b>
                        <span>{s.location || 'Chưa thiết lập vị trí'}</span>
                      </div>
                      <span className="stall-badge occupied">{sOcc}/{sTotal} ô có ngựa</span>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
          <section className="card">
            <div className="admin-section-head">
              <ChevronUp size={18}/>
              <h3>Cơ cấu tài chính</h3>
            </div>
            <div className="finance-lines">
              <Line label="Thức ăn" value={current.feedCost}/>
              <Line label="Y tế" value={current.medicalCost}/>
              <Line label="Vận hành" value={current.operatingCost}/>
              <Line label="Tiền thưởng" value={current.prizeRevenue} positive/>
              <Line label="Chênh lệch ròng" value={current.net} strong/>
            </div>
          </section>
        </div>

        {recentAudits.length > 0 && (
          <section className="card" style={{marginTop:'17px',overflow:'hidden'}}>
            <div className="admin-card-header-action" style={{borderBottom:'1px solid #edf0eb'}}>
              <div className="admin-section-head" style={{padding:0}}>
                <ScrollText size={18}/>
                <h3>Nhật ký an ninh & kiểm toán gần đây</h3>
              </div>
              <button type="button" onClick={()=>navigate('/admin/audit-logs')}>
                Xem tất cả nhật ký <ArrowRight size={14}/>
              </button>
            </div>
            <div style={{overflowX:'auto'}}>
              <table className="recent-audit-table">
                <thead>
                  <tr>
                    <th>Thời điểm</th>
                    <th>Người thao tác</th>
                    <th>Phân hệ</th>
                    <th>Hành động</th>
                    <th>Nội dung thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {recentAudits.map(x => (
                    <tr key={x.id}>
                      <td style={{whiteSpace:'nowrap',color:'var(--muted)'}}>
                        {x.occurredAt ? new Date(x.occurredAt).toLocaleString('vi-VN') : '—'}
                      </td>
                      <td><b>{x.actorUsername || 'Hệ thống'}</b></td>
                      <td><span className="role-tags"><span>{x.module}</span></span></td>
                      <td><b>{x.action}</b></td>
                      <td style={{color:'#465a51'}}>{x.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </>
    ) : !error && (
      <section className="card simple-empty">Không có dữ liệu tổng quan.</section>
    )}
  </>
}
function Line({label,value,positive,strong}){return <div className={strong?'strong':''}><span>{label}</span><b className={positive?'positive':''}>{money(value)}</b></div>}

export function AccountsPage(){
  const [items,setItems]=useState([]);const [query,setQuery]=useState('');const [role,setRole]=useState('');const [enabled,setEnabled]=useState('');const [selected,setSelected]=useState(null);const [creating,setCreating]=useState(false);const [error,setError]=useState('');const [loading,setLoading]=useState(true)
  const load=useCallback(()=>{
    setLoading(true);
    const p=new URLSearchParams({size:'100'});
    if(query)p.set('query',query);
    if(role)p.set('role',role);
    if(enabled!=='')p.set('enabled',enabled);
    api(`/api/admin/accounts?${p}`)
      .then(res=>setItems(Array.isArray(res)?res:[]))
      .catch(e=>setError(e.message))
      .finally(()=>setLoading(false))
  },[query,role,enabled])
  useEffect(()=>{void load()},[load])
  const safeItems = Array.isArray(items) ? items : []
  return <><Heading eyebrow="QUẢN TRỊ TRUY CẬP" title="Tài khoản nhân sự" text="Tạo, sửa, khóa, đặt lại mật khẩu và phân vai trò." action={<button className="button primary" onClick={()=>setCreating(true)}><UserPlus size={17}/>Tạo tài khoản</button>}/><div className="toolbar card"><label className="search-box"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Tên hoặc email…"/></label><select value={role} onChange={e=>setRole(e.target.value)}><option value="">Tất cả vai trò</option>{Object.entries(ROLES).map(([v,l])=><option value={v} key={v}>{l}</option>)}</select><select value={enabled} onChange={e=>setEnabled(e.target.value)}><option value="">Mọi trạng thái</option><option value="true">Hoạt động</option><option value="false">Đã khóa</option></select></div>{error&&<Notice text={error}/>}<section className="card admin-table"><div className="admin-table-head"><span>Nhân sự</span><span>Vai trò</span><span>Trạng thái</span><span>Thao tác</span></div>{loading ? <div className="simple-empty">Đang tải danh sách tài khoản…</div> : !safeItems.length ? <div className="simple-empty">Không tìm thấy tài khoản phù hợp.</div> : safeItems.map(u=><div className="admin-table-row" key={u.id}><div><b>{u.username}</b><small>{u.email}</small></div><div className="role-tags">{(u.roles || []).map(r=><span key={r}>{ROLES[r]}</span>)}</div><span className={`account-state ${u.enabled?'on':'off'}`}>{u.enabled?'Hoạt động':'Đã khóa'}</span><button className="icon-button" title="Quản lý" onClick={()=>setSelected(u)}><Pencil size={16}/></button></div>)}</section>{creating&&<CreateAccount onClose={()=>setCreating(false)} onSaved={()=>{setCreating(false);load()}}/>}{selected&&<ManageAccount user={selected} onClose={()=>setSelected(null)} onSaved={()=>{setSelected(null);load()}}/>}</>
}

export function PermissionsPage(){
  const [items,setItems]=useState([]);const [error,setError]=useState('');const [busy,setBusy]=useState('');const [loading,setLoading]=useState(true)
  const load=useCallback(async()=>{
    setLoading(true); setError('');
    try{
      const res = await api('/api/admin/role-permissions');
      setItems(Array.isArray(res) ? res : []);
    }catch(e){
      setError(e.message);
      setItems([]);
    }finally{
      setLoading(false);
    }
  },[])
  useEffect(()=>{void load()},[load])
  const safeItems = Array.isArray(items) ? items : []
  const roles=Object.keys(ROLES).filter(r=>r!=='CLUB_MANAGER');
  const modules=[...new Set(safeItems.map(x=>x.module))];
  const toggle=async item=>{
    const key=`${item.role}-${item.module}-${item.action}`;
    setBusy(key); setError('');
    try{
      await api(`/api/admin/role-permissions/${item.role}`,{method:'PUT',body:JSON.stringify({module:item.module,action:item.action,granted:!item.granted})});
      await load();
    }catch(e){
      setError(e.message);
    }finally{
      setBusy('');
    }
  }
  return <><Heading eyebrow="PHÂN QUYỀN RBAC" title="Ma trận quyền theo vai trò" text="Bật hoặc tắt các quyền mà từng phân hệ thực sự hỗ trợ."/>{error&&<Notice text={error}/>} {loading?<section className="card simple-empty">Đang tải ma trận quyền…</section>:modules.length?<div className="permission-grid">{roles.map(role=><section className="card permission-card" key={role}><SectionHead icon={SlidersHorizontal} title={ROLES[role]}/><div className="permission-head"><span>Phân hệ</span><span>Xem</span><span>Sửa</span><span>Duyệt</span></div>{modules.map(module=><div className="permission-row" key={module}><b>{MODULES[module]||module}</b>{['VIEW','EDIT','APPROVE'].map(action=>{const item=safeItems.find(x=>x.role===role&&x.module===module&&x.action===action);const key=`${role}-${module}-${action}`;return item?<button aria-label={`${ROLES[role]} ${MODULES[module]||module} ${action}`} disabled={busy===key} className={item.granted?'granted':''} key={action} onClick={()=>toggle(item)}>{item.granted?<Check size={15}/>:<X size={15}/>}</button>:<span className="unsupported-permission" key={action}>—</span>})}</div>)}</section>)}</div>:<section className="card simple-empty">Chưa có dữ liệu phân quyền.</section>}</>
}
function CreateAccount({onClose,onSaved}){const [form,setForm]=useState({username:'',email:'',password:'',roles:['HEAD_TRAINER']});const [error,setError]=useState('');const submit=async e=>{e.preventDefault();try{await api('/api/users',{method:'POST',body:JSON.stringify(form)});onSaved()}catch(x){setError(x.message)}};return <Dialog title="Tạo tài khoản" onClose={onClose}><form onSubmit={submit} className="form-stack">{error&&<Notice text={error}/>}<Input label="Tên tài khoản" value={form.username} onChange={v=>setForm({...form,username:v})}/><Input label="Email" type="email" value={form.email} onChange={v=>setForm({...form,email:v})}/><Input label="Mật khẩu ban đầu" type="password" value={form.password} onChange={v=>setForm({...form,password:v})}/><RoleChecks chosen={form.roles} setChosen={roles=>setForm({...form,roles})}/><Actions onClose={onClose}/></form></Dialog>}
function ManageAccount({user,onClose,onSaved}){const [username,setUsername]=useState(user.username);const [email,setEmail]=useState(user.email);const [roles,setRoles]=useState(user.roles);const [password,setPassword]=useState('');const [error,setError]=useState('');const run=async fn=>{setError('');try{await fn();onSaved()}catch(e){setError(e.message)}};return <Dialog title={`Quản lý · ${user.username}`} onClose={onClose}>{error&&<Notice text={error}/>}<div className="form-stack"><Input label="Tên tài khoản" value={username} onChange={setUsername}/><Input label="Email" type="email" value={email} onChange={setEmail}/><button className="button secondary" onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}`,{method:'PUT',body:JSON.stringify({username,email})}))}><Pencil size={16}/>Lưu thông tin</button><RoleChecks chosen={roles} setChosen={setRoles}/><button className="button secondary" onClick={()=>run(()=>api(`/api/users/${user.id}/roles`,{method:'PUT',body:JSON.stringify({roles})}))}><ShieldCheck size={16}/>Lưu vai trò</button><div className="inline-action"><input type="password" minLength="8" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Mật khẩu mới, tối thiểu 8 ký tự"/><button className="button secondary" disabled={password.length<8} onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}/reset-password`,{method:'POST',body:JSON.stringify({password})}))}><KeyRound size={16}/>Đặt lại</button></div><button className={`button ${user.enabled?'danger-outline':'secondary'}`} onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}/status`,{method:'PATCH',body:JSON.stringify({enabled:!user.enabled})}))}>{user.enabled?<Lock size={16}/>:<Unlock size={16}/>} {user.enabled?'Khóa tài khoản':'Mở khóa tài khoản'}</button></div></Dialog>}
function RoleChecks({chosen,setChosen}){const toggle=r=>setChosen(chosen.includes(r)?chosen.filter(x=>x!==r):[...chosen,r]);return <div className="compact-roles">{Object.entries(ROLES).map(([r,l])=><label className={chosen.includes(r)?'chosen':''} key={r}><input type="checkbox" checked={chosen.includes(r)} onChange={()=>toggle(r)}/><span>{l}</span>{chosen.includes(r)&&<Check size={15}/>}</label>)}</div>}

export function ArchivedHorses(){const [items,setItems]=useState([]);const [query,setQuery]=useState('');const [error,setError]=useState('');const [loading,setLoading]=useState(true);const load=useCallback(()=>{setLoading(true);api(`/api/admin/horses/archived?size=100&query=${encodeURIComponent(query)}`).then(res=>setItems(Array.isArray(res)?res:[])).catch(e=>setError(e.message)).finally(()=>setLoading(false))},[query]);useEffect(()=>{void load()},[load]);const safeItems=Array.isArray(items)?items:[];const restore=async h=>{if(!confirm(`Khôi phục hồ sơ ${h.name}?`))return;try{await api(`/api/admin/horses/${h.id}/restore`,{method:'PATCH'});await load()}catch(e){setError(e.message)}};return <><Heading eyebrow="QUẢN LÝ NGỰA" title="Hồ sơ đã lưu trữ" text="Kiểm tra và khôi phục hồ sơ ngựa khi cần."/><div className="toolbar card"><label className="search-box"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Tên hoặc mã ngựa…"/></label></div>{error&&<Notice text={error}/>}<section className="card archived-list">{loading ? <div className="simple-empty">Đang tải hồ sơ lưu trữ…</div> : !safeItems.length ? <div className="simple-empty">Không có hồ sơ đã lưu trữ.</div> : safeItems.map(h=><div key={h.id}><div><b>{h.name}</b><small>{h.code} · {h.breed} · Lưu {h.archivedAt ? new Date(h.archivedAt).toLocaleString('vi-VN') : '—'}</small></div><button className="button secondary" onClick={()=>restore(h)}><ArchiveRestore size={16}/>Khôi phục</button></div>)}</section></>}

export function AuditLogs(){const [items,setItems]=useState([]);const [module,setModule]=useState('');const [action,setAction]=useState('');const [selected,setSelected]=useState(null);const [error,setError]=useState('');const [loading,setLoading]=useState(true);useEffect(()=>{setLoading(true);const p=new URLSearchParams({size:'200'});if(module)p.set('module',module);if(action)p.set('action',action);api(`/api/admin/audit-logs?${p}`).then(res=>setItems(Array.isArray(res)?res:[])).catch(e=>setError(e.message)).finally(()=>setLoading(false))},[module,action]);const safeItems=Array.isArray(items)?items:[];return <><Heading eyebrow="AN TOÀN HỆ THỐNG" title="Nhật ký kiểm toán" text="Theo dõi ai đã thao tác, thời điểm và dữ liệu thay đổi."/><div className="toolbar card"><select value={module} onChange={e=>setModule(e.target.value)}><option value="">Tất cả phân hệ</option>{['ACCESS','PROFILE','TRAINING','MEDICAL','STABLE_CARE','RACING','REPORTING'].map(x=><option key={x}>{x}</option>)}</select><input value={action} onChange={e=>setAction(e.target.value)} placeholder="Lọc hành động…"/></div>{error&&<Notice text={error}/>}<section className="card audit-list">{loading ? <div className="simple-empty">Đang tải nhật ký kiểm toán…</div> : !safeItems.length ? <div className="simple-empty">Không có dữ liệu nhật ký.</div> : safeItems.map(x=><button key={x.id} onClick={()=>setSelected(x)}><span className="audit-icon"><ScrollText size={17}/></span><div><b>{x.actorUsername||'Hệ thống'} · {x.action}</b><small>{x.module} / {x.entityType||'SYSTEM'} · {x.occurredAt ? new Date(x.occurredAt).toLocaleString('vi-VN') : '—'}</small><p>{x.description}</p></div></button>)}</section>{selected&&<Dialog title={`Audit #${selected.id}`} onClose={()=>setSelected(null)}><dl className="audit-detail"><div><dt>Người thao tác</dt><dd>{selected.actorUsername||'Hệ thống'}</dd></div><div><dt>Đối tượng</dt><dd>{selected.entityType} {selected.entityId||''}</dd></div><div><dt>Trước thay đổi</dt><dd><pre>{pretty(selected.oldValuesJson)}</pre></dd></div><div><dt>Sau thay đổi</dt><dd><pre>{pretty(selected.newValuesJson)}</pre></dd></div><div><dt>IP / thiết bị</dt><dd>{selected.ipAddress||'—'} · {selected.userAgent||'—'}</dd></div></dl></Dialog>}</>}
function pretty(value){if(!value)return 'Không có dữ liệu';try{return JSON.stringify(JSON.parse(value),null,2)}catch{return value}}

function Heading({eyebrow,title,text,action}){return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{text}</p></div>{action}</div>}
function SectionHead({icon:Icon,title}){return <div className="admin-section-head"><Icon size={18}/><h3>{title}</h3></div>}
function Notice({text}){return <div className="notice error"><X size={16}/>{text}</div>}
function Dialog({title,onClose,children}){return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal admin-modal" onMouseDown={e=>e.stopPropagation()}><div className="modal-head"><h2>{title}</h2><button className="icon-button" onClick={onClose}><X/></button></div>{children}</div></div>}
function Input({label,type='text',value,onChange}){return <label className="field"><span>{label}</span><input required type={type} value={value} onChange={e=>onChange(e.target.value)}/></label>}
function Actions({onClose}){return <div className="modal-actions"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary"><Plus size={16}/>Lưu</button></div>}

export function StablesPage(){
  const [stables,setStables]=useState([]);
  const [horses,setHorses]=useState([]);
  const [query,setQuery]=useState('');
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [creatingStable,setCreatingStable]=useState(false);
  const [editingStable,setEditingStable]=useState(null);
  const [addingStallStable,setAddingStallStable]=useState(null);
  const [assigningStall,setAssigningStall]=useState(null);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [stablesRes, horsesRes] = await Promise.all([
        api('/api/stables?includeInactive=true'),
        api('/api/horses?size=200').catch(() => ({ content: [] }))
      ]);
      setStables(Array.isArray(stablesRes) ? stablesRes : []);
      const hList = Array.isArray(horsesRes) ? horsesRes : (horsesRes?.content || []);
      setHorses(hList);
    } catch(e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load() }, [load]);

  const stablesList = Array.isArray(stables) ? stables : [];
  const filteredStables = stablesList.filter(s => {
    if (!query) return true;
    const q = query.toLowerCase();
    return s.name?.toLowerCase().includes(q) || s.location?.toLowerCase().includes(q);
  });

  const totalStables = stablesList.length;
  const totalStalls = stablesList.reduce((acc, s) => acc + (s.stalls?.length || 0), 0);
  const occupiedStalls = stablesList.reduce((acc, s) => acc + (s.stalls?.filter(st => Boolean(st.currentAssignment))?.length || 0), 0);
  const vacantStalls = Math.max(0, totalStalls - occupiedStalls);

  const horsesMap = useMemo(() => {
    const map = {};
    horses.forEach(h => { if(h?.id) map[h.id] = h; });
    return map;
  }, [horses]);

  const unassignHorse = async (assignmentId) => {
    if (!confirm('Bạn có chắc muốn chuyển ngựa ra khỏi ô chuồng này?')) return;
    try {
      await api(`/api/stables/assignments/${assignmentId}/end`, { method: 'POST' });
      await load();
    } catch(e) {
      setError(e.message);
    }
  };

  const deleteStable = async (s) => {
    const sStalls = s.stalls || [];
    const occupied = sStalls.filter(st => Boolean(st.currentAssignment));
    let msg = `Bạn có chắc chắn muốn XÓA khu chuồng "${s.name}"?`;
    if (occupied.length > 0) {
      msg = `CẢNH BÁO: Khu chuồng "${s.name}" hiện đang nuôi ${occupied.length} con ngựa.\nKhi xóa chuồng, tất cả ${occupied.length} con ngựa này sẽ được CHUYỂN RA KHỎI CHUỒNG tự động.\n\nBạn có chắc chắn muốn xóa chuồng này không?`;
    }
    if (!confirm(msg)) return;
    try {
      await api(`/api/stables/${s.id}`, { method: 'DELETE' });
      await load();
    } catch(e) {
      setError(e.message);
    }
  };

  const deleteStall = async (stable, stall) => {
    const sStalls = stable.stalls || [];
    if (sStalls.length <= 1) {
      alert('Mỗi khu chuồng phải có ít nhất 1 ô chuồng, không thể xóa bớt nữa!');
      return;
    }
    const hasHorse = stall.currentAssignment;
    let msg = `Bạn có chắc muốn xóa ô "${stall.code}" khỏi chuồng "${stable.name}"?`;
    if (hasHorse) {
      const hName = hasHorse.horseName || 'ngựa đang nuôi';
      msg = `CẢNH BÁO: Ô "${stall.code}" hiện đang nuôi ${hName}.\nXóa ô chuồng sẽ CHUYỂN NGỰA RA KHỎI CHUỒNG tự động.\n\nBạn có chắc chắn muốn xóa ô này không?`;
    }
    if (!confirm(msg)) return;
    try {
      await api(`/api/stables/${stable.id}/stalls/${stall.id}`, { method: 'DELETE' });
      await load();
    } catch(e) {
      setError(e.message);
    }
  };

  const revokeOverride = async (horse) => {
    if (!horse) return;
    if (!confirm(`Bạn có chắc muốn HỦY BẢO LÃNH dịch vụ của ngựa "${horse.name}"? Ngựa này chưa thanh toán phí dịch vụ.`)) return;
    try {
      await api(`/api/admin/horses/${horse.id}/service-access`, {
        method: 'PATCH',
        body: JSON.stringify({ serviceOverride: false, overrideReason: null })
      });
      await load();
    } catch(e) {
      setError(e.message);
    }
  };

  return (
    <>
      <Heading
        eyebrow="QUẢN TRỊ CƠ SỞ VẬT CHẤT"
        title="Quản lý Chuồng trại"
        text="Thêm khu chuồng (tối đa 4 con/chuồng), quản lý tình trạng thanh toán dịch vụ và xếp vị trí ngựa."
        action={
          <div style={{display:'flex',gap:'8px'}}>
            <button className="button secondary" onClick={()=>setAssigningStall({ stableId: '', stallId: '' })}>
              <Plus size={17}/> Xếp ngựa vào chuồng
            </button>
            <button className="button primary" onClick={()=>setCreatingStable(true)}>
              <Plus size={17}/> Thêm chuồng mới
            </button>
          </div>
        }
      />

      <div className="stables-summary-cards">
        <div className="card comparison-card">
          <span>Khu chuồng</span>
          <strong>{totalStables}</strong>
          <small>Tối đa 4 con / khu chuồng</small>
        </div>
        <div className="card comparison-card">
          <span>Tổng số ô chuồng</span>
          <strong>{totalStalls}</strong>
          <small>Khả năng nuôi giữ hiện tại</small>
        </div>
        <div className="card comparison-card">
          <span>Đang có ngựa</span>
          <strong style={{color:'#286d49'}}>{occupiedStalls}</strong>
          <small>{totalStalls > 0 ? `${Math.round((occupiedStalls/totalStalls)*100)}% lấp đầy` : '0%'}</small>
        </div>
        <div className="card comparison-card">
          <span>Ô chuồng còn trống</span>
          <strong style={{color:'#466759'}}>{vacantStalls}</strong>
          <small>Sẵn sàng tiếp nhận ngựa</small>
        </div>
      </div>

      <div className="toolbar card">
        <label className="search-box">
          <Search size={17}/>
          <input
            value={query}
            onChange={e=>setQuery(e.target.value)}
            placeholder="Tìm theo tên chuồng hoặc vị trí…"
          />
        </label>
      </div>

      {error && <Notice text={error}/>}

      {loading ? (
        <section className="card simple-empty">Đang tải danh sách chuồng trại…</section>
      ) : !filteredStables.length ? (
        <section className="card simple-empty">
          {query ? 'Không tìm thấy chuồng trại phù hợp.' : 'Chưa có khu chuồng nào. Hãy bấm "Thêm chuồng mới" ở trên để khởi tạo.'}
        </section>
      ) : (
        filteredStables.map(s => {
          const sStalls = s.stalls || [];
          const sOcc = sStalls.filter(st => Boolean(st.currentAssignment)).length;
          const sVac = sStalls.length - sOcc;
          const isFull = sOcc >= 4;
          return (
            <section className="card stable-block-card" key={s.id}>
              <div className="stable-card-top">
                <div className="stable-card-title">
                  <h3><Warehouse size={20}/> {s.name}</h3>
                  <div style={{display:'flex',alignItems:'center',gap:'8px',flexWrap:'wrap',marginTop:'3px'}}>
                    <p style={{margin:0}}>{s.location || 'Vị trí chưa thiết lập'} · <b>{sStalls.length} ô chuồng</b> ({sOcc} đang nuôi, {sVac} còn trống)</p>
                    <span className={`capacity-tag ${isFull ? 'full' : 'normal'}`}>
                      {isFull ? '4/4 con (ĐÃ ĐẦY)' : `${sOcc}/4 con`}
                    </span>
                  </div>
                </div>
                <div className="stable-card-actions">
                  <span className={`account-state ${s.active ? 'on' : 'off'}`}>
                    {s.active ? 'Đang hoạt động' : 'Ngừng hoạt động'}
                  </span>
                  {sStalls.length < 4 && (
                    <button 
                      type="button"
                      className="button secondary" 
                      title="Thêm ô chuồng mới (tối đa 4 ô)"
                      onClick={()=>setAddingStallStable(s)}
                    >
                      <Plus size={15}/> Thêm ô chuồng
                    </button>
                  )}
                  <button 
                    type="button"
                    className="button secondary" 
                    title="Sửa tên và thông tin khu chuồng"
                    onClick={()=>setEditingStable(s)}
                  >
                    <Pencil size={14}/> Sửa
                  </button>
                  <button 
                    type="button"
                    className="button secondary" 
                    style={{color:'#b3261e',borderColor:'#f3c2be'}}
                    title="Xóa khu chuồng này"
                    onClick={()=>deleteStable(s)}
                  >
                    <Trash2 size={14}/> Xóa chuồng
                  </button>
                </div>
              </div>
              <div className="stalls-grid">
                {!sStalls.length ? (
                  <div className="simple-empty" style={{gridColumn:'1/-1',padding:'25px'}}>
                    Khu chuồng này chưa có ô chuồng nào. Hãy bấm "Thêm ô chuồng" để tạo ô mới.
                  </div>
                ) : (
                  sStalls.map(st => {
                    const assign = st.currentAssignment;
                    const horse = assign ? horsesMap[assign.horseId] : null;
                    const isPaid = horse?.paid;
                    const isOverride = Boolean(horse?.serviceOverride);
                    return (
                      <div className={`stall-box ${assign ? 'occupied' : 'empty'}`} key={st.id}>
                        <div className="stall-box-head">
                          <b style={{fontSize:'14px',color:'#183f32'}}>Ô {st.code}</b>
                          <span className={`stall-badge ${assign ? 'occupied' : 'empty'}`}>
                            {assign ? 'Đang nuôi' : 'Trống'}
                          </span>
                        </div>
                        {assign ? (
                          <div className="stall-horse-info">
                            <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',gap:'4px'}}>
                              <b>🐴 {horse ? horse.name : (assign.horseName || 'Ngựa đã phân bổ')}</b>
                              {isPaid ? (
                                <span className="badge-payment paid" title="Đã thanh toán phí dịch vụ">✓ Đã trả phí</span>
                              ) : isOverride ? (
                                <span className="badge-payment override" title={horse?.overrideReason || "Được Club Manager bảo lãnh"}>⭐ Bảo lãnh</span>
                              ) : (
                                <span className="badge-payment unpaid" title="Chưa thanh toán phí dịch vụ">⚠️ Chưa phí</span>
                              )}
                            </div>
                            <small>Mã: {horse ? horse.code : (assign.horseCode || assign.horseId?.slice(0,8))}</small>
                            <small>Từ ngày: {new Date(assign.assignedFrom).toLocaleDateString('vi-VN')}</small>
                          </div>
                        ) : (
                          <div className="stall-empty-placeholder">
                            Chưa có ngựa phân bổ vào ô này.
                          </div>
                        )}
                        <div className="stall-actions">
                          {assign ? (
                            <div style={{display:'flex',gap:'6px',width:'100%',flexWrap:'wrap'}}>
                              <button
                                type="button"
                                className="button secondary"
                                style={{flex:1}}
                                onClick={()=>unassignHorse(assign.id)}
                              >
                                Chuyển ra
                              </button>
                              {isOverride && horse && (
                                <button
                                  type="button"
                                  className="button secondary"
                                  style={{color:'#b3261e',borderColor:'#f3c2be'}}
                                  title="Hủy quyền bảo lãnh của ngựa này"
                                  onClick={()=>revokeOverride(horse)}
                                >
                                  Hủy bảo lãnh
                                </button>
                              )}
                              {sStalls.length > 1 && (
                                <button
                                  type="button"
                                  className="button secondary"
                                  style={{color:'#b3261e',borderColor:'#f3c2be',padding:'5px 8px'}}
                                  title="Xóa ô này (ngựa sẽ được chuyển ra ngoài)"
                                  onClick={()=>deleteStall(s, st)}
                                >
                                  <Trash2 size={14}/>
                                </button>
                              )}
                            </div>
                          ) : (
                            <div style={{display:'flex',gap:'6px',width:'100%'}}>
                              <button
                                type="button"
                                className="button primary"
                                style={{flex:1}}
                                onClick={()=>setAssigningStall({ stableId: s.id, stallId: st.id, stallCode: st.code })}
                              >
                                Xếp ngựa vào ô
                              </button>
                              {sStalls.length > 1 && (
                                <button
                                  type="button"
                                  className="button secondary"
                                  style={{color:'#b3261e',borderColor:'#f3c2be',padding:'5px 8px'}}
                                  title="Xóa ô chuồng trống này"
                                  onClick={()=>deleteStall(s, st)}
                                >
                                  <Trash2 size={14}/>
                                </button>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </section>
          );
        })
      )}

      {creatingStable && (
        <CreateStableDialog
          onClose={()=>setCreatingStable(false)}
          onSaved={()=>{setCreatingStable(false); load();}}
        />
      )}

      {editingStable && (
        <EditStableDialog
          stable={editingStable}
          onClose={()=>setEditingStable(null)}
          onSaved={()=>{setEditingStable(null); load();}}
        />
      )}

      {addingStallStable && (
        <AddStallDialog
          stable={addingStallStable}
          onClose={()=>setAddingStallStable(null)}
          onSaved={()=>{setAddingStallStable(null); load();}}
        />
      )}

      {assigningStall && (
        <AssignHorseDialog
          stallInfo={assigningStall}
          stables={stablesList}
          horses={horses}
          onClose={()=>setAssigningStall(null)}
          onSaved={(shouldClose = true)=>{
            load();
            if (shouldClose) setAssigningStall(null);
          }}
        />
      )}
    </>
  );
}

function EditStableDialog({ stable, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: stable?.name || '',
    location: stable?.location || '',
    active: Boolean(stable?.active)
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api(`/api/stables/${stable.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: form.name.trim(),
          location: form.location.trim() || null,
          active: form.active
        })
      });
      onSaved();
    } catch(err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title={`Sửa khu chuồng: ${stable?.name}`} onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        {error && <Notice text={error}/>}
        <Input label="Tên khu chuồng *" value={form.name} onChange={v=>setForm({...form, name: v})} placeholder="Tên khu chuồng..."/>
        <Input label="Vị trí / Khu vực" value={form.location} onChange={v=>setForm({...form, location: v})} placeholder="Vị trí chuồng..."/>
        <label className="service-override-checkbox" style={{marginTop:'4px'}}>
          <input
            type="checkbox"
            checked={form.active}
            onChange={e=>setForm({...form, active: e.target.checked})}
          />
          <span>Khu chuồng đang hoạt động (cho phép phân bổ ngựa)</span>
        </label>
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button className="button primary" disabled={busy || !form.name.trim()}>
            <Save size={16}/> {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function CreateStableDialog({ onClose, onSaved }) {
  const [form, setForm] = useState({ name: '', location: '', stallCodes: 'A-01, A-02, A-03, A-04' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    const stallCodesList = form.stallCodes
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);
    if (stallCodesList.length < 1 || stallCodesList.length > 4) {
      setError('Mỗi khu chuồng phải có ít nhất 1 ô chuồng và nhiều nhất 4 ô chuồng');
      setBusy(false);
      return;
    }
    try {
      await api('/api/stables', {
        method: 'POST',
        body: JSON.stringify({
          name: form.name.trim(),
          location: form.location.trim() || null,
          stallCodes: stallCodesList
        })
      });
      onSaved();
    } catch(err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title="Thêm khu chuồng mới (Tối đa 4 con/chuồng)" onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        {error && <Notice text={error}/>}
        <Input label="Tên khu chuồng *" value={form.name} onChange={v=>setForm({...form, name: v})} placeholder="Ví dụ: Khu chuồng B, Trại phía Tây..."/>
        <Input label="Vị trí / Khu vực" value={form.location} onChange={v=>setForm({...form, location: v})} placeholder="Ví dụ: Cạnh đường đua 1, Trại nuôi chính..."/>
        <label className="field">
          <span>Mã các ô chuồng ban đầu (tối đa 4 ô, cách nhau dấu phẩy)</span>
          <input 
            value={form.stallCodes} 
            onChange={e=>setForm({...form, stallCodes: e.target.value})} 
            placeholder="Ví dụ: B-01, B-02, B-03, B-04"
          />
          <small style={{color:'var(--muted)',fontSize:'10px',marginTop:'3px'}}>Mỗi khu chuồng chứa tối đa 4 ô chuồng tương ứng sức chứa tối đa 4 con ngựa.</small>
        </label>
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button className="button primary" disabled={busy || !form.name.trim()}>
            <Plus size={16}/> {busy ? 'Đang tạo…' : 'Tạo chuồng'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function AddStallDialog({ stable, onClose, onSaved }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const currentStallCount = stable?.stalls?.length || 0;
  if (currentStallCount >= 4) {
    return (
      <Dialog title={`Khu chuồng ${stable?.name}`} onClose={onClose}>
        <div className="form-stack">
          <Notice text="Khu chuồng này đã đạt sức chứa tối đa 4 ô chuồng. Không thể thêm ô chuồng mới." />
          <div className="modal-actions">
            <button type="button" className="button primary" onClick={onClose}>Đã hiểu</button>
          </div>
        </div>
      </Dialog>
    );
  }

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api(`/api/stables/${stable.id}/stalls`, {
        method: 'POST',
        body: JSON.stringify({ code: code.trim() })
      });
      onSaved();
    } catch(err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title={`Thêm ô chuồng vào ${stable?.name}`} onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        {error && <Notice text={error}/>}
        <Input label="Mã ô chuồng mới" value={code} onChange={setCode} placeholder="Ví dụ: A-04, B-02..."/>
        <small style={{color:'var(--muted)',fontSize:'10px'}}>Khu chuồng hiện có {currentStallCount}/4 ô chuồng.</small>
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button className="button primary" disabled={busy || !code.trim()}>
            <Plus size={16}/> {busy ? 'Đang thêm…' : 'Thêm ô chuồng'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function AssignHorseDialog({ stallInfo, stables, horses, onClose, onSaved }) {
  const [selectedStableId, setSelectedStableId] = useState(stallInfo?.stableId || stables?.[0]?.id || '');
  const [selectedStallId, setSelectedStallId] = useState(stallInfo?.stallId || '');
  const [selectedHorseId, setSelectedHorseId] = useState('');
  const [search, setSearch] = useState('');
  const [serviceOverride, setServiceOverride] = useState(false);
  const [overrideReason, setOverrideReason] = useState('Quản lý CLB bảo lãnh dịch vụ');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const currentStable = useMemo(() => {
    return (stables || []).find(s => s.id === selectedStableId) || null;
  }, [stables, selectedStableId]);

  const vacantStalls = useMemo(() => {
    if (!currentStable) return [];
    return (currentStable.stalls || []).filter(st => st.active && (!st.currentAssignment || st.id === stallInfo?.stallId));
  }, [currentStable, stallInfo]);

  useEffect(() => {
    if (vacantStalls.length > 0 && (!selectedStallId || !vacantStalls.some(st => st.id === selectedStallId))) {
      setSelectedStallId(vacantStalls[0].id);
    }
  }, [vacantStalls, selectedStallId]);

  const filteredHorses = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (horses || []).filter(h => {
      if (!q) return true;
      return (
        h.name?.toLowerCase().includes(q) ||
        h.code?.toLowerCase().includes(q) ||
        h.breed?.toLowerCase().includes(q) ||
        h.stableLocation?.toLowerCase().includes(q)
      );
    });
  }, [horses, search]);

  const selectedHorse = useMemo(() => {
    return (horses || []).find(h => h.id === selectedHorseId) || null;
  }, [horses, selectedHorseId]);

  useEffect(() => {
    if (selectedHorse) {
      if (selectedHorse.serviceOverride) {
        setServiceOverride(true);
        setOverrideReason(selectedHorse.overrideReason || 'Quản lý CLB bảo lãnh dịch vụ');
      } else {
        setServiceOverride(false);
        setOverrideReason('Quản lý CLB bảo lãnh dịch vụ');
      }
    }
  }, [selectedHorse]);

  const isEligible = useMemo(() => {
    if (!selectedHorse) return false;
    return selectedHorse.paid || serviceOverride;
  }, [selectedHorse, serviceOverride]);

  const togglePaymentDirectly = async (newStatus) => {
    if (!selectedHorse) return;
    setBusy(true); setError('');
    try {
      await api(`/api/admin/horses/${selectedHorse.id}/service-access`, {
        method: 'PATCH',
        body: JSON.stringify({ paymentStatus: newStatus })
      });
      selectedHorse.paymentStatus = newStatus;
      if (newStatus === 'PAID') setServiceOverride(false);
      onSaved(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const revokeOverrideDirectly = async () => {
    if (!selectedHorse) return;
    if (!confirm(`Bạn có chắc muốn HỦY BẢO LÃNH dịch vụ của ngựa "${selectedHorse.name}"?`)) return;
    setBusy(true); setError('');
    try {
      await api(`/api/admin/horses/${selectedHorse.id}/service-access`, {
        method: 'PATCH',
        body: JSON.stringify({ serviceOverride: false, overrideReason: null })
      });
      selectedHorse.serviceOverride = false;
      selectedHorse.overrideReason = null;
      setServiceOverride(false);
      onSaved(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!selectedHorseId) {
      setError('Vui lòng chọn một con ngựa');
      return;
    }
    if (!selectedStableId || !selectedStallId) {
      setError('Vui lòng chọn khu chuồng và ô chuồng còn trống');
      return;
    }
    if (!isEligible) {
      setError('Ngựa chưa thanh toán phí dịch vụ. Club Manager cần duyệt bảo lãnh dịch vụ để tiếp tục.');
      return;
    }

    setBusy(true); setError('');
    try {
      if (serviceOverride && !selectedHorse?.serviceOverride) {
        await api(`/api/admin/horses/${selectedHorseId}/service-access`, {
          method: 'PATCH',
          body: JSON.stringify({
            serviceOverride: true,
            overrideReason: overrideReason.trim() || 'Quản lý CLB bảo lãnh dịch vụ'
          })
        });
      }

      await api(`/api/stables/${selectedStableId}/stalls/${selectedStallId}/assign`, {
        method: 'POST',
        body: JSON.stringify({ horseId: selectedHorseId })
      });

      onSaved(true);
    } catch(err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title="Xếp ngựa vào ô chuồng" onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        {error && <Notice text={error}/>}

        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'10px'}}>
          <label className="field">
            <span>Chọn khu chuồng *</span>
            <select 
              value={selectedStableId} 
              onChange={e=>{
                setSelectedStableId(e.target.value);
                setSelectedStallId('');
              }}
            >
              {!stables?.length && <option value="">Chưa có chuồng nào</option>}
              {(stables || []).map(s => {
                const occ = (s.stalls || []).filter(st => Boolean(st.currentAssignment)).length;
                const isFull = occ >= 4;
                return (
                  <option key={s.id} value={s.id}>
                    {s.name} ({occ}/4 con{isFull ? ' - ĐÃ ĐẦY' : ''})
                  </option>
                );
              })}
            </select>
          </label>

          <label className="field">
            <span>Chọn ô chuồng trống *</span>
            <select 
              value={selectedStallId} 
              onChange={e=>setSelectedStallId(e.target.value)}
              disabled={!vacantStalls.length}
            >
              {!vacantStalls.length ? (
                <option value="">Không có ô trống</option>
              ) : (
                vacantStalls.map(st => (
                  <option key={st.id} value={st.id}>
                    Ô {st.code} (Sẵn sàng)
                  </option>
                ))
              )}
            </select>
          </label>
        </div>

        <div className="field">
          <span>Tìm & chọn ngựa vào chuồng (Dạng lăn chuột) *</span>
          <div className="horse-search-wrap">
            <Search size={15}/>
            <input 
              type="text"
              value={search}
              onChange={e=>setSearch(e.target.value)}
              placeholder="Tìm theo tên ngựa, mã ngựa, giống để chọn nhanh…"
            />
          </div>

          <div className="horse-scroll-list">
            {!filteredHorses.length ? (
              <div style={{textAlign:'center',padding:'18px',color:'var(--muted)',fontSize:'12px'}}>
                {search ? 'Không tìm thấy ngựa phù hợp từ khóa.' : 'Chưa có dữ liệu ngựa.'}
              </div>
            ) : (
              filteredHorses.map(h => {
                const isSelected = h.id === selectedHorseId;
                const isPaid = h.paid;
                const isOverride = Boolean(h.serviceOverride);
                return (
                  <div 
                    key={h.id} 
                    className={`horse-select-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedHorseId(h.id)}
                  >
                    <div className="horse-card-meta">
                      <div className="horse-card-avatar">{initials(h.name)}</div>
                      <div className="horse-card-info">
                        <b>{h.name} <small style={{color:'var(--muted)',fontWeight:400}}>({h.code})</small></b>
                        <span>{h.breed} · Vị trí: {h.stableLocation || 'Chưa xếp'}</span>
                      </div>
                    </div>
                    <div>
                      {isPaid ? (
                        <span className="badge-payment paid">✓ Đã trả phí</span>
                      ) : isOverride ? (
                        <span className="badge-payment override">⭐ Quản lý bảo lãnh</span>
                      ) : (
                        <span className="badge-payment unpaid">⚠️ Chưa trả phí</span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {selectedHorse && (
          <div>
            {selectedHorse.paid && !selectedHorse.serviceOverride ? (
              <div className="service-eligible-notice">
                <Check size={16}/>
                <span>Ngựa đã đóng phí dịch vụ chăm sóc định kỳ đầy đủ. Sẵn sàng tiếp nhận vào chuồng.</span>
              </div>
            ) : selectedHorse.serviceOverride ? (
              <div className="service-override-box" style={{background:'#fcfaff',borderColor:'#e2d8f9'}}>
                <div className="service-override-header" style={{color:'#5b21b6'}}>
                  <ShieldCheck size={16}/>
                  <span>Ngựa đang được Club Manager duyệt bảo lãnh dịch vụ</span>
                </div>
                <p className="service-override-note">
                  Ghi chú bảo lãnh: "<b>{selectedHorse.overrideReason || 'CLB bảo lãnh'}</b>".
                  Nếu chủ ngựa chưa thanh toán, Club Manager có thể quyết định hủy bảo lãnh để ngừng cung cấp dịch vụ.
                </p>
                <div style={{display:'flex',gap:'8px',flexWrap:'wrap',marginTop:'4px'}}>
                  <button 
                    type="button" 
                    className="button secondary"
                    style={{fontSize:'11px',padding:'6px 12px',color:'#b3261e',borderColor:'#f3c2be'}}
                    disabled={busy}
                    onClick={revokeOverrideDirectly}
                  >
                    Hủy bảo lãnh dịch vụ
                  </button>
                  <button 
                    type="button" 
                    className="button secondary"
                    style={{fontSize:'11px',padding:'6px 12px'}}
                    disabled={busy}
                    onClick={()=>togglePaymentDirectly('PAID')}
                  >
                    Đánh dấu đã đóng phí
                  </button>
                </div>
              </div>
            ) : (
              <div className="service-override-box">
                <div className="service-override-header">
                  <AlertTriangle size={16}/>
                  <span>Ngựa chưa thanh toán phí dịch vụ chăm sóc</span>
                </div>
                <p className="service-override-note">
                  Theo quy định, chỉ ngựa đã thanh toán mới được nhận chăm sóc chuồng trại.
                  Với vai trò <b>Club Manager</b>, bạn có thể quyết định cho phép ngựa sử dụng dịch vụ bằng quyền bảo lãnh hoặc từ chối bảo lãnh.
                </p>
                <label className="service-override-checkbox">
                  <input 
                    type="checkbox"
                    checked={serviceOverride}
                    onChange={e => setServiceOverride(e.target.checked)}
                  />
                  <span>Quyết định cho phép sử dụng dịch vụ (Club Manager bảo lãnh)</span>
                </label>
                {serviceOverride && (
                  <Input 
                    label="Lý do bảo lãnh / Ghi chú dịch vụ"
                    value={overrideReason}
                    onChange={setOverrideReason}
                    placeholder="Nhập lý do bảo lãnh dịch vụ..."
                  />
                )}
                <div style={{display:'flex',justifyContent:'flex-end',marginTop:'4px'}}>
                  <button 
                    type="button" 
                    className="button secondary"
                    style={{fontSize:'10px',padding:'4px 8px'}}
                    disabled={busy}
                    onClick={()=>togglePaymentDirectly('PAID')}
                  >
                    Đánh dấu đã thanh toán phí trực tiếp
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button 
            className="button primary" 
            disabled={busy || !selectedHorseId || !selectedStallId || !isEligible}
          >
            <Check size={16}/> 
            {busy ? 'Đang xếp…' : !isEligible ? 'Chưa đủ điều kiện dịch vụ' : 'Xác nhận xếp chuồng'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
