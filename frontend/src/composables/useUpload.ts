import { ref } from 'vue'
import * as uploadApi from '@/api/upload'
import type { AuthUser } from '@/types'

const loadingVisible = ref(false)
const loadingText = ref('智能运维分析中，请稍候...')
const loadingSubtext = ref('后端正在处理，请耐心等待')

const allowedExtensions = ['.txt', '.md', '.markdown']
const maxFileSize = 50 * 1024 * 1024

export function useUpload() {
  async function upload(file: File, currentUser: AuthUser | null) {
    validate(file, currentUser)
    loadingVisible.value = true
    loadingText.value = '正在上传文件...'
    loadingSubtext.value = `上传: ${file.name}`
    try {
      await uploadApi.uploadDocument(file)
      return `${file.name} 上传到知识库成功`
    } finally {
      loadingVisible.value = false
      loadingText.value = '智能运维分析中，请稍候...'
      loadingSubtext.value = '后端正在处理，请耐心等待'
    }
  }

  return {
    loadingVisible,
    loadingText,
    loadingSubtext,
    upload,
  }
}

function validate(file: File, currentUser: AuthUser | null) {
  const fileName = file.name.toLowerCase()
  if (!allowedExtensions.some((extension) => fileName.endsWith(extension))) {
    throw new Error('只支持上传 TXT 或 Markdown (.md) 格式的文件')
  }
  if (file.size > maxFileSize) {
    throw new Error('文件大小不能超过50MB')
  }
  if (!currentUser) {
    throw new Error('请先登录')
  }
  if (String(currentUser.role || '').toUpperCase() !== 'ADMIN') {
    throw new Error('仅管理员可以上传文档')
  }
}
