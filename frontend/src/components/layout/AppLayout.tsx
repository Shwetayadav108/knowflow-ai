import React, { useState, useEffect } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Sidebar } from './Sidebar';
import { Navbar } from './Navbar';
import { ToastContainer, toast } from '../ui/Toast';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { OnboardingWizard } from '../onboarding/OnboardingWizard';
import { useAuthStore } from '../../store/useAuthStore';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { workspaceApi } from '../../api/workspaces';

export const AppLayout: React.FC = () => {
  const { isAuthenticated, user } = useAuthStore();
  const { setWorkspaces, setCurrentWorkspace } = useWorkspaceStore();
  const [isCreateWsOpen, setIsCreateWsOpen] = useState(false);
  const [newWsName, setNewWsName] = useState('');
  const [newWsDesc, setNewWsDesc] = useState('');
  const [isCreatingWs, setIsCreatingWs] = useState(false);
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    if (user && user.onboardingCompleted === false) {
      setShowOnboarding(true);
    }
  }, [user]);

  if (!isAuthenticated) {
    return <Navigate to="/auth/login" replace />;
  }

  // Load user workspaces
  const { data: workspaces = [], refetch: refetchWorkspaces } = useQuery({
    queryKey: ['workspaces'],
    queryFn: workspaceApi.getWorkspaces,
    enabled: isAuthenticated,
  });

  useEffect(() => {
    if (workspaces.length > 0) {
      setWorkspaces(workspaces);
    }
  }, [workspaces, setWorkspaces]);

  const handleCreateWorkspace = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWsName.trim()) return;

    try {
      setIsCreatingWs(true);
      const created = await workspaceApi.createWorkspace({
        name: newWsName.trim(),
        description: newWsDesc.trim() || undefined,
      });
      await refetchWorkspaces();
      setCurrentWorkspace(created);
      toast.success('Workspace created', `Switched to "${created.name}"`);
      setIsCreateWsOpen(false);
      setNewWsName('');
      setNewWsDesc('');
    } catch (err) {
      toast.error('Failed to create workspace', (err as Error).message);
    } finally {
      setIsCreatingWs(false);
    }
  };

  return (
    <div className="flex h-screen w-full overflow-hidden bg-slate-50 dark:bg-slate-950">
      {/* Sidebar */}
      <Sidebar />

      {/* Main Container */}
      <div className="flex flex-1 flex-col min-w-0 overflow-hidden">
        <Navbar onOpenNewWorkspaceModal={() => setIsCreateWsOpen(true)} />

        <main className="flex-1 overflow-y-auto p-4 md:p-8">
          <Outlet />
        </main>
      </div>

      {/* Global Toast Container */}
      <ToastContainer />

      {/* Onboarding Wizard */}
      <OnboardingWizard
        isOpen={showOnboarding}
        onClose={() => setShowOnboarding(false)}
      />

      {/* Create Workspace Modal */}
      <Modal
        isOpen={isCreateWsOpen}
        onClose={() => setIsCreateWsOpen(false)}
        title="Create New Workspace"
        description="Workspaces provide isolated document silos and team permissions."
      >
        <form onSubmit={handleCreateWorkspace} className="space-y-4">
          <Input
            label="Workspace Name"
            placeholder="e.g. Legal & Compliance"
            value={newWsName}
            onChange={(e) => setNewWsName(e.target.value)}
            required
            autoFocus
          />
          <Input
            label="Description (Optional)"
            placeholder="Departmental policies and contracts"
            value={newWsDesc}
            onChange={(e) => setNewWsDesc(e.target.value)}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsCreateWsOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" isLoading={isCreatingWs}>
              Create Workspace
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
