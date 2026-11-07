import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';
import { Button } from '../components/ui/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-6 text-center text-slate-100">
      <div className="w-16 h-16 rounded-2xl bg-indigo-600/20 text-indigo-400 flex items-center justify-center mb-6">
        <Compass className="w-8 h-8 animate-spin-slow" />
      </div>
      <h1 className="text-6xl font-extrabold tracking-tight text-white">404</h1>
      <h2 className="text-xl font-semibold text-slate-200 mt-2">Page Not Found</h2>
      <p className="text-sm text-slate-400 max-w-sm mt-2 mb-8">
        The knowledge artifact or endpoint you are looking for does not exist in this realm.
      </p>
      <Link to="/">
        <Button className="inline-flex items-center gap-2">
          <ArrowLeft className="w-4 h-4" /> Return to Dashboard
        </Button>
      </Link>
    </div>
  );
};
