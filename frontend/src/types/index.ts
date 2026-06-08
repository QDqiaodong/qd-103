// 问卷相关类型定义

export type QuestionType = 'single' | 'multiple' | 'text'

export type QuestionnaireStatus = 'draft' | 'active' | 'closed' | 'expired'

export type ResultVisibility = 'INSTANT_PUBLIC' | 'AFTER_DEADLINE' | 'PRIVATE'

export const RESULT_VISIBILITY_OPTIONS: Array<{
  value: ResultVisibility
  label: string
  description: string
  icon: string
}> = [
  {
    value: 'INSTANT_PUBLIC',
    label: '即时公开',
    description: '任何人随时可以查看统计结果',
    icon: '🌐'
  },
  {
    value: 'AFTER_DEADLINE',
    label: '截止后公开',
    description: '问卷截止后才能查看统计结果，避免投票中途被实时票数带偏',
    icon: '⏰'
  },
  {
    value: 'PRIVATE',
    label: '仅自己可见',
    description: '只有创建者本人才能查看统计结果',
    icon: '🔒'
  }
]

export interface QuestionOption {
  id: string
  content: string
  orderIndex: number
  terminateSurvey?: boolean
  terminateMessage?: string
}

export interface ShowCondition {
  dependOnQuestionId: string
  optionIds: string[]
}

export interface Question {
  id: string
  questionnaireId?: string
  type: QuestionType
  content: string
  orderIndex: number
  options?: QuestionOption[]
  required?: boolean
  showCondition?: string | null
}

export interface Questionnaire {
  id: string
  title: string
  description: string
  deadline?: string
  status: QuestionnaireStatus
  resultVisibility: ResultVisibility
  creatorToken?: string
  createdAt: string
  questions: Question[]
  responseCount?: number
  coverConfig?: CoverConfig
  passwordProtected?: boolean
  accessPassword?: string
  maxResponses?: number
  closedMessage?: string
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
  accessPassword?: string
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

export interface DedupedTextAnswer {
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
  dedupedTextAnswers?: DedupedTextAnswer[]
  distinctTextAnswerCount?: number
}

export interface StatisticsResponse {
  resultsVisible?: boolean
  visibilityMessage?: string
  resultVisibility?: ResultVisibility
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

export interface Snapshot {
  id: string
  questionnaireId: string
  title: string
  description: string
  deadline?: string
  status: QuestionnaireStatus
  originalStatus: QuestionnaireStatus
  snapshotReason: string
  createdAt: string
  snapshotAt: string
  responseCount: number
  questionCount: number
  coverConfig?: CoverConfig
  maxResponses?: number
  closedMessage?: string
}

export interface SnapshotDetail extends Snapshot {
  questions: Question[]
  statistics: StatisticsResponse
}

export interface DraftAnswer {
  questionId: string
  value: string | string[]
}

export interface DraftData {
  questionnaireId: string
  respondentId: string
  answers: DraftAnswer[]
  lastQuestionId: string
  elapsedSeconds: number
  updatedAt: string
}

export interface ApiResponse<T> {
  code: number
  data?: T
  message?: string
}
