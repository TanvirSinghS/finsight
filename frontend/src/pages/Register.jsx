import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import Logo from '../components/Logo.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { AuthShell } from './Login.jsx';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'CAD', 'AUD', 'JPY', 'CHF', 'SGD', 'NZD'];

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', baseCurrency: 'USD' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const update = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register({ ...form, fullName: form.fullName.trim(), email: form.email.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message || 'Could not create your account.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="w-full max-w-md">
        <div className="mb-8 lg:hidden">
          <Logo />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">Create your account</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          You start with sensible categories and a checking account — no setup wizard.
        </p>

        <form className="mt-6 space-y-4" onSubmit={submit}>
          <div>
            <label htmlFor="fullName" className="label">Full name</label>
            <input id="fullName" className="input" required maxLength={120} autoComplete="name" value={form.fullName} onChange={update('fullName')} placeholder="Alex Morgan" />
          </div>
          <div>
            <label htmlFor="email" className="label">Email</label>
            <input id="email" type="email" className="input" required autoComplete="email" value={form.email} onChange={update('email')} placeholder="you@example.com" />
          </div>
          <div>
            <label htmlFor="password" className="label">Password</label>
            <input id="password" type="password" className="input" required minLength={8} maxLength={72} autoComplete="new-password" value={form.password} onChange={update('password')} placeholder="At least 8 characters" />
            <p className="mt-1 text-xs text-slate-500">Hashed with BCrypt. Minimum 8 characters.</p>
          </div>
          <div>
            <label htmlFor="baseCurrency" className="label">Base currency</label>
            <select id="baseCurrency" className="input" value={form.baseCurrency} onChange={update('baseCurrency')}>
              {CURRENCIES.map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
            <p className="mt-1 text-xs text-slate-500">All totals are converted to this currency using live FX rates.</p>
          </div>

          {error && (
            <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/60 dark:text-rose-200">
              {error}
            </p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? <Spinner className="h-4 w-4" /> : <UserPlus className="h-4 w-4" aria-hidden="true" />}
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-8 text-center text-sm text-slate-500 dark:text-slate-400">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700 dark:text-brand-400">
            Sign in
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}
