export const FITNESS_THRESHOLDS = { greenBelow: 120, yellowThrough: 140 }

export function fitnessStatusFromBpm(value) {
  const bpm = Number(value)
  if (!Number.isFinite(bpm)) return ''
  if (bpm < FITNESS_THRESHOLDS.greenBelow) return 'GREEN'
  if (bpm <= FITNESS_THRESHOLDS.yellowThrough) return 'YELLOW'
  return 'RED'
}

export function restDaysIssue(restDays) {
  const dates = [...new Set(restDays)].sort()
  let consecutive = 1
  for (let index = 1; index < dates.length; index += 1) {
    const previous = new Date(`${dates[index - 1]}T00:00:00Z`)
    const current = new Date(`${dates[index]}T00:00:00Z`)
    const daysApart = (current - previous) / 86400000
    consecutive = daysApart === 1 ? consecutive + 1 : 1
    if (consecutive > 2) return 'Không thể nghỉ 3 ngày liên tiếp.'
  }

  const weekCounts = new Map()
  dates.forEach((date) => {
    const day = new Date(`${date}T00:00:00Z`)
    const mondayOffset = (day.getUTCDay() + 6) % 7
    day.setUTCDate(day.getUTCDate() - mondayOffset)
    const monday = day.toISOString().slice(0, 10)
    weekCounts.set(monday, (weekCounts.get(monday) || 0) + 1)
  })
  if ([...weekCounts.values()].some((count) => count > 1)) {
    return 'Chỉ được chọn tối đa 1 ngày nghỉ từ thứ Hai đến Chủ nhật.'
  }
  return ''
}

export const isInjuredHorse = (horse) =>
  horse?.healthStatus?.toUpperCase() === 'INJURED' || horse?.status?.toUpperCase() === 'INJURED'

export function isHorseFitForTraining(horse) {
  if (!horse) return false
  const health = horse.healthStatus ? String(horse.healthStatus).trim().toUpperCase() : ''
  const status = horse.status ? String(horse.status).trim().toUpperCase() : ''
  return health === 'FIT' && status !== 'INJURED'
}

export function getHorseIneligibilityReason(horse) {
  if (!horse) return 'Không xác định'
  const status = horse.status ? String(horse.status).trim().toUpperCase() : ''
  const health = horse.healthStatus ? String(horse.healthStatus).trim().toUpperCase() : ''
  if (status === 'INJURED' || health === 'INJURED') return 'Chấn thương'
  if (health === 'QUARANTINED') return 'Đang cách ly'
  if (health === 'MONITORING' || health === 'MONITOR') return 'Cần theo dõi y tế'
  if (health === 'RESTING') return 'Nghỉ ngơi'
  if (health === 'RECOVERING') return 'Đang hồi phục'
  if (health === 'SICK' || health === 'UNDER_TREATMENT') return 'Đang điều trị'
  if (health === 'RESTRICTED') return 'Hạn chế y tế'
  if (!health) return 'Chưa có trạng thái FIT'
  if (health !== 'FIT') return `Trạng thái: ${health}`
  return ''
}

export function formatTimeHHmm(val) {
  if (!val) return ''
  const str = String(val).trim()
  const m = str.match(/^(\d{1,2}):(\d{2})(?::\d{2})?/)
  if (m) {
    return `${m[1].padStart(2, '0')}:${m[2]}`
  }
  return str
}

export function formatTimeRange(start, end) {
  if (!start && !end) return '—'
  const fmt = (val) => {
    if (!val) return ''
    try {
      const d = new Date(val)
      if (isNaN(d.getTime())) return String(val)
      return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
    } catch {
      return String(val)
    }
  }
  const s = fmt(start)
  const e = fmt(end)
  return s && e ? `${s} - ${e}` : (s || e)
}

export function isActionableTrainingSchedule(schedule) {
  if (!schedule) return false
  const status = (schedule.status || schedule.sessionStatus || '').trim().toUpperCase()
  return status === 'PREPARING' || status === 'READY'
}

export function filterTrainingSchedules(schedules, statusFilter = 'ALL') {
  if (!Array.isArray(schedules)) return []
  const actionable = schedules.filter(isActionableTrainingSchedule)
  if (!statusFilter || statusFilter === 'ALL') return actionable
  return actionable.filter((s) => s.status === statusFilter)
}

export function computeTrainingScheduleCounts(schedules) {
  const list = Array.isArray(schedules) ? schedules.filter(isActionableTrainingSchedule) : []
  return {
    all: list.length,
    preparing: list.filter((s) => s.status === 'PREPARING').length,
    ready: list.filter((s) => s.status === 'READY').length,
  }
}

export function buildTrainingScheduleQueryParams(appliedDate) {
  if (!appliedDate) return {}
  return { from: appliedDate, to: appliedDate }
}

export function getTrainingEmptyStateMessage(appliedDate, formatVNDateFn) {
  const fmt = formatVNDateFn || ((val) => {
    if (!val) return ''
    try {
      const [y, m, d] = String(val).split('T')[0].split('-')
      if (y && m && d) return `${d}/${m}/${y}`
      return String(val)
    } catch {
      return String(val)
    }
  })

  if (appliedDate) {
    return {
      title: `Không có buổi tập nào trong ngày ${fmt(appliedDate)}`,
      subtitle: 'Hiện tại chưa có lịch huấn luyện nào cho ngựa do bạn phụ trách cần chuẩn bị trong ngày này.',
    }
  }
  return {
    title: 'Không có buổi tập nào cần chuẩn bị',
    subtitle: 'Hiện tại chưa có lịch huấn luyện nào cho ngựa do bạn phụ trách cần chuẩn bị.',
  }
}

export function formatScheduleTime(item) {
  if (!item) return '05:00 - 11:00'
  if (item.operatingWindowStart && item.operatingWindowEnd) {
    return `${formatTimeHHmm(item.operatingWindowStart)} - ${formatTimeHHmm(item.operatingWindowEnd)}`
  }
  if (item.startTime && item.endTime) {
    return `${formatTimeHHmm(item.startTime)} - ${formatTimeHHmm(item.endTime)}`
  }
  if (item.scheduledStart && item.scheduledEnd) {
    return formatTimeRange(item.scheduledStart, item.scheduledEnd)
  }
  return '05:00 - 11:00'
}

export function getScheduleDate(schedule) {
  if (!schedule) return null
  const raw = schedule.date || schedule.sessionDate || schedule.trainingDate || schedule.scheduledStart
  if (!raw) return null
  if (typeof raw === 'string') {
    const match = raw.match(/^\d{4}-\d{2}-\d{2}/)
    if (match) return match[0]
  }
  try {
    const d = new Date(raw)
    if (!isNaN(d.getTime())) {
      return d.toISOString().slice(0, 10)
    }
  } catch {
    return null
  }
  return null
}

export function isTrainingScheduleActiveForDay(schedule, targetDate = null) {
  if (!schedule) return false
  const status = (schedule.sessionStatus || schedule.status || '').trim().toUpperCase()
  if (status && status !== 'PREPARING' && status !== 'READY' && status !== 'IN_PROGRESS') {
    return false
  }
  if (status === 'COMPLETED' || status === 'CANCELLED' || status === 'INJURED' || status === 'EMERGENCY_STOPPED') {
    return false
  }
  if (targetDate) {
    const schedDate = getScheduleDate(schedule)
    if (schedDate) {
      const normTarget = typeof targetDate === 'string' && targetDate.length >= 10
        ? targetDate.slice(0, 10)
        : (targetDate instanceof Date ? targetDate.toISOString().slice(0, 10) : null)
      if (normTarget && schedDate !== normTarget) {
        return false
      }
    }
  }
  return true
}

export function computeHorseDailyRoutineState(horse, targetDate = null) {
  const isMedicallyRestricted = Boolean(
    horse && (horse.emergencyActive || horse.healthStatus === 'INJURED' || horse.trainingLocked)
  )
  const scheduleActive = Boolean(
    horse && horse.trainingSchedule && isTrainingScheduleActiveForDay(horse.trainingSchedule, targetDate)
  )
  const isTrainingActive = Boolean(
    horse && !isMedicallyRestricted && (
      scheduleActive || (horse.isTrainingActive && (!horse.trainingSchedule || scheduleActive))
    )
  )

  if (isMedicallyRestricted) {
    return {
      state: 'STATE_C_MEDICAL_RESTRICTION',
      showMedicalAlert: true,
      showTrainingSchedule: false,
      showDefaultRoutine: false,
      canMutateRoutine: false,
      tooltip: 'Không thể thay đổi lịch sinh hoạt khi ngựa đang chấn thương hoặc hạn chế y tế',
    }
  }
  if (isTrainingActive) {
    return {
      state: 'STATE_B_TRAINING_ACTIVE',
      showMedicalAlert: false,
      showTrainingSchedule: true,
      showDefaultRoutine: false,
      canMutateRoutine: false,
      tooltip: 'Ngựa đang trong lịch trình huấn luyện hôm nay. Không thể chỉnh sửa lịch sinh hoạt mặc định.',
    }
  }
  return {
    state: 'STATE_A_NORMAL',
    showMedicalAlert: false,
    showTrainingSchedule: false,
    showDefaultRoutine: true,
    canMutateRoutine: true,
    tooltip: 'Thay đổi lịch sinh hoạt của ngựa',
  }
}
