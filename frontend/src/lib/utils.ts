import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import type { Questionnaire } from '../types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function getDeadlineInfo(q: Questionnaire): { daysLeft: number | null; hoursLeft: number | null; urgency: 'critical' | 'warning' | 'normal' | 'none'; label: string } {
  if (!q.deadline || q.status !== 'active') {
    return { daysLeft: null, hoursLeft: null, urgency: 'none', label: '' }
  }
  const deadline = new Date(q.deadline).getTime()
  const now = Date.now()
  const diffMs = deadline - now
  const daysLeft = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  const hoursLeft = Math.ceil(diffMs / (1000 * 60 * 60))

  if (diffMs <= 0) {
    return { daysLeft: 0, hoursLeft: 0, urgency: 'critical', label: '已截止' }
  }
  if (daysLeft <= 1) {
    const hours = Math.max(1, hoursLeft)
    return { daysLeft, hoursLeft, urgency: 'critical', label: `剩 ${hours} 小时` }
  }
  if (daysLeft <= 2) {
    return { daysLeft, hoursLeft, urgency: 'critical', label: `剩 ${daysLeft} 天` }
  }
  if (daysLeft <= 7) {
    return { daysLeft, hoursLeft, urgency: 'warning', label: `剩 ${daysLeft} 天` }
  }
  return { daysLeft, hoursLeft, urgency: 'normal', label: `剩 ${daysLeft} 天` }
}

export function getHeatLevel(q: Questionnaire): { level: number; label: string; color: string } {
  const count = q.responseCount || 0
  const createdAt = new Date(q.createdAt).getTime()
  const now = Date.now()
  const daysActive = Math.max(1, (now - createdAt) / (1000 * 60 * 60 * 24))
  const avgPerDay = count / daysActive

  if (avgPerDay >= 20) return { level: 5, label: '爆热', color: '#EF4444' }
  if (avgPerDay >= 10) return { level: 4, label: '火热', color: '#F97316' }
  if (avgPerDay >= 5) return { level: 3, label: '活跃', color: '#F59E0B' }
  if (avgPerDay >= 2) return { level: 2, label: '平稳', color: '#10B981' }
  return { level: 1, label: '冷清', color: '#9CA3AF' }
}

export function isDeadlineRisk(q: Questionnaire): boolean {
  const deadlineInfo = getDeadlineInfo(q)
  const heat = getHeatLevel(q)
  return deadlineInfo.urgency === 'critical' && heat.level <= 2
}

export function getDeadlineRiskInfo(q: Questionnaire): { hasRisk: boolean; message: string; tips: string } {
  if (!isDeadlineRisk(q)) {
    return { hasRisk: false, message: '', tips: '' }
  }
  const deadlineInfo = getDeadlineInfo(q)
  const count = q.responseCount || 0
  const timeLabel = deadlineInfo.hoursLeft && deadlineInfo.hoursLeft <= 24
    ? `${deadlineInfo.hoursLeft} 小时`
    : `${deadlineInfo.daysLeft} 天`
  
  return {
    hasRisk: true,
    message: `距截止仅剩 ${timeLabel}，目前仅 ${count} 人填写`,
    tips: '建议尽快分享推广，提高回收量'
  }
}
