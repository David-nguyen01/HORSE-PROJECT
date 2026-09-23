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

  return auth;
}
