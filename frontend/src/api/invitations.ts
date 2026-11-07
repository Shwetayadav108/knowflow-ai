import { apiClient } from './client';
import { WorkspaceInvitationDto, WorkspaceRole } from '../types';

export interface CreateInvitationParams {
  email: string;
  role: WorkspaceRole;
}

export const invitationsApi = {
  createInvitation: (workspaceId: string, data: CreateInvitationParams): Promise<WorkspaceInvitationDto> => {
    return apiClient.post(`/workspaces/${workspaceId}/invitations`, data);
  },

  getWorkspaceInvitations: (workspaceId: string): Promise<WorkspaceInvitationDto[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/invitations`);
  },

  revokeInvitation: (workspaceId: string, invitationId: string): Promise<void> => {
    return apiClient.delete(`/workspaces/${workspaceId}/invitations/${invitationId}`);
  },

  validateToken: (token: string): Promise<WorkspaceInvitationDto> => {
    return apiClient.get(`/invitations/${token}`);
  },

  acceptInvitation: (token: string): Promise<void> => {
    return apiClient.post(`/invitations/${token}/accept`, {});
  },
};
