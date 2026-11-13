import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, PauseCircle, Pencil, PlayCircle, Plus, Repeat2, Trash2, Zap } from 'lucide-react';
import { api } from '../api/client.js';
import { EmptyState, ErrorState, Modal, PageHeader, SkeletonBlock, Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency, formatDate, todayValue } from '../utils/format.js';
import { classNames } from '../utils/format.js';

const FREQUENCIES = [
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
];

export default function Recurring() {
  const { user } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [running, setRunning] = useState(false);

  const currency = user?.baseCurrency || 'USD';

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    Promise.all([api.get('/api/recurring'), api.get('/api/accounts'), api.get('/api/categories')])
      .then(([recurring, accountList, categoryList]) => {
        setItems(recurring);
        setAccounts(accountList);
        setCategories(categoryList);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const save = async (payload) => {
    setSubmitting(true);
    try {
      if (editing) {
        await api.put(`/api/recurring/${editing.id}`, payload);
        toast.success('Recurring transaction updated.');
      } else {
        await api.post('/api/recurring', payload);
        toast.success('Recurring transaction created.');
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

  const remove = async () => {
    try {
      await api.delete(`/api/recurring/${deleting.id}`);
      toast.success('Recurring transaction removed.');
      setDeleting(null);
      load();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const runDue = async () => {
    setRunning(true);
    try {
      const result = await api.post('/api/recurring/run-due');
      toast.success(result.created > 0 ? `${result.created} due transaction${result.created === 1 ? '' : 's'} posted.` : 'Nothing is due right now.');
      load();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Recurring"
        description="Rent, salary, subscriptions — posted automatically every day at 06:00 UTC."
        actions={
          <>
            <button type="button" className="btn-secondary" onClick={runDue} disabled={running}>
              {running ? <Spinner className="h-4 w-4" /> : <Zap className="h-4 w-4" aria-hidden="true" />} Run due now
            </button>
            <button type="button" className="btn-primary" onClick={() => { setEditing(null); setFormOpen(true); }}>
              <Plus className="h-4 w-4" aria-hidden="true" /> New schedule
            </button>
          </>
        }
      />

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : loading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => <SkeletonBlock key={index} className="h-20" />)}
        </div>
      ) : items.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Repeat2}
            title="No recurring transactions"
            description="Add your salary or rent once and FinSight will post it on schedule."
            action={<button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>Create a schedule</button>}
          />
        </div>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {items.map((item) => (
            <li key={item.id} className="card flex flex-col gap-3 p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className={classNames('grid h-10 w-10 place-items-center rounded-xl', item.active ? 'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300' : 'bg-slate-100 text-slate-400 dark:bg-slate-800')}>
                    <CalendarClock className="h-5 w-5" aria-hidden="true" />
                  </span>
                  <div>
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{item.description}</h2>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {item.categoryName} · {item.accountName} · {FREQUENCIES.find((frequency) => frequency.value === item.frequency)?.label || item.frequency}
                    </p>
                  </div>
                </div>
                <div className="flex gap-0.5">
                  <button type="button" className="btn-ghost p-1.5" aria-label={`Edit ${item.description}`} onClick={() => { setEditing(item); setFormOpen(true); }}>
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button type="button" className="btn-ghost p-1.5 text-rose-600 dark:text-rose-400" aria-label={`Delete ${item.description}`} onClick={() => setDeleting(item)}>
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3 dark:border-slate-800">
                <div>
                  <p className="text-lg font-bold text-slate-900 dark:text-white">
                    {item.type === 'INCOME' ? '+' : '−'}{formatCurrency(item.amount, item.currency)}
                  </p>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    {item.active ? `Next run ${formatDate(item.nextRunDate)}` : 'Paused'}
                  </p>
                </div>
                <span className={classNames('badge', item.active ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400')}>
                  {item.active ? <PlayCircle className="h-3 w-3" aria-hidden="true" /> : <PauseCircle className="h-3 w-3" aria-hidden="true" />}
                  {item.active ? 'Active' : 'Paused'}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}

      <RecurringModal
        open={formOpen}
        initial={editing}
        accounts={accounts}
        categories={categories}
        baseCurrency={currency}
        onClose={() => { setFormOpen(false); setEditing(null); }}
        onSubmit={save}
        submitting={submitting}
      />

      <Modal
        open={Boolean(deleting)}
        title="Delete schedule?"
        onClose={() => setDeleting(null)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={remove}>Delete</button>
          </div>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {deleting?.description} will stop posting. Transactions already created stay in your history.
        </p>
      </Modal>
    </>
  );
}

function RecurringModal({ open, initial, accounts, categories, baseCurrency, onClose, onSubmit, submitting }) {
  const [form, setForm] = useState({
    description: '', amount: '', type: 'EXPENSE', accountId: '', categoryId: '',
    currency: baseCurrency, frequency: 'MONTHLY', nextRunDate: todayValue(), active: true,
  });

  useEffect(() => {
    if (!open) return;
    setForm({
      description: initial?.description || '',
      amount: initial ? String(initial.amount) : '',
      type: initial?.type || 'EXPENSE',
      accountId: initial?.accountId || accounts[0]?.id || '',
      categoryId: initial?.categoryId || '',
      currency: initial?.currency || baseCurrency,
      frequency: initial?.frequency || 'MONTHLY',
      nextRunDate: initial?.nextRunDate || todayValue(),
      active: initial?.active ?? true,
    });
  }, [open, initial, accounts, baseCurrency]);

  const update = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm((prev) => {
      const next = { ...prev, [field]: value };
      if (field === 'type') next.categoryId = '';
      return next;
    });
  };

  const relevant = categories.filter((category) => (form.type === 'INCOME' ? category.kind === 'INCOME' : category.kind === 'EXPENSE'));

  return (
    <Modal open={open} title={initial ? 'Edit schedule' : 'New recurring transaction'} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit({
            accountId: form.accountId,
            categoryId: form.categoryId || null,
            type: form.type,
            amount: Number(form.amount),
            currency: form.currency,
            description: form.description.trim(),
            frequency: form.frequency,
            nextRunDate: form.nextRunDate,
            active: form.active,
          });
        }}
      >
        <div>
          <label htmlFor="rec-description" className="label">Description</label>
          <input id="rec-description" className="input" required maxLength={255} value={form.description} onChange={update('description')} placeholder="Monthly rent" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="rec-type" className="label">Type</label>
            <select id="rec-type" className="input" value={form.type} onChange={update('type')}>
              <option value="EXPENSE">Expense</option>
              <option value="INCOME">Income</option>
            </select>
          </div>
          <div>
            <label htmlFor="rec-amount" className="label">Amount</label>
            <input id="rec-amount" type="number" min="0.01" step="0.01" required className="input" value={form.amount} onChange={update('amount')} />
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="rec-account" className="label">Account</label>
            <select id="rec-account" className="input" required value={form.accountId} onChange={update('accountId')}>
              {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="rec-category" className="label">Category</label>
            <select id="rec-category" className="input" value={form.categoryId} onChange={update('categoryId')}>
              <option value="">Uncategorized</option>
              {relevant.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="rec-frequency" className="label">Frequency</label>
            <select id="rec-frequency" className="input" value={form.frequency} onChange={update('frequency')}>
              {FREQUENCIES.map((frequency) => <option key={frequency.value} value={frequency.value}>{frequency.label}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="rec-date" className="label">Next run</label>
            <input id="rec-date" type="date" required className="input" value={form.nextRunDate} onChange={update('nextRunDate')} />
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="rec-currency" className="label">Currency</label>
            <input id="rec-currency" className="input uppercase" maxLength={3} value={form.currency} onChange={update('currency')} />
          </div>
          <label className="flex items-end gap-2 pb-2.5 text-sm text-slate-600 dark:text-slate-300">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" checked={form.active} onChange={update('active')} />
            Active
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {initial ? 'Save schedule' : 'Create schedule'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
