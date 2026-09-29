/**
 * auth.js — Turfline Stable Manager
 * Module dùng chung cho tất cả trang HTML.
 * Xử lý: lưu token, gọi API với header auth, kiểm tra role, đăng xuất.
 */

const AUTH_KEY = 'stride_auth';

// ── Đọc / Ghi / Xóa auth ──────────────────────────────────────────────────
export function getAuth() {
  try { return JSON.parse(localStorage.getItem(AUTH_KEY)); } catch { return null; }
}

export function saveAuth(data) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(data));
}

export function clearAuth() {
  localStorage.removeItem(AUTH_KEY);
}

// ── Lấy role từ auth object ────────────────────────────────────────────────
export function getRoles() {
  const auth = getAuth();
  return auth?.user?.roles ?? [];
}

export function hasRole(...roles) {
  const userRoles = getRoles();
  const flatRoles = roles.flat();
  return flatRoles.some(r => userRoles.includes(r));
}

// ── Điền thông tin user vào sidebar ──────────────────────────────────────
export function renderUserInfo() {
  const auth = getAuth();
  if (!auth?.user) return;

  const { username, email, roles } = auth.user;
  const displayName = username || email || 'Người dùng';
  const initials = displayName.split(' ').map(w => w[0]).slice(-2).join('').toUpperCase() || 'U';

  // Avatar initials
  const avatarEl = document.querySelector('.user-chip .avatar');
  if (avatarEl) avatarEl.textContent = initials;

  // Tên hiển thị
  const nameEl = document.querySelector('.user-chip .user-info b');
  if (nameEl) nameEl.textContent = displayName;

  // Role hiển thị (lấy role đầu tiên)
  const ROLE_LABELS = {
    CLUB_MANAGER: 'Quản lý câu lạc bộ',
    HEAD_TRAINER: 'Huấn luyện viên Trưởng',
    HORSE_OWNER: 'Chủ sở hữu ngựa',
    STABLE_STAFF: 'Nhân viên chuồng',
    VETERINARIAN: 'Bác sĩ thú y',
  };
  const roleEl = document.querySelector('.user-chip .user-info small');
  if (roleEl && roles?.[0]) roleEl.textContent = ROLE_LABELS[roles[0]] || roles[0];
}

// ── Ẩn sidebar items theo role ────────────────────────────────────────────
export function applyNavRoles() {
  const auth = getAuth();
  const roles = auth?.user?.roles ?? [];

  // users.html chỉ CLUB_MANAGER
  if (!roles.includes('CLUB_MANAGER')) {
    document.querySelectorAll('a[href="users.html"]').forEach(el => {
      el.closest('.nav-link, a') && (el.style.display = 'none');
    });
    // Ẩn cả section label "Quản trị" nếu không có mục nào khác
    document.querySelectorAll('.nav-section-label').forEach(label => {
      if (label.textContent.trim() === 'Quản trị') {
        let next = label.nextElementSibling;
        let allHidden = true;
        while (next && !next.classList.contains('nav-section-label')) {
          if (next.style.display !== 'none') { allHidden = false; break; }
          next = next.nextElementSibling;
        }
        if (allHidden) label.style.display = 'none';
      }
    });
  }

  // fitness.html và training-plans.html: ẩn với HORSE_OWNER, STABLE_STAFF
  const canSeeTraining = roles.some(r => ['CLUB_MANAGER', 'HEAD_TRAINER', 'VETERINARIAN'].includes(r));
  if (!canSeeTraining) {
    document.querySelectorAll('a[href="fitness.html"], a[href="training-plans.html"]')
      .forEach(el => (el.style.display = 'none'));
  }

  // training-schedule.html: STABLE_STAFF thấy nhưng chỉ của mình (không ẩn)
  // dashboard.html: tất cả đều thấy
}

// ── Kiểm tra quyền truy cập trang ─────────────────────────────────────────
/**
 * @param {string[]} allowedRoles - danh sách role được vào trang này (rỗng = yêu cầu đăng nhập thôi)
 * @returns {object|null} auth object nếu hợp lệ
 */
export function requireAuth(allowedRoles = []) {
  const auth = getAuth();

  // Chưa đăng nhập
  if (!auth?.accessToken) {
    window.location.replace('/login.html');
    return null;
  }

  // Kiểm tra role (nếu có yêu cầu)
  if (allowedRoles.length > 0) {
    const userRoles = auth?.user?.roles ?? [];
    const allowed = allowedRoles.some(r => userRoles.includes(r));
    if (!allowed) {
      window.location.replace('/403.html');
      return null;
    }
  }

  return auth;
}

// ── Fetch wrapper với auth header ─────────────────────────────────────────
/**
 * @param {string} path - đường dẫn API (vd: '/api/horses')
 * @param {RequestInit} options - fetch options (method, body, ...)
 * @returns {Promise<any>} - JSON response
 */
export async function apiFetch(path, options = {}) {
  const auth = getAuth();
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };
  if (auth?.accessToken) {
    headers['Authorization'] = `Bearer ${auth.accessToken}`;
  }

  const res = await fetch(path, { ...options, headers });

  if (res.status === 401) {
    clearAuth();
    window.location.replace('/login.html');
    return null;
  }

  if (res.status === 403) {
    window.location.replace('/403.html');
    return null;
  }

  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${errText || res.statusText}`);
  }

  // 204 No Content
  if (res.status === 204) return null;

  return res.json();
}

// ── Đăng xuất ─────────────────────────────────────────────────────────────
export async function logout() {
  const auth = getAuth();
  if (auth?.refreshToken) {
    try {
      await apiFetch('/api/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: auth.refreshToken }),
      });
    } catch (_) { /* bỏ qua lỗi mạng khi logout */ }
  }
  clearAuth();
  window.location.replace('/login.html');
}

// ── Init chung: gắn nút đăng xuất, render user info ──────────────────────
export function initShell(allowedRoles = []) {
  const auth = requireAuth(allowedRoles);
  if (!auth) return null; // đã redirect

  renderUserInfo();
  applyNavRoles();

  // Nút đăng xuất
  document.querySelectorAll('.btn-logout').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      logout();
    });
  });

  // Mobile sidebar toggle
  const toggle = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  const backdrop = document.getElementById('sidebarBackdrop');
  if (toggle && sidebar) {
    toggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      backdrop?.classList.toggle('active');
    });
    backdrop?.addEventListener('click', () => {
      sidebar.classList.remove('open');
      backdrop.classList.remove('active');
    });
  }

  // --- FR16: GLOBAL EMERGENCY INCIDENT FEATURE ---
  setupGlobalIncidentFeature();
  if (hasRole('VETERINARIAN', 'HEAD_TRAINER')) {
    startSseListener();
  }

  return auth;
}

// ── SSE LISTENER (FR16) ───────────────────────────────────────────────────
function startSseListener() {
  const auth = getAuth();
  if (!auth?.accessToken) return;

  const eventSource = new EventSource('/api/notifications/stream?access_token=' + auth.accessToken);
  // Note: EventSource default doesn't support headers well in all browsers. 
  // If backend expects Bearer token in header, a common workaround is passing via query param. 
  // We'll try query param or just simple implementation assuming proxy can handle it.
  // We assume the backend allows JWT via query param for this endpoint.

  eventSource.addEventListener('notification', (e) => {
    try {
      const data = JSON.parse(e.data);
      showToast(data.title, data.message, data.priority === 'HIGH' || data.priority === 'CRITICAL');
    } catch (err) { console.error('Error parsing SSE event:', err); }
  });
  
  eventSource.addEventListener('ping', () => console.log('SSE Ping received'));
  eventSource.onerror = () => console.log('SSE connection error. Reconnecting...');
}

// ── TOAST NOTIFICATION ────────────────────────────────────────────────────
function showToast(title, message, isHighPriority) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.style.cssText = 'position:fixed;top:20px;right:20px;z-index:9999;display:flex;flex-direction:column;gap:10px;';
    document.body.appendChild(container);
  }

  const bg = isHighPriority ? '#e53e3e' : '#2b6cb0';
  const toast = document.createElement('div');
  toast.style.cssText = `background:${bg};color:#fff;padding:15px;border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,0.3);min-width:250px;max-width:350px;animation:slideIn 0.3s ease-out;`;
  toast.innerHTML = `<h4 style="margin:0 0 5px 0;font-size:15px;">${title}</h4><p style="margin:0;font-size:13px;opacity:0.9;">${message}</p>`;
  
  container.appendChild(toast);
  
  // Custom animation slide in/out
  if (!document.getElementById('toast-style')) {
    const style = document.createElement('style');
    style.id = 'toast-style';
    style.innerHTML = `@keyframes slideIn { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }`;
    document.head.appendChild(style);
  }

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 0.3s';
    setTimeout(() => toast.remove(), 300);
  }, 10000); // 10s auto hide
}

// ── GLOBAL INCIDENT MODAL ─────────────────────────────────────────────────
function setupGlobalIncidentFeature() {
  const isStaffOrTrainer = hasRole('STABLE_STAFF', 'HEAD_TRAINER', 'CLUB_MANAGER');
  if (!isStaffOrTrainer) return;

  // 1. Create floating button
  const fab = document.createElement('button');
  fab.innerHTML = '🚨 Báo Sự cố';
  fab.style.cssText = 'position:fixed;bottom:30px;right:30px;z-index:9000;background:#e53e3e;color:#fff;border:none;border-radius:50px;padding:12px 20px;font-size:15px;font-weight:600;box-shadow:0 4px 12px rgba(229,62,62,0.4);cursor:pointer;display:flex;align-items:center;gap:8px;transition:transform 0.2s;';
  fab.onmouseover = () => fab.style.transform = 'scale(1.05)';
  fab.onmouseout = () => fab.style.transform = 'scale(1)';
  document.body.appendChild(fab);

  // 2. Create Modal
  const modalHTML = `
    <div id="incidentModal" style="display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.7);z-index:9001;align-items:center;justify-content:center;">
      <div style="background:#151a28;border:1px solid #2a303f;border-radius:8px;width:90%;max-width:500px;overflow:hidden;box-shadow:0 10px 25px rgba(0,0,0,0.5);">
        <div style="background:#e53e3e;padding:15px 20px;display:flex;justify-content:space-between;align-items:center;">
          <h2 style="margin:0;color:#fff;font-size:1.2rem;">Báo cáo Sự cố Khẩn cấp</h2>
          <button id="closeIncidentBtn" style="background:transparent;border:none;color:#fff;font-size:1.5rem;cursor:pointer;">&times;</button>
        </div>
        <div style="padding:20px;">
          <div style="margin-bottom:15px;">
            <label style="display:block;margin-bottom:5px;color:#a89e7a;font-size:14px;">Chọn Ngựa *</label>
            <select id="inc-horseId" style="width:100%;padding:10px;background:#0a0d14;border:1px solid #2a303f;color:#fff;border-radius:4px;"></select>
          </div>
          <div style="margin-bottom:15px;display:flex;gap:15px;">
            <div style="flex:1;">
              <label style="display:block;margin-bottom:5px;color:#a89e7a;font-size:14px;">Loại sự cố *</label>
              <select id="inc-type" style="width:100%;padding:10px;background:#0a0d14;border:1px solid #2a303f;color:#fff;border-radius:4px;">
                <option value="Bỏ ăn">Bỏ ăn</option>
                <option value="Dấu hiệu Đau bụng">Dấu hiệu Đau bụng</option>
                <option value="Sốt">Sốt</option>
                <option value="Trầy móng / Què">Trầy móng / Què</option>
                <option value="Khác">Khác</option>
              </select>
            </div>
            <div style="flex:1;">
              <label style="display:block;margin-bottom:5px;color:#a89e7a;font-size:14px;">Mức độ *</label>
              <select id="inc-severity" style="width:100%;padding:10px;background:#0a0d14;border:1px solid #2a303f;color:#fff;border-radius:4px;">
                <option value="HIGH">Nghiêm trọng (HIGH)</option>
                <option value="CRITICAL">Nguy kịch (CRITICAL)</option>
                <option value="MEDIUM">Vừa (MEDIUM)</option>
              </select>
            </div>
          </div>
          <div style="margin-bottom:15px;">
            <label style="display:block;margin-bottom:5px;color:#a89e7a;font-size:14px;">Mô tả chi tiết *</label>
            <textarea id="inc-desc" rows="3" style="width:100%;padding:10px;background:#0a0d14;border:1px solid #2a303f;color:#fff;border-radius:4px;resize:vertical;" placeholder="Mô tả tình trạng..."></textarea>
          </div>
          <button id="submitIncidentBtn" style="width:100%;padding:12px;background:#e53e3e;color:#fff;border:none;border-radius:4px;font-size:16px;font-weight:600;cursor:pointer;">Gửi Báo Cáo Ngay</button>
        </div>
      </div>
    </div>
  `;
  document.body.insertAdjacentHTML('beforeend', modalHTML);

  const modal = document.getElementById('incidentModal');
  const horseSelect = document.getElementById('inc-horseId');

  fab.addEventListener('click', async () => {
    modal.style.display = 'flex';
    if (horseSelect.children.length === 0) {
      try {
        const horses = await apiFetch('/api/horses');
        horseSelect.innerHTML = horses.map(h => `<option value="${h.id}">[${h.registrationCode||'?'}] ${h.name}</option>`).join('');
      } catch (e) {
        horseSelect.innerHTML = '<option value="">Lỗi tải danh sách</option>';
      }
    }
  });

  document.getElementById('closeIncidentBtn').addEventListener('click', () => modal.style.display = 'none');

  document.getElementById('submitIncidentBtn').addEventListener('click', async () => {
    const btn = document.getElementById('submitIncidentBtn');
    const body = {
      horseId: horseSelect.value,
      incidentType: document.getElementById('inc-type').value,
      severity: document.getElementById('inc-severity').value,
      description: document.getElementById('inc-desc').value.trim()
    };
    
    if (!body.horseId || !body.description) return alert('Vui lòng điền đủ Ngựa và Mô tả!');
    
    btn.disabled = true; btn.textContent = 'Đang gửi...';
    try {
      await apiFetch('/api/incidents', { method: 'POST', body: JSON.stringify(body) });
      alert('Đã gửi báo cáo sự cố thành công! Cảnh báo đã được chuyển cho Bác sĩ & Quản lý.');
      modal.style.display = 'none';
      document.getElementById('inc-desc').value = '';
    } catch (e) {
      alert('Lỗi khi gửi sự cố: ' + e.message);
    } finally {
      btn.disabled = false; btn.textContent = 'Gửi Báo Cáo Ngay';
    }
  });
}

