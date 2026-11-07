import { describe, expect, it } from 'vitest';
import { int, meanStd, num, percent, statusClass, timestamp } from './format';

describe('format', () => {
  it('renders an em dash for a value that was not measured', () => {
    expect(num(null)).toBe('—');
    expect(int(undefined)).toBe('—');
    expect(percent(Number.NaN)).toBe('—');
    expect(meanStd(undefined)).toBe('—');
    expect(timestamp(null)).toBe('—');
  });

  it('rounds integers and fixes decimals', () => {
    expect(int(1234.6)).toBe('1235');
    expect(num(12.345, 1)).toBe('12.3');
  });

  it('turns a 0..1 error rate into a percentage', () => {
    expect(percent(0)).toBe('0.00%');
    expect(percent(0.025)).toBe('2.50%');
  });

  it('renders mean and standard deviation together', () => {
    expect(meanStd({ mean: 320.94, stdDev: 67.31 }, 1)).toBe('320.9 ± 67.3');
    expect(meanStd({ mean: 1, stdDev: 0 }, 0)).toBe('1 ± 0');
  });

  it('keeps an unparseable timestamp visible instead of hiding it', () => {
    expect(timestamp('not-a-date')).toBe('not-a-date');
  });

  it('maps every status to a badge class', () => {
    expect(statusClass('COMPLETED')).toBe('badge badge-ok');
    expect(statusClass('RUNNING')).toBe('badge badge-info');
    expect(statusClass('PENDING')).toBe('badge badge-muted');
    expect(statusClass('REJECTED')).toBe('badge badge-warn');
    expect(statusClass('FAILED')).toBe('badge badge-error');
  });
});
