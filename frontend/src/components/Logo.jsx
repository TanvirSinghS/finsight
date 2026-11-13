import { Link } from 'react-router-dom';

export default function Logo({ to = '/', compact = false }) {
  return (
    <Link to={to} className="inline-flex items-center gap-2.5" aria-label="FinSight home">
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-emerald-500 text-white shadow-sm">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
          <path d="M4 17V9m0 0 3.5-3.5M4 9l3.5 3.5" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="17" cy="9" r="4" />
          <path d="M12 18h9" strokeLinecap="round" />
        </svg>
      </span>
      {!compact && (
        <span className="text-lg font-extrabold tracking-tight text-slate-900 dark:text-white">
          Fin<span className="text-brand-600 dark:text-brand-400">Sight</span>
        </span>
      )}
    </Link>
  );
}
