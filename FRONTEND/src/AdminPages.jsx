import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, ArchiveRestore, ArrowRight, Boxes, Check, CheckCircle2, ChevronLeft, ChevronRight, ChevronUp, DollarSign, Download, Filter, HeartPulse, KeyRound, Lock, LogOut, Monitor, Package, Pencil, Plus, RotateCcw, Save, ScrollText, Search, ShieldCheck, SlidersHorizontal, Smartphone, Tag, Trash2, Unlock, UserPlus, Utensils, Warehouse, X, XCircle } from 'lucide-react'
import { api, apiFetch, inventoryApi, stableCareApi } from './api'

const initials = (name = '') => name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'RC'

const ROLES={CLUB_MANAGER:'Quản lý câu lạc bộ',HEAD_TRAINER:'Huấn luyện viên trưởng',VETERINARIAN:'Bác sĩ thú y',STABLE_STAFF:'Nhân viên chuồng',HORSE_OWNER:'Chủ sở hữu ngựa'}
const MODULES={PROFILE:'Hồ sơ ngựa',TRAINING:'Huấn luyện',MEDICAL:'Y tế',STABLE_CARE:'Chăm sóc chuồng',RACING:'Giải đua',REPORTING:'Báo cáo',INVENTORY:'Kho vật tư',SYSTEM:'Hệ thống'}
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
            <div className="admin-card-header-action" style={{borderBottom:'1px solid #edf0eb'}}>
              <div className="admin-section-head" style={{padding:0}}>
                <DollarSign size={18}/>
                <h3>Cơ cấu tài chính</h3>
              </div>
              <button type="button" onClick={()=>navigate('/admin/finance-reports')}>
                Sổ quỹ & Báo cáo <ArrowRight size={14}/>
              </button>
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
  return <><Heading eyebrow="PHÂN QUYỀN RBAC" title="Ma trận quyền theo vai trò" text="Bật hoặc tắt các quyền mà từng phân hệ thực sự hỗ trợ."/>{error&&<Notice text={error}/>} {loading?<section className="card simple-empty">Đang tải ma trận quyền…</section>:modules.length?<div className="permission-grid">{roles.map(role=><section className="card permission-card" key={role}><SectionHead icon={SlidersHorizontal} title={ROLES[role]}/><div className="permission-head"><span>Phân hệ</span><span>Xem</span><span>Sửa</span><span>Duyệt</span><span>Tiếp nhận</span></div>{modules.map(module=><div className="permission-row" key={module}><b>{MODULES[module]||module}</b>{['VIEW','EDIT','APPROVE','CLAIM'].map(action=>{const item=safeItems.find(x=>x.role===role&&x.module===module&&x.action===action);const key=`${role}-${module}-${action}`;return item?<button aria-label={`${ROLES[role]} ${MODULES[module]||module} ${action}`} disabled={busy===key} className={item.granted?'granted':''} key={action} onClick={()=>toggle(item)}>{item.granted?<Check size={15}/>:<X size={15}/>}</button>:<span className="unsupported-permission" key={action}>—</span>})}</div>)}</section>)}</div>:<section className="card simple-empty">Chưa có dữ liệu phân quyền.</section>}</>
}
function checkPasswordStrength(pw){
  if(!pw)return{score:0,label:'',color:'',width:'0%'};
  let score=0;
  if(pw.length>=8)score++;
  if(/[A-Z]/.test(pw)&&/[a-z]/.test(pw))score++;
  if(/[0-9]/.test(pw))score++;
  if(/[^A-Za-z0-9]/.test(pw))score++;
  if(pw.length<8)return{score:1,label:'Quá ngắn (ít nhất 8 ký tự)',color:'#d15f57',width:'25%'};
  if(score<=2)return{score:2,label:'Trung bình (nên thêm số & chữ hoa)',color:'#d8a33d',width:'50%'};
  if(score===3)return{score:3,label:'Khá an toàn',color:'#42a467',width:'75%'};
  return{score:4,label:'Rất an toàn & mạnh mẽ',color:'#27704e',width:'100%'};
}

function PasswordStrength({password}){
  if(!password)return null;
  const {label,color,width}=checkPasswordStrength(password);
  return <div className="pw-strength-box">
    <div className="pw-strength-track"><div className="pw-strength-bar" style={{width,backgroundColor:color}}/></div>
    <div className="pw-strength-text" style={{color}}>{label}</div>
  </div>;
}

function cleanUserAgent(ua){
  if(!ua)return 'Trình duyệt Web';
  if(ua.includes('Edg/'))return 'Microsoft Edge';
  if(ua.includes('Chrome/'))return 'Google Chrome';
  if(ua.includes('Firefox/'))return 'Mozilla Firefox';
  if(ua.includes('Safari/')&&!ua.includes('Chrome'))return 'Apple Safari';
  return ua.length>45?ua.slice(0,45)+'…':ua;
}

function UserSessionsList({userId}){
  const [sessions,setSessions]=useState([]);
  const [loading,setLoading]=useState(true);
  const [revoking,setRevoking]=useState(false);
  const [msg,setMsg]=useState('');

  const loadSessions=useCallback(()=>{
    setLoading(true);
    api(`/api/admin/accounts/${userId}/sessions`)
      .then(res=>setSessions(Array.isArray(res)?res:[]))
      .catch(()=>setSessions([]))
      .finally(()=>setLoading(false));
  },[userId]);

  useEffect(()=>{loadSessions()},[loadSessions]);

  const revokeAll=async()=>{
    if(!confirm('Bạn có chắc chắn muốn đăng xuất người dùng này khỏi tất cả các thiết bị?'))return;
    setRevoking(true);setMsg('');
    try{
      await api(`/api/admin/accounts/${userId}/revoke-sessions`,{method:'POST'});
      setMsg('Đã thu hồi tất cả phiên đăng nhập thành công.');
      await loadSessions();
    }catch(e){
      setMsg(e.message);
    }finally{
      setRevoking(false);
    }
  };

  return <div className="active-sessions-section">
    <div className="sessions-header">
      <div style={{display:'flex',alignItems:'center',gap:'6px'}}>
        <Monitor size={16}/>
        <b>Phiên đăng nhập đang hoạt động ({sessions.length})</b>
      </div>
      {sessions.length>0&&(
        <button
          type="button"
          className="button secondary"
          style={{fontSize:'11px',padding:'5px 10px',color:'#b3261e',borderColor:'#f3c2be'}}
          disabled={revoking}
          onClick={revokeAll}
        >
          <LogOut size={13}/> {revoking?'Đang thu hồi…':'Đăng xuất mọi thiết bị'}
        </button>
      )}
    </div>
    {msg&&<div className="notice success" style={{margin:'8px 0',fontSize:'11px'}}>{msg}</div>}
    {loading?(
      <div style={{color:'var(--muted)',fontSize:'11px',padding:'8px 0'}}>Đang tải danh sách phiên…</div>
    ):!sessions.length?(
      <div style={{color:'var(--muted)',fontSize:'11px',padding:'8px 0'}}>Không có phiên đăng nhập nào đang kết nối.</div>
    ):(
      <div className="sessions-scroll-list">
        {sessions.map(s=>{
          const isMobile=(s.userAgent||'').toLowerCase().includes('mobile');
          return <div key={s.id} className="session-item-row">
            <span className="session-icon">{isMobile?<Smartphone size={16}/>:<Monitor size={16}/>}</span>
            <div className="session-item-info">
              <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
                <b>IP: {s.ipAddress||'127.0.0.1'}</b>
                <span className="session-active-pill">Đang hoạt động</span>
              </div>
              <small title={s.userAgent}>{cleanUserAgent(s.userAgent)}</small>
              <small style={{color:'#889891'}}>Hoạt động: {s.lastActivityAt?new Date(s.lastActivityAt).toLocaleString('vi-VN'):'—'}</small>
            </div>
          </div>;
        })}
      </div>
    )}
  </div>;
}

function CreateAccount({onClose,onSaved}){
  const [form,setForm]=useState({username:'',email:'',password:'',roles:['HEAD_TRAINER']});
  const [error,setError]=useState('');
  const submit=async e=>{
    e.preventDefault();
    if(form.password.length<8){
      setError('Mật khẩu cần ít nhất 8 ký tự');
      return;
    }
    if(!form.roles||form.roles.length!==1){setError('Mỗi tài khoản chỉ được có đúng một vai trò');return;} try{
      await api('/api/users',{method:'POST',body:JSON.stringify(form)});
      onSaved();
    }catch(x){
      setError(x.message);
    }
  };
  return <Dialog title="Tạo tài khoản" onClose={onClose}>
    <form onSubmit={submit} className="form-stack">
      {error&&<Notice text={error}/>}
      <Input label="Tên tài khoản" value={form.username} onChange={v=>setForm({...form,username:v})}/>
      <Input label="Email" type="email" value={form.email} onChange={v=>setForm({...form,email:v})}/>
      <div>
        <Input label="Mật khẩu ban đầu" type="password" value={form.password} onChange={v=>setForm({...form,password:v})}/>
        <PasswordStrength password={form.password}/>
      </div>
      <RoleChecks chosen={form.roles} setChosen={roles=>setForm({...form,roles})}/>
      <Actions onClose={onClose}/>
    </form>
  </Dialog>;
}

function ManageAccount({user,onClose,onSaved}){
  const [username,setUsername]=useState(user.username);
  const [email,setEmail]=useState(user.email);
  const [roles,setRoles]=useState(user.roles&&user.roles.length?[user.roles[0]]:['STABLE_STAFF']);
  const [password,setPassword]=useState('');
  const [error,setError]=useState('');
  const run=async fn=>{setError('');try{await fn();onSaved()}catch(e){setError(e.message)}};
  return <Dialog title={`Quản lý · ${user.username}`} onClose={onClose}>
    {error&&<Notice text={error}/>}
    <div className="form-stack">
      <Input label="Tên tài khoản" value={username} onChange={setUsername}/>
      <Input label="Email" type="email" value={email} onChange={setEmail}/>
      <button className="button secondary" onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}`,{method:'PUT',body:JSON.stringify({username,email})}))}><Pencil size={16}/>Lưu thông tin</button>
      <RoleChecks chosen={roles} setChosen={setRoles}/>
      <small>Chỉ đổi vai trò khi tài khoản chưa tham gia nghiệp vụ; cần bàn giao nhiệm vụ đang mở trước khi khóa.</small>
      <button className="button secondary" disabled={roles[0]===user.roles?.[0]} onClick={()=>run(()=>api(`/api/users/${user.id}/roles`,{method:'PUT',body:JSON.stringify({roles:roles.slice(0,1)})}))}><ShieldCheck size={16}/>Lưu vai trò</button>
      <div style={{display:'grid',gap:'4px'}}>
        <div className="inline-action">
          <input type="password" minLength="8" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Mật khẩu mới, tối thiểu 8 ký tự"/>
          <button className="button secondary" disabled={password.length<8} onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}/reset-password`,{method:'POST',body:JSON.stringify({password})}))}><KeyRound size={16}/>Đặt lại</button>
        </div>
        <PasswordStrength password={password}/>
      </div>
      <button className={`button ${user.enabled?'danger-outline':'secondary'}`} onClick={()=>run(()=>api(`/api/admin/accounts/${user.id}/status`,{method:'PATCH',body:JSON.stringify({enabled:!user.enabled})}))}>{user.enabled?<Lock size={16}/>:<Unlock size={16}/>} {user.enabled?'Khóa tài khoản':'Mở khóa tài khoản'}</button>
      <UserSessionsList userId={user.id}/>
    </div>
  </Dialog>;
}

function RoleChecks({chosen,setChosen}){const selectedRole=chosen&&chosen.length?chosen[0]:'';return <div className="compact-roles">{Object.entries(ROLES).map(([r,l])=><label className={selectedRole===r?'chosen':''} key={r}><input type="radio" name="admin-account-role" checked={selectedRole===r} onChange={()=>setChosen([r])}/><span>{l}</span>{selectedRole===r&&<Check size={15}/>}</label>)}</div>}

export function ArchivedHorses(){const [items,setItems]=useState([]);const [query,setQuery]=useState('');const [error,setError]=useState('');const [loading,setLoading]=useState(true);const load=useCallback(()=>{setLoading(true);api(`/api/admin/horses/archived?size=100&query=${encodeURIComponent(query)}`).then(res=>setItems(Array.isArray(res)?res:[])).catch(e=>setError(e.message)).finally(()=>setLoading(false))},[query]);useEffect(()=>{void load()},[load]);const safeItems=Array.isArray(items)?items:[];const restore=async h=>{if(!confirm(`Khôi phục hồ sơ ${h.name}?`))return;try{await api(`/api/admin/horses/${h.id}/restore`,{method:'PATCH'});await load()}catch(e){setError(e.message)}};return <><Heading eyebrow="QUẢN LÝ NGỰA" title="Hồ sơ đã lưu trữ" text="Kiểm tra và khôi phục hồ sơ ngựa khi cần."/><div className="toolbar card"><label className="search-box"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Tên hoặc mã ngựa…"/></label></div>{error&&<Notice text={error}/>}<section className="card archived-list">{loading ? <div className="simple-empty">Đang tải hồ sơ lưu trữ…</div> : !safeItems.length ? <div className="simple-empty">Không có hồ sơ đã lưu trữ.</div> : safeItems.map(h=><div key={h.id}><div><b>{h.name}</b><small>{h.code} · {h.breed} · Lưu {h.archivedAt ? new Date(h.archivedAt).toLocaleString('vi-VN') : '—'}</small></div><button className="button secondary" onClick={()=>restore(h)}><ArchiveRestore size={16}/>Khôi phục</button></div>)}</section></>}

export function AuditLogs(){
  const [items,setItems]=useState([]);
  const [totalCount,setTotalCount]=useState(0);
  const [module,setModule]=useState('');
  const [query,setQuery]=useState('');
  const [from,setFrom]=useState('');
  const [to,setTo]=useState('');
  const [page,setPage]=useState(0);
  const [size,setSize]=useState(25);
  const [selected,setSelected]=useState(null);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const [exporting,setExporting]=useState(false);

  const load=useCallback(()=>{
    setLoading(true);setError('');
    const p=new URLSearchParams({page:String(page),size:String(size)});
    if(module)p.set('module',module);
    if(query.trim())p.set('query',query.trim());
    if(from)p.set('from',from);
    if(to)p.set('to',to);

    const countP=new URLSearchParams();
    if(module)countP.set('module',module);
    if(query.trim())countP.set('query',query.trim());
    if(from)countP.set('from',from);
    if(to)countP.set('to',to);

    Promise.all([
      api(`/api/admin/audit-logs?${p}`),
      api(`/api/admin/audit-logs/count?${countP}`).catch(()=>({total:0}))
    ])
      .then(([logsData,countData])=>{
        setItems(Array.isArray(logsData)?logsData:[]);
        setTotalCount(countData?.total||0);
      })
      .catch(e=>setError(e.message))
      .finally(()=>setLoading(false));
  },[module,query,from,to,page,size]);

  useEffect(()=>{load()},[load]);

  const totalPages=Math.max(1,Math.ceil(totalCount/size));

  const resetFilters=()=>{
    setModule('');setQuery('');setFrom('');setTo('');setPage(0);
  };

  const exportCsv=async()=>{
    setExporting(true);
    try{
      const p=new URLSearchParams();
      if(module)p.set('module',module);
      if(query.trim())p.set('query',query.trim());
      if(from)p.set('from',from);
      if(to)p.set('to',to);

      const auth=JSON.parse(sessionStorage.getItem('racehorse.auth')||'{}');
      const token=auth?.accessToken;
      const res=await apiFetch(`/api/admin/audit-logs/export?${p}`,{headers:token?{Authorization:`Bearer ${token}`}:{}});
      if(!res.ok)throw new Error('Không thể tải xuống tệp kiểm toán');
      const blob=await res.blob();
      const downloadUrl=window.URL.createObjectURL(blob);
      const a=document.createElement('a');
      a.href=downloadUrl;
      a.download=`nhat-ky-kiem-toan-${new Date().toISOString().slice(0,10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(downloadUrl);
    }catch(err){
      alert(err.message);
    }finally{
      setExporting(false);
    }
  };

  const safeItems=Array.isArray(items)?items:[];

  return <>
    <Heading
      eyebrow="AN TOÀN HỆ THỐNG"
      title="Nhật ký kiểm toán"
      text="Theo dõi toàn bộ hoạt động của các vai trò, lịch sử can thiệp dữ liệu và an ninh truy cập."
      action={
        <button
          type="button"
          className="button secondary"
          disabled={exporting}
          onClick={exportCsv}
          title="Xuất file CSV nhật ký kiểm toán mở bằng Excel"
        >
          <Download size={16}/> {exporting?'Đang xuất…':'Xuất CSV'}
        </button>
      }
    />

    <div className="card audit-filter-panel">
      <div className="audit-filter-row">
        <label className="field-compact">
          <span>Phân hệ</span>
          <select value={module} onChange={e=>{setModule(e.target.value);setPage(0)}}>
            <option value="">Tất cả phân hệ</option>
            {['ACCESS','PROFILE','TRAINING','MEDICAL','STABLE_CARE','RACING','REPORTING'].map(x=>(
              <option key={x} value={x}>{MODULES[x]||x}</option>
            ))}
          </select>
        </label>

        <label className="field-compact">
          <span>Từ ngày</span>
          <input type="date" value={from} onChange={e=>{setFrom(e.target.value);setPage(0)}}/>
        </label>

        <label className="field-compact">
          <span>Đến ngày</span>
          <input type="date" value={to} onChange={e=>{setTo(e.target.value);setPage(0)}}/>
        </label>

        <label className="field-compact flex-grow">
          <span>Tìm kiếm từ khóa</span>
          <div className="search-box">
            <Search size={15}/>
            <input
              value={query}
              onChange={e=>{setQuery(e.target.value);setPage(0)}}
              placeholder="Người thao tác, hành động, IP, mô tả..."
            />
          </div>
        </label>

        <div style={{display:'flex',alignItems:'flex-end'}}>
          <button
            type="button"
            className="button secondary"
            style={{minHeight:'36px',padding:'0 12px'}}
            onClick={resetFilters}
            title="Đặt lại bộ lọc"
          >
            <RotateCcw size={14}/> Đặt lại
          </button>
        </div>
      </div>
    </div>

    {error&&<Notice text={error}/>}

    <section className="card audit-list" style={{marginTop:'16px'}}>
      {loading ? (
        <div className="simple-empty">Đang tải nhật ký kiểm toán…</div>
      ) : !safeItems.length ? (
        <div className="simple-empty">Không tìm thấy bản ghi nhật ký phù hợp với bộ lọc.</div>
      ) : (
        safeItems.map(x=>(
          <button key={x.id} onClick={()=>setSelected(x)}>
            <span className="audit-icon"><ScrollText size={17}/></span>
            <div>
              <b>{x.actorUsername||'Hệ thống'} · <span className="action-tag">{x.action}</span></b>
              <small>
                Phân hệ: <b>{MODULES[x.module]||x.module}</b> · Đối tượng: {x.entityType||'SYSTEM'} · {x.occurredAt?new Date(x.occurredAt).toLocaleString('vi-VN'):'—'}
              </small>
              <p>{x.description}</p>
            </div>
          </button>
        ))
      )}
    </section>

    <div className="card audit-pagination-bar">
      <div className="pagination-info">
        Hiển thị <b>{safeItems.length}</b> / <b>{totalCount}</b> bản ghi · Trang <b>{page+1}</b> / <b>{totalPages}</b>
      </div>
      <div className="pagination-controls">
        <label style={{display:'flex',alignItems:'center',gap:'6px',fontSize:'11px',color:'var(--muted)'}}>
          Dòng/trang:
          <select
            value={size}
            onChange={e=>{setSize(Number(e.target.value));setPage(0)}}
            style={{padding:'4px 8px',borderRadius:'6px',border:'1px solid #d9ded6',fontSize:'11px'}}
          >
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </label>
        <button
          type="button"
          className="button secondary"
          style={{minHeight:'32px',padding:'0 10px',fontSize:'11px'}}
          disabled={page===0||loading}
          onClick={()=>setPage(p=>Math.max(0,p-1))}
        >
          <ChevronLeft size={14}/> Trước
        </button>
        <button
          type="button"
          className="button secondary"
          style={{minHeight:'32px',padding:'0 10px',fontSize:'11px'}}
          disabled={page+1>=totalPages||loading}
          onClick={()=>setPage(p=>p+1)}
        >
          Sau <ChevronRight size={14}/>
        </button>
      </div>
    </div>

    {selected&&<Dialog title={`Chi tiết nhật ký kiểm toán #${selected.id}`} onClose={()=>setSelected(null)}>
      <dl className="audit-detail">
        <div><dt>Thời điểm thao tác</dt><dd>{selected.occurredAt?new Date(selected.occurredAt).toLocaleString('vi-VN'):'—'}</dd></div>
        <div><dt>Người thao tác</dt><dd><b>{selected.actorUsername||'Hệ thống'}</b> (ID: {selected.actorId||'N/A'})</dd></div>
        <div><dt>Phân hệ & Hành động</dt><dd><span className="role-tags"><span>{MODULES[selected.module]||selected.module}</span></span> <b>{selected.action}</b></dd></div>
        <div><dt>Đối tượng tác động</dt><dd>{selected.entityType} {selected.entityId||''}</dd></div>
        <div><dt>Mô tả chi tiết</dt><dd>{selected.description}</dd></div>
        <div><dt>Dữ liệu trước thay đổi</dt><dd><pre>{pretty(selected.oldValuesJson)}</pre></dd></div>
        <div><dt>Dữ liệu sau thay đổi</dt><dd><pre>{pretty(selected.newValuesJson)}</pre></dd></div>
        <div><dt>Địa chỉ IP client</dt><dd><code>{selected.ipAddress||'—'}</code></dd></div>
        <div><dt>Trình duyệt & Thiết bị (User-Agent)</dt><dd><small style={{wordBreak:'break-all'}}>{selected.userAgent||'—'}</small></dd></div>
      </dl>
    </Dialog>}
  </>;
}
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
                    const isPaid = horse?.paymentStatus === 'PAID';
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
    return selectedHorse.paymentStatus === 'PAID' || selectedHorse.serviceOverride || serviceOverride;
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
                const isPaid = h.paymentStatus === 'PAID';
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
            {selectedHorse.paymentStatus === 'PAID' ? (
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

// ============================================================
// INVENTORY CATALOG PAGE  –  Quản lý danh mục vật tư tổng
// ============================================================
const INV_CATEGORY_LABELS = {
  FEED:      'Thức ăn & Cỏ',
  MEDICATION:'Vật tư y tế',
  EQUIPMENT: 'Thiết bị & Dụng cụ',
  SUPPLEMENT:'Thực phẩm bổ sung',
}
const INV_CATEGORY_COLORS = {
  FEED:      '#d4edda',
  MEDICATION:'#fde8e8',
  EQUIPMENT: '#e8eaf6',
  SUPPLEMENT:'#fff3cd',
}
const INV_CATEGORY_TEXT = {
  FEED:      '#1a4d2e',
  MEDICATION:'#7b1f1f',
  EQUIPMENT: '#283593',
  SUPPLEMENT:'#856404',
}

const fmtPrice = (v) =>
  v != null ? new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(v) : '—'

export function InventoryCatalogPage() {
  const [items, setItems]       = useState([])
  const [requests, setRequests] = useState([])
  const [usage, setUsage] = useState([])
  const [horses, setHorses] = useState([])
  const [stables, setStables] = useState([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState('')
  const [catFilter, setCatFilter] = useState('')
  const [query, setQuery]       = useState('')
  const [statusFilter, setStatusFilter] = useState('ACTIVE')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing]   = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [reviewing, setReviewing] = useState(null)
  const [reviewNotes, setReviewNotes] = useState('')
  const [reviewBusy, setReviewBusy] = useState(false)
  const [receiveImmediately, setReceiveImmediately] = useState(false)
  const [receiving, setReceiving] = useState(null)
  const [usingItem, setUsingItem] = useState(null)
  const [allActiveItems, setAllActiveItems] = useState([])

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const params = {}
      if (catFilter) params.category = catFilter
      if (statusFilter && statusFilter !== 'ALL') params.status = statusFilter
      const [data, proposals, recentUsage, activeCatalog] = await Promise.all([
        stableCareApi.getInventoryItems(params),
        stableCareApi.getReplenishments(),
        stableCareApi.getUsage(20),
        stableCareApi.getInventoryItems({status:'ACTIVE'}),
      ])
      setItems(Array.isArray(data) ? data : [])
      setRequests(Array.isArray(proposals) ? proposals : [])
      setUsage(Array.isArray(recentUsage) ? recentUsage : [])
      setAllActiveItems(Array.isArray(activeCatalog) ? activeCatalog : [])
    } catch(e) { setError(e.message) }
    finally { setLoading(false) }
  }, [catFilter, statusFilter])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    Promise.all([
      api('/api/horses?size=200'),
      api('/api/stables'),
    ]).then(([horsePage, stableList]) => {
      setHorses(Array.isArray(horsePage) ? horsePage : horsePage?.content || [])
      setStables(Array.isArray(stableList) ? stableList : [])
    }).catch(e => setError(e.message))
  }, [])

  const filtered = useMemo(() => {
    if (!query.trim()) return items
    const q = query.toLowerCase()
    return items.filter(i =>
      i.itemName?.toLowerCase().includes(q) ||
      i.location?.toLowerCase().includes(q) ||
      i.description?.toLowerCase().includes(q)
    )
  }, [items, query])

  const handleDelete = async (item) => {
    if (!confirm(`Xác nhận vô hiệu hóa mặt hàng "${item.itemName}"?\n\nMặt hàng sẽ bị ẩn khỏi danh sách tồn kho nhưng dữ liệu lịch sử vẫn được giữ lại.`)) return
    setDeletingId(item.id)
    try {
      await stableCareApi.deleteInventoryItem(item.id)
      await load()
    } catch(e) { setError(e.message) }
    finally { setDeletingId(null) }
  }

  const reviewRequest = async (status) => {
    if (!reviewing) return
    setReviewBusy(true); setError('')
    try {
      await stableCareApi.reviewReplenishment(reviewing.id, {
        status,
        reviewNotes: reviewNotes.trim() || null,
        receiveImmediately: status === 'APPROVED' && receiveImmediately,
      })
      setReviewing(null); setReviewNotes(''); setReceiveImmediately(false)
      await load()
    } catch (e) { setError(e.message) }
    finally { setReviewBusy(false) }
  }

  const stats = useMemo(() => ({
    total:      items.length,
    feed:       items.filter(i=>i.category==='FEED').length,
    medication: items.filter(i=>i.category==='MEDICATION').length,
    equipment:  items.filter(i=>i.category==='EQUIPMENT').length,
    supplement: items.filter(i=>i.category==='SUPPLEMENT').length,
    lowStock:   items.filter(i=>i.isLowStock).length,
    pendingRequests: requests.filter(r => r.status === 'PENDING_APPROVAL').length,
  }), [items, requests])

  const pendingRequests = requests.filter(r => r.status === 'PENDING_APPROVAL')
  const approvedRequests = requests.filter(r => r.status === 'APPROVED')

  return (
    <>
      <Heading
        eyebrow="QUẢN TRỊ KHO VẬT TƯ"
        title="Danh mục vật tư & thức ăn"
        text="Quản lý toàn bộ danh mục mặt hàng tồn kho: thức ăn ngựa, vật tư y tế, thiết bị chuồng trại và thực phẩm bổ sung."
        action={
          <button className="button primary" onClick={() => setCreating(true)}>
            <Plus size={16}/> Thêm mặt hàng mới
          </button>
        }
      />

      {/* Stats strip */}
      <div className="admin-kpis" style={{gridTemplateColumns:'repeat(6,1fr)',marginBottom:'16px'}}>
        {[
          ['Tổng mặt hàng', stats.total,       '#2d6a4f'],
          ['Thức ăn & Cỏ',  stats.feed,        '#52b788'],
          ['Vật tư y tế',   stats.medication,  '#e76f51'],
          ['Thiết bị',      stats.equipment,   '#4361ee'],
          ['Bổ sung',       stats.supplement,  '#f4a261'],
          ['Tồn thấp ⚠',   stats.lowStock,    '#d62828'],
        ].map(([label,val,color])=>(
          <div className="card comparison-card" key={label} style={{borderTop:`3px solid ${color}`}}>
            <span>{label}</span>
            <strong style={{color}}>{val}</strong>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div className="toolbar card" style={{display:'flex',gap:'10px',alignItems:'center',flexWrap:'wrap'}}>
        <label className="search-box" style={{flex:'1 1 200px'}}>
          <Search size={15}/>
          <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Tìm tên, vị trí, mô tả…"/>
        </label>
        <select value={catFilter} onChange={e=>setCatFilter(e.target.value)} style={{minWidth:'160px'}}>
          <option value="">Tất cả danh mục</option>
          {Object.entries(INV_CATEGORY_LABELS).map(([k,v])=>(
            <option key={k} value={k}>{v}</option>
          ))}
        </select>
        <select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)} style={{minWidth:'140px'}}>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Đã vô hiệu hóa</option>
          <option value="ALL">Tất cả trạng thái</option>
        </select>
        <button className="button secondary" onClick={load} style={{minHeight:'36px',padding:'0 12px'}}>
          <RotateCcw size={14}/> Làm mới
        </button>
      </div>

      {error && <div className="notice error"><X size={16}/>{error}</div>}

      <section className="card inventory-request-panel">
        <div className="card-head">
          <div>
            <h3>Đề xuất vật tư chờ Club Manager xử lý ({stats.pendingRequests})</h3>
            <p className="muted" style={{margin:'5px 0 0',fontSize:'11px'}}>
              Có thể đề xuất mặt hàng đã có trong kho hoặc vật tư mới chưa nằm trong danh mục.
            </p>
          </div>
        </div>
        {loading ? <div className="simple-empty">Đang tải đề xuất…</div> : !pendingRequests.length ? (
          <div className="simple-empty">Không có đề xuất đang chờ duyệt.</div>
        ) : <div className="inventory-request-list">
          {pendingRequests.map(request => <div className="inventory-request-row" key={request.id}>
            <div>
              <b>{request.itemName}</b>
              <small>{request.itemId ? 'Vật tư đã có trong danh mục' : 'Vật tư mới cần bổ sung danh mục'} · {request.proposedByName || 'Nhân viên'} · {request.requestedQuantity} {request.unit}</small>
              <p>{request.reason}</p>
            </div>
            <button type="button" className="button secondary" onClick={() => {setReviewing(request);setReviewNotes('');setReceiveImmediately(false)}}>Xem xét</button>
          </div>)}
        </div>}
      </section>

      {approvedRequests.length > 0 && <section className="card inventory-request-panel">
        <div className="card-head"><div><h3>Đã duyệt, chờ xác nhận nhập kho ({approvedRequests.length})</h3><p className="muted" style={{margin:'5px 0 0',fontSize:'11px'}}>Số tồn chỉ tăng sau khi xác nhận hàng thực sự đã vào kho.</p></div></div>
        <div className="inventory-request-list">{approvedRequests.map(request => <div className="inventory-request-row" key={request.id}>
          <div><b>{request.itemName}</b><small>{request.requestedQuantity} {request.unit} · {request.itemId ? 'Mặt hàng đã có' : 'Mặt hàng mới'}</small><p>{request.reviewNotes || request.reason}</p></div>
          <button type="button" className="button primary" onClick={() => setReceiving(request)}>Xác nhận nhập kho</button>
        </div>)}</div>
      </section>}

      <section className="card" style={{overflow:'auto',marginTop:'2px'}}>
        <div className="card-head">
          <h3>Danh sách mặt hàng ({filtered.length})</h3>
          <span className="record-count">{filtered.length}/{items.length} hiển thị</span>
        </div>

        {loading ? (
          <div className="simple-empty">Đang tải danh mục vật tư…</div>
        ) : !filtered.length ? (
          <div className="simple-empty">Không tìm thấy mặt hàng phù hợp.</div>
        ) : (
          <div className="data-table" style={{minWidth:'900px'}}>
            <div className="table-header" style={{gridTemplateColumns:'2fr 120px 110px 90px 90px 110px 150px'}}>
              <span>Tên mặt hàng</span>
              <span>Danh mục</span>
              <span>Vị trí kho</span>
              <span>Hiện có</span>
              <span>Ngưỡng an toàn</span>
              <span>Đơn giá TK</span>
              <span>Thao tác</span>
            </div>
            {filtered.map(item => (
              <div
                className="table-row"
                style={{gridTemplateColumns:'2fr 120px 110px 90px 90px 110px 150px'}}
                key={item.id}
              >
                <div>
                  <b style={{fontSize:'12px'}}>{item.itemName}</b>
                  {item.description && <small className="muted" style={{display:'block',marginTop:'2px'}}>{item.description}</small>}
                  {item.status === 'INACTIVE' && (
                    <span style={{fontSize:'9px',color:'#b73830',fontWeight:700,marginTop:'2px',display:'block'}}>VÔ HIỆU HÓA</span>
                  )}
                </div>
                <div>
                  <span style={{
                    fontSize:'9px',fontWeight:700,padding:'3px 7px',borderRadius:'6px',
                    background: INV_CATEGORY_COLORS[item.category]||'#eee',
                    color: INV_CATEGORY_TEXT[item.category]||'#333',
                  }}>
                    {INV_CATEGORY_LABELS[item.category]||item.category}
                  </span>
                </div>
                <span style={{fontSize:'11px',color:'#24483c'}}>{item.location||'Kho chung'}</span>
                <div>
                  <b style={{fontSize:'13px',color: item.isLowStock ? '#b73830' : '#123f32'}}>
                    {item.quantityOnHand}
                  </b> <small>{item.unit}</small>
                  {item.isLowStock && (
                    <span style={{display:'block',fontSize:'8px',color:'#b73830',fontWeight:700}}>TỒN THẤP</span>
                  )}
                </div>
                <span style={{fontSize:'11px',color:'var(--muted)'}}>{item.minThreshold} {item.unit}</span>
                <span style={{fontSize:'12px',color:'#1a3c31'}}>
                  {fmtPrice(item.referencePrice)}
                  {item.referencePrice && <small className="muted" style={{display:'block',fontSize:'9px'}}>/{item.unit}</small>}
                </span>
                <div style={{display:'flex',gap:'5px'}}>
                  {item.status === 'ACTIVE' && <button className="button secondary" type="button" title="Ghi nhận vật tư đã sử dụng" style={{minHeight:'30px',padding:'0 8px',fontSize:'10px'}} onClick={() => setUsingItem(item)}>Xuất dùng</button>}
                  <button
                    className="icon-button"
                    title="Sửa thông tin"
                    onClick={() => setEditing(item)}
                  >
                    <Pencil size={14}/>
                  </button>
                  {item.status !== 'INACTIVE' && (
                    <button
                      className="icon-button"
                      title="Vô hiệu hóa"
                      style={{color:'#b73830'}}
                      disabled={deletingId === item.id}
                      onClick={() => handleDelete(item)}
                    >
                      <Trash2 size={14}/>
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="card inventory-request-panel" style={{marginTop:'16px'}}>
        <div className="card-head"><div><h3>Lịch sử xuất dùng gần đây</h3><p className="muted" style={{margin:'5px 0 0',fontSize:'11px'}}>Mỗi lần xuất dùng trừ số tồn và ghi một khoản chi trong sổ tài chính Club Manager.</p></div></div>
        {!usage.length ? <div className="simple-empty">Chưa ghi nhận lần xuất dùng nào.</div> : <div className="inventory-request-list">
          {usage.map(record => <div className="inventory-request-row" key={record.id}>
            <div><b>{record.itemName}</b><small>{record.quantity} {record.unit} · còn {record.remainingQuantity} {record.unit} · {record.recordedAt ? new Date(record.recordedAt).toLocaleString('vi-VN') : '—'}</small><p>{record.notes || 'Xuất dùng vật tư'}</p></div>
            <strong>{fmtPrice(record.totalCostVnd)}</strong>
          </div>)}
        </div>}
      </section>

      {creating && (
        <CreateInventoryItemModal
          onClose={() => setCreating(false)}
          onSaved={() => { setCreating(false); load() }}
        />
      )}
      {editing && (
        <EditInventoryItemModal
          item={editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); load() }}
        />
      )}
      {reviewing && <div className="modal-backdrop" onMouseDown={() => !reviewBusy && setReviewing(null)}>
        <div className="modal admin-modal" onMouseDown={e => e.stopPropagation()}>
          <div className="modal-head"><div><p className="eyebrow">ĐỀ XUẤT VẬT TƯ</p><h2>Xem xét yêu cầu</h2></div><button type="button" className="icon-button" disabled={reviewBusy} onClick={() => setReviewing(null)}><X size={18}/></button></div>
          <dl className="audit-detail">
            <div><dt>Mặt hàng</dt><dd>{reviewing.itemName} · {reviewing.requestedQuantity} {reviewing.unit}</dd></div>
            <div><dt>Danh mục</dt><dd>{INV_CATEGORY_LABELS[reviewing.category] || reviewing.category}</dd></div>
            <div><dt>Người đề xuất</dt><dd>{reviewing.proposedByName || '—'}</dd></div>
            <div><dt>Lý do</dt><dd>{reviewing.reason}</dd></div>
          </dl>
          <label className="field" style={{marginTop:'16px'}}><span>Ghi chú quyết định</span><textarea rows={3} value={reviewNotes} onChange={e => setReviewNotes(e.target.value)} placeholder="Lý do duyệt hoặc từ chối…" /></label>
          <label className="inventory-checkbox"><input type="checkbox" checked={receiveImmediately} onChange={e => setReceiveImmediately(e.target.checked)} /><span>Duyệt và xác nhận hàng đã nhập kho ngay. Nếu chưa nhận hàng, bỏ chọn để xác nhận sau.</span></label>
          <div className="modal-actions">
            <button type="button" className="button secondary" disabled={reviewBusy} onClick={() => reviewRequest('REJECTED')}>Từ chối</button>
            <button type="button" className="button primary" disabled={reviewBusy} onClick={() => reviewRequest('APPROVED')}>{reviewBusy ? 'Đang xử lý…' : 'Duyệt đề xuất'}</button>
          </div>
        </div>
      </div>}
      {receiving && <ReceiveInventoryModal request={receiving} items={allActiveItems} onClose={() => setReceiving(null)} onSaved={() => {setReceiving(null);void load()}} />}
      {usingItem && <RecordInventoryUsageModal item={usingItem} horses={horses} stables={stables} onClose={() => setUsingItem(null)} onSaved={() => {setUsingItem(null);void load()}} />}
    </>
  )
}

function ReceiveInventoryModal({ request, items, onClose, onSaved }) {
  const [catalogItemId, setCatalogItemId] = useState('')
  const [referencePrice, setReferencePrice] = useState('')
  const [minThreshold, setMinThreshold] = useState('0')
  const [location, setLocation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const compatibleItems = items.filter(item => item.status === 'ACTIVE' && item.category === request.category && item.unit === request.unit)

  const submit = async (event) => {
    event.preventDefault(); setError('')
    if (referencePrice && Number(referencePrice) <= 0) { setError('Đơn giá tham chiếu phải lớn hơn 0.'); return }
    setBusy(true)
    try {
      await stableCareApi.receiveReplenishment(request.id, {
        catalogItemId: request.itemId || catalogItemId || null,
        referencePriceVnd: referencePrice ? Number(referencePrice) : null,
        minThreshold: Number(minThreshold),
        location: location.trim(),
      })
      onSaved()
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  return <Dialog title={`Xác nhận nhập kho: ${request.itemName}`} onClose={onClose}>
    <form className="form-stack" onSubmit={submit}>
      <p className="muted" style={{fontSize:'12px',margin:0}}>Nhập {request.requestedQuantity} {request.unit}. Số tồn được cộng khi bạn xác nhận.</p>
      {!request.itemId && <label className="field"><span>Gắn vào mặt hàng sẵn có (nếu trùng)</span>
        <select value={catalogItemId} onChange={e => setCatalogItemId(e.target.value)}>
          <option value="">Tạo mặt hàng mới trong danh mục</option>
          {compatibleItems.map(item => <option value={item.id} key={item.id}>{item.itemName} · còn {item.quantityOnHand} {item.unit}</option>)}
        </select>
      </label>}
      <label className="field"><span>Đơn giá tham chiếu VND / {request.unit}</span><input type="number" min="0.01" step="0.01" value={referencePrice} onChange={e => setReferencePrice(e.target.value)} placeholder="Bỏ trống nếu chưa có giá" /></label>
      {!request.itemId && !catalogItemId && <div className="form-grid">
        <label className="field"><span>Ngưỡng cảnh báo mặt hàng mới</span><input type="number" min="0" step="0.001" value={minThreshold} onChange={e => setMinThreshold(e.target.value)} /></label>
        <label className="field"><span>Vị trí kho</span><input value={location} onChange={e => setLocation(e.target.value)} /></label>
      </div>}
      {error && <div className="notice error">{error}</div>}
      <div className="modal-actions"><button type="button" className="button secondary" onClick={onClose} disabled={busy}>Hủy</button><button type="submit" className="button primary" disabled={busy}>{busy ? 'Đang nhập kho…' : 'Xác nhận đã nhập kho'}</button></div>
    </form>
  </Dialog>
}

function RecordInventoryUsageModal({ item, horses, stables, onClose, onSaved }) {
  const [quantity, setQuantity] = useState('')
  const [unitPrice, setUnitPrice] = useState(item.referencePrice != null ? String(item.referencePrice) : '')
  const [targetType, setTargetType] = useState(item.category === 'MEDICATION' ? 'HORSE' : 'STABLE')
  const [targetId, setTargetId] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const estimatedCost = Number(quantity) > 0 && Number(unitPrice) > 0 ? Number(quantity) * Number(unitPrice) : null

  const submit = async (event) => {
    event.preventDefault(); setError('')
    if (!Number.isFinite(Number(quantity)) || Number(quantity) <= 0 || Number(quantity) > item.quantityOnHand) {
      setError(`Số lượng phải lớn hơn 0 và không vượt quá ${item.quantityOnHand} ${item.unit}.`); return
    }
    if (!Number.isFinite(Number(unitPrice)) || Number(unitPrice) <= 0) { setError('Nhập đơn giá xuất dùng lớn hơn 0.'); return }
    if (!targetId) { setError('Chọn ngựa hoặc chuồng sử dụng.'); return }
    setBusy(true)
    try {
      await stableCareApi.recordUsage(item.id, {
        quantity: Number(quantity),
        unitPriceVnd: Number(unitPrice),
        horseId: targetType === 'HORSE' ? targetId : null,
        stableId: targetType === 'STABLE' ? targetId : null,
        notes: notes.trim(),
      })
      onSaved()
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  return <Dialog title={`Ghi nhận xuất dùng: ${item.itemName}`} onClose={onClose}>
    <form className="form-stack" onSubmit={submit}>
      <p className="muted" style={{fontSize:'12px',margin:0}}>Tồn hiện tại: <b>{item.quantityOnHand} {item.unit}</b>. Xác nhận sẽ trừ tồn và ghi chi phí vào sổ tài chính.</p>
      <div className="form-grid">
        <label className="field"><span>Số lượng sử dụng *</span><input type="number" required min="0.001" max={item.quantityOnHand} step="0.001" value={quantity} onChange={e => setQuantity(e.target.value)} /></label>
        <label className="field"><span>Đơn giá xuất dùng (VND / {item.unit}) *</span><input type="number" required min="0.01" step="0.01" value={unitPrice} onChange={e => setUnitPrice(e.target.value)} /></label>
      </div>
      <div className="form-grid">
        <label className="field"><span>Đối tượng sử dụng</span><select value={targetType} onChange={e => {setTargetType(e.target.value);setTargetId('')}}>
          <option value="HORSE">Ngựa</option>{item.category !== 'MEDICATION' && <option value="STABLE">Chuồng</option>}
        </select></label>
        <label className="field"><span>{targetType === 'HORSE' ? 'Chọn ngựa *' : 'Chọn chuồng *'}</span><select required value={targetId} onChange={e => setTargetId(e.target.value)}>
          <option value="">Chọn {targetType === 'HORSE' ? 'ngựa' : 'chuồng'}</option>
          {(targetType === 'HORSE' ? horses : stables).map(target => <option key={target.id} value={target.id}>{targetType === 'HORSE' ? `${target.code} · ${target.name}` : target.name}</option>)}
        </select></label>
      </div>
      <label className="field"><span>Ghi chú sử dụng</span><textarea rows={2} value={notes} onChange={e => setNotes(e.target.value)} placeholder="VD: Dùng trong ca chăm sóc buổi sáng" /></label>
      <p className="inventory-cost-preview">Chi phí sẽ ghi nhận: <b>{fmtPrice(estimatedCost)}</b></p>
      {error && <div className="notice error">{error}</div>}
      <div className="modal-actions"><button type="button" className="button secondary" onClick={onClose} disabled={busy}>Hủy</button><button type="submit" className="button primary" disabled={busy}>{busy ? 'Đang ghi nhận…' : 'Xác nhận xuất dùng'}</button></div>
    </form>
  </Dialog>
}

function CreateInventoryItemModal({ onClose, onSaved }) {
  const [form, setForm] = useState({
    itemName:'', category:'FEED', unit:'', initialQuantity:'0',
    minThreshold:'0', location:'', description:'', referencePrice:'',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (v) => setForm(f => ({...f, [k]: typeof v === 'string' ? v : v.target.value}))

  const submit = async (e) => {
    e.preventDefault(); setError('')
    const payload = {
      itemName: form.itemName.trim(),
      category: form.category,
      unit: form.unit.trim(),
      initialQuantity: Number(form.initialQuantity) || 0,
      minThreshold: Number(form.minThreshold) || 0,
      location: form.location.trim(),
      description: form.description.trim(),
      referencePrice: form.referencePrice !== '' ? Number(form.referencePrice) : null,
    }
    if (!payload.itemName) { setError('Tên mặt hàng không được để trống.'); return }
    if (!payload.unit)     { setError('Đơn vị tính không được để trống.'); return }
    setBusy(true)
    try {
      await stableCareApi.createInventoryItem(payload)
      onSaved()
    } catch(err) { setError(err.message) }
    finally { setBusy(false) }
  }

  return (
    <Dialog title="Thêm mặt hàng mới vào danh mục" onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        <label className="field">
          <span>Tên mặt hàng *</span>
          <input required value={form.itemName} onChange={set('itemName')} placeholder="VD: Cỏ khô Alfalfa (Mỹ vụ mới)"/>
        </label>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'10px'}}>
          <label className="field">
            <span>Danh mục *</span>
            <select value={form.category} onChange={set('category')}>
              {Object.entries(INV_CATEGORY_LABELS).map(([k,v])=>(
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Đơn vị tính *</span>
            <input required value={form.unit} onChange={set('unit')} placeholder="VD: kg, cuộn, hộp…"/>
          </label>
        </div>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'10px'}}>
          <label className="field">
            <span>Số lượng ban đầu</span>
            <input type="number" min="0" step="0.001" value={form.initialQuantity} onChange={set('initialQuantity')}/>
          </label>
          <label className="field">
            <span>Ngưỡng an toàn (cảnh báo tồn thấp)</span>
            <input type="number" min="0" step="0.001" value={form.minThreshold} onChange={set('minThreshold')}/>
          </label>
        </div>
        <label className="field">
          <span>Vị trí lưu kho</span>
          <input value={form.location} onChange={set('location')} placeholder="VD: Kho Thức Ăn A - Dãy 1"/>
        </label>
        <label className="field">
          <span>Đơn giá tham chiếu (VNĐ)</span>
          <input type="number" min="0" step="100" value={form.referencePrice} onChange={set('referencePrice')} placeholder="Bỏ trống nếu chưa có"/>
        </label>
        <label className="field">
          <span>Mô tả / Ghi chú</span>
          <input value={form.description} onChange={set('description')} placeholder="Mô tả ngắn về mặt hàng…"/>
        </label>
        {error && <div className="notice error"><X size={14}/>{error}</div>}
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button className="button primary" disabled={busy}>
            <Plus size={15}/> {busy ? 'Đang lưu…' : 'Thêm mặt hàng'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

function EditInventoryItemModal({ item, onClose, onSaved }) {
  const [form, setForm] = useState({
    itemName: item.itemName || '',
    category: item.category || 'FEED',
    unit: item.unit || '',
    minThreshold: String(item.minThreshold ?? 0),
    location: item.location || '',
    description: item.description || '',
    referencePrice: item.referencePrice != null ? String(item.referencePrice) : '',
    status: item.status || 'ACTIVE',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (v) => setForm(f => ({...f, [k]: typeof v === 'string' ? v : v.target.value}))

  const submit = async (e) => {
    e.preventDefault(); setError('')
    const payload = {
      itemName: form.itemName.trim(),
      category: form.category,
      unit: form.unit.trim(),
      minThreshold: Number(form.minThreshold) || 0,
      location: form.location.trim(),
      description: form.description.trim(),
      referencePrice: form.referencePrice !== '' ? Number(form.referencePrice) : null,
      status: form.status,
    }
    if (!payload.itemName) { setError('Tên mặt hàng không được để trống.'); return }
    if (!payload.unit)     { setError('Đơn vị tính không được để trống.'); return }
    setBusy(true)
    try {
      await stableCareApi.updateInventoryItem(item.id, payload)
      onSaved()
    } catch(err) { setError(err.message) }
    finally { setBusy(false) }
  }

  return (
    <Dialog title={`Sửa mặt hàng: ${item.itemName}`} onClose={onClose}>
      <form onSubmit={submit} className="form-stack">
        <label className="field">
          <span>Tên mặt hàng *</span>
          <input required value={form.itemName} onChange={set('itemName')}/>
        </label>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'10px'}}>
          <label className="field">
            <span>Danh mục *</span>
            <select value={form.category} onChange={set('category')}>
              {Object.entries(INV_CATEGORY_LABELS).map(([k,v])=>(
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Đơn vị tính *</span>
            <input required value={form.unit} onChange={set('unit')}/>
          </label>
        </div>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'10px'}}>
          <label className="field">
            <span>Ngưỡng an toàn (cảnh báo tồn thấp)</span>
            <input type="number" min="0" step="0.001" value={form.minThreshold} onChange={set('minThreshold')}/>
          </label>
          <label className="field">
            <span>Đơn giá tham chiếu (VNĐ)</span>
            <input type="number" min="0" step="100" value={form.referencePrice} onChange={set('referencePrice')} placeholder="Bỏ trống nếu chưa có"/>
          </label>
        </div>
        <label className="field">
          <span>Vị trí lưu kho</span>
          <input value={form.location} onChange={set('location')}/>
        </label>
        <label className="field">
          <span>Mô tả / Ghi chú</span>
          <input value={form.description} onChange={set('description')}/>
        </label>
        <label className="field">
          <span>Trạng thái</span>
          <select value={form.status} onChange={set('status')}>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="INACTIVE">Vô hiệu hóa</option>
          </select>
        </label>
        <div className="notice" style={{fontSize:'10px',background:'#f0f4ff',border:'1px solid #c5d0f5',borderRadius:'8px',padding:'8px 10px',color:'#283593',display:'flex',gap:'6px',alignItems:'flex-start'}}>
          <Package size={13} style={{flexShrink:0,marginTop:'1px'}}/>
          <span>Số lượng tồn kho hiện tại: <b>{item.quantityOnHand} {item.unit}</b>. Để điều chỉnh tồn kho, dùng nút <b>Sửa kho</b> trong màn hình Tồn kho &amp; Đề xuất vật tư.</span>
        </div>
        {error && <div className="notice error"><X size={14}/>{error}</div>}
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button className="button primary" disabled={busy}>
            <Save size={15}/> {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
