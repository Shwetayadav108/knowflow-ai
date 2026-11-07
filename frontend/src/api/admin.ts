import { apiClient } from './client';
import { AdminSystemStatsDto, AuditLogDto, GlobalRole, PageResponse, UserDto, UserStatus, WorkspaceDto } from '../types';

export const adminApi = {
  getStats: (): Promise<AdminSystemStatsDto> => {
    return apiClient.get('/admin/stats');
  },

  getSystemStats: (): Promise<AdminSystemStatsDto> => {
    return apiClient.get('/admin/stats');
  },

  getUsers: (): Promise<UserDto[]> => {
    return apiClient.get('/admin/users');
  },

  updateUserRole: (userId: string, role: GlobalRole): Promise<UserDto> => {
    return apiClient.patch(`/admin/users/${userId}/role`, { role });
  },

  updateUserStatus: (userId: string, status: UserStatus): Promise<UserDto> => {
    return apiClient.patch(`/admin/users/${userId}/status`, { status });
  },

  getAllWorkspaces: (): Promise<WorkspaceDto[]> => {
    return apiClient.get('/admin/workspaces');
  },

  getAuditLogs: (params?: { page?: number; size?: number }): Promise<PageResponse<AuditLogDto>> => {
    return apiClient.get('/admin/audit-logs', { params });
  },
};
