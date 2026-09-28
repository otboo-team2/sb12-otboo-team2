import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';
import {renderToStaticMarkup} from 'react-dom/server';
import jsxRuntime from 'react/jsx-runtime';

const source = readFileSync(
  new URL('../src/components/recommendations/RecommendationReason.tsx', import.meta.url),
  'utf8',
);
const {outputText} = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    jsx: ts.JsxEmit.ReactJSX,
  },
});
const context = {
  exports: {},
  require: name => {
    assert.equal(name, 'react/jsx-runtime');
    return jsxRuntime;
  },
};
runInNewContext(outputText, context);
const RecommendationReason = context.exports.default;

test('renders a non-blank AI recommendation reason', () => {
  const reason = '오늘 날씨와 캐주얼한 스타일에 어울리는 조합이에요.';
  const html = renderToStaticMarkup(jsxRuntime.jsx(RecommendationReason, {reason}));

  assert.match(html, /AI 추천 이유/);
  assert.match(html, new RegExp(reason));
});

for (const [name, reason] of [
  ['null', null],
  ['undefined', undefined],
  ['empty', ''],
  ['whitespace-only', '   \n  '],
]) {
  test(`does not render the reason area for ${name}`, () => {
    const html = renderToStaticMarkup(jsxRuntime.jsx(RecommendationReason, {reason}));
    assert.equal(html, '');
  });
}
