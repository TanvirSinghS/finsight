import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, BarChart3, LockKeyhole, ShieldCheck, Sparkles, Wallet } from 'lucide-react';
import Logo from '../components/Logo.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [waking, setWaking] = useState(false);

  const signIn = async (credentials) => {
    setError('');
    setLoading(true);
    // A free-tier backend that has been idle needs a while to boot; say so
    // instead of leaving the form looking frozen.
    const slowTimer = setTimeout(() => setWaking(true), 2000);
    try {
      try {
        await login(credentials.email, credentials.password);
      } catch (err) {
        // A sleeping instance can answer with a gateway error before it is
        // listening. Signing in is safe to repeat, so retry once.
        if (err.status && err.status < 500) throw err;
        await new Promise((resolve) => setTimeout(resolve, 1500));
        await login(credentials.email, credentials.password);
      }
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(err.status >= 500
        ? 'The server is still waking up. Please try again in a few seconds.'
        : err.message || 'Could not sign in.');
    } finally {
      clearTimeout(slowTimer);
      setWaking(false);
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="w-full max-w-md">
        <div className="mb-8 lg:hidden">
          <Logo />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">Welcome back</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Sign in to see today's money picture.</p>

        <form
          className="mt-6 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            signIn({ email, password });
          }}
        >
          <div>
            <label htmlFor="email" className="label">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              required
              className="input"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="password" className="label">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              className="input"
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error && (
            <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/60 dark:text-rose-200">
              {error}
            </p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? <Spinner className="h-4 w-4" /> : <LockKeyhole className="h-4 w-4" aria-hidden="true" />}
            {loading ? 'Signing in…' : 'Sign in'}
          </button>

          {waking && loading && (
            <p role="status" className="text-center text-xs text-slate-500 dark:text-slate-400">
              Waking the server — the first sign-in after a quiet spell can take up to a minute.
            </p>
          )}
        </form>

        <div className="my-6 flex items-center gap-3 text-xs font-medium uppercase tracking-wide text-slate-400">
          <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
          or explore first
          <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
        </div>

        <button
          type="button"
          className="btn-secondary w-full"
          disabled={loading}
          onClick={() => signIn({ email: 'demo@finsight.app', password: 'Demo@1234' })}
        >
          <Sparkles className="h-4 w-4 text-brand-500" aria-hidden="true" />
          Open the demo account
        </button>
        <p className="mt-2 text-center text-xs text-slate-400">demo@finsight.app · Demo@1234</p>

        <p className="mt-8 text-center text-sm text-slate-500 dark:text-slate-400">
          New to FinSight?{' '}
          <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700 dark:text-brand-400">
            Create an account
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}

export function AuthShell({ children }) {
  return (
    <div className="grid min-h-screen bg-slate-50 dark:bg-slate-950 lg:grid-cols-[1.05fr_1fr]">
      <div className="relative hidden overflow-hidden bg-slate-900 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-brand-600/30 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-32 -right-16 h-96 w-96 rounded-full bg-emerald-500/20 blur-3xl" aria-hidden="true" />
        <div className="relative">
          <span className="inline-flex items-center gap-2.5 text-lg font-extrabold tracking-tight">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-emerald-500">
              <Wallet className="h-5 w-5" aria-hidden="true" />
            </span>
            FinSight
          </span>
        </div>
        <div className="relative max-w-lg">
          <h2 className="text-4xl font-extrabold leading-tight tracking-tight">
            Every account, every expense — finally in focus.
          </h2>
          <p className="mt-4 text-base leading-relaxed text-slate-300">
            Track spending, set budgets, hit savings goals and let the analytics engine show you what next month looks like.
          </p>
          <ul className="mt-8 space-y-4 text-sm">
            {[
              { icon: BarChart3, text: 'Live dashboard with cashflow and category breakdowns' },
              { icon: Sparkles, text: 'AI forecasts, anomaly alerts and auto-categorization' },
              { icon: ShieldCheck, text: 'JWT-secured API, per-user data isolation' },
            ].map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-start gap-3">
                <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-white/10">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </span>
                <span className="text-slate-200">{text}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="relative flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 p-4 text-sm">
          <div className="flex-1">
            <p className="font-semibold">Demo dataset ready</p>
            <p className="text-slate-300">12 months of transactions, budgets, goals and one suspicious laptop.</p>
          </div>
          <ArrowRight className="h-5 w-5 shrink-0 text-slate-300" aria-hidden="true" />
        </div>
      </div>
      <div className="flex items-center justify-center px-5 py-12 sm:px-10">{children}</div>
    </div>
  );
}
