import { apiClient } from './client';
import { DashboardMetricsDto } from '../types';

export const analyticsApi = {
  getWorkspaceMetrics: (workspaceId: string): Promise<DashboardMetricsDto> => {
    return apiClient.get(`/workspaces/${workspaceId}/analytics`);
  },
};
