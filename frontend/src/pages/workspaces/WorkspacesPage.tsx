import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FolderKanban,
  Plus,
  Users,
  FileText,
  Calendar,
  Edit2,
  Trash2,
  CheckCircle,
} from 'lucide-react';
import { workspaceApi, CreateWorkspaceParams, UpdateWorkspaceParams } from '../../api/workspaces';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { WorkspaceDto } from '../../types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { RoleBadge } from '../../components/ui/Badge';
import { Skeleton } from '../../components/ui/Skeleton';
import { Card, CardHeader, CardContent, CardFooter } from '../../components/ui/Card';
import { toast } from '../../components/ui/Toast';
import { formatDate } from '../../lib/utils';

export const WorkspacesPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentWorkspace, setCurrentWorkspace } = useWorkspaceStore();

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [selectedWorkspace, setSelectedWorkspace] = useState<WorkspaceDto | null>(null);

  // Form states
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const { data: workspaces, isLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: workspaceApi.getWorkspaces,
  });

  const createMutation = useMutation({
    mutationFn: (params: CreateWorkspaceParams) => workspaceApi.createWorkspace(params),
    onSuccess: (newWs) => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      toast.success('Workspace created', `${newWs.name} is ready for use.`);
      setCreateModalOpen(false);
      setName('');
      setDescription('');
      if (!currentWorkspace) {
        setCurrentWorkspace(newWs);
      }
    },
    onError: (err: any) => {
      toast.error('Failed to create workspace', err.response?.data?.message || err.message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, params }: { id: string; params: UpdateWorkspaceParams }) =>
      workspaceApi.updateWorkspace(id, params),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      toast.success('Workspace updated', 'Changes have been saved.');
      setEditModalOpen(false);
      if (currentWorkspace?.id === updated.id) {
        setCurrentWorkspace(updated);
      }
    },
    onError: (err: any) => {
      toast.error('Failed to update', err.response?.data?.message || err.message);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => workspaceApi.deleteWorkspace(id),
    onSuccess: (_, deletedId) => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      toast.success('Workspace deleted', 'Workspace and its resources have been removed.');
      setDeleteModalOpen(false);
      if (currentWorkspace?.id === deletedId) {
        setCurrentWorkspace(null);
      }
    },
    onError: (err: any) => {
      toast.error('Failed to delete workspace', err.response?.data?.message || err.message);
    },
  });

  const openEdit = (ws: WorkspaceDto, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedWorkspace(ws);
    setName(ws.name);
    setDescription(ws.description || '');
    setEditModalOpen(true);
  };

  const openDelete = (ws: WorkspaceDto, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedWorkspace(ws);
    setDeleteModalOpen(true);
  };

  const handleSelect = (ws: WorkspaceDto) => {
    setCurrentWorkspace(ws);
    toast.info('Switched workspace', `Now active in ${ws.name}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Workspaces
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Manage your knowledge workspaces, member access, and document partitions.
          </p>
        </div>
        <Button
          onClick={() => {
            setName('');
            setDescription('');
            setCreateModalOpen(true);
          }}
          className="inline-flex items-center gap-2"
        >
          <Plus className="w-4 h-4" /> New Workspace
        </Button>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="p-6 space-y-4">
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-10 w-full" />
            </Card>
          ))}
        </div>
      ) : workspaces && workspaces.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {workspaces.map((ws) => {
            const isActive = currentWorkspace?.id === ws.id;
            return (
              <Card
                key={ws.id}
                className={`relative cursor-pointer transition-all hover:shadow-md ${
                  isActive
                    ? 'border-indigo-500 ring-1 ring-indigo-500 dark:border-indigo-500'
                    : 'border-slate-200 dark:border-slate-800'
                }`}
                onClick={() => handleSelect(ws)}
              >
                <CardHeader className="flex flex-row items-start justify-between pb-2">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-slate-900 dark:text-white text-base">
                        {ws.name}
                      </h3>
                      {isActive && (
                        <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 font-medium">
                          <CheckCircle className="w-3 h-3" /> Active
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-500 line-clamp-2">
                      {ws.description || 'No description provided.'}
                    </p>
                  </div>
                  <RoleBadge role={ws.userRole} />
                </CardHeader>

                <CardContent className="py-3">
                  <div className="grid grid-cols-2 gap-2 text-xs text-slate-500 dark:text-slate-400">
                    <div className="flex items-center gap-1.5">
                      <FileText className="w-3.5 h-3.5 text-slate-400" />
                      <span>{ws.documentCount || 0} Documents</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Users className="w-3.5 h-3.5 text-slate-400" />
                      <span>{ws.memberCount || 1} Members</span>
                    </div>
                  </div>
                </CardContent>

                <CardFooter className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
                  <span className="text-xs text-slate-400 flex items-center gap-1">
                    <Calendar className="w-3 h-3" /> {formatDate(ws.createdAt)}
                  </span>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/workspaces/${ws.id}/members`);
                      }}
                      title="Manage Members"
                    >
                      <Users className="w-3.5 h-3.5" />
                    </Button>
                    {ws.userRole === 'WORKSPACE_ADMIN' && (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => openEdit(ws, e)}
                          title="Edit"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-rose-500 hover:text-rose-600"
                          onClick={(e) => openDelete(ws, e)}
                          title="Delete"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </Button>
                      </>
                    )}
                  </div>
                </CardFooter>
              </Card>
            );
          })}
        </div>
      ) : (
        <div className="text-center py-16 border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-2xl">
          <FolderKanban className="w-12 h-12 text-slate-400 mx-auto mb-3" />
          <h3 className="text-lg font-medium text-slate-900 dark:text-white">No workspaces yet</h3>
          <p className="text-sm text-slate-500 dark:text-slate-400 max-w-sm mx-auto mt-1 mb-4">
            Create your first workspace to start uploading documents and asking questions with AI.
          </p>
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="w-4 h-4 mr-1.5" /> Create Workspace
          </Button>
        </div>
      )}

      {/* Create Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create New Workspace"
        description="Workspaces provide isolated knowledge vaults with custom access control."
      >
        <div className="space-y-4">
          <Input
            label="Workspace Name"
            placeholder="e.g. Legal & Compliance, Engineering, Q4 Finance"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              Description (Optional)
            </label>
            <textarea
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              rows={3}
              placeholder="Brief description of the workspace knowledge contents..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={!name.trim() || createMutation.isPending}
              loading={createMutation.isPending}
              onClick={() => createMutation.mutate({ name: name.trim(), description: description.trim() || undefined })}
            >
              Create Workspace
            </Button>
          </div>
        </div>
      </Modal>

      {/* Edit Modal */}
      <Modal
        isOpen={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Workspace"
      >
        <div className="space-y-4">
          <Input
            label="Workspace Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              Description
            </label>
            <textarea
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setEditModalOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={!name.trim() || updateMutation.isPending}
              loading={updateMutation.isPending}
              onClick={() =>
                selectedWorkspace &&
                updateMutation.mutate({
                  id: selectedWorkspace.id,
                  params: { name: name.trim(), description: description.trim() || undefined },
                })
              }
            >
              Save Changes
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        title="Delete Workspace"
        description="Are you sure you want to delete this workspace? All uploaded documents, vector chunks, and conversations inside will be permanently deleted."
      >
        <div className="flex justify-end gap-2 pt-4">
          <Button variant="ghost" onClick={() => setDeleteModalOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteMutation.isPending}
            onClick={() => selectedWorkspace && deleteMutation.mutate(selectedWorkspace.id)}
          >
            Delete Workspace
          </Button>
        </div>
      </Modal>
    </div>
  );
};
