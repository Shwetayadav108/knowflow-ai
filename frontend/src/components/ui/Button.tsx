import React from 'react';
import { cn } from '../../lib/utils';
import { Loader2 } from 'lucide-react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'danger';
  size?: 'sm' | 'md' | 'lg' | 'icon';
  isLoading?: boolean;
  loading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = 'primary',
      size = 'md',
      isLoading = false,
      loading = false,
      children,
      disabled,
      ...props
    },
    ref
  ) => {
    const isSpinner = isLoading || loading;
    const baseStyles =
      'inline-flex items-center justify-center font-medium transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:opacity-50 disabled:pointer-events-none rounded-lg select-none active:scale-[0.98]';

    const normalizedVariant = variant === 'danger' ? 'destructive' : variant;

    const variants: Record<string, string> = {
      primary:
        'bg-indigo-600 text-white hover:bg-indigo-700 shadow-sm shadow-indigo-600/30 dark:bg-indigo-500 dark:hover:bg-indigo-600',
      secondary:
        'bg-slate-100 text-slate-800 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700',
      outline:
        'border border-slate-200 bg-transparent text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60',
      ghost:
        'bg-transparent text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800/60',
      destructive: 'bg-rose-600 text-white hover:bg-rose-700 shadow-sm shadow-rose-600/30',
    };

    const sizes = {
      sm: 'text-xs px-2.5 py-1.5 gap-1.5 h-8',
      md: 'text-sm px-4 py-2 gap-2 h-9',
      lg: 'text-base px-5 py-2.5 gap-2.5 h-11',
      icon: 'h-9 w-9 p-0',
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isSpinner}
        className={cn(baseStyles, variants[normalizedVariant] || variants.primary, sizes[size], className)}
        {...props}
      >
        {isSpinner && <Loader2 className="w-4 h-4 animate-spin shrink-0 mr-1.5" />}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
