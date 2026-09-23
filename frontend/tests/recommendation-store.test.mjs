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

test('first AI recommendation sends no exclusions', async () => {
  const {store, calls} = fixture();
  const request = store.getState().fetchAiRecommendation('면접 코디');

  assert.equal(calls[0].kind, 'AI');
  assert.equal(calls[0].args[0], 'A');
  assert.equal(calls[0].args[1], '면접 코디');
  assert.equal(calls[0].args[2].length, 0);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'TOP-1'}]});
  await request;
});

test('alternative AI recommendation keeps prompt and excludes current clothes', async () => {
  const {store, calls} = fixture();
  const first = store.getState().fetchAiRecommendation('면접 코디');
  calls[0].resolve({weatherId: 'A', clothes: [
    {clothesId: 'TOP-1'}, {clothesId: 'BOTTOM-1'},
  ]});
  await first;

  const alternative = store.getState().fetchAlternative();
  assert.equal(calls[1].kind, 'AI');
  assert.deepEqual([calls[1].args[0], calls[1].args[1], [...calls[1].args[2]]],
    ['A', '면접 코디', ['TOP-1', 'BOTTOM-1']]);
  const next = {weatherId: 'A', clothes: [{clothesId: 'TOP-2'}]};
  calls[1].resolve(next);
  await alternative;

  assert.equal(store.getState().data, next);
  assert.equal(store.getState().loading, false);
  assert.equal(store.getState().error, undefined);
  assert.deepEqual([...store.getState().seenClothesIds], ['TOP-1', 'BOTTOM-1', 'TOP-2']);
});

test('alternative button keeps the basic recommendation GET behavior before AI use', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [
    {clothesId: 'TOP-1'}, {clothesId: 'BOTTOM-1'},
  ]}});
  const refresh = store.getState().fetchAlternative();

  assert.equal(calls[0].kind, 'GET');
  assert.equal(calls[0].args[0].weatherId, 'A');
  assert.deepEqual([...calls[0].args[1]], ['TOP-1', 'BOTTOM-1']);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'TOP'}]});
  await refresh;
});

test('base alternatives cumulatively exclude every successfully displayed item without duplicates', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'A'}]}});

  const second = store.getState().fetchAlternative();
  assert.deepEqual([...calls[0].args[1]], ['A']);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'B'}, {clothesId: 'B'}]});
  await second;
  assert.deepEqual([...store.getState().seenClothesIds], ['A', 'B']);

  const third = store.getState().fetchAlternative();
  assert.deepEqual([...calls[1].args[1]], ['A', 'B']);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'C'}]});
  await third;
  assert.deepEqual([...store.getState().seenClothesIds], ['A', 'B', 'C']);
});

test('weather change starts a fresh base cycle', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'A'}]}, seenClothesIds: ['A']});

  store.getState().updateParams({weatherId: 'B'});
  assert.deepEqual([...store.getState().seenClothesIds], []);
  calls[0].resolve({weatherId: 'B', clothes: [{clothesId: 'B'}]});
  await flush();

  assert.deepEqual([...store.getState().seenClothesIds], ['B']);
  assert.equal(store.getState().lastAiPrompt, undefined);
});

test('new AI prompt resets base history and same-prompt alternatives use only the AI cycle', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'BASE'}]}, seenClothesIds: ['BASE']});

  const firstAi = store.getState().fetchAiRecommendation('새 프롬프트');
  assert.deepEqual([...store.getState().seenClothesIds], []);
  assert.deepEqual([...calls[0].args[2]], []);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-1'}]});
  await firstAi;

  const alternative = store.getState().fetchAlternative();
  assert.deepEqual([calls[1].args[0], calls[1].args[1], [...calls[1].args[2]]],
    ['A', '새 프롬프트', ['AI-1']]);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-2'}]});
  await alternative;
  assert.deepEqual([...store.getState().seenClothesIds], ['AI-1', 'AI-2']);

  const nextAlternative = store.getState().fetchAlternative();
  assert.deepEqual([calls[2].args[0], calls[2].args[1], [...calls[2].args[2]]],
    ['A', '새 프롬프트', ['AI-1', 'AI-2']]);
  calls[2].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-3'}]});
  await nextAlternative;
  assert.deepEqual([...store.getState().seenClothesIds], ['AI-1', 'AI-2', 'AI-3']);
});

test('failed alternative does not add unseen clothes to cycle history', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'A'}]}, seenClothesIds: ['A']});

  const alternative = store.getState().fetchAlternative();
  calls[0].reject(new Error('failed'));
  await alternative;

  assert.deepEqual([...store.getState().seenClothesIds], ['A']);
  assert.equal(store.getState().error, 'failed');
});

test('exhausted cycle resets once and starts again without recursive retries', async () => {
  const {store, calls} = fixture();
  store.setState({
    data: {weatherId: 'A', clothes: [{clothesId: 'B'}]},
    seenClothesIds: ['A', 'B'],
  });

  const alternative = store.getState().fetchAlternative();
  assert.deepEqual([...calls[0].args[1]], ['A', 'B']);
  calls[0].resolve({weatherId: 'A', clothes: []});
  await flush();
  assert.equal(calls.length, 2);
  assert.deepEqual([...calls[1].args[1]], []);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'A'}]});
  await alternative;

  assert.equal(calls.length, 2);
  assert.deepEqual([...store.getState().seenClothesIds], ['A']);
  assert.equal(store.getState().error, undefined);
});
