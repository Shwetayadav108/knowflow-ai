import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Sparkles, ArrowLeft, KeyRound } from 'lucide-react';
import { authApi } from '../../api/auth';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { toast } from '../../components/ui/Toast';

const resetSchema = z.object({
  email: z.string().email('Please enter a valid work email address'),
  code: z.string().min(6, 'Verification code must be at least 6 digits'),
  newPassword: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .regex(/[A-Z]/, 'Must contain at least one uppercase letter')
    .regex(/[0-9]/, 'Must contain at least one number'),
});

type ResetFormData = z.infer<typeof resetSchema>;

export const ResetPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetFormData>({
    resolver: zodResolver(resetSchema),
  });

  const onSubmit = async (data: ResetFormData) => {
    setLoading(true);
    try {
      await authApi.resetPassword({
        email: data.email,
        code: data.code,
        newPassword: data.newPassword,
      });
      toast.success('Password updated!', 'You can now sign in with your new password.');
      navigate('/auth/login');
    } catch (err: any) {
      toast.error(
        'Reset failed',
        err.response?.data?.message || err.message || 'Invalid or expired code.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8 text-slate-100">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 shadow-lg shadow-indigo-500/25 mb-4">
          <KeyRound className="w-6 h-6 text-white" />
        </div>
        <h2 className="text-3xl font-extrabold tracking-tight text-white">Create New Password</h2>
        <p className="mt-2 text-sm text-slate-400">
          Enter your email, the code sent to you, and your new password.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-slate-900/90 border border-slate-800 rounded-2xl py-8 px-6 shadow-2xl backdrop-blur-xl sm:px-10">
          <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
            <Input
              label="Work Email"
              type="email"
              placeholder="sarah@enterprise.com"
              error={errors.email?.message}
              {...register('email')}
            />

            <Input
              label="Verification Code"
              type="text"
              placeholder="123456"
              error={errors.code?.message}
              {...register('code')}
            />

            <Input
              label="New Password"
              type="password"
              placeholder="••••••••"
              helperText="Min 8 chars, 1 uppercase & 1 number"
              error={errors.newPassword?.message}
              {...register('newPassword')}
            />

            <Button
              type="submit"
              className="w-full mt-4 bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500"
              size="lg"
              loading={loading}
            >
              Update Password
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-slate-800 text-center">
            <Link
              to="/auth/login"
              className="inline-flex items-center text-sm font-medium text-slate-400 hover:text-white"
            >
              <ArrowLeft className="w-4 h-4 mr-2" /> Back to sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
