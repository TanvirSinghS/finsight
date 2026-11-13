import { useCallback, useEffect, useState } from 'react';
import { CalendarDays, CheckCircle2, Pencil, Plus, Target, Trash2 } from 'lucide-react';
import { api } from '../api/client.js';
import { EmptyState, ErrorState, Modal, PageHeader, ProgressBar, SkeletonBlock, Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency, formatDate } from '../utils/format.js';

const COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#0ea5e9', '#8b5cf6'];

export default function Goals() {
  const { user } = useAuth();
  const toast = useToast();
  const [goals, setGoals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [contributing, setContributing] = useState(null);
  const [contribution, setContribution] = useState('');
  const [deleting, setDeleting] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const currency = user?.baseCurrency || 'USD';

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    api.get('/api/goals')
      .then(setGoals)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const save = async (payload) => {
    setSubmitting(true);
    try {
      if (editing) {
        await api.put(`/api/goals/${editing.id}`, payload);
        toast.success('Goal updated.');
      } else {
        await api.post('/api/goals', payload);
        toast.success('Goal created.');
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

  const addContribution = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/api/goals/${contributing.id}/contribute`, { amount: Number(contribution) });
      toast.success(`Added ${formatCurrency(contribution, currency)} to ${contributing.name}.`);
      setContributing(null);
      setContribution('');
      load();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async () => {
    try {
      await api.delete(`/api/goals/${deleting.id}`);
      toast.success('Goal removed.');
      setDeleting(null);
      load();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader
        title="Savings goals"
        description="Turn big plans into a progress bar you can actually see."
        actions={<button type="button" className="btn-primary" onClick={() => { setEditing(null); setFormOpen(true); }}><Plus className="h-4 w-4" aria-hidden="true" /> New goal</button>}
      />

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : loading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => <SkeletonBlock key={index} className="h-56" />)}
        </div>
      ) : goals.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Target}
            title="No goals yet"
            description="Emergency fund, a trip, a new laptop — set a target and track every contribution."
            action={<button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>Create your first goal</button>}
          />
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {goals.map((goal) => (
            <article key={goal.id} className="card flex flex-col p-5">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <span className="grid h-9 w-9 place-items-center rounded-xl text-white" style={{ backgroundColor: goal.color }}>
                    {goal.completed ? <CheckCircle2 className="h-5 w-5" aria-hidden="true" /> : <Target className="h-5 w-5" aria-hidden="true" />}
                  </span>
                  <div>
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{goal.name}</h2>
                    {goal.targetDate && (
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
                        <CalendarDays className="h-3 w-3" aria-hidden="true" /> {formatDate(goal.targetDate)}
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex gap-0.5">
                  <button type="button" className="btn-ghost p-1.5" aria-label={`Edit ${goal.name}`} onClick={() => { setEditing(goal); setFormOpen(true); }}>
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button type="button" className="btn-ghost p-1.5 text-rose-600 dark:text-rose-400" aria-label={`Delete ${goal.name}`} onClick={() => setDeleting(goal)}>
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>

              <div className="mt-4 flex items-baseline justify-between">
                <p className="text-xl font-bold text-slate-900 dark:text-white">{formatCurrency(goal.currentAmount, currency)}</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">of {formatCurrency(goal.targetAmount, currency)}</p>
              </div>
              <div className="mt-2">
                <ProgressBar value={goal.percentComplete} tone={goal.completed ? 'emerald' : 'brand'} label={`${goal.name} progress`} />
              </div>
              <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                {goal.completed ? 'Goal reached — legend.' : `${formatCurrency(goal.remaining, currency)} to go · ${goal.percentComplete}%`}
              </p>

              <button
                type="button"
                className="btn-secondary mt-4 w-full"
                onClick={() => { setContributing(goal); setContribution(''); }}
              >
                <Plus className="h-4 w-4" aria-hidden="true" /> Add contribution
              </button>
            </article>
          ))}
        </div>
      )}

      <GoalModal open={formOpen} initial={editing} onClose={() => { setFormOpen(false); setEditing(null); }} onSubmit={save} submitting={submitting} />

      <Modal
        open={Boolean(contributing)}
        title={contributing ? `Contribute to ${contributing.name}` : 'Contribute'}
        description={contributing ? `${formatCurrency(contributing.remaining, currency)} remaining to reach the goal.` : ''}
        onClose={() => setContributing(null)}
      >
        <form onSubmit={addContribution} className="space-y-4">
          <div>
            <label htmlFor="contribution" className="label">Amount ({currency})</label>
            <input id="contribution" type="number" min="0.01" step="0.01" required className="input" value={contribution} onChange={(event) => setContribution(event.target.value)} placeholder="100.00" autoFocus />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setContributing(null)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={submitting}>{submitting && <Spinner className="h-4 w-4" />} Add funds</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={Boolean(deleting)}
        title="Delete goal?"
        onClose={() => setDeleting(null)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={remove}>Delete</button>
          </div>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">{deleting?.name} and its saved progress will be removed. This does not touch any account balances.</p>
      </Modal>
    </>
  );
}

function GoalModal({ open, initial, onClose, onSubmit, submitting }) {
  const [form, setForm] = useState({ name: '', targetAmount: '', currentAmount: '0', targetDate: '', color: COLORS[0] });

  useEffect(() => {
    if (!open) return;
    setForm({
      name: initial?.name || '',
      targetAmount: initial ? String(initial.targetAmount) : '',
      currentAmount: initial ? String(initial.currentAmount) : '0',
      targetDate: initial?.targetDate || '',
      color: initial?.color || COLORS[0],
    });
  }, [open, initial]);

  const update = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));

  return (
    <Modal open={open} title={initial ? 'Edit goal' : 'New savings goal'} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit({
            name: form.name.trim(),
            targetAmount: Number(form.targetAmount),
            currentAmount: Number(form.currentAmount || 0),
            targetDate: form.targetDate || null,
            color: form.color,
          });
        }}
      >
        <div>
          <label htmlFor="goal-name" className="label">Goal name</label>
          <input id="goal-name" className="input" required maxLength={100} value={form.name} onChange={update('name')} placeholder="Emergency fund" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="goal-target" className="label">Target amount</label>
            <input id="goal-target" type="number" min="1" step="0.01" required className="input" value={form.targetAmount} onChange={update('targetAmount')} placeholder="10000" />
          </div>
          <div>
            <label htmlFor="goal-current" className="label">Already saved</label>
            <input id="goal-current" type="number" min="0" step="0.01" className="input" value={form.currentAmount} onChange={update('currentAmount')} />
          </div>
        </div>
        <div>
          <label htmlFor="goal-date" className="label">Target date <span className="font-normal text-slate-400">(optional)</span></label>
          <input id="goal-date" type="date" className="input" value={form.targetDate} onChange={update('targetDate')} />
        </div>
        <fieldset>
          <legend className="label">Accent colour</legend>
          <div className="flex gap-2">
            {COLORS.map((color) => (
              <button
                key={color}
                type="button"
                onClick={() => setForm((prev) => ({ ...prev, color }))}
                aria-label={`Use colour ${color}`}
                aria-pressed={form.color === color}
                className={`h-8 w-8 rounded-full transition ${form.color === color ? 'ring-2 ring-offset-2 ring-slate-900 dark:ring-white dark:ring-offset-slate-900' : ''}`}
                style={{ backgroundColor: color }}
              />
            ))}
          </div>
        </fieldset>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {initial ? 'Save goal' : 'Create goal'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
