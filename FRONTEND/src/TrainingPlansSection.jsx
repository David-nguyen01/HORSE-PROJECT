import { useState, useEffect } from 'react';
import { Plus, X, Calendar, Edit2, CalendarPlus, Check } from 'lucide-react';
import { api, pageContent } from './api';
import { restDaysIssue } from './trainingRules';
const todayVN = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

export default function TrainingPlansSection({ horseId, isManager, isHeadTrainer, isOwner, isInjured = false }) {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showPlanModal, setShowPlanModal] = useState(false);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [selectedPhase, setSelectedPhase] = useState(null);

  const canEdit = isManager || isHeadTrainer;

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const p = await api(`/api/horses/${horseId}/training-plans`);
      setPlans(pageContent(p));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [horseId]);

  const activatePlan = async (plan) => {
    setError('');
    try {
      await api(`/api/training-plans/${plan.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          horseId,
          name: plan.name,
          active: true,
          phases: plan.phases.map(({ name, startDate, endDate, targetDistanceMeters, workloadIntensity, surfaceType, restDays }) => ({
            name, startDate, endDate, targetDistanceMeters, workloadIntensity, surfaceType, restDays
          }))
        })
      });
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

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
                <h4>{plan.name} <span className={`status ${plan.status === 'ACTIVE' ? 'active' : ''}`}>{plan.status === 'ACTIVE' ? 'Đang áp dụng' : plan.status === 'INACTIVE' ? 'Chưa kích hoạt' : 'Hết hạn'}</span></h4>
                {canEdit && plan.status === 'INACTIVE' && <button className="button secondary" onClick={() => activatePlan(plan)}>Kích hoạt</button>}
              </div>
              <div style={{display:'flex', flexDirection:'column', gap:'8px'}}>
                {plan.phases?.map(phase => (
                  <div key={phase.id} style={{background: '#f8f9fa', padding: '12px', borderRadius: '6px', display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center'}}>
                    <div>
                      <b>{phase.name}</b>
                      <div style={{fontSize: '13px', color: '#555', marginTop: '4px'}}>
                        {phase.startDate} - {phase.endDate} · {phase.workloadIntensity} · {phase.surfaceType}{phase.restDays?.length ? ` · Nghỉ: ${phase.restDays.join(', ')}` : ' · Không có ngày nghỉ'}
                      </div>
                    </div>
                    {canEdit && plan.status === 'ACTIVE' && (
                      <button className="button secondary" style={{fontSize: '12px', padding: '4px 8px'}} onClick={() => { setSelectedPhase(phase); setShowScheduleModal(true); }}>
                        <CalendarPlus size={14} /> Lên lịch tập
                      </button>
                    )}
                    <div style={{ width: '100%', marginTop: '10px' }}>
                      {(phase.sessions || []).length ? phase.sessions.map(session => (
                        <div key={session.id} style={{ padding: '6px 0', borderTop: '1px solid #e3e7e1', fontSize: '13px' }}>
                          {session.date} · {{ PREPARING: 'Đang chuẩn bị', READY: 'Sẵn sàng', IN_PROGRESS: 'Đang huấn luyện', COMPLETED: 'Hoàn tất', INJURED: 'Chấn thương', CANCELLED: 'Đã hủy' }[session.status] || session.status}
                          {session.evaluation && ` · ${session.evaluation.fitnessStatus || '-'} · Cường độ ${session.evaluation.intensityPercent ?? '-'}% · HRR1 ${session.evaluation.recoveryRate ?? '-'} nhịp/phút`}
                        </div>
                      )) : <small>Chưa có buổi tập trong giai đoạn.</small>}
                    </div>
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
          isInjured={isInjured}
        />
      )}

      {showScheduleModal && (
        <ScheduleModal
          horseId={horseId}
          phase={selectedPhase}
          onClose={() => { setShowScheduleModal(false); setSelectedPhase(null); }}
          onSaved={() => { setShowScheduleModal(false); setSelectedPhase(null); load(); }}
        />
      )}
    </section>
  );
}

function PlanModal({ horseId, onClose, onSaved, isInjured }) {
  const [form, setForm] = useState({ name: '', active: true, phases: [{ name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'GRASS', restDays: [] }] });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [restDateInputs, setRestDateInputs] = useState({});

  const updatePhase = (index, key, value) => setForm(current => ({
    ...current,
    phases: current.phases.map((phase, phaseIndex) => {
      if (phaseIndex !== index) return phase;
      const next = { ...phase, [key]: value };
      if (['startDate', 'endDate'].includes(key) && phase.startDate && phase.endDate) {
        const start = key === 'startDate' ? value : phase.startDate;
        const end = key === 'endDate' ? value : phase.endDate;
        next.restDays = (phase.restDays || []).filter(date => date >= start && date <= end);
      }
      return next;
    })
  }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const ordered = [...form.phases].sort((a, b) => a.startDate.localeCompare(b.startDate));
      if (ordered.some((phase, index) => index > 0 && phase.startDate <= ordered[index - 1].endDate)) {
        throw new Error('Các giai đoạn trong cùng kế hoạch không được trùng thời gian.');
      }
      const phaseWithoutRest = form.phases.find(phase => !phase.restDays?.length);
      if (phaseWithoutRest) throw new Error(`Mỗi giai đoạn phải có ít nhất một ngày nghỉ: ${phaseWithoutRest.name || 'Giai đoạn chưa đặt tên'}.`);
      if (!isInjured) {
        const issue = form.phases.map(phase => restDaysIssue(phase.restDays || [])).find(Boolean);
        if (issue) throw new Error(issue);
      }
      await api('/api/training-plans', { method: 'POST', body: JSON.stringify({
        ...form, horseId,
        phases: form.phases.map(phase => ({ ...phase, targetDistanceMeters: Number(phase.targetDistanceMeters) }))
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
          <div className="field full"><label>Giai đoạn *</label>
            {form.phases.map((phase, index) => <div key={index} className="card" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', padding: '12px', marginBottom: '10px' }}>
              <b style={{ gridColumn: '1 / -1' }}>Giai đoạn {index + 1}</b>
              <input required maxLength="200" placeholder="Tên giai đoạn" value={phase.name} onChange={e => updatePhase(index, 'name', e.target.value)} />
              <input required type="number" min="0.01" step="0.01" placeholder="Mục tiêu cự ly (m)" value={phase.targetDistanceMeters} onChange={e => updatePhase(index, 'targetDistanceMeters', e.target.value)} />
              <input required type="date" min={todayVN()} value={phase.startDate} onChange={e => updatePhase(index, 'startDate', e.target.value)} />
              <input required type="date" min={phase.startDate} value={phase.endDate} onChange={e => updatePhase(index, 'endDate', e.target.value)} />
              <select value={phase.workloadIntensity} onChange={e => updatePhase(index, 'workloadIntensity', e.target.value)}><option value="LOW">LOW · Nhẹ</option><option value="MEDIUM">MEDIUM · Vừa</option><option value="HIGH">HIGH · Nặng</option></select>
              <select value={phase.surfaceType} onChange={e => updatePhase(index, 'surfaceType', e.target.value)}><option value="GRASS">GRASS · Cỏ</option><option value="DIRT">DIRT · Đất</option><option value="SYNTHETIC">SYNTHETIC · Nhân tạo</option></select>
              <label className="field" style={{ gridColumn: '1 / -1' }}><span>Thêm ngày nghỉ trong giai đoạn *</span><div style={{ display: 'flex', gap: '8px' }}><input type="date" min={phase.startDate > todayVN() ? phase.startDate : todayVN()} max={phase.endDate} disabled={!phase.startDate || !phase.endDate} value={restDateInputs[index] || ''} onChange={e => setRestDateInputs(current => ({ ...current, [index]: e.target.value }))} /><button type="button" className="button secondary" disabled={!restDateInputs[index] || (phase.restDays || []).includes(restDateInputs[index])} onClick={() => { updatePhase(index, 'restDays', [...(phase.restDays || []), restDateInputs[index]].sort()); setRestDateInputs(current => ({ ...current, [index]: '' })); }}>Thêm</button></div><div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '6px' }}>{(phase.restDays || []).map(date => <button type="button" className="button secondary" key={date} onClick={() => updatePhase(index, 'restDays', phase.restDays.filter(item => item !== date))}>{date} ×</button>)}</div>{!(phase.restDays || []).length && <small className="notice error">Cần chọn ít nhất một ngày nghỉ.</small>}{!isInjured && restDaysIssue(phase.restDays || []) && <small className="notice error">{restDaysIssue(phase.restDays || [])}</small>}</label>
              {form.phases.length > 1 && <button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, phases: current.phases.filter((_, phaseIndex) => phaseIndex !== index) }))}>Xóa giai đoạn</button>}
            </div>)}
            <button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, phases: [...current.phases, { name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'GRASS', restDays: [] }] }))}><Plus size={15} /> Thêm giai đoạn</button>
          </div>
          {isInjured && <p className="evaluation-note full">Ngựa đang bị thương; giới hạn ngày nghỉ không được áp dụng.</p>}
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || form.phases.some(phase => !phase.restDays?.length)}>{busy ? 'Đang lưu...' : 'Lưu kế hoạch'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ScheduleModal({ horseId, phase, onClose, onSaved }) {
  const [form, setForm] = useState({ taskName: '', trainingDate: '', racetrackId: '', laneId: '' });
  const [racetracks, setRacetracks] = useState([]);
  const [lanes, setLanes] = useState([]);
  const [lanesLoading, setLanesLoading] = useState(false);
  const [lanesError, setLanesError] = useState('');
  const [racetracksLoading, setRacetracksLoading] = useState(true);
  const [racetracksError, setRacetracksError] = useState('');
  const [lockState, setLockState] = useState({ state: 'loading', reason: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let current = true;
    api('/api/racetracks')
      .then(res => { if (current) setRacetracks(pageContent(res).filter(track => track.active)); })
      .catch(err => { if (current) setRacetracksError(err.message); })
      .finally(() => { if (current) setRacetracksLoading(false); });
    return () => { current = false; };
  }, []);

  useEffect(() => {
    setForm(value => ({ ...value, laneId: '' }));
    if (!form.racetrackId || !form.trainingDate) {
      setLanes([]);
      return;
    }
    let current = true;
    setLanesLoading(true); setLanesError('');
    api(`/api/racetracks/${form.racetrackId}/availability?date=${form.trainingDate}`)
      .then(value => { if (current) setLanes(pageContent(value)); })
      .catch(err => { if (current) { setLanes([]); setLanesError(err.message); } })
      .finally(() => { if (current) setLanesLoading(false); });
    return () => { current = false; };
  }, [form.racetrackId, form.trainingDate]);

  useEffect(() => {
    let current = true;
    api(`/api/horses/${horseId}/training-locks/current`)
      .then(lock => { if (current) setLockState({ state: 'locked', reason: lock.reason }); })
      .catch(err => {
        if (!current) return;
        setLockState(err.status === 404
          ? { state: 'clear', reason: '' }
          : { state: 'error', reason: err.message });
      });
    return () => { current = false; };
  }, [horseId]);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api('/api/training-schedules', {
        method: 'POST',
        body: JSON.stringify({
          trainingPlanPhaseId: phase.id,
          racetrackId: form.racetrackId,
          racetrackLaneId: form.laneId,
          taskName: form.taskName,
          trainingDate: form.trainingDate
        })
      });
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
          <label className="field full"><span>Tên bài tập *</span><input required maxLength={300} value={form.taskName} onChange={e => setForm({...form, taskName: e.target.value})} /></label>
          <label className="field full"><span>Sân tập *</span><select required value={form.racetrackId} disabled={racetracksLoading || racetracks.length === 0} onChange={e => setForm({...form, racetrackId: e.target.value})}><option value="">{racetracksLoading ? 'Đang tải sân tập...' : racetracks.length ? '-- Chọn sân tập --' : 'Không có sân tập hoạt động'}</option>{racetracks.map(track => <option key={track.id} value={track.id}>{track.name}</option>)}</select></label>
          <label className="field"><span>Ngày tập *</span><input required type="date" min={phase.startDate > todayVN() ? phase.startDate : todayVN()} max={phase.endDate} value={form.trainingDate} onChange={e => setForm({...form, trainingDate: e.target.value})} /></label>
          <label className="field"><span>Làn còn trống *</span><select required value={form.laneId} disabled={!form.racetrackId || !form.trainingDate || lanesLoading || lanes.length === 0} onChange={e => setForm({...form, laneId: e.target.value})}><option value="">{lanesLoading ? 'Đang tải làn...' : lanesError ? 'Không tải được làn' : !form.racetrackId || !form.trainingDate ? 'Chọn sân tập và ngày trước' : lanes.length ? 'Chọn làn' : 'Không còn làn trống trong ngày này'}</option>{lanes.map(lane => <option key={lane.laneId} value={lane.laneId}>Làn {lane.laneNumber}</option>)}</select></label>
          {racetracksError && <div className="notice error full">{racetracksError}</div>}
          {lanesError && <div className="notice error full">{lanesError}</div>}
          {phase.workloadIntensity === 'HIGH' && lockState.state === 'locked' && <div className="notice error full">Ngựa đang bị khóa huấn luyện: {lockState.reason}. Không thể tạo buổi cường độ cao.</div>}
          {phase.workloadIntensity === 'HIGH' && lockState.state === 'loading' && <div className="evaluation-note full">Đang kiểm tra khóa huấn luyện…</div>}
          {phase.workloadIntensity === 'HIGH' && lockState.state === 'error' && <div className="notice error full">Không thể kiểm tra khóa huấn luyện: {lockState.reason}. Máy chủ vẫn áp dụng quy tắc khi lưu.</div>}
          {phase.restDays?.includes(form.trainingDate) && <div className="notice error full">Ngày này là ngày nghỉ của giai đoạn đã chọn.</div>}
          <div className="evaluation-note full">Khung giờ hoạt động: 05:00 - 11:00. Nhân viên chuồng hoặc bác sĩ thú y tự nhận buổi tập.</div>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || !racetracks.length || !form.laneId || phase.restDays?.includes(form.trainingDate) || (phase.workloadIntensity === 'HIGH' && ['loading', 'locked'].includes(lockState.state))}>{busy ? 'Đang lưu...' : 'Tạo buổi tập'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
