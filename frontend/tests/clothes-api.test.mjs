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

test('getClothes requests only favorite clothes when favorite filter is enabled', async () => {
  const calls = [];
  const apiClient = {
    get: async (...args) => {
      calls.push({method: 'get', args});
      return {data: [], hasNext: false, totalCount: 0};
    },
  };
  const {getClothes} = load('src/lib/api/clothes.ts', {'./client': {apiClient}});

  await getClothes({ownerId: 'owner-1', favorite: true, limit: 20});

  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, 'get');
  assert.equal(calls[0].args[0], '/api/clothes');
  assert.equal(calls[0].args[1].params.ownerId, 'owner-1');
  assert.equal(calls[0].args[1].params.favorite, true);
  assert.equal(calls[0].args[1].params.limit, 20);
});

test('favorite actions call the clothes favorite endpoints', async () => {
  const calls = [];
  const apiClient = {
    post: async (...args) => calls.push({method: 'post', args}),
    delete: async (...args) => calls.push({method: 'delete', args}),
  };
  const {addFavorite, removeFavorite} = load('src/lib/api/clothes.ts', {'./client': {apiClient}});

  await addFavorite('clothes-1');
  await removeFavorite('clothes-1');

  assert.equal(calls.length, 2);
  assert.equal(calls[0].method, 'post');
  assert.equal(calls[0].args[0], '/api/clothes/clothes-1/favorite');
  assert.equal(calls[1].method, 'delete');
  assert.equal(calls[1].args[0], '/api/clothes/clothes-1/favorite');
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
