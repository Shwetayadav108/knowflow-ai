import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { OnboardingWizard } from '../components/onboarding/OnboardingWizard';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/useAuthStore';

vi.mock('../api/auth', () => ({
  authApi: {
    completeOnboarding: vi.fn().mockResolvedValue({
      id: 'user-1',
      fullName: 'Sarah Connor',
      email: 'sarah@enterprise.com',
      onboardingCompleted: true,
    }),
  },
}));

describe('OnboardingWizard Flow', () => {
  it('renders step 1 and advances through multi-step pipeline', async () => {
    useAuthStore.setState({
      user: {
        id: 'user-1',
        email: 'sarah@enterprise.com',
        fullName: 'Sarah Connor',
        globalRole: 'USER',
        status: 'ACTIVE',
        emailVerified: true,
        onboardingCompleted: false,
      },
    });

    const onClose = vi.fn();
    render(
      <BrowserRouter>
        <OnboardingWizard isOpen={true} onClose={onClose} />
      </BrowserRouter>
    );

    expect(screen.getByText(/Welcome to KnowFlow AI, Sarah Connor!/i)).toBeInTheDocument();
    expect(screen.getByText(/Step 1 of 7/i)).toBeInTheDocument();

    const nextBtn = screen.getByRole('button', { name: /Next/i });
    fireEvent.click(nextBtn);

    // Step 2
    expect(screen.getByText(/Workspace Isolation/i)).toBeInTheDocument();
    expect(screen.getByText(/Step 2 of 7/i)).toBeInTheDocument();

    // Skip Tour calls complete
    const skipBtn = screen.getByRole('button', { name: /Skip Tour/i });
    fireEvent.click(skipBtn);

    await waitFor(() => {
      expect(authApi.completeOnboarding).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });
});
