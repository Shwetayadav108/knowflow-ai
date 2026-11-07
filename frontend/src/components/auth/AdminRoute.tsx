import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/useAuthStore';
import { toast } from '../ui/Toast';

export const AdminRoute: React.FC = () => {
  const { user, isAuthenticated } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/auth/login" replace />;
  }

  if (user?.globalRole !== 'SUPER_ADMIN') {
    toast.error('Access Denied', 'Global administrator privileges required.');
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
};
