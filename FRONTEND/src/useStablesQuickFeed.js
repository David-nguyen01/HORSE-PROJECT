import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { stableCareApi } from './api'

export function useStablesQuickFeed({
  targetDate,
  isStaff,
  isVet = false,
  enabled = true,
  refreshIntervalMs = 60000,
  onConfirmed = null,
}) {
  // Operational Quick-Feed is strictly for Stable Staff and excluded from Veterinarians
  const isOperationalStaff = Boolean(isStaff && !isVet)
  const isHookActive = Boolean(enabled && isOperationalStaff)

  const [quickFeedData, setQuickFeedData] = useState(null)
  const [quickFeedLoading, setQuickFeedLoading] = useState(isHookActive)
  const [quickFeedRefreshing, setQuickFeedRefreshing] = useState(false)
  const [quickFeedError, setQuickFeedError] = useState('')
  const [confirmingMealKey, setConfirmingMealKey] = useState(null)
  const [confirmError, setConfirmError] = useState('')

  const requestIdRef = useRef(0)
  const isFetchingRef = useRef(false)

  const fetchQuickFeed = useCallback(async (isBackground = false) => {
    if (!isHookActive || !targetDate) return
    if (isFetchingRef.current) return // Prevent overlapping requests

    isFetchingRef.current = true
    const currentRequestId = ++requestIdRef.current

    if (isBackground) {
      setQuickFeedRefreshing(true)
    } else {
      setQuickFeedLoading(true)
      setQuickFeedError('')
    }

    try {
      const data = await stableCareApi.getStablesQuickFeed(targetDate)

      // Guard against out-of-order stale response
      if (currentRequestId !== requestIdRef.current) return

      setQuickFeedData(Array.isArray(data) ? data : [])
      setQuickFeedError('')
    } catch (err) {
      if (currentRequestId !== requestIdRef.current) return
      // API Failure: do NOT overwrite existing data with empty array
      setQuickFeedError(err.message || 'Không thể tải dữ liệu cho ăn trong ca')
    } finally {
      if (currentRequestId === requestIdRef.current) {
        setQuickFeedLoading(false)
        setQuickFeedRefreshing(false)
      }
      isFetchingRef.current = false
    }
  }, [isHookActive, targetDate])

  // Fetch when targetDate changes or isHookActive changes
  useEffect(() => {
    if (!isHookActive || !targetDate) return
    fetchQuickFeed(false)
  }, [fetchQuickFeed, isHookActive, targetDate])

  // Periodic refresh (around 60s)
  useEffect(() => {
    if (!isHookActive || refreshIntervalMs <= 0) return

    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        return // skip when tab is hidden
      }
      fetchQuickFeed(true)
    }, refreshIntervalMs)

    return () => {
      clearInterval(timer)
    }
  }, [isHookActive, refreshIntervalMs, fetchQuickFeed])

  // Lookup map by stallId
  const quickFeedByStallId = useMemo(() => {
    const map = new Map()
    if (Array.isArray(quickFeedData)) {
      quickFeedData.forEach((item) => {
        if (item && item.stallId != null) {
          map.set(item.stallId, item)
        }
      })
    }
    return map
  }, [quickFeedData])

  // 1-click Quick Feed confirm handler
  const handleQuickConfirmMeal = useCallback(async (stall, meal) => {
    if (!isStaff || isVet) return
    if (!stall?.horseId || !meal) return
    const key = meal.mealId || meal.mealKey || `${targetDate}-${stall.horseId}-${meal.mealName}`

    if (confirmingMealKey === key || meal.confirmed || meal.status === 'CONFIRMED') return

    setConfirmingMealKey(key)
    setConfirmError('')

    const payload = {
      date: targetDate,
      mealKey: meal.mealId || meal.mealKey || undefined,
      mealName: meal.mealName,
      feedingTime: meal.feedingTime,
    }

    try {
      const savedLog = await stableCareApi.confirmDailyFeedingMeal(stall.horseId, payload)

      // Immediately update local quickFeedData
      setQuickFeedData((prev) => {
        if (!Array.isArray(prev)) return prev
        return prev.map((item) => {
          if (item.stallId !== stall.stallId) return item
          return {
            ...item,
            meals: (item.meals || []).map((m) => {
              const isMatch = (m.mealId && (m.mealId === meal.mealId || m.mealId === savedLog?.mealKey)) ||
                              (m.mealName === meal.mealName && m.feedingTime === meal.feedingTime)
              if (isMatch) {
                return {
                  ...m,
                  confirmed: true,
                  status: 'CONFIRMED',
                  confirmedAt: savedLog?.confirmedAt || new Date().toISOString(),
                  confirmedByName: savedLog?.confirmedByName || 'Nhân viên chuồng',
                }
              }
              return m
            }),
          }
        })
      })

      if (onConfirmed && savedLog) {
        onConfirmed(savedLog, stall, meal)
      }
      return savedLog
    } catch (err) {
      setConfirmError(err.message || 'Không thể xác nhận cho ăn. Vui lòng thử lại.')
      throw err
    } finally {
      setConfirmingMealKey(null)
    }
  }, [isStaff, isVet, targetDate, confirmingMealKey, onConfirmed])

  // Synchronize a confirmed meal from an external view (e.g. detailed checklist)
  const syncConfirmedMeal = useCallback((stallId, mealKeyOrName, feedingTime, savedLog) => {
    setQuickFeedData((prev) => {
      if (!Array.isArray(prev)) return prev
      return prev.map((item) => {
        if (item.stallId !== stallId) return item
        return {
          ...item,
          meals: (item.meals || []).map((m) => {
            const isMatch = (m.mealId && (m.mealId === mealKeyOrName || m.mealId === savedLog?.mealKey)) ||
                            (m.mealName === mealKeyOrName && m.feedingTime === feedingTime)
            if (isMatch) {
              return {
                ...m,
                confirmed: true,
                status: 'CONFIRMED',
                confirmedAt: savedLog?.confirmedAt || new Date().toISOString(),
                confirmedByName: savedLog?.confirmedByName || 'Nhân viên chuồng',
              }
            }
            return m
          }),
        }
      })
    })
  }, [])

  return {
    quickFeedData: quickFeedData || [],
    rawQuickFeedData: quickFeedData,
    quickFeedByStallId,
    quickFeedLoading,
    quickFeedRefreshing,
    quickFeedError,
    confirmingMealKey,
    confirmError,
    setConfirmError,
    handleQuickConfirmMeal,
    syncConfirmedMeal,
    reloadQuickFeed: () => (isHookActive ? fetchQuickFeed(false) : Promise.resolve()),
  }
}
