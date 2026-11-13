import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="grid min-h-screen place-items-center bg-slate-50 px-6 dark:bg-slate-950">
      <div className="text-center">
        <p className="text-sm font-semibold uppercase tracking-widest text-brand-600 dark:text-brand-400">404</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900 dark:text-white">This page moved off the ledger</h1>
        <p className="mx-auto mt-2 max-w-md text-sm text-slate-500 dark:text-slate-400">
          The link may be old, or the page never existed. Your transactions are safe.
        </p>
        <Link to="/" className="btn-primary mt-6">Back to dashboard</Link>
      </div>
    </div>
  );
}
