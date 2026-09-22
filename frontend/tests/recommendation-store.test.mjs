import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';
import {createStore} from 'zustand/vanilla';

// 실제 store/actions를 실행하고 네트워크 경계만 대체한다. 테스트 전용 의존성은 없다.
function load(path, dependencies = {}) {
  const source = readFileSync(new URL('../' + path, import.meta.url), 'utf8');
  const {outputText} = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
  });
  const context = {exports: {}, require: name => {
    assert.ok(name in dependencies, `Unexpected import: ${name}`);
    return dependencies[name];
  }, console: {error() {}, warn() {}}};
  runInNewContext(outputText, context);
  return context.exports;
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {resolve = yes; reject = no;});
  return {promise, resolve, reject};
}
function fixture() {
  const calls = [];
  const api = kind => (...args) => {
    const pending = deferred();
    calls.push({kind, args, ...pending});
    return pending.promise;
  };
  const store = load('src/lib/stores/useRecommendationStore.ts', {
    zustand: {create: createStore},
    '@/lib/stores/actions.ts': load('src/lib/stores/actions.ts'),
    '@/lib/api/recommendations': {getRecommendation: api('GET'), getAiRecommendation: api('AI')},
  }).useRecommendationStore;
  store.setState({params: {weatherId: 'A'}, data: {weatherId: 'A'}});
  return {store, calls};
}
const flush = () => new Promise(resolve => setImmediate(resolve));

for (const oldFirst of [true, false]) {
  test(`weather change starts GET during AI; old response first=${oldFirst}`, async () => {
    const {store, calls} = fixture();
    const ai = store.getState().fetchAiRecommendation('데이트');
    store.getState().updateParams({weatherId: 'B'});
    assert.equal(calls.length, 2);
    assert.equal(calls[1].kind, 'GET');
    assert.equal(calls[1].args[0].weatherId, 'B');
    assert.equal(store.getState().data, null);
    if (oldFirst) {
      calls[0].resolve({weatherId: 'A'});
      await ai;
      assert.equal(store.getState().loading, true);
      assert.equal(store.getState().data, null);
    }
    const latest = {weatherId: 'B'};
    calls[1].resolve(latest);
    await flush();
    if (!oldFirst) {calls[0].resolve({weatherId: 'A'}); await ai;}
    assert.equal(store.getState().data, latest);
    assert.equal(store.getState().loading, false);
    assert.equal(store.getState().error, undefined);
  });
}

test('stale AI failure cannot replace the latest error or loading state', async () => {
  const {store, calls} = fixture();
  const ai = store.getState().fetchAiRecommendation('데이트');
  store.getState().updateParams({weatherId: 'B'});
  calls[0].reject(new Error('old failure'));
  await ai;
  assert.equal(store.getState().error, undefined);
  assert.equal(store.getState().loading, true);
  calls[1].reject(new Error('latest failure'));
  await flush();
  assert.equal(store.getState().error, 'latest failure');
  assert.equal(store.getState().loading, false);
});

test('A -> B -> A uses request identity, not just weather identity', async () => {
  const {store, calls} = fixture();
  const ai = store.getState().fetchAiRecommendation('데이트');
  store.getState().updateParams({weatherId: 'B'});
  store.getState().updateParams({weatherId: 'A'});
  const latest = {weatherId: 'A', clothes: ['latest']};
  calls[2].resolve(latest);
  await flush();
  calls[1].resolve({weatherId: 'B'});
  calls[0].resolve({weatherId: 'A', clothes: ['old']});
  await ai;
  assert.equal(store.getState().data, latest);
});

test('clear invalidates pending responses; same-weather duplicate stays guarded', async () => {
  const {store, calls} = fixture();
  const ai = store.getState().fetchAiRecommendation('데이트');
  await store.getState().fetch();
  assert.equal(calls.length, 1);
  store.getState().clear();
  calls[0].resolve({weatherId: 'A'});
  await ai;
  assert.equal(store.getState().data, null);
  assert.equal(store.getState().loading, false);
});

test('stale GET cannot overwrite a newer AI recommendation', async () => {
  const {store, calls} = fixture();
  const oldGet = store.getState().fetch();
  store.getState().updateParams({weatherId: 'B'});
  calls[1].resolve({weatherId: 'B'});
  await flush();
  const ai = store.getState().fetchAiRecommendation('캐주얼');
  const result = {weatherId: 'B', clothes: ['AI']};
  calls[2].resolve(result);
  await ai;
  calls[0].resolve({weatherId: 'A'});
  await oldGet;
  assert.equal(store.getState().data, result);
});

test('explicit refresh supersedes the pending request even for the same weather', async () => {
  const {store, calls} = fixture();
  const ai = store.getState().fetchAiRecommendation('데이트');
  const refresh = store.getState().fetch({ignoreLoading: true});
  const latest = {weatherId: 'A', clothes: ['refreshed']};
  calls[1].resolve(latest);
  await refresh;
  calls[0].reject(new Error('stale AI failure'));
  await ai;
  assert.equal(store.getState().data, latest);
  assert.equal(store.getState().error, undefined);
});

test('latest GET still propagates errors with throwError', async () => {
  const {store, calls} = fixture();
  const pending = store.getState().fetch({throwError: true});
  const error = new Error('unavailable');
  calls[0].reject(error);
  await assert.rejects(pending, error);
  assert.equal(store.getState().error, 'unavailable');
  assert.equal(store.getState().loading, false);
});

test('AI fallback response without reason remains a successful recommendation', async () => {
  const {store, calls} = fixture();
  const request = store.getState().fetchAiRecommendation('캐주얼');
  const fallback = {weatherId: 'A', clothes: [{clothesId: 'TOP'}]};

  calls[0].resolve(fallback);
  await request;

  assert.equal(store.getState().data, fallback);
  assert.equal(store.getState().error, undefined);
  assert.equal(store.getState().loading, false);
});
