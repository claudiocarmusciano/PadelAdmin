import api from '@/lib/axios'
import type { Complex, Court } from '@/types'

export const getComplexes = async (): Promise<Complex[]> => {
  const { data } = await api.get('/complexes')
  return data
}

export const createComplex = async (dto: Omit<Complex, 'id'>): Promise<Complex> => {
  const { data } = await api.post('/complexes', dto)
  return data
}

export const updateComplex = async (id: number, dto: Omit<Complex, 'id'>): Promise<Complex> => {
  const { data } = await api.put(`/complexes/${id}`, dto)
  return data
}

export const deleteComplex = async (id: number): Promise<void> => {
  await api.delete(`/complexes/${id}`)
}

// Courts
export const getCourts = async (complexId: number): Promise<Court[]> => {
  const { data } = await api.get(`/complexes/${complexId}/courts`)
  return data
}

export const createCourt = async (complexId: number, dto: { name: string }): Promise<Court> => {
  const { data } = await api.post(`/complexes/${complexId}/courts`, dto)
  return data
}

export const updateCourt = async (
  complexId: number,
  courtId: number,
  dto: { name: string; active: boolean }
): Promise<Court> => {
  const { data } = await api.put(`/complexes/${complexId}/courts/${courtId}`, dto)
  return data
}

export const deleteCourt = async (complexId: number, courtId: number): Promise<void> => {
  await api.delete(`/complexes/${complexId}/courts/${courtId}`)
}
