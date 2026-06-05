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

function isSuccess<T>(response: { data: ApiResponse<T> }): boolean {
  return response.data.code === 200
}

function getErrorMessage<T>(response: { data: ApiResponse<T> }, defaultMsg: string): string {
  return response.data.message || defaultMsg
}

// 问卷列表
export async function getQuestionnaires(): Promise<Questionnaire[]> {
  const response = await api.get<ApiResponse<Questionnaire[]>>('/questionnaires')
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取问卷列表失败'))
  }
  return response.data.data || []
}

// 获取问卷详情
export async function getQuestionnaire(id: string): Promise<Questionnaire | null> {
  const response = await api.get<ApiResponse<Questionnaire>>(`/questionnaires/${id}`)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取问卷详情失败'))
  }
  return response.data.data || null
}

// 创建问卷
export async function createQuestionnaire(data: Partial<Questionnaire>): Promise<Questionnaire | null> {
  const response = await api.post<ApiResponse<Questionnaire>>('/questionnaires', data)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '创建问卷失败'))
  }
  return response.data.data || null
}

// 更新问卷
export async function updateQuestionnaire(id: string, data: Partial<Questionnaire>): Promise<Questionnaire | null> {
  const response = await api.put<ApiResponse<Questionnaire>>(`/questionnaires/${id}`, data)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '更新问卷失败'))
  }
  return response.data.data || null
}

// 删除问卷
export async function deleteQuestionnaire(id: string): Promise<boolean> {
  const response = await api.delete<ApiResponse<boolean>>(`/questionnaires/${id}`)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '删除问卷失败'))
  }
  return true
}

// 提交问卷
export async function submitQuestionnaire(id: string, data: SubmitRequest): Promise<boolean> {
  const response = await api.post<ApiResponse<boolean>>(`/questionnaires/${id}/submit`, data)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '提交失败'))
  }
  return true
}

// 获取统计数据
export async function getStatistics(id: string): Promise<StatisticsResponse | null> {
  const response = await api.get<ApiResponse<StatisticsResponse>>(`/questionnaires/${id}/statistics`)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取统计数据失败'))
  }
  return response.data.data || null
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
