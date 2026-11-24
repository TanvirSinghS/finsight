import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Download, Pencil, Plus, Search, Trash2, Upload,
} from 'lucide-react';
import { api, queryString } from '../api/client.js';
import TransactionForm from '../components/TransactionForm.jsx';
import { EmptyState, ErrorState, Modal, PageHeader, Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { classNames, formatCurrency, formatDate, todayValue } from '../utils/format.js';

const PAGE_SIZE = 15;

export default function Transactions() {
  const { user } = useAuth();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const fileInput = useRef(null);

  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [sortBy, setSortBy] = useState('occurredOn');
  const [direction, setDirection] = useState('desc');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filters, setFilters] = useState({ type: '', categoryId: '', accountId: '', from: '', to: '' });

  const [formOpen, setFormOpen] = useState(searchParams.get('new') === '1');
  const [editing, setEditing] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [importing, setImporting] = useState(false);

  const currency = user?.baseCurrency || 'USD';

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 350);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    Promise.all([api.get('/api/accounts'), api.get('/api/categories')])
      .then(([accountList, categoryList]) => {
        setAccounts(accountList);
        setCategories(categoryList);
      })
      .catch((err) => setError(err.message));
  }, []);

  const params = useMemo(
    () => ({
      ...filters,
      search: debouncedSearch,
      page,
      size: PAGE_SIZE,
      sortBy,
      direction,
    }),
    [filters, debouncedSearch, page, sortBy, direction],
  );

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    api
      .get(`/api/transactions${queryString(params)}`)
      .then(setResult)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [params]);

  useEffect(load, [load]);

  useEffect(() => {
    setPage(0);
  }, [filters, debouncedSearch]);

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setFormOpen(true);
      setEditing(null);
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const save = async (payload) => {
    setSubmitting(true);
    try {
      if (editing) {
        await api.put(`/api/transactions/${editing.id}`, payload);
        toast.success('Transaction updated.');
      } else {
        await api.post('/api/transactions', payload);
        toast.success('Transaction added.');
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

  const confirmDelete = async () => {
    try {
      await api.delete(`/api/transactions/${deleting.id}`);
      toast.success('Transaction deleted.');
      setDeleting(null);
      load();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const exportCsv = async () => {
    try {
      const response = await api.download(`/api/transactions/export${queryString({ ...filters, search: debouncedSearch })}`);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `finsight-transactions-${todayValue()}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
      toast.success('CSV exported.');
    } catch (err) {
      toast.error(err.message);
    }
  };

  const importCsv = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setImporting(true);
    try {
      const body = new FormData();
      body.append('file', file);
      const outcome = await api.post('/api/transactions/import', body);
      toast.success(`Imported ${outcome.imported} transaction${outcome.imported === 1 ? '' : 's'}, skipped ${outcome.skipped}.`);
      if (outcome.errors?.length) toast.info(outcome.errors[0]);
      load();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setImporting(false);
    }
  };

  const toggleSort = (field) => {
    if (sortBy === field) {
      setDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortBy(field);
      setDirection('desc');
    }
    setPage(0);
  };

  const SortIcon = direction === 'asc' ? ArrowUp : ArrowDown;
  const transactions = result?.content || [];

  return (
    <>
      <PageHeader
        title="Transactions"
        description="Search, filter, edit and export every movement."
        actions={
          <>
            <input ref={fileInput} type="file" accept=".csv,text/csv" className="sr-only" onChange={importCsv} aria-label="Import CSV" />
            <button type="button" className="btn-secondary" onClick={() => fileInput.current?.click()} disabled={importing}>
              {importing ? <Spinner className="h-4 w-4" /> : <Upload className="h-4 w-4" aria-hidden="true" />} Import CSV
            </button>
            <button type="button" className="btn-secondary" onClick={exportCsv}>
              <Download className="h-4 w-4" aria-hidden="true" /> Export
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              <Plus className="h-4 w-4" aria-hidden="true" /> Add
            </button>
          </>
        }
      />

      <section className="card p-4 sm:p-5" aria-label="Filters">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="relative sm:col-span-2">
            <label htmlFor="search" className="sr-only">Search transactions</label>
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input
              id="search"
              className="input pl-9"
              placeholder="Search description or merchant…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="type-filter" className="sr-only">Type</label>
            <select id="type-filter" className="input" value={filters.type} onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value }))}>
              <option value="">All types</option>
              <option value="EXPENSE">Expenses</option>
              <option value="INCOME">Income</option>
            </select>
          </div>
          <div>
            <label htmlFor="category-filter" className="sr-only">Category</label>
            <select id="category-filter" className="input" value={filters.categoryId} onChange={(event) => setFilters((prev) => ({ ...prev, categoryId: event.target.value }))}>
              <option value="">All categories</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="account-filter" className="sr-only">Account</label>
            <select id="account-filter" className="input" value={filters.accountId} onChange={(event) => setFilters((prev) => ({ ...prev, accountId: event.target.value }))}>
              <option value="">All accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.name}</option>
              ))}
            </select>
          </div>
          <div className="min-w-0">
            <label htmlFor="from-filter" className="sr-only">From date</label>
            <input id="from-filter" type="date" className="input" value={filters.from} onChange={(event) => setFilters((prev) => ({ ...prev, from: event.target.value }))} aria-label="From date" />
          </div>
          <div className="min-w-0">
            <label htmlFor="to-filter" className="sr-only">To date</label>
            <input id="to-filter" type="date" className="input" value={filters.to} onChange={(event) => setFilters((prev) => ({ ...prev, to: event.target.value }))} aria-label="To date" />
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
          <span>
            {result ? `${result.totalElements.toLocaleString()} transaction${result.totalElements === 1 ? '' : 's'}` : 'Loading…'}
            {debouncedSearch && ' matching your search'}
          </span>
          {(debouncedSearch || Object.values(filters).some(Boolean)) && (
            <button
              type="button"
              className="font-semibold text-brand-600 hover:text-brand-700 dark:text-brand-400"
              onClick={() => {
                setFilters({ type: '', categoryId: '', accountId: '', from: '', to: '' });
                setSearch('');
              }}
            >
              Clear filters
            </button>
          )}
        </div>
      </section>

      <section className="card mt-4 overflow-hidden" aria-label="Transaction list">
        {error ? (
          <div className="p-4"><ErrorState message={error} onRetry={load} /></div>
        ) : loading ? (
          <div className="space-y-3 p-5">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="skeleton h-12 w-full" />
            ))}
          </div>
        ) : transactions.length === 0 ? (
          <EmptyState
            title="No transactions match"
            description="Try a different search or clear the filters. New transactions show up here instantly."
            action={<button type="button" className="btn-secondary" onClick={() => { setFilters({ type: '', categoryId: '', accountId: '', from: '', to: '' }); setSearch(''); }}>Clear filters</button>}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left">
                <caption className="sr-only">Transactions with actions</caption>
                <thead className="border-b border-slate-200 bg-slate-50/70 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-400">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-semibold">
                      <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort('description')}>
                        Description {sortBy === 'description' && <SortIcon className="h-3 w-3" />}
                      </button>
                    </th>
                    <th scope="col" className="px-4 py-3 font-semibold">Category</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Account</th>
                    <th scope="col" className="px-4 py-3 font-semibold">
                      <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort('occurredOn')}>
                        Date {sortBy === 'occurredOn' && <SortIcon className="h-3 w-3" />}
                      </button>
                    </th>
                    <th scope="col" className="px-4 py-3 text-right font-semibold">
                      <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort('amount')}>
                        Amount {sortBy === 'amount' && <SortIcon className="h-3 w-3" />}
                      </button>
                    </th>
                    <th scope="col" className="px-4 py-3 text-right font-semibold"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((transaction) => (
                    <tr key={transaction.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/60 dark:border-slate-800/70 dark:hover:bg-slate-800/30">
                      <td className="table-cell max-w-[280px]">
                        <p className="truncate font-medium text-slate-900 dark:text-white">{transaction.description}</p>
                        <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                          {transaction.merchant || (transaction.recurringId ? 'Recurring' : '—')}
                          {transaction.currency !== currency && ` · ${transaction.currency} ${transaction.amount.toFixed(2)}`}
                        </p>
                      </td>
                      <td className="table-cell">
                        <span className="inline-flex items-center gap-1.5 text-slate-600 dark:text-slate-300">
                          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: transaction.categoryColor }} aria-hidden="true" />
                          {transaction.categoryName}
                        </span>
                      </td>
                      <td className="table-cell text-slate-500 dark:text-slate-400">{transaction.accountName}</td>
                      <td className="table-cell text-slate-500 dark:text-slate-400">{formatDate(transaction.occurredOn)}</td>
                      <td className={classNames('table-cell text-right font-semibold', transaction.type === 'INCOME' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-900 dark:text-white')}>
                        {transaction.type === 'INCOME' ? '+' : '−'}{formatCurrency(transaction.amountBase, currency)}
                      </td>
                      <td className="table-cell text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            className="btn-ghost p-1.5"
                            aria-label={`Edit ${transaction.description}`}
                            onClick={() => {
                              setEditing(transaction);
                              setFormOpen(true);
                            }}
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button type="button" className="btn-ghost p-1.5 text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/40" aria-label={`Delete ${transaction.description}`} onClick={() => setDeleting(transaction)}>
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <nav className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-4 py-3 dark:border-slate-800" aria-label="Pagination">
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Page {(result.page ?? 0) + 1} of {Math.max(result.totalPages, 1)}
              </p>
              <div className="flex gap-2">
                <button type="button" className="btn-secondary px-3 py-2" onClick={() => setPage((prev) => Math.max(prev - 1, 0))} disabled={page === 0}>
                  <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Prev
                </button>
                <button type="button" className="btn-secondary px-3 py-2" onClick={() => setPage((prev) => prev + 1)} disabled={page + 1 >= result.totalPages}>
                  Next <ChevronRight className="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </nav>
          </>
        )}
      </section>

      <Modal
        open={formOpen}
        title={editing ? 'Edit transaction' : 'New transaction'}
        description={editing ? 'Update the details — totals recalculate automatically.' : 'Record an expense or income. Amounts convert to your base currency.'}
        onClose={() => {
          setFormOpen(false);
          setEditing(null);
        }}
      >
        <TransactionForm
          initial={editing}
          accounts={accounts}
          categories={categories}
          baseCurrency={currency}
          onSubmit={save}
          submitting={submitting}
          onCancel={() => {
            setFormOpen(false);
            setEditing(null);
          }}
        />
      </Modal>

      <Modal
        open={Boolean(deleting)}
        title="Delete transaction?"
        description="This cannot be undone."
        onClose={() => setDeleting(null)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={confirmDelete}>Delete</button>
          </div>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          <span className="font-semibold text-slate-900 dark:text-white">{deleting?.description}</span> for{' '}
          {deleting && formatCurrency(deleting.amountBase, currency)} on {deleting && formatDate(deleting.occurredOn)} will be removed.
        </p>
      </Modal>
    </>
  );
}
