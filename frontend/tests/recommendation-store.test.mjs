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
const plain = value => JSON.parse(JSON.stringify(value));

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

test('successful AI recommendation clears input and blank alternative switches to base', async () => {
  const {store, calls} = fixture();
  store.getState().setInputPrompt('면접 코디');
  const first = store.getState().fetchAiRecommendation('면접 코디');
  calls[0].resolve({weatherId: 'A', clothes: [
    {clothesId: 'TOP-1'}, {clothesId: 'BOTTOM-1'},
  ]});
  await first;

  assert.equal(store.getState().inputPrompt, '');
  assert.equal(store.getState().recommendationMode, 'AI');
  const alternative = store.getState().fetchAlternative();
  assert.equal(calls[1].kind, 'GET');
  assert.deepEqual([...calls[1].args[1]], []);
  assert.deepEqual(plain(calls[1].args[2]), []);
  const next = {weatherId: 'A', clothes: [{clothesId: 'BASE-1'}]};
  calls[1].resolve(next);
  await alternative;

  assert.equal(store.getState().data, next);
  assert.equal(store.getState().recommendationMode, 'BASE');
  assert.equal(store.getState().loading, false);
  assert.equal(store.getState().error, undefined);
  assert.deepEqual(plain(store.getState().seenOutfits), [['BASE-1']]);
});

test('whitespace-only input uses base recommendation and resets AI history', async () => {
  const {store, calls} = fixture();
  const first = store.getState().fetchAiRecommendation('면접 코디');
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-1'}]});
  await first;
  store.getState().setInputPrompt('   ');

  const alternative = store.getState().fetchAlternative();

  assert.equal(calls[1].kind, 'GET');
  assert.deepEqual(plain(calls[1].args[2]), []);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'BASE-1'}]});
  await alternative;
  assert.equal(store.getState().recommendationMode, 'BASE');
  assert.equal(store.getState().inputPrompt, '');
  assert.deepEqual(plain(store.getState().seenOutfits), [['BASE-1']]);
});

test('a changed non-blank prompt starts a new AI cycle for alternative recommendation', async () => {
  const {store, calls} = fixture();
  const first = store.getState().fetchAiRecommendation('데이트 코디');
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'DATE-1'}]});
  await first;

  store.getState().setInputPrompt('  면접 코디  ');
  const alternative = store.getState().fetchAlternative();

  assert.equal(calls[1].kind, 'AI');
  assert.deepEqual(plain([calls[1].args[1], [...calls[1].args[2]], calls[1].args[3]]),
    ['면접 코디', [], []]);
  assert.equal(store.getState().recommendationMode, 'AI');
  assert.equal(store.getState().inputPrompt, '  면접 코디  ');
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'WORK-1'}]});
  await alternative;

  assert.equal(store.getState().recommendationMode, 'AI');
  assert.equal(store.getState().inputPrompt, '');
  assert.deepEqual(plain(store.getState().seenOutfits), [['WORK-1']]);
});

test('failed AI recommendation preserves the input and current recommendation cycle', async () => {
  const {store, calls} = fixture();
  const first = store.getState().fetchAiRecommendation('데이트 코디');
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'DATE-1'}]});
  await first;
  store.getState().setInputPrompt('면접 코디');

  const failed = store.getState().fetchAiRecommendation('면접 코디');
  calls[1].reject(new Error('failed'));
  await failed;

  assert.equal(store.getState().inputPrompt, '면접 코디');
  assert.equal(store.getState().recommendationMode, 'AI');
  assert.deepEqual(plain(store.getState().seenOutfits), [['DATE-1']]);
});

test('alternative button keeps the basic recommendation GET behavior before AI use', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [
    {clothesId: 'TOP-1'}, {clothesId: 'BOTTOM-1'},
  ]}});
  const refresh = store.getState().fetchAlternative();

  assert.equal(calls[0].kind, 'GET');
  assert.equal(calls[0].args[0].weatherId, 'A');
  assert.deepEqual([...calls[0].args[1]], []);
  assert.deepEqual(plain(calls[0].args[2]), [['BOTTOM-1', 'TOP-1']]);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'TOP'}]});
  await refresh;
});

test('base alternatives accumulate canonical outfits while allowing individual clothes reuse', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [
    {clothesId: 'A'}, {clothesId: 'B'}, {clothesId: 'C'},
  ]}});

  const second = store.getState().fetchAlternative();
  assert.deepEqual([...calls[0].args[1]], []);
  assert.deepEqual(plain(calls[0].args[2]), [['A', 'B', 'C']]);
  calls[0].resolve({weatherId: 'A', clothes: [
    {clothesId: 'A'}, {clothesId: 'B'}, {clothesId: 'D'}, {clothesId: 'D'},
  ]});
  await second;
  assert.deepEqual(plain(store.getState().seenOutfits), [['A', 'B', 'C'], ['A', 'B', 'D']]);

  const third = store.getState().fetchAlternative();
  assert.deepEqual(plain(calls[1].args[2]), [['A', 'B', 'C'], ['A', 'B', 'D']]);
  calls[1].resolve({weatherId: 'A', clothes: [
    {clothesId: 'C'}, {clothesId: 'E'}, {clothesId: 'A'},
  ]});
  await third;
  assert.deepEqual(plain(store.getState().seenOutfits),
    [['A', 'B', 'C'], ['A', 'B', 'D'], ['A', 'C', 'E']]);
});

test('weather change starts a fresh base cycle', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'A'}]}, seenOutfits: [['A']]});

  store.getState().updateParams({weatherId: 'B'});
  assert.deepEqual(plain(store.getState().seenOutfits), []);
  calls[0].resolve({weatherId: 'B', clothes: [{clothesId: 'B'}]});
  await flush();

  assert.deepEqual(plain(store.getState().seenOutfits), [['B']]);
  assert.equal(store.getState().recommendationMode, 'BASE');
});

test('new AI prompt starts an AI cycle without mixing base history', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'BASE'}]}, seenOutfits: [['BASE']]});
  store.getState().setInputPrompt('새 프롬프트');

  const firstAi = store.getState().fetchAiRecommendation('새 프롬프트');
  assert.deepEqual([...calls[0].args[2]], []);
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-1'}]});
  await firstAi;

  assert.equal(store.getState().inputPrompt, '');
  assert.equal(store.getState().recommendationMode, 'AI');
  assert.deepEqual(plain(store.getState().seenOutfits), [['AI-1']]);
});

test('switching from AI back to base resets the AI outfit history', async () => {
  const {store, calls} = fixture();
  const ai = store.getState().fetchAiRecommendation('면접 코디');
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'AI-1'}]});
  await ai;

  const base = store.getState().fetch();
  assert.equal(store.getState().recommendationMode, 'BASE');
  assert.deepEqual(plain(store.getState().seenOutfits), []);
  assert.equal(calls[1].kind, 'GET');
  assert.equal(calls[1].args.length, 1);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'BASE-1'}]});
  await base;

  assert.deepEqual(plain(store.getState().seenOutfits), [['BASE-1']]);
});

test('submitting a new AI prompt starts a fresh AI outfit history', async () => {
  const {store, calls} = fixture();
  const first = store.getState().fetchAiRecommendation('데이트 코디');
  calls[0].resolve({weatherId: 'A', clothes: [{clothesId: 'DATE-1'}]});
  await first;

  const next = store.getState().fetchAiRecommendation('면접 코디');
  assert.equal(calls[1].args[1], '면접 코디');
  assert.deepEqual([...calls[1].args[2]], []);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'WORK-1'}]});
  await next;

  assert.equal(store.getState().recommendationMode, 'AI');
  assert.deepEqual(plain(store.getState().seenOutfits), [['WORK-1']]);
});

test('failed alternative does not add unseen clothes to cycle history', async () => {
  const {store, calls} = fixture();
  store.setState({data: {weatherId: 'A', clothes: [{clothesId: 'A'}]}, seenOutfits: [['A']]});

  const alternative = store.getState().fetchAlternative();
  calls[0].reject(new Error('failed'));
  await alternative;

  assert.deepEqual(plain(store.getState().seenOutfits), [['A']]);
  assert.equal(store.getState().error, 'failed');
});

test('exhausted cycle resets once and starts again without recursive retries', async () => {
  const {store, calls} = fixture();
  store.setState({
    data: {weatherId: 'A', clothes: [{clothesId: 'B'}]},
    seenOutfits: [['A'], ['B']],
  });

  const alternative = store.getState().fetchAlternative();
  assert.deepEqual([...calls[0].args[1]], []);
  assert.deepEqual(plain(calls[0].args[2]), [['A'], ['B']]);
  calls[0].resolve({weatherId: 'A', clothes: []});
  await flush();
  assert.equal(calls.length, 2);
  assert.deepEqual([...calls[1].args[1]], []);
  assert.deepEqual(plain(calls[1].args[2]), []);
  calls[1].resolve({weatherId: 'A', clothes: [{clothesId: 'A'}]});
  await alternative;

  assert.equal(calls.length, 2);
  assert.deepEqual(plain(store.getState().seenOutfits), [['A']]);
  assert.equal(store.getState().error, undefined);
});
