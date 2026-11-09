import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Users,
  UserPlus,
  ArrowLeft,
  Mail,
  Trash2,
  Calendar,
  Copy,
  Check,
  Send,
  XCircle,
} from 'lucide-react';
import { workspaceApi } from '../../api/workspaces';
import { invitationsApi } from '../../api/invitations';
import { WorkspaceRole, WorkspaceMemberDto, WorkspaceInvitationDto } from '../../types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { RoleBadge } from '../../components/ui/Badge';
import { Skeleton } from '../../components/ui/Skeleton';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { toast } from '../../components/ui/Toast';
import { formatDate } from '../../lib/utils';

export const WorkspaceMembersPage: React.FC = () => {
  const { id: workspaceId } = useParams<{ id: string }>();
  const queryClient = useQueryClient();

  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<WorkspaceRole>('VIEWER');
  const [copiedToken, setCopiedToken] = useState<string | null>(null);

  const { data: members, isLoading: loadingMembers } = useQuery({
    queryKey: ['workspace-members', workspaceId],
    queryFn: () => workspaceApi.getMembers(workspaceId!),
    enabled: !!workspaceId,
  });

  const { data: invitations, isLoading: loadingInvitations } = useQuery({
    queryKey: ['workspace-invitations', workspaceId],
    queryFn: () => invitationsApi.getWorkspaceInvitations(workspaceId!),
    enabled: !!workspaceId,
  });

  const { data: workspace } = useQuery({
    queryKey: ['workspace', workspaceId],
    queryFn: () => workspaceApi.getWorkspace(workspaceId!),
    enabled: !!workspaceId,
  });

  const inviteMutation = useMutation({
    mutationFn: (data: { email: string; role: WorkspaceRole }) =>
      invitationsApi.createInvitation(workspaceId!, data),
    onSuccess: (newInvite) => {
      queryClient.invalidateQueries({ queryKey: ['workspace-invitations', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['workspace-members', workspaceId] });

      const inviteUrl = `${window.location.origin}/invite/${newInvite.token}`;
      navigator.clipboard.writeText(inviteUrl);
      toast.success(
        'Invitation created & copied',
        `Link copied to clipboard: ${newInvite.email}`
      );

      setInviteModalOpen(false);
      setEmail('');
      setRole('VIEWER');
    },
    onError: (err: any) => {
      toast.error('Failed to send invitation', err.response?.data?.message || err.message);
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (invitationId: string) =>
      invitationsApi.revokeInvitation(workspaceId!, invitationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspace-invitations', workspaceId] });
      toast.success('Invitation revoked', 'The invitation link has been invalidated.');
    },
    onError: (err: any) => {
      toast.error('Failed to revoke invitation', err.response?.data?.message || err.message);
    },
  });

  const removeMemberMutation = useMutation({
    mutationFn: (memberId: string) => workspaceApi.removeMember(workspaceId!, memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspace-members', workspaceId] });
      toast.success('Member removed', 'Access revoked successfully.');
    },
    onError: (err: any) => {
      toast.error('Failed to remove member', err.response?.data?.message || err.message);
    },
  });

  const copyInviteLink = (token?: string) => {
    if (!token) return;
    const url = `${window.location.origin}/invite/${token}`;
    navigator.clipboard.writeText(url);
    setCopiedToken(token);
    toast.success('Link copied', 'Invitation link copied to clipboard.');
    setTimeout(() => setCopiedToken(null), 2000);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Link to="/workspaces" className="hover:text-slate-900 dark:hover:text-white flex items-center gap-1">
          <ArrowLeft className="w-4 h-4" /> Back to Workspaces
        </Link>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Workspace Members & Invitations
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {workspace ? `Managing access and roles for "${workspace.name}"` : 'Loading workspace details...'}
          </p>
        </div>
        <Button onClick={() => setInviteModalOpen(true)} className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white">
          <UserPlus className="w-4 h-4" /> Invite Member
        </Button>
      </div>

      {/* Active Members Table */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            Active Members ({members?.length || 0})
          </h2>
        </CardHeader>
        <CardContent className="p-0">
          {loadingMembers ? (
            <div className="p-6 space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : members && members.length > 0 ? (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {members.map((member: WorkspaceMemberDto) => (
                <div
                  key={member.userId}
                  className="flex items-center justify-between p-4 hover:bg-slate-50 dark:hover:bg-slate-900/50 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-indigo-500 to-violet-500 flex items-center justify-center text-white font-medium text-sm">
                      {member.fullName ? member.fullName.charAt(0).toUpperCase() : 'U'}
                    </div>
                    <div>
                      <div className="font-medium text-sm text-slate-900 dark:text-white">
                        {member.fullName || 'Member'}
                      </div>
                      <div className="text-xs text-slate-500 flex items-center gap-1">
                        <Mail className="w-3 h-3" /> {member.email}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-4">
                    <RoleBadge role={member.role} />
                    <span className="text-xs text-slate-400 hidden sm:inline-flex items-center gap-1">
                      <Calendar className="w-3 h-3" /> Joined {formatDate(member.joinedAt)}
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-rose-500 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30"
                      onClick={() => removeMemberMutation.mutate(member.userId)}
                      title="Remove member"
                    >
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-12 text-center text-slate-500">
              <Users className="w-10 h-10 mx-auto text-slate-400 mb-2" />
              <p>No members found in this workspace.</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Pending / Active Invitations Table */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white flex items-center gap-2">
            <span>Pending Invitations</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 font-bold">
              {invitations?.filter((i) => i.status === 'PENDING').length || 0}
            </span>
          </h2>
        </CardHeader>
        <CardContent className="p-0">
          {loadingInvitations ? (
            <div className="p-6 space-y-3">
              {[1, 2].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : invitations && invitations.length > 0 ? (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {invitations.map((inv: WorkspaceInvitationDto) => (
                <div
                  key={inv.id}
                  className="flex items-center justify-between p-4 hover:bg-slate-50 dark:hover:bg-slate-900/50 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center text-slate-600 dark:text-slate-300 font-medium text-sm">
                      <Send className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="font-medium text-sm text-slate-900 dark:text-white">
                        {inv.email}
                      </div>
                      <div className="text-xs text-slate-500 flex items-center gap-2">
                        <span>Role: {inv.role}</span>
                        <span>•</span>
                        <span>Expires {formatDate(inv.expiresAt)}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <span
                      className={`text-xs px-2 py-0.5 rounded font-semibold ${
                        inv.status === 'PENDING'
                          ? 'bg-amber-500/10 text-amber-500 border border-amber-500/20'
                          : inv.status === 'ACCEPTED'
                          ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                          : 'bg-rose-500/10 text-rose-500 border border-rose-500/20'
                      }`}
                    >
                      {inv.status}
                    </span>

                    {inv.status === 'PENDING' && (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => copyInviteLink(inv.token)}
                          className="text-xs"
                          title="Copy invitation URL"
                        >
                          {copiedToken === inv.token ? (
                            <Check className="w-3.5 h-3.5 text-emerald-500 mr-1" />
                          ) : (
                            <Copy className="w-3.5 h-3.5 mr-1" />
                          )}
                          Copy Link
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-rose-500 hover:text-rose-600"
                          onClick={() => revokeMutation.mutate(inv.id)}
                          title="Revoke invitation"
                        >
                          <XCircle className="w-4 h-4" />
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-8 text-center text-xs text-slate-400">
              No pending invitations for this workspace.
            </div>
          )}
        </CardContent>
      </Card>

      {/* Invite Member Modal */}
      <Modal
        isOpen={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Member to Workspace"
        description="Generates a 7-day secure invitation token and link."
      >
        <div className="space-y-4">
          <Input
            label="Work Email"
            type="email"
            placeholder="colleague@enterprise.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              Role & Permissions
            </label>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as WorkspaceRole)}
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="VIEWER">Viewer (Can search and chat)</option>
              <option value="EDITOR">Editor (Can upload and edit documents)</option>
              <option value="WORKSPACE_ADMIN">Workspace Admin (Full workspace control)</option>
            </select>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setInviteModalOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={!email.trim() || inviteMutation.isPending}
              isLoading={inviteMutation.isPending}
              onClick={() => inviteMutation.mutate({ email: email.trim(), role })}
              className="bg-indigo-600 hover:bg-indigo-500 text-white"
            >
              Create & Copy Link
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
