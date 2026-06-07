import axios from 'axios'
import type {
  ApiResponse,
  Questionnaire,
  StatisticsResponse,
  SubmitRequest,
  Fingerprint,
  FingerprintStatistics,
  Snapshot,
  SnapshotDetail
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
export async function getQuestionnaire(id: string, viewerToken?: string): Promise<Questionnaire | null> {
  const params = viewerToken ? { viewerToken } : {}
  const response = await api.get<ApiResponse<Questionnaire>>(`/questionnaires/${id}`, { params })
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
export async function getStatistics(id: string, viewerToken?: string): Promise<StatisticsResponse | null> {
  const params = viewerToken ? { viewerToken } : {}
  const response = await api.get<ApiResponse<StatisticsResponse>>(`/questionnaires/${id}/statistics`, { params })
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取统计数据失败'))
  }
  return response.data.data || null
}

// 保存问卷创建者令牌
export function saveCreatorToken(questionnaireId: string, token: string): void {
  const key = 'survey_creator_tokens'
  try {
    const tokens = JSON.parse(localStorage.getItem(key) || '{}')
    tokens[questionnaireId] = token
    localStorage.setItem(key, JSON.stringify(tokens))
  } catch (e) {
    console.error('保存创建者令牌失败', e)
  }
}

// 获取问卷创建者令牌
export function getCreatorToken(questionnaireId: string): string | null {
  const key = 'survey_creator_tokens'
  try {
    const tokens = JSON.parse(localStorage.getItem(key) || '{}')
    return tokens[questionnaireId] || null
  } catch (e) {
    return null
  }
}

// 检查是否是问卷创建者
export function isCreator(questionnaireId: string): boolean {
  return getCreatorToken(questionnaireId) !== null
}

// 获取指纹档案列表
export async function getFingerprints(id: string, viewerToken?: string): Promise<Fingerprint[]> {
  const params = viewerToken ? { viewerToken } : {}
  const response = await api.get<ApiResponse<Fingerprint[]>>(`/questionnaires/${id}/fingerprints`, { params })
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取指纹档案失败'))
  }
  return response.data.data || []
}

// 获取风险指纹列表
export async function getRiskyFingerprints(id: string, viewerToken?: string): Promise<Fingerprint[]> {
  const params = viewerToken ? { viewerToken } : {}
  const response = await api.get<ApiResponse<Fingerprint[]>>(`/questionnaires/${id}/fingerprints/risky`, { params })
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取风险指纹失败'))
  }
  return response.data.data || []
}

// 获取指纹统计数据
export async function getFingerprintStatistics(id: string, viewerToken?: string): Promise<FingerprintStatistics | null> {
  const params = viewerToken ? { viewerToken } : {}
  const response = await api.get<ApiResponse<FingerprintStatistics>>(`/questionnaires/${id}/fingerprints/statistics`, { params })
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取指纹统计失败'))
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

// 获取问卷的快照列表
export async function getSnapshots(questionnaireId: string): Promise<Snapshot[]> {
  const response = await api.get<ApiResponse<Snapshot[]>>(`/snapshots/questionnaire/${questionnaireId}`)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取快照列表失败'))
  }
  return response.data.data || []
}

// 获取快照详情
export async function getSnapshot(id: string): Promise<SnapshotDetail | null> {
  const response = await api.get<ApiResponse<SnapshotDetail>>(`/snapshots/${id}`)
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '获取快照详情失败'))
  }
  return response.data.data || null
}

// 创建快照
export async function createSnapshot(questionnaireId: string, reason?: string): Promise<Snapshot | null> {
  const params = reason ? { reason } : {}
  const response = await api.post<ApiResponse<Snapshot>>(`/snapshots/questionnaire/${questionnaireId}`, null, { params })
  if (!isSuccess(response)) {
    throw new Error(getErrorMessage(response, '创建快照失败'))
  }
  return response.data.data || null
}

export default api
