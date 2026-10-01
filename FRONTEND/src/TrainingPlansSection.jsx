import { useState, useEffect } from 'react';
import { Plus, X, Calendar, Edit2, CalendarPlus, Check } from 'lucide-react';
import { api, pageContent } from './api';

export default function TrainingPlansSection({ horseId, isManager, isHeadTrainer, isOwner }) {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [selectedPhase, setSelectedPhase] = useState(null);

  const canEdit = isManager || isHeadTrainer;

  const load = async () => {
    try {
      const p = await api(`/api/horses/${horseId}/training-plans`);
      setPlans(p);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [horseId]);

  return (
    <section className="card">
      <div className="card-head">
        <h3>Kế hoạch huấn luyện</h3>
        {canEdit && (
          <button className="button secondary" onClick={() => setShowPlanModal(true)}>
            <Plus size={16} /> Lên kế hoạch
          </button>
        )}
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading ? (
        <div className="empty">Đang tải...</div>
      ) : plans.length === 0 ? (
        <div className="empty">Chưa có kế hoạch huấn luyện.</div>
      ) : (
        <div className="training-plans-list" style={{display:'flex', flexDirection:'column', gap:'16px'}}>
          {plans.map(plan => (
            <div key={plan.id} style={{border: '1px solid #e0e0e0', borderRadius: '8px', padding: '16px'}}>
              <div style={{display:'flex', justifyContent:'space-between', marginBottom:'12px'}}>
                <h4>{plan.name} {plan.active && <span className="status active">Đang áp dụng</span>}</h4>
              </div>
              <div style={{display:'flex', flexDirection:'column', gap:'8px'}}>
                {plan.phases?.map(phase => (
                  <div key={phase.id} style={{background: '#f8f9fa', padding: '12px', borderRadius: '6px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                    <div>
                      <b>{phase.name}</b>
                      <div style={{fontSize: '13px', color: '#555', marginTop: '4px'}}>
                        {phase.startDate} — {phase.endDate} · {phase.workloadIntensity} · {phase.surfaceType}
                      </div>
                    </div>
                    {canEdit && plan.active && (
                      <button className="button secondary" style={{fontSize: '12px', padding: '4px 8px'}} onClick={() => { setSelectedPhase(phase); setShowScheduleModal(true); }}>
                        <CalendarPlus size={14} /> Lên lịch tập
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {showPlanModal && (
        <PlanModal 
          horseId={horseId} 
          onClose={() => setShowPlanModal(false)} 
          onSaved={() => { setShowPlanModal(false); load(); }} 
        />
      )}
      
      {showScheduleModal && (
        <ScheduleModal 
          phase={selectedPhase} 
          onClose={() => { setShowScheduleModal(false); setSelectedPhase(null); }} 
          onSaved={() => { setShowScheduleModal(false); setSelectedPhase(null); load(); }} 
        />
      )}
    </section>
  );
}

function PlanModal({ horseId, onClose, onSaved }) {
  const [form, setForm] = useState({ name: '', active: true, phases: [{ name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'DIRT' }] });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api('/api/training-plans', { method: 'POST', body: JSON.stringify({ ...form, horseId }) });
      onSaved();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{maxWidth: '600px'}} onMouseDown={e => e.stopPropagation()}>
        <div className="modal-head">
          <div><h2>Lên kế hoạch huấn luyện mới</h2></div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full">{error}</div>}
          <div className="field full">
            <label>Tên kế hoạch *</label>
            <input required value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="VD: Chuẩn bị giải mùa Thu" />
          </div>
          <div className="field full">
            <label>Giai đoạn 1 *</label>
            <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px'}}>
              <input required placeholder="Tên giai đoạn" value={form.phases[0].name} onChange={e => { const p = [...form.phases]; p[0].name = e.target.value; setForm({...form, phases: p}) }} />
              <input required type="number" step="0.01" placeholder="Mục tiêu cự ly (m)" value={form.phases[0].targetDistanceMeters} onChange={e => { const p = [...form.phases]; p[0].targetDistanceMeters = e.target.value; setForm({...form, phases: p}) }} />
              <input required type="date" value={form.phases[0].startDate} onChange={e => { const p = [...form.phases]; p[0].startDate = e.target.value; setForm({...form, phases: p}) }} />
              <input required type="date" value={form.phases[0].endDate} onChange={e => { const p = [...form.phases]; p[0].endDate = e.target.value; setForm({...form, phases: p}) }} />
              <select value={form.phases[0].workloadIntensity} onChange={e => { const p = [...form.phases]; p[0].workloadIntensity = e.target.value; setForm({...form, phases: p}) }}>
                <option value="LOW">Nhẹ</option>
                <option value="MEDIUM">Vừa</option>
                <option value="HIGH">Nặng</option>
              </select>
              <select value={form.phases[0].surfaceType} onChange={e => { const p = [...form.phases]; p[0].surfaceType = e.target.value; setForm({...form, phases: p}) }}>
                <option value="DIRT">Đất</option>
                <option value="TURF">Cỏ</option>
                <option value="SYNTHETIC">Nhân tạo</option>
              </select>
            </div>
          </div>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu kế hoạch'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ScheduleModal({ phase, onClose, onSaved }) {
  const [form, setForm] = useState({ taskName: '', scheduledStart: '', scheduledEnd: '', assignedStaffId: '' });
  const [staffs, setStaffs] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    api('/api/admin/accounts?role=STABLE_STAFF').then(res => setStaffs(res.content || [])).catch(()=>api('/api/admin/accounts?role=HEAD_TRAINER').then(res=>setStaffs(res.content || [])));
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const start = new Date(form.scheduledStart).toISOString();
      const end = new Date(form.scheduledEnd).toISOString();
      await api('/api/training-schedules', { method: 'POST', body: JSON.stringify({ 
        trainingPlanPhaseId: phase.id, 
        assignedStaffId: form.assignedStaffId, 
        taskName: form.taskName, 
        scheduledStart: start, 
        scheduledEnd: end 
      }) });
      onSaved();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={e => e.stopPropagation()}>
        <div className="modal-head">
          <div><h2>Lên lịch tập: {phase.name}</h2></div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full">{error}</div>}
          <div className="field full">
            <label>Tên bài tập *</label>
            <input required value={form.taskName} onChange={e => setForm({...form, taskName: e.target.value})} placeholder="VD: Tập phi nước đại 1000m" />
          </div>
          <div className="field full">
            <label>Bắt đầu *</label>
            <input required type="datetime-local" value={form.scheduledStart} onChange={e => setForm({...form, scheduledStart: e.target.value})} />
          </div>
          <div className="field full">
            <label>Kết thúc *</label>
            <input required type="datetime-local" value={form.scheduledEnd} onChange={e => setForm({...form, scheduledEnd: e.target.value})} />
          </div>
          <div className="field full">
            <label>Người phụ trách (Trainer/Staff) *</label>
            <select required value={form.assignedStaffId} onChange={e => setForm({...form, assignedStaffId: e.target.value})}>
              <option value="">-- Chọn nhân viên --</option>
              {staffs.map(s => <option key={s.id} value={s.id}>{s.username} - {s.email}</option>)}
            </select>
          </div>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu lịch tập'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
