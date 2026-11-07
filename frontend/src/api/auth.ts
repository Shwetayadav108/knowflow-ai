import { apiClient } from './client';
import { AuthResponse, UserDto } from '../types';

export interface LoginParams {
  email: string;
  password: string;
}

export interface RegisterParams {
  fullName: string;
  email: string;
  password: string;
  inviteToken?: string;
}

export interface ResetPasswordParams {
  email: string;
  code: string;
  newPassword: string;
}

export const authApi = {
  login: (data: LoginParams): Promise<AuthResponse> => {
    return apiClient.post('/auth/login', data);
  },

  register: (data: RegisterParams): Promise<AuthResponse> => {
    return apiClient.post('/auth/register', data);
  },

  getMe: (): Promise<UserDto> => {
    return apiClient.get('/auth/me');
  },

  completeOnboarding: (): Promise<UserDto> => {
    return apiClient.patch('/auth/onboarding-complete');
  },

  forgotPassword: (email: string): Promise<string> => {
    return apiClient.post('/auth/forgot-password', { email });
  },

  resetPassword: (data: ResetPasswordParams): Promise<string> => {
    return apiClient.post('/auth/reset-password', data);
  },
};
