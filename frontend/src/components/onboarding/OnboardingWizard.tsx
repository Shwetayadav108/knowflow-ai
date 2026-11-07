import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Sparkles,
  ArrowRight,
  ArrowLeft,
  X,
  Building2,
  UserPlus,
  UploadCloud,
  Cpu,
  CheckCircle2,
  MessageSquare,
  ShieldAlert,
  Layers,
  Search,
} from 'lucide-react';
import { Button } from '../ui/Button';
import { useAuthStore } from '../../store/useAuthStore';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { authApi } from '../../api/auth';
import { toast } from '../ui/Toast';

interface OnboardingWizardProps {
  isOpen: boolean;
  onClose: () => void;
}

export const OnboardingWizard: React.FC<OnboardingWizardProps> = ({ isOpen, onClose }) => {
  const navigate = useNavigate();
  const { user, completeOnboarding } = useAuthStore();
  const { currentWorkspace } = useWorkspaceStore();
  const [step, setStep] = useState(1);
  const totalSteps = 7;

  if (!isOpen) return null;

  const handleFinish = async () => {
    try {
      await authApi.completeOnboarding();
      completeOnboarding();
      toast.success('Onboarding completed!', 'You are all set to explore KnowFlow AI.');
    } catch {
      completeOnboarding();
    }
    onClose();
  };

  const nextStep = () => {
    if (step < totalSteps) {
      setStep(step + 1);
    } else {
      handleFinish();
    }
  };

  const prevStep = () => {
    if (step > 1) {
      setStep(step - 1);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl overflow-hidden text-slate-100">
        {/* Ambient Top Glow */}
        <div className="absolute -top-24 -right-24 w-60 h-60 bg-indigo-500/15 rounded-full blur-3xl pointer-events-none" />

        {/* Top Header */}
        <div className="flex items-center justify-between pb-6 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-xl bg-gradient-to-tr from-indigo-500 to-cyan-400 flex items-center justify-center text-white shadow-md">
              <Sparkles className="h-4 w-4" />
            </div>
            <div>
              <span className="font-bold text-sm tracking-tight text-white block">KnowFlow Onboarding</span>
              <span className="text-xs text-slate-400">Step {step} of {totalSteps}</span>
            </div>
          </div>
          <button
            onClick={handleFinish}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
            title="Dismiss Tour"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Step Progress Bar */}
        <div className="w-full bg-slate-800 h-1.5 mt-4 rounded-full overflow-hidden">
          <div
            className="bg-gradient-to-r from-indigo-500 to-cyan-400 h-full transition-all duration-300 rounded-full"
            style={{ width: `${(step / totalSteps) * 100}%` }}
          />
        </div>

        {/* Step Content */}
        <div className="py-8 min-h-[300px] flex flex-col justify-center">
          {step === 1 && (
            <div className="space-y-4 text-center">
              <div className="h-16 w-16 mx-auto rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center">
                <Sparkles className="h-8 w-8" />
              </div>
              <h3 className="text-2xl font-bold text-white">Welcome to KnowFlow AI, {user?.fullName}!</h3>
              <p className="text-sm text-slate-400 max-w-lg mx-auto">
                KnowFlow AI is an enterprise knowledge retrieval engine that transforms your corporate documents into verifiable, grounded intelligence without hallucinations.
              </p>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center shrink-0">
                  <Building2 className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Workspace Isolation</h3>
                  <p className="text-xs text-slate-400">Multi-tenant boundary protecting your corporate secrets</p>
                </div>
              </div>
              <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-400">Active Workspace:</span>
                  <span className="font-semibold text-white">{currentWorkspace?.name || 'Default Workspace'}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-400">Your Workspace Role:</span>
                  <span className="px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 text-xs font-bold border border-indigo-800">
                    {currentWorkspace?.userRole || 'WORKSPACE_ADMIN'}
                  </span>
                </div>
              </div>
              <p className="text-xs text-slate-400">
                Documents uploaded inside this workspace are never accessible by other tenants or unauthorized workspace roles.
              </p>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center shrink-0">
                  <UserPlus className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Invite Your Team</h3>
                  <p className="text-xs text-slate-400">Role-based access control with secure expiring links</p>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 text-center">
                  <span className="text-xs font-bold text-indigo-400 block">WORKSPACE_ADMIN</span>
                  <span className="text-[11px] text-slate-400">Full management, deletion, & invites</span>
                </div>
                <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 text-center">
                  <span className="text-xs font-bold text-cyan-400 block">EDITOR</span>
                  <span className="text-[11px] text-slate-400">Upload, edit docs, & manage categories</span>
                </div>
                <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 text-center">
                  <span className="text-xs font-bold text-emerald-400 block">VIEWER</span>
                  <span className="text-[11px] text-slate-400">Read-only search & ask questions</span>
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0">
                  <UploadCloud className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Ingest Any Document</h3>
                  <p className="text-xs text-slate-400">Enterprise parsers handle PDF, DOCX, TXT, CSV & PPTX</p>
                </div>
              </div>
              <div className="p-6 rounded-2xl border-2 border-dashed border-slate-700 bg-slate-800/30 text-center space-y-2">
                <UploadCloud className="h-8 w-8 text-indigo-400 mx-auto" />
                <p className="text-sm font-medium text-white">Drag & drop files or click to browse</p>
                <p className="text-xs text-slate-500">Duplicate detection prevents redundant embeddings</p>
              </div>
            </div>
          )}

          {step === 5 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-xl bg-violet-500/10 border border-violet-500/20 text-violet-400 flex items-center justify-center shrink-0">
                  <Cpu className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Real-Time Processing Pipeline</h3>
                  <p className="text-xs text-slate-400">From raw files to structured semantic vectors</p>
                </div>
              </div>
              <div className="flex items-center justify-between p-4 rounded-xl bg-slate-800/80 border border-slate-700/60 text-xs text-slate-300">
                <div className="flex flex-col items-center gap-1">
                  <span className="h-6 w-6 rounded-full bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold">1</span>
                  <span>Extract</span>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-600" />
                <div className="flex flex-col items-center gap-1">
                  <span className="h-6 w-6 rounded-full bg-cyan-500/20 text-cyan-400 flex items-center justify-center font-bold">2</span>
                  <span>Chunk</span>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-600" />
                <div className="flex flex-col items-center gap-1">
                  <span className="h-6 w-6 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">3</span>
                  <span>Embed</span>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-600" />
                <div className="flex flex-col items-center gap-1">
                  <span className="h-6 w-6 rounded-full bg-violet-500/20 text-violet-400 flex items-center justify-center font-bold">4</span>
                  <span>Index</span>
                </div>
              </div>
            </div>
          )}

          {step === 6 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center shrink-0">
                  <Layers className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Verifiable Citations & Truth</h3>
                  <p className="text-xs text-slate-400">Strict hallucination defense and citation guarantees</p>
                </div>
              </div>
              <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-2 text-xs">
                <div className="flex items-center gap-2 text-emerald-400 font-semibold">
                  <CheckCircle2 className="h-4 w-4" />
                  Exact page numbers & section headings for every retrieved passage.
                </div>
                <div className="flex items-center gap-2 text-indigo-400 font-semibold">
                  <ShieldAlert className="h-4 w-4" />
                  Explicit refusal if documents do not contain the answer.
                </div>
              </div>
            </div>
          )}

          {step === 7 && (
            <div className="space-y-4 text-center">
              <div className="h-16 w-16 mx-auto rounded-2xl bg-gradient-to-tr from-indigo-500 to-cyan-400 text-white flex items-center justify-center shadow-lg shadow-indigo-500/30">
                <MessageSquare className="h-8 w-8" />
              </div>
              <h3 className="text-2xl font-bold text-white">You're Ready to Ask!</h3>
              <p className="text-sm text-slate-400 max-w-lg mx-auto">
                Head over to the Chat or Documents page to upload your company SOPs, manuals, or policies and ask your first grounded question.
              </p>
              <div className="pt-2 flex justify-center gap-3">
                <button
                  onClick={() => {
                    handleFinish();
                    navigate('/documents');
                  }}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-white transition flex items-center gap-1.5"
                >
                  <UploadCloud className="h-4 w-4 text-indigo-400" />
                  Upload Documents
                </button>
                <button
                  onClick={() => {
                    handleFinish();
                    navigate('/chat');
                  }}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-500 hover:from-indigo-600 hover:to-cyan-600 text-xs font-semibold text-white transition flex items-center gap-1.5 shadow-md shadow-indigo-500/20"
                >
                  <MessageSquare className="h-4 w-4" />
                  Open AI Chat
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Bottom Navigation */}
        <div className="flex items-center justify-between pt-6 border-t border-slate-800">
          <Button
            type="button"
            variant="ghost"
            onClick={prevStep}
            disabled={step === 1}
            className="text-xs"
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Previous
          </Button>

          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={handleFinish}
              className="text-xs"
            >
              Skip Tour
            </Button>
            <Button
              type="button"
              onClick={nextStep}
              className="bg-indigo-600 hover:bg-indigo-500 text-white text-xs"
            >
              {step === totalSteps ? 'Finish & Start' : 'Next'}
              <ArrowRight className="h-4 w-4 ml-1" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};
