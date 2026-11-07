import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Lock, Mail, Eye, EyeOff, ArrowRight, Shield, Sparkles, CheckCircle2, Cpu, FileCheck } from 'lucide-react';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { toast } from '../../components/ui/Toast';
import { authApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid work email'),
  password: z.string().min(1, 'Password is required'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get('invite');
  const redirectPath = searchParams.get('redirect');

  const { setAuth } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (values: LoginFormValues) => {
    setErrorMsg(null);
    try {
      setIsLoading(true);
      const authData = await authApi.login(values);
      setAuth(authData);
      toast.success('Welcome back', `Signed in as ${authData.user.fullName}`);

      if (inviteToken) {
        navigate(`/invite/${inviteToken}`);
      } else if (redirectPath) {
        navigate(redirectPath);
      } else {
        navigate('/');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Authentication failed. Please check your credentials.';
      setErrorMsg(msg);
      toast.error('Authentication Failed', msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex bg-slate-950 text-slate-100 selection:bg-indigo-500 selection:text-white">
      {/* Left Column: Enterprise Value Proposition Hero */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between p-12 bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950/40 border-r border-slate-800 relative overflow-hidden">
        {/* Ambient background glows */}
        <div className="absolute top-0 -left-20 w-96 h-96 bg-indigo-600/15 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 -right-20 w-96 h-96 bg-cyan-600/15 rounded-full blur-3xl pointer-events-none" />

        {/* Brand Header */}
        <div className="relative z-10 flex items-center gap-3">
          <div className="h-11 w-11 rounded-2xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-cyan-400 flex items-center justify-center text-white shadow-xl shadow-indigo-500/25">
            <Sparkles className="h-6 w-6" />
          </div>
          <div>
            <span className="font-bold text-2xl tracking-tight text-white block">KnowFlow AI</span>
            <span className="text-xs uppercase tracking-wider text-indigo-400 font-semibold">Enterprise RAG Platform</span>
          </div>
        </div>

        {/* Center Hero Copy */}
        <div className="relative z-10 max-w-lg space-y-6">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
            <Cpu className="w-3.5 h-3.5" /> Next-Gen Hybrid Semantic Search & Citations
          </div>
          <h2 className="text-4xl font-extrabold tracking-tight text-white leading-tight">
            Verifiable truth for mission-critical enterprise knowledge.
          </h2>
          <p className="text-slate-400 text-base leading-relaxed">
            Eliminate AI hallucinations with grounded multi-vector retrieval, multi-tenant RBAC, and instant page-accurate citations directly from your internal documents.
          </p>

          <div className="grid grid-cols-2 gap-4 pt-2">
            <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 backdrop-blur-sm">
              <FileCheck className="w-5 h-5 text-emerald-400 mb-2" />
              <h4 className="text-sm font-semibold text-white">100% Grounded Citations</h4>
              <p className="text-xs text-slate-400 mt-0.5">Every statement linked directly to source paragraphs and page numbers.</p>
            </div>
            <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 backdrop-blur-sm">
              <Shield className="w-5 h-5 text-indigo-400 mb-2" />
              <h4 className="text-sm font-semibold text-white">Tenant & Role Isolation</h4>
              <p className="text-xs text-slate-400 mt-0.5">Strict role-based and document-level permission barriers.</p>
            </div>
          </div>
        </div>

        {/* Footer Proof */}
        <div className="relative z-10 flex items-center justify-between text-xs text-slate-500 border-t border-slate-800/80 pt-6">
          <span>Enterprise Grade • SOC2 Compliant</span>
          <span>pgvector • Google Gemini</span>
        </div>
      </div>

      {/* Right Column: Sign In Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-md space-y-8">
          {/* Mobile Brand Header */}
          <div className="lg:hidden flex items-center gap-3 mb-6">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-cyan-400 flex items-center justify-center text-white shadow-lg">
              <Sparkles className="h-5 w-5" />
            </div>
            <span className="font-bold text-xl tracking-tight text-white">KnowFlow AI</span>
          </div>

          <div>
            <h1 className="text-3xl font-bold tracking-tight text-white">Sign in to your account</h1>
            <p className="mt-2 text-sm text-slate-400">
              Enter your work credentials to access your workspaces.
            </p>
          </div>

          {inviteToken && (
            <div className="p-3.5 rounded-xl bg-indigo-950/50 border border-indigo-800/60 flex items-center gap-3">
              <CheckCircle2 className="h-5 w-5 text-indigo-400 shrink-0" />
              <p className="text-xs text-indigo-200">
                Sign in to automatically accept your pending workspace invitation.
              </p>
            </div>
          )}

          {errorMsg && (
            <div className="p-3.5 rounded-xl bg-red-950/50 border border-red-800/60 text-xs text-red-200 flex items-center gap-2">
              <Shield className="h-4 w-4 text-red-400 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Work Email"
              type="email"
              placeholder="name@company.com"
              leftIcon={<Mail className="w-4 h-4 text-slate-400" />}
              error={errors.email?.message}
              {...register('email')}
              autoFocus
            />

            <div className="space-y-1">
              <Input
                label="Password"
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                leftIcon={<Lock className="w-4 h-4 text-slate-400" />}
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="text-slate-400 hover:text-slate-200 transition"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                }
                error={errors.password?.message}
                {...register('password')}
              />
              <div className="flex items-center justify-between pt-2">
                <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="w-4 h-4 rounded border-slate-700 bg-slate-900 text-indigo-500 focus:ring-indigo-500 focus:ring-offset-slate-950"
                  />
                  Remember my session
                </label>
                <Link
                  to="/auth/forgot-password"
                  className="text-xs font-medium text-indigo-400 hover:text-indigo-300 transition"
                >
                  Forgot password?
                </Link>
              </div>
            </div>

            <Button
              type="submit"
              className="w-full mt-2 bg-gradient-to-r from-indigo-500 via-indigo-600 to-cyan-500 hover:from-indigo-600 hover:to-cyan-600 shadow-lg shadow-indigo-500/25 text-white font-medium"
              size="lg"
              isLoading={isLoading}
            >
              Sign In
              <ArrowRight className="w-4 h-4 ml-1.5" />
            </Button>
          </form>

          <div className="pt-6 border-t border-slate-800 text-center text-sm text-slate-400">
            Don't have an enterprise account?{' '}
            <Link
              to={inviteToken ? `/auth/register?invite=${inviteToken}` : '/auth/register'}
              className="font-semibold text-indigo-400 hover:text-indigo-300 transition"
            >
              Create account
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
