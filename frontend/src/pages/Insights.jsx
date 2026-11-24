import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle, ArrowDownRight, ArrowUpRight, Info, Lightbulb, RefreshCw, Sparkles, TrendingDown, TrendingUp,
} from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client.js';
import { EmptyState, ErrorState, PageHeader, ProgressBar, SkeletonBlock } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../context/ThemeContext.jsx';
import { formatCurrency, formatDate } from '../utils/format.js';
import { classNames } from '../utils/format.js';

const SEVERITY = {
  high: 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300',
  medium: 'bg-amber-50 text-amber-800 dark:bg-amber-500/10 dark:text-amber-300',
  low: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
};

const INSIGHT_STYLES = {
  warning: { icon: AlertTriangle, tone: 'text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10' },
  positive: { icon: TrendingDown, tone: 'text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10' },
  trend: { icon: TrendingUp, tone: 'text-brand-600 dark:text-brand-400 bg-brand-50 dark:bg-brand-500/10' },
  forecast: { icon: Sparkles, tone: 'text-violet-600 dark:text-violet-400 bg-violet-50 dark:bg-violet-500/10' },
  info: { icon: Info, tone: 'text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800' },
};

export default function Insights() {
  const { user } = useAuth();
  const { theme } = useTheme();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    api
      .get('/api/insights/overview')
      .then(setData)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const currency = user?.baseCurrency || 'USD';
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

  const forecast = data?.forecast || [];
  const anomalies = data?.anomalies || [];
  const insights = data?.insights || [];
  const projected = forecast.reduce((sum, item) => sum + item.forecastNextMonth, 0);
  const chartData = forecast.slice(0, 6).map((item) => ({
    name: item.category.length > 12 ? `${item.category.slice(0, 11)}…` : item.category,
    last: item.lastMonth,
    forecast: item.forecastNextMonth,
  }));

  return (
    <>
      <PageHeader
        title="AI insights"
        description="Forecasts, anomalies and plain-language takeaways from your last six months."
        actions={<button type="button" className="btn-secondary" onClick={load} disabled={loading}><RefreshCw className={classNames('h-4 w-4', loading && 'animate-spin')} aria-hidden="true" /> Refresh</button>}
      />

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : loading ? (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => <SkeletonBlock key={index} className="h-28" />)}
          </div>
          <SkeletonBlock className="h-80" />
          <SkeletonBlock className="h-60" />
        </div>
      ) : !data?.available ? (
        <div className="card flex flex-col items-center gap-3 px-6 py-14 text-center">
          <span className="grid h-12 w-12 place-items-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
            <Sparkles className="h-6 w-6" aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">Coming soon</h2>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500 dark:text-slate-400">
              AI forecasting, anomaly detection and plain-language takeaways are on the way. Your dashboard and budgets work exactly the same meanwhile.
            </p>
          </div>
        </div>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-3" aria-label="Insight summary">
            <div className="card p-5">
              <p className="flex items-center gap-2 text-sm font-medium text-slate-500 dark:text-slate-400"><Sparkles className="h-4 w-4 text-brand-500" aria-hidden="true" /> Projected next month</p>
              <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{formatCurrency(projected, currency)}</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Sum of category forecasts</p>
            </div>
            <div className="card p-5">
              <p className="flex items-center gap-2 text-sm font-medium text-slate-500 dark:text-slate-400"><AlertTriangle className="h-4 w-4 text-amber-500" aria-hidden="true" /> Unusual transactions</p>
              <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{anomalies.length}</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Flagged by z-score analysis</p>
            </div>
            <div className="card p-5">
              <p className="flex items-center gap-2 text-sm font-medium text-slate-500 dark:text-slate-400"><Lightbulb className="h-4 w-4 text-violet-500" aria-hidden="true" /> Takeaways</p>
              <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{insights.length}</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Generated from your trends</p>
            </div>
          </section>

          <section className="card mt-4 p-5" aria-label="Plain-language insights">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">What changed</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">Automatic observations, not financial advice</p>
            <ul className="mt-4 grid gap-3 md:grid-cols-2">
              {insights.map((insight, index) => {
                const style = INSIGHT_STYLES[insight.type] || INSIGHT_STYLES.info;
                const Icon = style.icon;
                return (
                  <li key={`${insight.type}-${index}`} className="flex items-start gap-3 rounded-xl border border-slate-200 p-3.5 dark:border-slate-800">
                    <span className={classNames('grid h-8 w-8 shrink-0 place-items-center rounded-lg', style.tone)}>
                      <Icon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <div>
                      <p className="text-sm text-slate-700 dark:text-slate-200">{insight.text}</p>
                      {insight.category && <p className="mt-1 text-xs font-semibold text-slate-400">{insight.category}</p>}
                    </div>
                  </li>
                );
              })}
              {insights.length === 0 && <li className="text-sm text-slate-500">Not enough history for observations yet.</li>}
            </ul>
          </section>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <section className="card p-5" aria-label="Forecast by category">
              <h2 className="text-base font-semibold text-slate-900 dark:text-white">Next-month forecast</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">Linear trend per category, with confidence</p>
              <div className="mt-4 max-h-[440px] overflow-auto">
                <table className="w-full min-w-[460px] text-left text-sm">
                  <caption className="sr-only">Category forecast for next month</caption>
                  <thead>
                    <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400 dark:border-slate-800">
                      <th scope="col" className="py-2 pr-3 font-semibold">Category</th>
                      <th scope="col" className="py-2 pr-3 text-right font-semibold">Last month</th>
                      <th scope="col" className="py-2 pr-3 text-right font-semibold">Forecast</th>
                      <th scope="col" className="py-2 font-semibold">Trend</th>
                    </tr>
                  </thead>
                  <tbody>
                    {forecast.map((item) => (
                      <tr key={item.categoryId} className="border-b border-slate-100 last:border-0 dark:border-slate-800/60">
                        <td className="py-2.5 pr-3 font-medium text-slate-800 dark:text-slate-200">{item.category}</td>
                        <td className="py-2.5 pr-3 text-right text-slate-500 dark:text-slate-400">{formatCurrency(item.lastMonth, currency)}</td>
                        <td className="py-2.5 pr-3 text-right font-semibold text-slate-900 dark:text-white">{formatCurrency(item.forecastNextMonth, currency)}</td>
                        <td className="py-2.5">
                          <span className={classNames('badge', item.trend === 'rising' ? 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300' : item.trend === 'falling' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300')}>
                            {item.trend === 'rising' ? <ArrowUpRight className="h-3 w-3" aria-hidden="true" /> : item.trend === 'falling' ? <ArrowDownRight className="h-3 w-3" aria-hidden="true" /> : null}
                            {item.changePercent > 0 ? '+' : ''}{item.changePercent}%
                          </span>
                          <div className="mt-1 w-20">
                            <ProgressBar value={item.confidence * 100} tone="brand" label={`${item.category} forecast confidence`} />
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="card p-5" aria-label="Forecast chart">
              <h2 className="text-base font-semibold text-slate-900 dark:text-white">This month vs next</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">Six largest categories</p>
              {chartData.length === 0 ? (
                <EmptyState title="No forecast yet" description="Spend in a few categories to unlock projections." />
              ) : (
                <div className="mt-4 h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={theme === 'dark' ? '#1e293b' : '#e2e8f0'} vertical={false} />
                      <XAxis dataKey="name" tick={{ fill: axisColor, fontSize: 11 }} axisLine={false} tickLine={false} interval={0} angle={-12} textAnchor="end" height={48} />
                      <YAxis tick={{ fill: axisColor, fontSize: 11 }} axisLine={false} tickLine={false} tickFormatter={(value) => `${Math.round(value / 1000)}k`} />
                      <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipLabelStyle} formatter={(value) => formatCurrency(value, currency)} />
                      <Bar dataKey="last" name="Last month" fill="#94a3b8" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="forecast" name="Forecast" fill="#6366f1" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>
          </div>

          <section className="card mt-4 p-5" aria-label="Anomalies">
            <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
              <AlertTriangle className="h-4 w-4 text-amber-500" aria-hidden="true" /> Unusual spending
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">Transactions well outside your normal pattern for that category</p>
            {anomalies.length === 0 ? (
              <p className="py-6 text-center text-sm text-slate-500 dark:text-slate-400">Nothing unusual in the last six months. Suspiciously well behaved.</p>
            ) : (
              <ul className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                {anomalies.map((anomaly) => (
                  <li key={anomaly.transactionId} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="text-sm font-semibold text-slate-900 dark:text-white">{anomaly.description}</p>
                      <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{anomaly.category} · {formatDate(anomaly.occurredOn)} · {anomaly.reason}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={classNames('badge', SEVERITY[anomaly.severity] || SEVERITY.low)}>{anomaly.severity}</span>
                      <span className="text-sm font-bold text-slate-900 dark:text-white">{formatCurrency(anomaly.amount, currency)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <p className="mt-4 text-center text-xs text-slate-400">
            {data.meta?.model} · {data.meta?.monthsAnalysed} months · {data.meta?.transactionsAnalysed} transactions · generated {data.meta?.generatedAt ? formatDate(data.meta.generatedAt.slice(0, 10)) : '—'}
          </p>
        </>
      )}
    </>
  );
}
