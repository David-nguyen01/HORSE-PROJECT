import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { X } from 'lucide-react'
import { api, pageContent } from './api'

const GENDER_LABELS = { STALLION: 'Ngựa đực', MARE: 'Ngựa cái', GELDING: 'Ngựa thiến' }

const emptyHorse = { 
  name: '', 
  microchipPassportNumber: '', 
  birthDate: '', 
  gender: 'STALLION', 
  colorMarkings: '', 
  breed: '',
  sireSelectionType: 'NONE', // NONE, OWNED, MANUAL
  sireId: '',
  sireManual: { name: '', birthDate: '', microchipPassportNumber: '' },
  damSelectionType: 'NONE',
  damId: '',
  damManual: { name: '', birthDate: '', microchipPassportNumber: '' }
}

const isParentValid = (parentDobStr, childDobStr) => {
  if (!parentDobStr || !childDobStr) return false;
  const parentDate = new Date(parentDobStr);
  const childDate = new Date(childDobStr);
  parentDate.setFullYear(parentDate.getFullYear() + 4);
  return parentDate <= childDate;
}

export function OwnerHorseModal({ horseId, onClose, onSaved }) {
  const isEdit = !!horseId
  const [form, setForm] = useState(emptyHorse)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ownedHorses, setOwnedHorses] = useState([])
  const [file, setFile] = useState(null)
  const [paymentPending, setPaymentPending] = useState(false)

  useEffect(() => {
    api('/api/horses/mine?size=100').then(data => {
      setOwnedHorses(pageContent(data).filter(h => h.id !== horseId && h.paymentStatus === 'PAID'))
    }).catch(console.error)

    if (isEdit) {
      setBusy(true)
      api(`/api/horses/${horseId}`)
        .then(h => {
          setForm({
            ...emptyHorse,
            name: h.name,
            microchipPassportNumber: h.microchipPassportNumber || '',
            birthDate: h.birthDate,
            gender: h.gender,
            colorMarkings: h.colorMarkings,
            breed: h.breed,
            sireSelectionType: h.sire?.id ? 'OWNED' : 'NONE',
            sireId: h.sire?.id || '',
            damSelectionType: h.dam?.id ? 'OWNED' : 'NONE',
            damId: h.dam?.id || '',
          })
          // Tải thông tin manual từ API nếu backend hỗ trợ (ở đây đơn giản hóa bỏ qua phần get manual vì backend không trả về sẵn trong entity lúc lấy list)
        })
        .catch(err => setError('Lỗi khi tải hồ sơ: ' + err.message))
        .finally(() => setBusy(false))
    }
  }, [horseId, isEdit])

  const set = (key) => (e) => setForm(prev => ({ ...prev, [key]: e.target.value }))
  
  const handleBirthDateChange = (e) => {
    const newDob = e.target.value;
    const updates = { birthDate: newDob };
    let msg = '';
    
    if (newDob) {
       if (form.sireSelectionType === 'OWNED' && form.sireId) {
          const sire = ownedHorses.find(h => h.id === form.sireId);
          if (sire && !isParentValid(sire.birthDate, newDob)) {
             updates.sireId = '';
             msg += 'Ngựa cha đã chọn không còn hợp lệ (chưa đủ 4 tuổi). ';
          }
       }
       if (form.damSelectionType === 'OWNED' && form.damId) {
          const dam = ownedHorses.find(h => h.id === form.damId);
          if (dam && !isParentValid(dam.birthDate, newDob)) {
             updates.damId = '';
             msg += 'Ngựa mẹ đã chọn không còn hợp lệ (chưa đủ 4 tuổi).';
          }
       }
    } else {
       updates.sireId = '';
       updates.damId = '';
    }
    
    setForm(prev => ({ ...prev, ...updates }));
    if (msg) setError(msg.trim());
    else setError('');
  }

  const handleSireDamChange = (parent, field, value) => {
    setForm(prev => ({
      ...prev,
      [parent]: { ...prev[parent], [field]: value }
    }))
  }

  const validate = () => {
    if (form.sireSelectionType === 'OWNED' && form.damSelectionType === 'OWNED') {
      if (form.sireId && form.sireId === form.damId) {
        return 'Ngựa cha và ngựa mẹ không được trùng nhau.'
      }
    }
    // Xác thực tuổi cho ngựa nhập tay
    if (form.sireSelectionType === 'MANUAL' && form.sireManual.birthDate) {
      if (!isParentValid(form.sireManual.birthDate, form.birthDate)) {
        return 'Ngựa cha nhập tay chưa đủ 4 tuổi tại thời điểm ngựa con ra đời.';
      }
    }
    if (form.damSelectionType === 'MANUAL' && form.damManual.birthDate) {
      if (!isParentValid(form.damManual.birthDate, form.birthDate)) {
        return 'Ngựa mẹ nhập tay chưa đủ 4 tuổi tại thời điểm ngựa con ra đời.';
      }
    }
    return null
  }

  const submit = async (e, isDraft = false) => {
    e.preventDefault()
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }

    setBusy(true)
    setError('')
    try {
      const payload = {
        name: form.name,
        birthDate: form.birthDate,
        gender: form.gender,
        microchipPassportNumber: form.microchipPassportNumber || null,
        breed: form.breed,
        colorMarkings: form.colorMarkings,
        sireId: form.sireSelectionType === 'OWNED' ? form.sireId : null,
        damId: form.damSelectionType === 'OWNED' ? form.damId : null,
        sireManualInfo: form.sireSelectionType === 'MANUAL' ? JSON.stringify(form.sireManual) : null,
        damManualInfo: form.damSelectionType === 'MANUAL' ? JSON.stringify(form.damManual) : null
      }
      
      let savedHorse;
      if (isEdit) {
        savedHorse = await api(`/api/owner/horses/${horseId}`, {
          method: 'PUT',
          body: JSON.stringify(payload)
        })
      } else {
        savedHorse = await api('/api/owner/horses', {
          method: 'POST',
          body: JSON.stringify(payload)
        })
      }
      
      if (file && savedHorse.id) {
        const body = new FormData()
        body.append('file', file)
        await api(`/api/horses/${savedHorse.id}/main-file/proposals`, { method: 'POST', body })
      }

      if (isDraft) {
        onSaved()
      } else {
        await api(`/api/owner/horses/${savedHorse.id}/submit`, { method: 'POST' })
        setPaymentPending(true)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (paymentPending) {
    return (
      <Modal title="Hồ sơ đã tiếp nhận" onClose={onClose}>
        <div className="form-grid">
          <div className="notice full" style={{background: '#fff3cd', color: '#856404', border: '1px solid #ffeeba'}}>
            <b>Hồ sơ đang chờ thanh toán</b>
            <p>Hồ sơ ngựa đã được lưu trữ an toàn nhưng cần thanh toán gói cước để được câu lạc bộ xử lý và kích hoạt.</p>
          </div>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={() => { onClose(); onSaved() }}>Để sau</button>
            <Link to="/wallet" className="button primary" onClick={() => { onClose(); onSaved() }}>Đến Ví thanh toán</Link>
          </div>
        </div>
      </Modal>
    )
  }

  const maleHorses = ownedHorses.filter(h => h.gender === 'STALLION' || h.gender === 'GELDING');
  const validMaleHorses = maleHorses.filter(h => isParentValid(h.birthDate, form.birthDate));
  
  const femaleHorses = ownedHorses.filter(h => h.gender === 'MARE');
  const validFemaleHorses = femaleHorses.filter(h => isParentValid(h.birthDate, form.birthDate));

  return (
    <Modal title={isEdit ? 'Sửa hồ sơ ngựa' : 'Đăng ký ngựa mới'} onClose={onClose}>
      <form className="form-grid">
        {error && <div className="notice error full"><X size={17} />{error}</div>}
        
        <Field label="Tên ngựa *"><input required value={form.name} onChange={set('name')} /></Field>
        <Field label="Ngày sinh *"><input required type="date" max={new Date().toISOString().slice(0, 10)} value={form.birthDate} onChange={handleBirthDateChange} /></Field>
        <Field label="Giới tính *">
          <select value={form.gender} onChange={set('gender')}>
            {Object.entries(GENDER_LABELS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Số chip / hộ chiếu (nếu có)"><input value={form.microchipPassportNumber} onChange={set('microchipPassportNumber')} /></Field>
        <Field label="Giống *"><input required value={form.breed} onChange={set('breed')} /></Field>
        <Field label="Màu sắc / nhận dạng *"><input required value={form.colorMarkings} onChange={set('colorMarkings')} /></Field>

        <div className="full" style={{marginTop: '1rem', borderTop: '1px solid #eee', paddingTop: '1rem'}}>
          <h4>Thông tin Ngựa Cha</h4>
        </div>
        <Field label="Tùy chọn">
          <select value={form.sireSelectionType} onChange={set('sireSelectionType')}>
            <option value="NONE">Không xác định</option>
            <option value="OWNED">Chọn từ ngựa đang sở hữu</option>
            <option value="MANUAL">Nhập tay (Hồ sơ ngoài)</option>
          </select>
        </Field>
        {form.sireSelectionType === 'OWNED' && (
          <Field label="Chọn ngựa cha">
            {!form.birthDate ? (
              <div className="notice" style={{marginTop: 4, padding: 8, fontSize: 13}}>Vui lòng nhập ngày sinh của ngựa trước để có thể chọn ngựa cha phù hợp.</div>
            ) : (
              <>
                <select value={form.sireId} onChange={set('sireId')}>
                  <option value="">-- Chọn ngựa --</option>
                  {validMaleHorses.map(h => (
                    <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
                  ))}
                </select>
                {maleHorses.length > 0 && validMaleHorses.length === 0 && (
                  <div className="notice error" style={{marginTop: 4, padding: 8, fontSize: 13}}>Chưa có ngựa đực nào đủ 4 tuổi tại thời điểm ngựa con ra đời.</div>
                )}
                {maleHorses.length === 0 && (
                  <div className="notice error" style={{marginTop: 4, padding: 8, fontSize: 13}}>Bạn chưa sở hữu ngựa đực/thiến nào hợp lệ (đã thanh toán phí).</div>
                )}
              </>
            )}
          </Field>
        )}
        {form.sireSelectionType === 'MANUAL' && (
          <>
            <Field label="Tên ngựa cha"><input value={form.sireManual.name} onChange={(e) => handleSireDamChange('sireManual', 'name', e.target.value)} /></Field>
            <Field label="Ngày sinh"><input type="date" value={form.sireManual.birthDate} onChange={(e) => handleSireDamChange('sireManual', 'birthDate', e.target.value)} /></Field>
            <Field label="Số chip/ID"><input value={form.sireManual.microchipPassportNumber} onChange={(e) => handleSireDamChange('sireManual', 'microchipPassportNumber', e.target.value)} /></Field>
          </>
        )}

        <div className="full" style={{marginTop: '1rem', borderTop: '1px solid #eee', paddingTop: '1rem'}}>
          <h4>Thông tin Ngựa Mẹ</h4>
        </div>
        <Field label="Tùy chọn">
          <select value={form.damSelectionType} onChange={set('damSelectionType')}>
            <option value="NONE">Không xác định</option>
            <option value="OWNED">Chọn từ ngựa đang sở hữu</option>
            <option value="MANUAL">Nhập tay (Hồ sơ ngoài)</option>
          </select>
        </Field>
        {form.damSelectionType === 'OWNED' && (
          <Field label="Chọn ngựa mẹ">
            {!form.birthDate ? (
              <div className="notice" style={{marginTop: 4, padding: 8, fontSize: 13}}>Vui lòng nhập ngày sinh của ngựa trước để có thể chọn ngựa mẹ phù hợp.</div>
            ) : (
              <>
                <select value={form.damId} onChange={set('damId')}>
                  <option value="">-- Chọn ngựa --</option>
                  {validFemaleHorses.map(h => (
                    <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
                  ))}
                </select>
                {femaleHorses.length > 0 && validFemaleHorses.length === 0 && (
                  <div className="notice error" style={{marginTop: 4, padding: 8, fontSize: 13}}>Chưa có ngựa cái nào đủ 4 tuổi tại thời điểm ngựa con ra đời.</div>
                )}
                {femaleHorses.length === 0 && (
                  <div className="notice error" style={{marginTop: 4, padding: 8, fontSize: 13}}>Bạn chưa sở hữu ngựa cái nào hợp lệ (đã thanh toán phí).</div>
                )}
              </>
            )}
          </Field>
        )}
        {form.damSelectionType === 'MANUAL' && (
          <>
            <Field label="Tên ngựa mẹ"><input value={form.damManual.name} onChange={(e) => handleSireDamChange('damManual', 'name', e.target.value)} /></Field>
            <Field label="Ngày sinh"><input type="date" value={form.damManual.birthDate} onChange={(e) => handleSireDamChange('damManual', 'birthDate', e.target.value)} /></Field>
            <Field label="Số chip/ID"><input value={form.damManual.microchipPassportNumber} onChange={(e) => handleSireDamChange('damManual', 'microchipPassportNumber', e.target.value)} /></Field>
          </>
        )}

        <div className="full" style={{marginTop: '1rem', borderTop: '1px solid #eee', paddingTop: '1rem'}}>
          <Field label="File/Ảnh hồ sơ (Đề xuất)">
            <input type="file" accept="image/*,.pdf,.docx" onChange={(e) => setFile(e.target.files?.[0])} />
          </Field>
        </div>

        <div className="modal-actions full">
          <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
          <button type="button" className="button secondary" disabled={busy} onClick={(e) => submit(e, true)}>
            {busy ? 'Đang xử lý…' : 'Lưu nháp'}
          </button>
          <button type="submit" className="button primary" disabled={busy} onClick={(e) => submit(e, false)}>
            {busy ? 'Đang gửi…' : 'Gửi hồ sơ'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }
function Modal({ title, onClose, children }) { return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={(e) => e.stopPropagation()}><div className="modal-head"><div><p className="eyebrow">RACEHORSE CLUB</p><h2>{title}</h2></div><button type="button" className="icon-button" onClick={onClose}><X /></button></div>{children}</div></div> }
