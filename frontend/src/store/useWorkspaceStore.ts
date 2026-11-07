import { create } from 'zustand';
import { WorkspaceDto } from '../types';

interface WorkspaceState {
  currentWorkspace: WorkspaceDto | null;
  workspaces: WorkspaceDto[];
  setCurrentWorkspace: (workspace: WorkspaceDto | null) => void;
  setWorkspaces: (workspaces: WorkspaceDto[]) => void;
}

const STORAGE_WORKSPACE_KEY = 'knowflow_active_workspace';

export const useWorkspaceStore = create<WorkspaceState>((set) => {
  let initialWorkspace: WorkspaceDto | null = null;
  try {
    const stored = localStorage.getItem(STORAGE_WORKSPACE_KEY);
    if (stored) {
      initialWorkspace = JSON.parse(stored);
    }
  } catch {
    initialWorkspace = null;
  }

  return {
    currentWorkspace: initialWorkspace,
    workspaces: [],

    setCurrentWorkspace: (workspace: WorkspaceDto | null) => {
      if (workspace) {
        localStorage.setItem(STORAGE_WORKSPACE_KEY, JSON.stringify(workspace));
      } else {
        localStorage.removeItem(STORAGE_WORKSPACE_KEY);
      }
      set({ currentWorkspace: workspace });
    },

    setWorkspaces: (workspaces: WorkspaceDto[]) => {
      set((state) => {
        let current = state.currentWorkspace;
        // If current is not set or not in list, default to first workspace
        if (workspaces.length > 0 && (!current || !workspaces.some((w) => w.id === current?.id))) {
          current = workspaces[0];
          localStorage.setItem(STORAGE_WORKSPACE_KEY, JSON.stringify(current));
        }
        return { workspaces, currentWorkspace: current };
      });
    },
  };
});
