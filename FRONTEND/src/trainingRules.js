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

