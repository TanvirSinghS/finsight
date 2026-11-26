import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Area, AreaChart, CartesianGrid, Cell, Legend, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import {
  ArrowDownRight, ArrowUpRight, ArrowRightLeft, PiggyBank, Plus, Sparkles, TrendingUp, Wallet,
} from 'lucide-react';
import { fetchDashboard, readDashboardCache } from '../api/client.js';
import { budgetTone, EmptyState, ErrorState, PageHeader, ProgressBar, SkeletonBlock, StatCard } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../context/ThemeContext.jsx';
import { formatCurrency, formatDate, formatMonth } from '../utils/format.js';

const percentTones = {
  emerald: 'font-semibold text-emerald-600 dark:text-emerald-400',
  yellow: 'font-semibold text-yellow-600 dark:text-yellow-400',
  orange: 'font-semibold text-orange-600 dark:text-orange-400',
  rose: 'font-semibold text-rose-600 dark:text-rose-400',
};

export default function Dashboard() {
  const { user } = useAuth();
  const { theme } = useTheme();
  // Paint the last payload instantly, then reconcile with the API in the background.
  const [data, setData] = useState(readDashboardCache);
  const hadCachedData = useRef(data !== null);
  const [loading, setLoading] = useState(!hadCachedData.current);
  const [error, setError] = useState('');

  const load = useCallback(({ silent = false } = {}) => {
    if (!silent) {
      setLoading(true);
      setError('');
    }
    return fetchDashboard()
      .then((payload) => {
        setData(payload);
        setError('');
      })
      .catch((err) => {
        if (!silent) setError(err.message);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load({ silent: hadCachedData.current });
  }, [load]);

  const currency = data?.currency || user?.baseCurrency || 'USD';
  const axisColor = theme === 'dark' ? '#94a3b8' : '#64748b';
  const tooltipStyle = {
    backgroundColor: theme === 'dark' ? '#0f172a' : '#ffffff',
    border: `1px solid ${theme === 'dark' ? '#1e293b' : '#e2e8f0'}`,
    borderRadius: 12,
    fontSize: 12,
    color: theme === 'dark' ? '#e2e8f0' : '#0f172a',
  };
  const tooltipItemStyle = { color: theme === 'dark' ? '#e2e8f0' : '#0f172a' };
  const tooltipLabelStyle = { color: theme === 'dark' ? '#94a3b8' : '#475569', fontWeight: 600 };

  if (error) {
    return (
      <>
        <PageHeader title="Dashboard" description="Your money at a glance." />
        <ErrorState message={error} onRetry={load} />
      </>
    );
  }

  const trend = (data?.monthlyTrend || []).map((point) => ({
    ...point,
    label: formatMonth(point.month).split(' ')[0],
  }));
  const hasData = data && (data.recentTransactions.length > 0 || data.monthlyExpense > 0);

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={formatMonth(new Date().toISOString().slice(0, 7))}
        actions={
          <Link to="/transactions?new=1" className="btn-primary">
            <Plus className="h-4 w-4" aria-hidden="true" /> Add transaction
          </Link>
        }
      />

      <section aria-label="Key figures" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Total balance"
          value={formatCurrency(data?.totalBalance, currency)}
          hint={data ? `Across ${data.accountCount} active account${data.accountCount === 1 ? '' : 's'}` : ''}
          icon={Wallet}
          loading={loading}
        />
        <StatCard
          label="Income this month"
          value={formatCurrency(data?.monthlyIncome, currency)}
          hint="Converted to your base currency"
          icon={ArrowDownRight}
          tone="emerald"
          loading={loading}
        />
        <StatCard
          label="Spent this month"
          value={formatCurrency(data?.monthlyExpense, currency)}
          hint={data ? `Net ${formatCurrency(data.netThisMonth, currency)}` : ''}
          icon={ArrowUpRight}
          tone="rose"
          loading={loading}
        />
        <StatCard
          label="Savings rate"
          value={data ? `${data.savingsRate.toFixed(1)}%` : '—'}
          hint={data && data.savingsRate >= 20 ? 'Above the 20% target' : 'Aim for 20% of income'}
          icon={PiggyBank}
          tone="amber"
          loading={loading}
        />
      </section>

      {loading ? (
        <div className="mt-6 grid gap-4 lg:grid-cols-3">
          <SkeletonBlock className="h-80 lg:col-span-2" />
          <SkeletonBlock className="h-80" />
          <SkeletonBlock className="h-64 lg:col-span-2" />
          <SkeletonBlock className="h-64" />
        </div>
      ) : !hasData ? (
        <div className="card mt-6">
          <EmptyState
            title="Your dashboard is waiting for its first transaction"
            description="Add an expense or import a CSV and charts, budgets and insights will spring to life."
            icon={Sparkles}
            action={<Link to="/transactions?new=1" className="btn-primary">Add your first transaction</Link>}
          />
        </div>
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-3">
          <section className="card p-5 lg:col-span-2" aria-label="Income versus expenses">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-slate-900 dark:text-white">Cashflow trend</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">Income vs expenses, last 6 months</p>
              </div>
              <span className="badge bg-brand-50 text-brand-700 dark:bg-brand-500/10 dark:text-brand-300">6 months</span>
            </div>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trend} margin={{ top: 4, right: 4, left: -12, bottom: 0 }}>
                  <defs>
                    <linearGradient id="incomeFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#10b981" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="expenseFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#f43f5e" stopOpacity={0.3} />
                      <stop offset="100%" stopColor="#f43f5e" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={theme === 'dark' ? '#1e293b' : '#e2e8f0'} vertical={false} />
                  <XAxis dataKey="label" tick={{ fill: axisColor, fontSize: 12 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: axisColor, fontSize: 12 }} axisLine={false} tickLine={false} tickFormatter={(value) => `${Math.round(value / 1000)}k`} />
                  <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipLabelStyle} formatter={(value, name) => [formatCurrency(value, currency), name]} />
                  <Legend wrapperStyle={{ fontSize: 12, color: axisColor }} />
                  <Area type="monotone" dataKey="income" name="Income" stroke="#10b981" strokeWidth={2} fill="url(#incomeFill)" />
                  <Area type="monotone" dataKey="expenses" name="Expenses" stroke="#f43f5e" strokeWidth={2} fill="url(#expenseFill)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section className="card p-5" aria-label="Spending by category">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">Where money went</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">This month by category</p>
            {data.spendingByCategory.length === 0 ? (
              <EmptyState title="No spending yet" description="Expenses this month will appear here." />
            ) : (
              <>
                <div className="relative h-48">
                  <div className="pointer-events-none absolute inset-0 z-10 grid place-items-center">
                    <div className="text-center leading-tight">
                      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">Spent</p>
                      <p className="text-sm font-bold text-slate-900 dark:text-white">{formatCurrency(data.monthlyExpense, currency)}</p>
                    </div>
                  </div>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={data.spendingByCategory.slice(0, 8)}
                        dataKey="total"
                        nameKey="name"
                        innerRadius={52}
                        outerRadius={78}
                        paddingAngle={2}
                        stroke="none"
                      >
                        {data.spendingByCategory.slice(0, 8).map((entry) => (
                          <Cell key={entry.categoryId} fill={entry.color || '#6366f1'} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipLabelStyle} formatter={(value) => formatCurrency(value, currency)} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="mt-2 space-y-2">
                  {data.spendingByCategory.slice(0, 4).map((category) => (
                    <li key={category.categoryId} className="flex items-center gap-2 text-sm">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: category.color }} aria-hidden="true" />
                      <span className="flex-1 truncate text-slate-600 dark:text-slate-300">{category.name}</span>
                      <span className="font-semibold text-slate-900 dark:text-white">{category.percent.toFixed(0)}%</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          <section className="card p-5 lg:col-span-2" aria-label="Recent transactions">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-slate-900 dark:text-white">Recent transactions</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">The latest eight movements</p>
              </div>
              <Link to="/transactions" className="inline-flex items-center gap-1 text-sm font-semibold text-brand-600 hover:text-brand-700 dark:text-brand-400">
                View all <ArrowRightLeft className="h-4 w-4" aria-hidden="true" />
              </Link>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[480px] text-left">
                <caption className="sr-only">Recent transactions</caption>
                <thead>
                  <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400 dark:border-slate-800">
                    <th scope="col" className="py-2 pr-4 font-semibold">Description</th>
                    <th scope="col" className="py-2 pr-4 font-semibold">Date</th>
                    <th scope="col" className="py-2 text-right font-semibold">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentTransactions.map((transaction) => (
                    <tr key={transaction.id} className="border-b border-slate-100 last:border-0 dark:border-slate-800/60">
                      <td className="py-3 pr-4">
                        <div className="flex items-center gap-2.5">
                          <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: transaction.categoryColor }} aria-hidden="true" />
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-slate-900 dark:text-white">{transaction.description}</p>
                            <p className="text-xs text-slate-500 dark:text-slate-400">{transaction.categoryName} · {transaction.accountName}</p>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 pr-4 text-sm text-slate-500 dark:text-slate-400">{formatDate(transaction.occurredOn, { month: 'short', day: 'numeric' })}</td>
                      <td className={`py-3 text-right text-sm font-semibold ${transaction.type === 'INCOME' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-900 dark:text-white'}`}>
                        {transaction.type === 'INCOME' ? '+' : '−'}{formatCurrency(transaction.amountBase, currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="card p-5" aria-label="Budget alerts">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
                <TrendingUp className="h-4 w-4 text-brand-500" aria-hidden="true" /> Budget watch
              </h2>
              <Link to="/budgets" className="text-sm font-semibold text-brand-600 hover:text-brand-700 dark:text-brand-400">Manage</Link>
            </div>
            {data.budgetAlerts.length === 0 ? (
              <p className="py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                No budgets are near their limit. Nice pacing.
              </p>
            ) : (
              <ul className="space-y-4">
                {data.budgetAlerts.map((alert) => {
                  const tone = budgetTone(alert.percentUsed);
                  return (
                  <li key={alert.budgetId}>
                    <div className="mb-1.5 flex items-baseline justify-between gap-2 text-sm">
                      <span className="font-medium text-slate-700 dark:text-slate-200">{alert.categoryName}</span>
                      <span className={percentTones[tone]}>{alert.percentUsed}%</span>
                    </div>
                    <ProgressBar value={alert.percentUsed} tone={tone} label={`${alert.categoryName} budget usage`} />
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      {formatCurrency(alert.spent, currency)} of {formatCurrency(alert.amount, currency)}
                      {alert.overBudget && <span className="ml-1 font-semibold text-rose-600 dark:text-rose-400">over budget</span>}
                    </p>
                  </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>
      )}
    </>
  );
}
