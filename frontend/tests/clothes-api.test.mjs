import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';

function load(path, dependencies = {}) {
  const source = readFileSync(new URL('../' + path, import.meta.url), 'utf8');
  const {outputText} = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
  });
  const context = {exports: {}, require: name => {
    assert.ok(name in dependencies, `Unexpected import: ${name}`);
    return dependencies[name];
  }};
  runInNewContext(outputText, context);
  return context.exports;
}

test('product-link extraction gets a 70-second request timeout', async () => {
  const calls = [];
  const expectedResult = {name: '테스트 상품'};
  const {extractByUrl} = load('src/lib/api/clothes.ts', {
    './client': {apiClient: {
      get: (...args) => {
        calls.push(args);
        return Promise.resolve(expectedResult);
      },
    }},
  });

  const result = await extractByUrl('https://shop.example/products/1');

  assert.equal(result, expectedResult);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], '/api/clothes/extractions');
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0][1].params)), {
    url: 'https://shop.example/products/1',
  });
  assert.equal(calls[0][1].timeout, 70_000);
});
