import axios from 'axios'
import type {
  ApiResponse,
  Questionnaire,
  StatisticsResponse,
  SubmitRequest
} from '../types'

const BASE_URL = '/api'

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 问卷列表
export async function getQuestionnaires(): Promise<Questionnaire[]> {
  const response = await api.get<ApiResponse<Questionnaire[]>>('/questionnaires')
  return response.data.data || []
}

// 获取问卷详情
export async function getQuestionnaire(id: string): Promise<Questionnaire | null> {
  try {
    const response = await api.get<ApiResponse<Questionnaire>>(`/questionnaires/${id}`)
    return response.data.data || null
  } catch {
    return null
  }
}

// 创建问卷
export async function createQuestionnaire(data: Partial<Questionnaire>): Promise<Questionnaire | null> {
  const response = await api.post<ApiResponse<Questionnaire>>('/questionnaires', data)
  return response.data.data || null
}

// 更新问卷
export async function updateQuestionnaire(id: string, data: Partial<Questionnaire>): Promise<Questionnaire | null> {
  const response = await api.put<ApiResponse<Questionnaire>>(`/questionnaires/${id}`, data)
  return response.data.data || null
}

// 删除问卷
export async function deleteQuestionnaire(id: string): Promise<boolean> {
  try {
    await api.delete(`/questionnaires/${id}`)
    return true
  } catch {
    return false
  }
}

// 提交问卷
export async function submitQuestionnaire(id: string, data: SubmitRequest): Promise<boolean> {
  try {
    await api.post(`/questionnaires/${id}/submit`, data)
    return true
  } catch {
    return false
  }
}

// 获取统计数据
export async function getStatistics(id: string): Promise<StatisticsResponse | null> {
  try {
    const response = await api.get<ApiResponse<StatisticsResponse>>(`/questionnaires/${id}/statistics`)
    return response.data.data || null
  } catch {
    return null
  }
}

// 获取访问者ID（基于localStorage）
export function getRespondentId(): string {
  const key = 'survey_respondent_id'
  let id = localStorage.getItem(key)
  if (!id) {
    id = 'r_' + Math.random().toString(36).substring(2) + Date.now().toString(36)
    localStorage.setItem(key, id)
  }
  return id
}

export default api
