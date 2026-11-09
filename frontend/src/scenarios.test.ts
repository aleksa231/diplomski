import { describe, expect, it } from 'vitest';
import { DEFAULT_DATASET_SIZE, SCENARIOS, scenarioPreset } from './scenarios';

describe('scenario catalogue', () => {
  it('defines the scenario catalogue exactly once each', () => {
    const codes = SCENARIOS.map((scenario) => scenario.code);
    expect(codes).toEqual(['S1', 'S2', 'S3', 'S5', 'S7']);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it('gives every scenario a title, a plain-language description and targets', () => {
    for (const scenario of SCENARIOS) {
      expect(scenario.title.trim().length).toBeGreaterThan(0);
      expect(scenario.description?.trim().length ?? 0).toBeGreaterThan(40);
      expect(scenario.targets.length).toBeGreaterThan(0);
      expect(scenario.clients).toBeGreaterThan(0);
      expect(scenario.durationSec).toBeGreaterThan(0);
    }
  });

  it('explains each scenario without unexplained IT jargon', () => {
    for (const scenario of SCENARIOS) {
      expect(scenario.description).not.toContain('kao kazalo u knjizi');
      expect(scenario.description).toMatch(/Šta se testira:/);
    }
  });

  it('keeps the default dataset large enough for the report scenarios', () => {
    expect(DEFAULT_DATASET_SIZE).toBeGreaterThanOrEqual(5000);
  });

  it('returns undefined for an unknown scenario', () => {
    expect(scenarioPreset('S99')).toBeUndefined();
  });
});