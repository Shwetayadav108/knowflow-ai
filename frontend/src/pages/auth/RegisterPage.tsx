import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Sparkles, ArrowRight, ShieldCheck, CheckCircle2, Lock, Mail, User, Eye, EyeOff } from 'lucide-react';
import { authApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { toast } from '../../components/ui/Toast';

const registerSchema = z
  .object({
    fullName: z.string().min(2, 'Full name must be at least 2 characters'),
    email: z.string().email('Please enter a valid work email address'),
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters')
      .regex(/[A-Z]/, 'Must contain at least one uppercase letter')
      .regex(/[0-9]/, 'Must contain at least one number'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
    terms: z.literal(true, {
      errorMap: () => ({ message: 'You must accept the terms of service' }),
    }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords don't match",
    path: ['confirmPassword'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get('invite') || undefined;
  const initialEmail = searchParams.get('email') || '';

  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: '',
      email: initialEmail,
      password: '',
      confirmPassword: '',
    },
  });

  useEffect(() => {
    if (initialEmail) {
      setValue('email', initialEmail);
    }
  }, [initialEmail, setValue]);

  const passwordValue = watch('password', '');

  // Calculate password strength
  const getPasswordStrength = (pass: string) => {
    let score = 0;
    if (pass.length >= 8) score += 1;
    if (/[A-Z]/.test(pass)) score += 1;
    if (/[0-9]/.test(pass)) score += 1;
    if (/[^A-Za-z0-9]/.test(pass)) score += 1;
    return score;
  };

  const strengthScore = getPasswordStrength(passwordValue);
  const strengthLabels = ['Too weak', 'Fair', 'Good', 'Strong'];
  const strengthColors = ['bg-rose-500', 'bg-amber-500', 'bg-indigo-500', 'bg-emerald-500'];

  const onSubmit = async (data: RegisterFormData) => {
    setLoading(true);
    try {
      const res = await authApi.register({
        fullName: data.fullName,
        email: data.email,
        password: data.password,
        inviteToken,
      });
      setAuth(res);
      toast.success('Account created successfully!', 'Welcome to KnowFlow AI.');
      navigate('/');
    } catch (err: any) {
      toast.error(
        'Registration failed',
        err.response?.data?.message || err.message || 'Could not create account.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8 text-slate-100">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-cyan-400 shadow-xl shadow-indigo-500/25 mb-4">
          <Sparkles className="w-6 h-6 text-white" />
        </div>
        <h2 className="text-3xl font-extrabold tracking-tight text-white">
          Create your enterprise account
        </h2>
        <p className="mt-2 text-sm text-slate-400">
          {inviteToken
            ? 'Complete registration to claim your workspace access.'
            : 'Get started with enterprise RAG intelligence in seconds.'}
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl py-8 px-6 shadow-2xl backdrop-blur-xl sm:px-10">
          {inviteToken && (
            <div className="mb-5 p-3 rounded-xl bg-indigo-950/60 border border-indigo-800/80 text-xs text-indigo-300 flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-indigo-400 shrink-0" />
              <span>Workspace invitation linked! You will be automatically added upon registration.</span>
            </div>
          )}

          <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
            <Input
              label="Full Name"
              type="text"
              placeholder="Sarah Connor"
              leftIcon={<User className="w-4 h-4 text-slate-400" />}
              error={errors.fullName?.message}
              {...register('fullName')}
            />

            <Input
              label="Work Email"
              type="email"
              placeholder="sarah@enterprise.com"
              leftIcon={<Mail className="w-4 h-4 text-slate-400" />}
              error={errors.email?.message}
              {...register('email')}
            />

            <div>
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

              {/* Password strength bar */}
              {passwordValue && (
                <div className="mt-2 space-y-1">
                  <div className="flex gap-1 h-1.5">
                    {[0, 1, 2, 3].map((step) => (
                      <div
                        key={step}
                        className={`h-full flex-1 rounded-full transition-all duration-300 ${
                          step < strengthScore ? strengthColors[strengthScore - 1] : 'bg-slate-800'
                        }`}
                      />
                    ))}
                  </div>
                  <div className="flex justify-between items-center text-[10px] text-slate-400">
                    <span>Must include uppercase & number</span>
                    <span className="font-semibold text-slate-300">
                      {strengthLabels[strengthScore - 1] || 'Too weak'}
                    </span>
                  </div>
                </div>
              )}
            </div>

            <Input
              label="Confirm Password"
              type={showConfirmPassword ? 'text' : 'password'}
              placeholder="••••••••"
              leftIcon={<Lock className="w-4 h-4 text-slate-400" />}
              rightIcon={
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="text-slate-400 hover:text-slate-200 transition"
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              }
              error={errors.confirmPassword?.message}
              {...register('confirmPassword')}
            />

            <div className="flex items-start pt-1">
              <div className="flex items-center h-5">
                <input
                  id="terms"
                  type="checkbox"
                  className="w-4 h-4 rounded border-slate-700 bg-slate-800 text-indigo-500 focus:ring-indigo-500 focus:ring-offset-slate-900"
                  {...register('terms')}
                />
              </div>
              <div className="ml-3 text-xs">
                <label htmlFor="terms" className="text-slate-400">
                  I agree to the{' '}
                  <span className="text-indigo-400 hover:text-indigo-300 cursor-pointer">
                    Enterprise Master Services Agreement
                  </span>{' '}
                  and Privacy Policy.
                </label>
                {errors.terms && (
                  <p className="text-rose-500 text-xs mt-1">{errors.terms.message}</p>
                )}
              </div>
            </div>

            <Button
              type="submit"
              className="w-full mt-4 bg-gradient-to-r from-indigo-500 via-indigo-600 to-cyan-500 hover:from-indigo-600 hover:to-cyan-600 shadow-lg shadow-indigo-500/25 text-white font-medium"
              size="lg"
              loading={loading}
            >
              Get Started <ArrowRight className="ml-2 w-4 h-4" />
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-slate-800">
            <div className="flex items-center justify-between text-xs text-slate-400">
              <span className="flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-emerald-400" /> SOC2 Type II
              </span>
              <span className="flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-indigo-400" /> Grounded RAG
              </span>
            </div>
          </div>
        </div>

        <p className="mt-6 text-center text-sm text-slate-400">
          Already have an account?{' '}
          <Link
            to={inviteToken ? `/auth/login?invite=${inviteToken}` : '/auth/login'}
            className="font-medium text-indigo-400 hover:text-indigo-300 transition"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};
