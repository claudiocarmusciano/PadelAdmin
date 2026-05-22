import api from '@/lib/axios'
import type { Category } from '@/types'

export const getCategories = async (): Promise<Category[]> => {
  const { data } = await api.get('/categories')
  return data
}

export const getCategory = async (id: number): Promise<Category> => {
  const { data } = await api.get(`/categories/${id}`)
  return data
}

export const createCategory = async (dto: Omit<Category, 'id'>): Promise<Category> => {
  const { data } = await api.post('/categories', dto)
  return data
}

export const updateCategory = async (id: number, dto: Omit<Category, 'id'>): Promise<Category> => {
  const { data } = await api.put(`/categories/${id}`, dto)
  return data
}

export const deleteCategory = async (id: number): Promise<void> => {
  await api.delete(`/categories/${id}`)
}
