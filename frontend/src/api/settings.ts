import api from '@/lib/axios'
import type { PointConfig, GlobalSettings } from '@/types'

export const getPointConfigs = async (): Promise<PointConfig[]> => {
  const { data } = await api.get('/settings/points')
  return data
}

export const updatePointConfigs = async (configs: PointConfig[]): Promise<PointConfig[]> => {
  const { data } = await api.put('/settings/points', configs)
  return data
}

export const getGlobalSettings = async (): Promise<GlobalSettings> => {
  const { data } = await api.get('/settings/general')
  return data
}

export const updateGlobalSettings = async (settings: GlobalSettings): Promise<GlobalSettings> => {
  const { data } = await api.put('/settings/general', settings)
  return data
}

export const resetPlayerPoints = async (): Promise<{ reset: number }> => {
  const { data } = await api.post('/settings/points/reset')
  return data
}
