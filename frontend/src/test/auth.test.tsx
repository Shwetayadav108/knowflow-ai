import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { LoginPage } from '../pages/auth/LoginPage';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/useAuthStore';

// Mock authApi
vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
  },
}));

describe('Auth Flow - LoginPage', () => {
  it('renders login form with work email and password inputs', () => {
    render(
      <BrowserRouter>
        <LoginPage />
      </BrowserRouter>
    );

    expect(screen.getByLabelText(/Work Email/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign in/i })).toBeInTheDocument();
  });

  it('validates email format before submitting', async () => {
    render(
      <BrowserRouter>
        <LoginPage />
      </BrowserRouter>
    );

    const submitBtn = screen.getByRole('button', { name: /Sign in/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Please enter a valid work email/i)).toBeInTheDocument();
    });
  });

  it('successfully logs in and stores auth tokens', async () => {
    const mockAuthResponse = {
      token: 'jwt-token-123',
      refreshToken: 'refresh-token-456',
      tokenType: 'Bearer',
      user: {
        id: 'usr-1',
        email: 'test@enterprise.com',
        fullName: 'Test User',
        globalRole: 'USER' as const,
        status: 'ACTIVE' as const,
        emailVerified: true,
      },
    };

    vi.mocked(authApi.login).mockResolvedValueOnce(mockAuthResponse);

    render(
      <BrowserRouter>
        <LoginPage />
      </BrowserRouter>
    );

    const emailInput = screen.getByLabelText(/Work Email/i);
    const passwordInput = screen.getByPlaceholderText('••••••••');
    const submitBtn = screen.getByRole('button', { name: /Sign in/i });

    fireEvent.change(emailInput, { target: { value: 'test@enterprise.com' } });
    fireEvent.change(passwordInput, { target: { value: 'ValidPass123' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'test@enterprise.com',
        password: 'ValidPass123',
      });
      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().user?.email).toBe('test@enterprise.com');
    });
  });
});
