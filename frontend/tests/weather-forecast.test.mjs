import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';

const source = readFileSync(
  new URL('../src/components/recommendations/weatherForecastUtils.ts', import.meta.url),
  'utf8',
);
const {outputText} = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
  },
});
const context = {exports: {}, require: name => {
  throw new Error(`Unexpected import: ${name}`);
}};
runInNewContext(outputText, context);

test('daily cards keep an actual Weather object as their selectable representative', () => {
  const first = {
    id: 'first', forecastAt: '2026-09-22T00:00:00+09:00',
    temperature: {current: 12, min: 10, max: 14}, skyStatus: 'CLEAR',
  };
  const later = {
    id: 'later', forecastAt: '2026-09-22T12:00:00+09:00',
    temperature: {current: 22, min: 20, max: 24}, skyStatus: 'CLOUDY',
  };
  const nextDay = {
    id: 'next', forecastAt: '2026-09-23T00:00:00+09:00',
    temperature: {current: 16, min: 14, max: 18}, skyStatus: 'CLEAR',
  };

  const result = context.exports.dailyRepresentativeWeathers([first, later, nextDay]);

  assert.equal(result.length, 2);
  assert.equal(result[0], first);
  assert.equal(result[0].temperature.current, 12);
  assert.equal(result[1], nextDay);
});
