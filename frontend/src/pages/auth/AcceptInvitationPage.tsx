import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { invitationsApi } from '../../api/invitations';
import { WorkspaceInvitationDto } from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { ShieldCheck, ArrowRight, AlertTriangle, CheckCircle, Sparkles, Building2, UserPlus, LogIn } from 'lucide-react';

export const AcceptInvitationPage: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuthStore();

  const [invitation, setInvitation] = useState<WorkspaceInvitationDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);
  const [accepted, setAccepted] = useState(false);

  useEffect(() => {
    if (!token) {
      setError('Invalid or missing invitation token.');
      setLoading(false);
      return;
    }

    invitationsApi
      .validateToken(token)
      .then((data) => {
        setInvitation(data);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.response?.data?.message || err.message || 'Invitation not found or has expired.');
        setLoading(false);
      });
  }, [token]);

  const handleAccept = async () => {
    if (!token) return;
    setAccepting(true);
    setError(null);
    try {
      await invitationsApi.acceptInvitation(token);
      setAccepted(true);
      setTimeout(() => {
        navigate('/');
      }, 1500);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to accept invitation.');
    } finally {
      setAccepting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
        <div className="flex flex-col items-center gap-3">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
          <p className="text-slate-400 font-medium">Validating workspace invitation...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-6 text-slate-100">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl relative overflow-hidden">
        {/* Glow backdrop */}
        <div className="absolute -top-24 -right-24 w-48 h-48 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-24 -left-24 w-48 h-48 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Logo header */}
        <div className="flex items-center gap-2 mb-6">
          <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-cyan-400 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20">
            <Sparkles className="h-5 w-5" />
          </div>
          <span className="font-bold text-xl tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
            KnowFlow AI
          </span>
        </div>

        {error ? (
          <div className="space-y-6">
            <div className="p-4 rounded-xl bg-red-950/40 border border-red-800/60 flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-red-400 shrink-0 mt-0.5" />
              <div>
                <h4 className="font-semibold text-red-200">Invalid Invitation</h4>
                <p className="text-sm text-red-300/80 mt-1">{error}</p>
              </div>
            </div>
            <Link
              to="/auth/login"
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-medium transition"
            >
              Return to Login
            </Link>
          </div>
        ) : accepted ? (
          <div className="text-center py-6 space-y-4">
            <div className="h-14 w-14 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto border border-emerald-500/40">
              <CheckCircle className="h-8 w-8" />
            </div>
            <h3 className="text-xl font-bold text-white">Welcome Aboard!</h3>
            <p className="text-sm text-slate-400">
              You are now a member of <strong className="text-white">{invitation?.workspaceName}</strong>. Redirecting to workspace...
            </p>
          </div>
        ) : (
          <div className="space-y-6">
            <div>
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-indigo-500/15 text-indigo-400 border border-indigo-500/30 mb-3">
                <ShieldCheck className="h-3.5 w-3.5" /> Workspace Invitation
              </span>
              <h2 className="text-2xl font-bold text-white">Join the Knowledge Base</h2>
              <p className="text-sm text-slate-400 mt-1">
                You have been invited to collaborate with your team on KnowFlow AI.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400 font-medium">Workspace</span>
                <span className="text-sm font-semibold text-white flex items-center gap-1.5">
                  <Building2 className="h-4 w-4 text-indigo-400" />
                  {invitation?.workspaceName}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400 font-medium">Invited Email</span>
                <span className="text-sm text-slate-200">{invitation?.email}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400 font-medium">Assigned Role</span>
                <span className="text-xs font-bold px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800">
                  {invitation?.role}
                </span>
              </div>
            </div>

            {isAuthenticated ? (
              <div className="space-y-3">
                <p className="text-xs text-slate-400 text-center">
                  Signed in as <strong className="text-white">{user?.email}</strong>
                </p>
                <button
                  onClick={handleAccept}
                  disabled={accepting}
                  className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-500 hover:from-indigo-600 hover:to-cyan-600 font-semibold text-white shadow-lg shadow-indigo-500/25 transition disabled:opacity-50"
                >
                  {accepting ? 'Joining Workspace...' : 'Accept & Join Workspace'}
                  <ArrowRight className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                <Link
                  to={`/auth/register?invite=${token}&email=${encodeURIComponent(invitation?.email || '')}`}
                  className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-500 hover:from-indigo-600 hover:to-cyan-600 font-semibold text-white shadow-lg shadow-indigo-500/25 transition"
                >
                  <UserPlus className="h-4 w-4" />
                  Create Account & Accept
                </Link>
                <Link
                  to={`/auth/login?invite=${token}`}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 font-medium text-slate-300 hover:text-white transition"
                >
                  <LogIn className="h-4 w-4" />
                  Sign In to Existing Account
                </Link>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
