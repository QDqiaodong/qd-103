// 问卷相关类型定义

export type QuestionType = 'single' | 'multiple' | 'text'

export type QuestionnaireStatus = 'draft' | 'active' | 'closed' | 'expired'

export interface QuestionOption {
  id: string
  content: string
  orderIndex: number
}

export interface Question {
  id: string
  questionnaireId?: string
  type: QuestionType
  content: string
  orderIndex: number
  options?: QuestionOption[]
  required?: boolean
}

export interface Questionnaire {
  id: string
  title: string
  description: string
  deadline?: string
  status: QuestionnaireStatus
  createdAt: string
  questions: Question[]
  responseCount?: number
  coverConfig?: CoverConfig
}

export interface Answer {
  questionId: string
  value: string | string[]
}

export interface SubmitRequest {
  respondentId: string
  answers: Answer[]
  submitDurationSeconds?: number
  userAgent?: string
}

export interface Fingerprint {
  id: string
  questionnaireId: string
  respondentId: string
  responseId: string
  fingerprintHash: string
  submittedAt: string
  submitDurationSeconds: number
  answerCount: number
  riskLevel: 'normal' | 'suspicious' | 'high_risk'
  riskReasons: string
  isDuplicateFingerprint: boolean
  duplicateCount: number
  isHighFrequency: boolean
  frequencyWindowMinutes: number
  frequencyCount: number
  isAnomalyCluster: boolean
  clusterSize: number
}

export interface FingerprintStatistics {
  totalFingerprints: number
  distinctFingerprints: number
  riskDistribution: Record<string, number>
  duplicateFingerprintGroups: number
  totalDuplicateSubmissions: number
  dailyTrend: Array<{ date: string; count: number }>
}

export interface OptionStatistic {
  content: string
  count: number
  percentage: number
}

export interface QuestionStatistic {
  questionId: string
  type: QuestionType
  content: string
  statistics: Record<string, number>
  totalResponses: number
  textAnswers?: string[]
}

export interface StatisticsResponse {
  totalResponses: number
  questions: QuestionStatistic[]
}

export type CoverLayout = 'centered' | 'left-aligned' | 'split' | 'hero'
export type CoverTheme = 'professional' | 'campus' | 'activity' | 'enterprise' | 'minimal'

export interface CoverConfig {
  layout: CoverLayout
  theme: CoverTheme
  headerImage: string
  openingText: string
  titlePosition: 'top' | 'middle' | 'bottom'
  showDescription: boolean
  accentColor: string
  backgroundStyle: 'solid' | 'gradient' | 'image'
  backgroundColor: string
  gradientStart: string
  gradientEnd: string
}

export const DEFAULT_COVER_CONFIG: CoverConfig = {
  layout: 'centered',
  theme: 'professional',
  headerImage: '',
  openingText: '',
  titlePosition: 'middle',
  showDescription: true,
  accentColor: '#4F46E5',
  backgroundStyle: 'solid',
  backgroundColor: '#FFFFFF',
  gradientStart: '#4F46E5',
  gradientEnd: '#7C3AED'
}

export const COVER_THEMES: Record<CoverTheme, { label: string; accentColor: string; backgroundStyle: 'solid' | 'gradient'; gradientStart: string; gradientEnd: string; backgroundColor: string; layout: CoverLayout }> = {
  professional: { label: '专业调研', accentColor: '#4F46E5', backgroundStyle: 'solid', gradientStart: '#4F46E5', gradientEnd: '#7C3AED', backgroundColor: '#FFFFFF', layout: 'centered' },
  campus: { label: '校园征集', accentColor: '#059669', backgroundStyle: 'gradient', gradientStart: '#065F46', gradientEnd: '#10B981', backgroundColor: '#ECFDF5', layout: 'left-aligned' },
  activity: { label: '活动投票', accentColor: '#DC2626', backgroundStyle: 'gradient', gradientStart: '#F59E0B', gradientEnd: '#EF4444', backgroundColor: '#FFF7ED', layout: 'hero' },
  enterprise: { label: '企业内调', accentColor: '#1E40AF', backgroundStyle: 'solid', gradientStart: '#1E3A5F', gradientEnd: '#2563EB', backgroundColor: '#F0F4FF', layout: 'split' },
  minimal: { label: '极简风格', accentColor: '#374151', backgroundStyle: 'solid', gradientStart: '#F3F4F6', gradientEnd: '#E5E7EB', backgroundColor: '#FAFAFA', layout: 'centered' }
}

export const COVER_LAYOUTS: Record<CoverLayout, { label: string; description: string }> = {
  centered: { label: '居中布局', description: '标题居中，经典对称' },
  'left-aligned': { label: '左对齐布局', description: '标题左对齐，适合长文本' },
  split: { label: '分栏布局', description: '图文分栏，信息层次分明' },
  hero: { label: '头图布局', description: '大图头图，视觉冲击力强' }
}

export interface ApiResponse<T> {
  code: number
  data?: T
  message?: string
}
