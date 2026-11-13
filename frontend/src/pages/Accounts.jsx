import { useCallback, useEffect, useState } from 'react';
import { Archive, Banknote, CreditCard, Landmark, Pencil, Plus, RefreshCw, Wallet } from 'lucide-react';
import { api } from '../api/client.js';
import { EmptyState, ErrorState, Modal, PageHeader, SkeletonBlock, Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { classNames, formatCurrency } from '../utils/format.js';

const TYPES = [
  { value: 'CHECKING', label: 'Checking', icon: Landmark },
  { value: 'SAVINGS', label: 'Savings', icon: PiggyBankIcon },
  { value: 'CREDIT', label: 'Credit card', icon: CreditCard },
  { value: 'CASH', label: 'Cash', icon: Banknote },
  { value: 'INVESTMENT', label: 'Investment', icon: Wallet },
];

const DISPLAY_RATES = ['USD', 'EUR', 'GBP', 'INR', 'JPY', 'CAD', 'AUD', 'SGD'];

export default function Accounts() {
  const { user } = useAuth();
  const toast = useToast();
  const [accounts, setAccounts] = useState([]);
  const [rates, setRates] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [archiving, setArchiving] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [ratesLoading, setRatesLoading] = useState(true);

  const currency = user?.baseCurrency || 'USD';

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    api.get('/api/accounts')
      .then(setAccounts)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const loadRates = useCallback(() => {
    setRatesLoading(true);
    api.get(`/api/currency/rates?base=${currency}`)
      .then(setRates)
      .catch(() => setRates(null))
      .finally(() => setRatesLoading(false));
  }, [currency]);

  useEffect(load, [load]);
  useEffect(loadRates, [loadRates]);

  const save = async (payload) => {
    setSubmitting(true);
    try {
      if (editing) {
        await api.put(`/api/accounts/${editing.id}`, payload);
        toast.success('Account updated.');
      } else {
        await api.post('/api/accounts', payload);
        toast.success('Account created.');
      }
      setFormOpen(false);
      setEditing(null);
      load();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const archive = async () => {
    try {
      await api.delete(`/api/accounts/${archiving.id}`);
      toast.success('Account archived.');
      setArchiving(null);
      load();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const total = accounts.reduce((sum, account) => sum + Number(account.balance), 0);

  return (
    <>
      <PageHeader
        title="Accounts"
        description="Every balance in one place, converted with live exchange rates."
        actions={<button type="button" className="btn-primary" onClick={() => { setEditing(null); setFormOpen(true); }}><Plus className="h-4 w-4" aria-hidden="true" /> New account</button>}
      />

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : loading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 4 }).map((_, index) => <SkeletonBlock key={index} className="h-36" />)}
        </div>
      ) : accounts.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Landmark}
            title="No accounts yet"
            description="Add a checking account, savings or credit card to start tracking balances."
            action={<button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>Create an account</button>}
          />
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {accounts.map((account) => {
            const meta = TYPES.find((type) => type.value === account.type) || TYPES[0];
            const Icon = meta.icon;
            return (
              <article key={account.id} className="card p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <span className="grid h-10 w-10 place-items-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
                      <Icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <div>
                      <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{account.name}</h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{meta.label} · {account.currency}</p>
                    </div>
                  </div>
                  <div className="flex gap-0.5">
                    <button type="button" className="btn-ghost p-1.5" aria-label={`Edit ${account.name}`} onClick={() => { setEditing(account); setFormOpen(true); }}>
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button type="button" className="btn-ghost p-1.5 text-rose-600 dark:text-rose-400" aria-label={`Archive ${account.name}`} onClick={() => setArchiving(account)}>
                      <Archive className="h-4 w-4" />
                    </button>
                  </div>
                </div>
                <p className={classNames('mt-4 text-2xl font-bold', account.balance < 0 ? 'text-rose-600 dark:text-rose-400' : 'text-slate-900 dark:text-white')}>
                  {formatCurrency(account.balance, currency)}
                </p>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Opening balance {formatCurrency(account.initialBalance, account.currency)}
                </p>
              </article>
            );
          })}
          <article className="card flex flex-col justify-center bg-gradient-to-br from-brand-600 to-brand-800 p-5 text-white">
            <p className="text-sm font-medium text-brand-100">Net worth</p>
            <p className="mt-1 text-2xl font-bold">{formatCurrency(total, currency)}</p>
            <p className="mt-1 text-xs text-brand-100/80">Sum of all active balances in {currency}</p>
          </article>
        </div>
      )}

      <section className="card mt-4 p-5" aria-label="Exchange rates">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">Live exchange rates</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">Frankfurter API · base {currency}{rates?.date ? ` · ${rates.date}` : ''}</p>
          </div>
          <div className="flex items-center gap-2">
            {rates?.cached && <span className="badge bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">Cached</span>}
            {rates?.stale && <span className="badge bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">Offline fallback</span>}
            <button type="button" className="btn-ghost p-2" onClick={loadRates} aria-label="Refresh exchange rates">
              <RefreshCw className={classNames('h-4 w-4', ratesLoading && 'animate-spin')} />
            </button>
          </div>
        </div>
        {ratesLoading ? (
          <div className="mt-4 grid gap-3 sm:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => <SkeletonBlock key={index} className="h-16" />)}
          </div>
        ) : rates ? (
          <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {DISPLAY_RATES.filter((code) => code !== currency).map((code) => {
              const rate = rates.rates?.[code];
              return (
                <div key={code} className="rounded-xl border border-slate-200 px-3.5 py-3 dark:border-slate-800">
                  <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">1 {currency} =</dt>
                  <dd className="mt-1 text-lg font-bold text-slate-900 dark:text-white">
                    {rate ? `${Number(rate).toLocaleString(undefined, { maximumFractionDigits: 4 })} ${code}` : '—'}
                  </dd>
                </div>
              );
            })}
          </dl>
        ) : (
          <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">Exchange rates are unavailable right now. Balances still use your stored conversion rates.</p>
        )}
      </section>

      <AccountModal open={formOpen} initial={editing} baseCurrency={currency} onClose={() => { setFormOpen(false); setEditing(null); }} onSubmit={save} submitting={submitting} />

      <Modal
        open={Boolean(archiving)}
        title="Archive account?"
        onClose={() => setArchiving(null)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setArchiving(null)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={archive}>Archive</button>
          </div>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {archiving?.name} will be hidden from pickers and totals. Its transactions remain in your history.
        </p>
      </Modal>
    </>
  );
}

function PiggyBankIcon(props) {
  return <Wallet {...props} />;
}

function AccountModal({ open, initial, baseCurrency, onClose, onSubmit, submitting }) {
  const [form, setForm] = useState({ name: '', type: 'CHECKING', currency: baseCurrency, initialBalance: '0' });

  useEffect(() => {
    if (!open) return;
    setForm({
      name: initial?.name || '',
      type: initial?.type || 'CHECKING',
      currency: initial?.currency || baseCurrency,
      initialBalance: initial ? String(initial.initialBalance) : '0',
    });
  }, [open, initial, baseCurrency]);

  const update = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));

  return (
    <Modal open={open} title={initial ? 'Edit account' : 'New account'} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit({ ...form, name: form.name.trim(), initialBalance: Number(form.initialBalance || 0) });
        }}
      >
        <div>
          <label htmlFor="account-name" className="label">Account name</label>
          <input id="account-name" className="input" required maxLength={80} value={form.name} onChange={update('name')} placeholder="Everyday Checking" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="account-type" className="label">Type</label>
            <select id="account-type" className="input" value={form.type} onChange={update('type')}>
              {TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="account-currency" className="label">Currency</label>
            <input id="account-currency" className="input uppercase" maxLength={3} value={form.currency} onChange={update('currency')} />
          </div>
        </div>
        <div>
          <label htmlFor="account-opening" className="label">Opening balance</label>
          <input id="account-opening" type="number" step="0.01" className="input" value={form.initialBalance} onChange={update('initialBalance')} />
          <p className="mt-1 text-xs text-slate-500">Current balance = opening balance + all transactions.</p>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {initial ? 'Save account' : 'Create account'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
