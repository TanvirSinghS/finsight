import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Pencil, Plus, Trash2 } from 'lucide-react';
import { api } from '../api/client.js';
import { budgetTone, EmptyState, ErrorState, Modal, PageHeader, ProgressBar, SkeletonBlock } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { currentMonthValue, formatCurrency, formatMonth } from '../utils/format.js';
import { Spinner } from '../components/ui.jsx';

export default function Budgets() {
  const { user } = useAuth();
  const toast = useToast();
  const [month, setMonth] = useState(currentMonthValue());
  const [budgets, setBudgets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const currency = user?.baseCurrency || 'USD';

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    api
      .get(`/api/budgets?month=${month}`)
      .then(setBudgets)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [month]);

  useEffect(load, [load]);

  useEffect(() => {
    api.get('/api/categories').then(setCategories).catch(() => {});
  }, []);

  const save = async (payload) => {
    setSubmitting(true);
    try {
      if (editing) {
        await api.put(`/api/budgets/${editing.id}`, payload);
        toast.success('Budget updated.');
      } else {
        await api.post('/api/budgets', payload);
        toast.success('Budget created.');
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
      await api.delete(`/api/budgets/${deleting.id}`);
      toast.success('Budget removed.');
      setDeleting(null);
      load();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const totals = budgets.reduce(
    (acc, budget) => ({ amount: acc.amount + budget.amount, spent: acc.spent + budget.spent }),
    { amount: 0, spent: 0 },
  );
  const overBudget = budgets.filter((budget) => budget.overBudget);

  return (
    <>
      <PageHeader
        title="Budgets"
        description="Give every category a ceiling and watch the pacing."
        actions={
          <>
            <div>
              <label htmlFor="budget-month" className="sr-only">Budget month</label>
              <input id="budget-month" type="month" className="input py-2" value={month} onChange={(event) => setMonth(event.target.value)} />
            </div>
            <button type="button" className="btn-primary" onClick={() => { setEditing(null); setFormOpen(true); }}>
              <Plus className="h-4 w-4" aria-hidden="true" /> New budget
            </button>
          </>
        }
      />

      {overBudget.length > 0 && (
        <div role="alert" className="mb-4 flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <p>
            Over budget in {overBudget.map((budget) => budget.categoryName).join(', ')} for {formatMonth(month)}. Consider shifting spend or raising the limit.
          </p>
        </div>
      )}

      <section className="mb-4 grid gap-4 sm:grid-cols-3" aria-label="Budget summary">
        <div className="card p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Budgeted</p>
          <p className="mt-1 text-xl font-bold text-slate-900 dark:text-white">{formatCurrency(totals.amount, currency)}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Spent</p>
          <p className="mt-1 text-xl font-bold text-slate-900 dark:text-white">{formatCurrency(totals.spent, currency)}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Remaining</p>
          <p className={`mt-1 text-xl font-bold ${totals.amount - totals.spent < 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
            {formatCurrency(totals.amount - totals.spent, currency)}
          </p>
        </div>
      </section>

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : loading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => <SkeletonBlock key={index} className="h-40" />)}
        </div>
      ) : budgets.length === 0 ? (
        <div className="card">
          <EmptyState
            title={`No budgets for ${formatMonth(month)}`}
            description="Create a monthly limit for a category and FinSight will track spending against it."
            action={<button type="button" className="btn-primary" onClick={() => { setEditing(null); setFormOpen(true); }}>Create a budget</button>}
          />
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {budgets.map((budget) => {
            const tone = budgetTone(budget.percentUsed);
            return (
              <article key={budget.id} className="card flex flex-col p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="h-3 w-3 rounded-full" style={{ backgroundColor: budget.categoryColor }} aria-hidden="true" />
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{budget.categoryName}</h2>
                  </div>
                  <div className="flex gap-0.5">
                    <button type="button" className="btn-ghost p-1.5" aria-label={`Edit ${budget.categoryName} budget`} onClick={() => { setEditing(budget); setFormOpen(true); }}>
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button type="button" className="btn-ghost p-1.5 text-rose-600 dark:text-rose-400" aria-label={`Delete ${budget.categoryName} budget`} onClick={() => setDeleting(budget)}>
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>

                <div className="mt-4 flex items-baseline justify-between">
                  <p className="text-lg font-bold text-slate-900 dark:text-white">{formatCurrency(budget.spent, currency)}</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400">of {formatCurrency(budget.amount, currency)}</p>
                </div>
                <div className="mt-2">
                  <ProgressBar value={budget.percentUsed} tone={tone} label={`${budget.categoryName} budget usage`} />
                </div>
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className={budget.overBudget ? 'font-semibold text-rose-600 dark:text-rose-400' : 'text-slate-500 dark:text-slate-400'}>
                    {budget.overBudget
                      ? `${formatCurrency(Math.abs(budget.remaining), currency)} over`
                      : `${formatCurrency(budget.remaining, currency)} left`}
                  </span>
                  <span className="font-semibold text-slate-600 dark:text-slate-300">{budget.percentUsed}%</span>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <BudgetModal
        open={formOpen}
        initial={editing}
        month={month}
        categories={categories.filter((category) => category.kind === 'EXPENSE')}
        onClose={() => { setFormOpen(false); setEditing(null); }}
        onSubmit={save}
        submitting={submitting}
      />

      <Modal
        open={Boolean(deleting)}
        title="Delete budget?"
        onClose={() => setDeleting(null)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={remove}>Delete</button>
          </div>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          The {deleting?.categoryName} budget for {formatMonth(month)} will be removed. Transactions are untouched.
        </p>
      </Modal>
    </>
  );
}

function BudgetModal({ open, initial, month, categories, onClose, onSubmit, submitting }) {
  const [categoryId, setCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [period, setPeriod] = useState(month);

  useEffect(() => {
    if (!open) return;
    setCategoryId(initial?.categoryId || '');
    setAmount(initial ? String(initial.amount) : '');
    setPeriod(initial?.periodStart?.slice(0, 7) || month);
  }, [open, initial, month]);

  return (
    <Modal open={open} title={initial ? 'Edit budget' : 'New budget'} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit({ categoryId, amount: Number(amount), periodStart: `${period}-01` });
        }}
      >
        <div>
          <label htmlFor="budget-category" className="label">Category</label>
          <select id="budget-category" className="input" required value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="" disabled>Choose an expense category</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </select>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="budget-amount" className="label">Monthly limit</label>
            <input id="budget-amount" type="number" min="0.01" step="0.01" required className="input" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="300.00" />
          </div>
          <div>
            <label htmlFor="budget-period" className="label">Month</label>
            <input id="budget-period" type="month" required className="input" value={period} onChange={(event) => setPeriod(event.target.value)} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {initial ? 'Save budget' : 'Create budget'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
