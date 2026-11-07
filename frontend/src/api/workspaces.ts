import { apiClient } from './client';
import { WorkspaceDto, WorkspaceMemberDto } from '../types';

export interface CreateWorkspaceParams {
  name: string;
  description?: string;
}

export interface UpdateWorkspaceParams {
  name: string;
  description?: string;
}

export const workspaceApi = {
  getWorkspaces: (): Promise<WorkspaceDto[]> => {
    return apiClient.get('/workspaces');
  },

  createWorkspace: (data: CreateWorkspaceParams): Promise<WorkspaceDto> => {
    return apiClient.post('/workspaces', data);
  },

  getWorkspace: (id: string): Promise<WorkspaceDto> => {
    return apiClient.get(`/workspaces/${id}`);
  },

  updateWorkspace: (id: string, data: UpdateWorkspaceParams): Promise<WorkspaceDto> => {
    return apiClient.put(`/workspaces/${id}`, data);
  },

  deleteWorkspace: (id: string): Promise<void> => {
    return apiClient.delete(`/workspaces/${id}`);
  },

  getMembers: (workspaceId: string): Promise<WorkspaceMemberDto[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/members`);
  },

  removeMember: (workspaceId: string, memberId: string): Promise<void> => {
    return apiClient.delete(`/workspaces/${workspaceId}/members/${memberId}`);
  },
};
