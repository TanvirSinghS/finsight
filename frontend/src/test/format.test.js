import { describe, expect, it } from 'vitest';
import { formatCompact, formatCurrency, formatDate, formatMonth, percentChange, todayValue } from '../utils/format.js';

describe('format helpers', () => {
  it('formats currency amounts with the requested currency', () => {
    expect(formatCurrency(1234.5, 'USD')).toMatch(/\$1,234\.50/);
    expect(formatCurrency(10, 'EUR')).toMatch(/€10\.00/);
  });

  it('formats compact currency for chart axes', () => {
    expect(formatCompact(12000, 'USD')).toMatch(/12/);
  });

  it('formats ISO dates without shifting the day', () => {
    expect(formatDate('2026-11-13', { month: 'short', day: 'numeric', year: 'numeric' })).toMatch(/Nov 13, 2026/);
  });

  it('formats YYYY-MM month labels', () => {
    expect(formatMonth('2026-03')).toMatch(/March 2026/);
  });

  it('computes percent change and guards division by zero', () => {
    expect(percentChange(120, 100)).toBeCloseTo(20);
    expect(percentChange(50, 0)).toBeNull();
  });

  it('returns today as an ISO date input value', () => {
    expect(todayValue()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
