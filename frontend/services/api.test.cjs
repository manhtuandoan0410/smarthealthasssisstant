const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, 'api.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

function loadApi({ store, fetch, platform = 'android' }) {
    const module = { exports: {} };
    const secureStore = {
        async getItemAsync(key) { return store.get(key) ?? null; },
        async deleteItemAsync(key) { store.delete(key); },
    };
    vm.runInNewContext(compiled, {
        module,
        exports: module.exports,
        require(name) {
            if (name === 'expo-secure-store') return secureStore;
            if (name === 'react-native') return { Platform: { OS: platform } };
            throw new Error(`Unexpected dependency: ${name}`);
        },
        process: { env: { EXPO_PUBLIC_API_BASE_URL: 'http://192.168.1.1:8080' } },
        fetch,
        URL,
        URLSearchParams,
        Error,
        TypeError,
    });
    return module.exports;
}

function response(body = null) {
    return { ok: true, json: async () => body };
}

test('login and signup tokens do not survive a fresh app runtime', async () => {
    for (const method of ['login', 'signup']) {
        const store = new Map();
        const fetch = async () => response({ accessToken: 'session-token', email: 'user@example.com' });
        const app = loadApi({ store, fetch });
        await app[method]({ email: 'user@example.com', password: 'password', firstName: 'First', lastName: 'Last' });
        assert.equal(await app.getAuthToken(), 'session-token');
        assert.equal(store.size, 0);

        const restartedApp = loadApi({ store, fetch });
        await restartedApp.clearLegacySession();
        assert.equal(await restartedApp.checkSession(), null);
    }
});

test('startup removes and revokes an old saved token', async () => {
    const store = new Map([['smart-health-auth-token', 'old-token']]);
    const calls = [];
    const app = loadApi({
        store,
        fetch: async (url, options) => {
            calls.push({ url, options });
            return response();
        },
    });

    await app.clearLegacySession();
    assert.equal(store.size, 0);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'http://192.168.1.1:8080/api/auth/logout');
    assert.equal(calls[0].options.headers.Authorization, 'Bearer old-token');
    assert.equal(await app.checkSession(), null);
});

test('startup removes an old token even if revocation fails', async () => {
    const store = new Map([['smart-health-auth-token', 'old-token']]);
    const app = loadApi({ store, fetch: async () => { throw new TypeError('network failure'); } });

    await assert.rejects(app.clearLegacySession(), /removed from this device but could not be revoked/);
    assert.equal(store.size, 0);
    assert.equal(await app.checkSession(), null);
});

test('logout clears the in-memory token even if the server is unreachable', async () => {
    let fail = false;
    const app = loadApi({
        store: new Map(),
        fetch: async () => {
            if (fail) throw new TypeError('network failure');
            return response({ accessToken: 'session-token', email: 'user@example.com' });
        },
    });
    await app.login({ email: 'user@example.com', password: 'password' });
    fail = true;

    await assert.rejects(app.logout(), /Cannot reach the backend/);
    assert.equal(await app.getAuthToken(), null);
});

test('web startup does not access unsupported secure storage', async () => {
    const store = { get() { throw new Error('Secure storage is unavailable'); } };
    const app = loadApi({ store, platform: 'web', fetch: async () => response() });
    await app.clearLegacySession();
    assert.equal(await app.checkSession(), null);
});

test('session verification returns the authenticated account and rejects expired sessions', async () => {
    let expired = false;
    const app = loadApi({
        store: new Map(),
        fetch: async (url) => {
            if (url.endsWith('/login')) return response({ accessToken: 'session-token', email: 'user@example.com' });
            if (expired) return { ok: false, status: 401, json: async () => ({}) };
            return response({ message: 'user@example.com' });
        },
    });
    assert.equal(await app.login({ email: 'USER@example.com', password: 'password' }), 'user@example.com');
    assert.equal(await app.checkSession(), 'user@example.com');
    expired = true;
    assert.equal(await app.checkSession(), null);
});
