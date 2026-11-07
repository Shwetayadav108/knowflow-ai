import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Sparkles, ArrowLeft, Mail, CheckCircle2 } from 'lucide-react';
import { authApi } from '../../api/auth';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { toast } from '../../components/ui/Toast';

const forgotSchema = z.object({
  email: z.string().email('Please enter a valid work email address'),
});

type ForgotFormData = z.infer<typeof forgotSchema>;

export const ForgotPasswordPage: React.FC = () => {
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotFormData>({
    resolver: zodResolver(forgotSchema),
  });

  const onSubmit = async (data: ForgotFormData) => {
    setLoading(true);
    try {
      await authApi.forgotPassword(data.email);
      setSubmitted(true);
      toast.success('Request received', 'If an account exists, a reset code has been sent.');
    } catch (err: any) {
      // Intentionally do not leak email existence
      setSubmitted(true);
      toast.info('Request received', 'If an account exists, a reset code has been sent.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8 text-slate-100">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 shadow-lg shadow-indigo-500/25 mb-4">
          <Sparkles className="w-6 h-6 text-white" />
        </div>
        <h2 className="text-3xl font-extrabold tracking-tight text-white">Reset Password</h2>
        <p className="mt-2 text-sm text-slate-400">
          We will send you a verification code to recover your account.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-slate-900/90 border border-slate-800 rounded-2xl py-8 px-6 shadow-2xl backdrop-blur-xl sm:px-10">
          {submitted ? (
            <div className="text-center space-y-4">
              <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <h3 className="text-lg font-semibold text-white">Check your email</h3>
              <p className="text-sm text-slate-400">
                If the email matches an active account, we have dispatched a secure recovery code.
              </p>
              <div className="pt-4">
                <Link to="/auth/reset-password">
                  <Button className="w-full">Enter Reset Code</Button>
                </Link>
              </div>
            </div>
          ) : (
            <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
              <Input
                label="Work Email"
                type="email"
                placeholder="sarah@enterprise.com"
                icon={<Mail className="w-4 h-4 text-slate-400" />}
                error={errors.email?.message}
                {...register('email')}
              />

              <Button
                type="submit"
                className="w-full mt-4 bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500"
                size="lg"
                loading={loading}
              >
                Send Instructions
              </Button>
            </form>
          )}

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
