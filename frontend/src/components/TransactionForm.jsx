import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { api } from '../api/client.js';
import { Spinner } from './ui.jsx';
import { todayValue } from '../utils/format.js';

const EMPTY = {
  type: 'EXPENSE',
  description: '',
  merchant: '',
  amount: '',
  accountId: '',
  categoryId: '',
  currency: '',
  occurredOn: todayValue(),
  notes: '',
};

export default function TransactionForm({ initial, accounts, categories, baseCurrency, onSubmit, onCancel, submitting }) {
  const [form, setForm] = useState(EMPTY);
  const [suggestion, setSuggestion] = useState(null);
  const [suggesting, setSuggesting] = useState(false);

  useEffect(() => {
    if (initial) {
      setForm({
        type: initial.type,
        description: initial.description || '',
        merchant: initial.merchant || '',
        amount: String(initial.amount ?? ''),
        accountId: initial.accountId || '',
        categoryId: initial.categoryId || '',
        currency: initial.currency || baseCurrency,
        occurredOn: initial.occurredOn,
        notes: initial.notes || '',
      });
    } else {
      setForm({
        ...EMPTY,
        accountId: accounts[0]?.id || '',
        currency: baseCurrency,
      });
    }
  }, [initial, accounts, baseCurrency]);

  const relevantCategories = categories.filter((category) =>
    form.type === 'INCOME' ? category.kind === 'INCOME' : category.kind === 'EXPENSE',
  );

  const update = (field) => (event) => {
    const value = event.target.value;
    setForm((prev) => {
      const next = { ...prev, [field]: value };
      if (field === 'type') next.categoryId = '';
      return next;
    });
  };

  const suggestCategory = async () => {
    if (!form.description.trim()) return;
    setSuggesting(true);
    setSuggestion(null);
    try {
      const result = await api.post('/api/insights/categorize', { description: form.description.trim() });
      const match = relevantCategories.find((category) => category.name === result.category);
      if (match) {
        setSuggestion({ ...result, categoryId: match.id });
      }
    } catch {
      setSuggestion({ error: true });
    } finally {
      setSuggesting(false);
    }
  };

  const submit = (event) => {
    event.preventDefault();
    onSubmit({
      accountId: form.accountId,
      categoryId: form.categoryId || null,
      type: form.type,
      amount: Number(form.amount),
      currency: form.currency || baseCurrency,
      description: form.description.trim(),
      merchant: form.merchant.trim() || null,
      notes: form.notes.trim() || null,
      occurredOn: form.occurredOn,
    });
  };

  return (
    <form id="transaction-form" onSubmit={submit} className="space-y-4">
      <fieldset>
        <legend className="label">Type</legend>
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Transaction type">
          {['EXPENSE', 'INCOME'].map((type) => (
            <button
              key={type}
              type="button"
              role="radio"
              aria-checked={form.type === type}
              onClick={() => setForm((prev) => ({ ...prev, type, categoryId: '' }))}
              className={
                form.type === type
                  ? 'rounded-xl border-2 border-brand-500 bg-brand-50 px-3 py-2 text-sm font-semibold text-brand-700 dark:bg-brand-500/10 dark:text-brand-300'
                  : 'rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800'
              }
            >
              {type === 'EXPENSE' ? 'Expense' : 'Income'}
            </button>
          ))}
        </div>
      </fieldset>

      <div>
        <label htmlFor="tx-description" className="label">Description</label>
        <input
          id="tx-description"
          className="input"
          required
          maxLength={255}
          value={form.description}
          onChange={update('description')}
          onBlur={suggestCategory}
          placeholder="e.g. Weekly groceries at Trader Joe's"
        />
        {suggesting && <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500"><Spinner className="h-3 w-3" /> Asking the AI for a category…</p>}
        {suggestion && !suggestion.error && !suggestion.categoryId && (
          <p className="mt-1 text-xs text-slate-500">No confident suggestion — pick a category below.</p>
        )}
        {suggestion?.categoryId && (
          <button
            type="button"
            onClick={() => {
              setForm((prev) => ({ ...prev, categoryId: suggestion.categoryId }));
              setSuggestion(null);
            }}
            className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-brand-50 px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100 dark:bg-brand-500/10 dark:text-brand-300"
          >
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
            AI suggests {suggestion.category} ({Math.round((suggestion.confidence || 0) * 100)}%) — apply
          </button>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="tx-amount" className="label">Amount</label>
          <input id="tx-amount" type="number" inputMode="decimal" min="0.01" step="0.01" required className="input" value={form.amount} onChange={update('amount')} placeholder="0.00" />
        </div>
        <div>
          <label htmlFor="tx-currency" className="label">Currency</label>
          <input id="tx-currency" className="input uppercase" maxLength={3} value={form.currency} onChange={update('currency')} placeholder={baseCurrency} />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="tx-account" className="label">Account</label>
          <select id="tx-account" className="input" required value={form.accountId} onChange={update('accountId')}>
            <option value="" disabled>Select an account</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>{account.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="tx-category" className="label">Category</label>
          <select id="tx-category" className="input" value={form.categoryId} onChange={update('categoryId')}>
            <option value="">Uncategorized</option>
            {relevantCategories.map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="tx-date" className="label">Date</label>
          <input id="tx-date" type="date" required max={todayValue()} className="input" value={form.occurredOn} onChange={update('occurredOn')} />
        </div>
        <div>
          <label htmlFor="tx-merchant" className="label">Merchant <span className="font-normal text-slate-400">(optional)</span></label>
          <input id="tx-merchant" className="input" maxLength={120} value={form.merchant} onChange={update('merchant')} />
        </div>
      </div>

      <div>
        <label htmlFor="tx-notes" className="label">Notes <span className="font-normal text-slate-400">(optional)</span></label>
        <textarea id="tx-notes" className="input min-h-20" maxLength={2000} value={form.notes} onChange={update('notes')} />
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          {initial ? 'Save changes' : 'Add transaction'}
        </button>
      </div>
    </form>
  );
}
