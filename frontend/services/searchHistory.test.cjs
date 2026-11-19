const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, 'searchHistory.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true },
}).outputText;

function loadHistory(store, backend = 'http://example.test') {
    const module = { exports: {} };
    vm.runInNewContext(compiled, {
        module,
        exports: module.exports,
        require(name) {
            if (name === '@react-native-async-storage/async-storage') {
                return {
                    getItem: async (key) => store.get(key) ?? null,
                    setItem: async (key, value) => { store.set(key, value); },
                };
            }
            if (name === './api') return { getApiBaseUrl: () => backend };
            throw new Error(`Unexpected dependency: ${name}`);
        },
        encodeURIComponent,
    });
    return module.exports;
}

function entry(query) {
    return { id: query, query, answer: { name: query }, searchedAt: '2026-01-01T00:00:00Z' };
}

test('history is separated by account and backend and excludes old shared searches', async () => {
    const legacy = JSON.stringify([entry('old shared search')]);
    const store = new Map([['@smarthhealth/search-history', legacy]]);
    const history = loadHistory(store);
    await history.saveSearchHistoryEntry('admin@example.com', entry('admin search'));
    await history.saveSearchHistoryEntry('user@example.com', entry('user search'));
    await history.saveSearchHistoryEntry('ADMIN@example.com', entry('second admin search'));

    assert.deepEqual(JSON.parse(JSON.stringify(await history.loadSearchHistory('admin@example.com'))),
        [entry('second admin search'), entry('admin search')]);
    assert.deepEqual(JSON.parse(JSON.stringify(await history.loadSearchHistory('user@example.com'))),
        [entry('user search')]);
    assert.equal((await loadHistory(store, 'http://other.test').loadSearchHistory('admin@example.com')).length, 0);
    assert.equal(store.get('@smarthhealth/search-history'), legacy);
});

test('storage and invalid data errors are surfaced', async () => {
    const store = new Map();
    const history = loadHistory(store);
    await history.saveSearchHistoryEntry('user@example.com', entry('flu'));
    const key = [...store.keys()][0];
    store.set(key, '{}');
    await assert.rejects(history.loadSearchHistory('user@example.com'), /invalid format/);
    await assert.rejects(history.saveSearchHistoryEntry('user@example.com', entry('cold')), /invalid format/);
});
