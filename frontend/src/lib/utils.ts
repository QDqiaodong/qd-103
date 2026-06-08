import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import type { Questionnaire, Question, ShowCondition } from '../types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export interface TerminateInfo {
  terminated: boolean
  message: string
}

export function parseShowCondition(conditionStr: string | null | undefined): ShowCondition | null {
  if (!conditionStr) return null
  try {
    return JSON.parse(conditionStr)
  } catch {
    return null
  }
}

export function isQuestionVisible(
  question: Question,
  questions: Question[],
  answers: Record<string, string | string[]>
): boolean {
  const condition = parseShowCondition(question.showCondition)
  if (!condition) return true

  const dependOnQuestion = questions.find(q => q.id === condition.dependOnQuestionId)
  if (!dependOnQuestion) return true

  const answer = answers[condition.dependOnQuestionId]
  if (!answer) return false

  if (Array.isArray(answer)) {
    return answer.some(a => condition.optionIds.includes(a))
  } else {
    return condition.optionIds.includes(answer)
  }
}

export function getVisibleQuestions(
  questions: Question[],
  answers: Record<string, string | string[]>
): Question[] {
  return questions.filter(q => isQuestionVisible(q, questions, answers))
}

export function getTerminateInfo(
  questions: Question[],
  answers: Record<string, string | string[]>
): TerminateInfo {
  for (const question of questions) {
    if (question.type === 'text') continue
    if (!question.options?.length) continue

    const answer = answers[question.id]
    if (!answer) continue

    const selectedOptionIds = Array.isArray(answer) ? answer : [answer]

    for (const optionId of selectedOptionIds) {
      const option = question.options.find(o => o.id === optionId)
      if (option?.terminateSurvey) {
        return {
          terminated: true,
          message: option.terminateMessage || ''
        }
      }
    }
  }

  return { terminated: false, message: '' }
}

export function getQuestionVisibleIndex(
  questionId: string,
  questions: Question[],
  answers: Record<string, string | string[]>
): number {
  const visible = getVisibleQuestions(questions, answers)
  return visible.findIndex(q => q.id === questionId)
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
