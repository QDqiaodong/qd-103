import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Questionnaire, Question, QuestionType, ResultVisibility } from '../types'
import * as api from '../services/api'

function generateId(): string {
  return 'q_' + Math.random().toString(36).substring(2) + Date.now().toString(36)
}

export const useQuestionnaireStore = defineStore('questionnaire', () => {
  const questionnaires = ref<Questionnaire[]>([])
  const currentQuestionnaire = ref<Questionnaire | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 获取问卷列表
  async function fetchQuestionnaires() {
    loading.value = true
    error.value = null
    try {
      questionnaires.value = await api.getQuestionnaires()
    } catch (e) {
      error.value = '获取问卷列表失败'
      console.error(e)
    } finally {
      loading.value = false
    }
  }

  // 获取单个问卷
  async function fetchQuestionnaire(id: string) {
    loading.value = true
    error.value = null
    try {
      currentQuestionnaire.value = await api.getQuestionnaire(id)
    } catch (e) {
      error.value = '获取问卷详情失败'
      console.error(e)
    } finally {
      loading.value = false
    }
  }

  // 创建问卷
  async function createQuestionnaire(data: Partial<Questionnaire>): Promise<Questionnaire | null> {
    loading.value = true
    error.value = null
    try {
      const result = await api.createQuestionnaire(data)
      if (result) {
        questionnaires.value.unshift(result)
        if (result.creatorToken) {
          api.saveCreatorToken(result.id, result.creatorToken)
        }
      }
      return result
    } catch (e) {
      error.value = '创建问卷失败'
      console.error(e)
      return null
    } finally {
      loading.value = false
    }
  }

  // 更新问卷
  async function updateQuestionnaire(id: string, data: Partial<Questionnaire>): Promise<Questionnaire | null> {
    loading.value = true
    error.value = null
    try {
      const result = await api.updateQuestionnaire(id, data)
      if (result) {
        const index = questionnaires.value.findIndex(q => q.id === id)
        if (index !== -1) {
          questionnaires.value[index] = result
        }
        if (currentQuestionnaire.value?.id === id) {
          currentQuestionnaire.value = result
        }
      }
      return result
    } catch (e) {
      error.value = '更新问卷失败'
      console.error(e)
      return null
    } finally {
      loading.value = false
    }
  }

  // 删除问卷
  async function removeQuestionnaire(id: string): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const success = await api.deleteQuestionnaire(id)
      if (success) {
        questionnaires.value = questionnaires.value.filter(q => q.id !== id)
        if (currentQuestionnaire.value?.id === id) {
          currentQuestionnaire.value = null
        }
      }
      return success
    } catch (e) {
      error.value = e instanceof Error ? e.message : '删除问卷失败'
      console.error(e)
      return false
    } finally {
      loading.value = false
    }
  }

  // 提交问卷
  async function submitQuestionnaire(
    id: string,
    answers: Array<{ questionId: string; value: string | string[] }>,
    submitDurationSeconds?: number
  ): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const success = await api.submitQuestionnaire(id, {
        respondentId: api.getRespondentId(),
        answers,
        submitDurationSeconds,
        userAgent: navigator.userAgent
      })
      return success
    } catch (e) {
      error.value = e instanceof Error ? e.message : '提交失败，您可能已经提交过'
      console.error(e)
      return false
    } finally {
      loading.value = false
    }
  }

  // 创建新问卷（本地）
  function createNewQuestionnaire(): Questionnaire {
    return {
      id: generateId(),
      title: '',
      description: '',
      status: 'draft',
      resultVisibility: 'INSTANT_PUBLIC',
      createdAt: new Date().toISOString(),
      questions: []
    }
  }

  // 添加题目
  function addQuestion(type: QuestionType, content = '', options: string[] = []): Question {
    const question: Question = {
      id: generateId(),
      type,
      content,
      orderIndex: currentQuestionnaire.value?.questions.length || 0,
      required: true
    }
    if (type !== 'text') {
      question.options = options.map((opt, idx) => ({
        id: generateId(),
        content: opt,
        orderIndex: idx
      }))
    }
    return question
  }

  // 更新本地问卷
  function updateLocalQuestionnaire(data: Partial<Questionnaire>) {
    if (currentQuestionnaire.value) {
      Object.assign(currentQuestionnaire.value, data)
    }
  }

  return {
    questionnaires,
    currentQuestionnaire,
    loading,
    error,
    fetchQuestionnaires,
    fetchQuestionnaire,
    createQuestionnaire,
    updateQuestionnaire,
    removeQuestionnaire,
    submitQuestionnaire,
    createNewQuestionnaire,
    addQuestion,
    updateLocalQuestionnaire
  }
})
