const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

function renderComponent(file, { auth, firstState, search, save, load }) {
    const source = fs.readFileSync(path.join(__dirname, file), 'utf8');
    const compiled = ts.transpileModule(source, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2020 },
    }).outputText;
    const module = { exports: {} };
    let stateIndex = 0;
    const jsx = (type, props) => ({ type, props: props ?? {} });
    vm.runInNewContext(compiled, {
        module,
        exports: module.exports,
        require(name) {
            if (name === 'react/jsx-runtime') return { jsx, jsxs: jsx };
            if (name === 'react') {
                return {
                    useState(value) {
                        const state = stateIndex++ === 0 && firstState !== undefined ? firstState : value;
                        return [state, () => {}];
                    },
                    useCallback: (callback) => callback,
                    useEffect: (callback) => callback(),
                };
            }
            if (name === 'react-native') {
                return {
                    ActivityIndicator: 'ActivityIndicator', FlatList: 'FlatList', Pressable: 'Pressable',
                    ScrollView: 'ScrollView', Text: 'Text', TextInput: 'TextInput', View: 'View',
                    StyleSheet: { create: (styles) => styles }, Alert: { alert() {} },
                };
            }
            if (name === '@react-navigation/native') return { useFocusEffect() {} };
            if (name === '../services/AuthContext') return { useAuth: () => auth };
            if (name === '../services/api') return { searchCondition: search };
            if (name === '../services/searchHistory') return { saveSearchHistoryEntry: save, loadSearchHistory: load };
            if (name === './ConditionResult') return { default: 'ConditionResult' };
            throw new Error(`Unexpected dependency: ${name}`);
        },
        Error,
        Date,
    });
    return module.exports.default({});
}

function find(node, predicate) {
    if (!node || typeof node !== 'object') return undefined;
    if (predicate(node)) return node;
    const children = node.props?.children;
    for (const child of Array.isArray(children) ? children : [children]) {
        const found = find(child, predicate);
        if (found) return found;
    }
}

test('history menu is visible only to signed-in users', () => {
    for (const status of ['signed-out', 'checking', 'error', 'signed-in']) {
        const tree = renderComponent('ButtonAppBar.tsx', { auth: { status }, firstState: true });
        const item = find(tree, (node) => node.type === 'Text' && node.props.children === 'Search History');
        assert.equal(Boolean(item), status === 'signed-in');
    }
});

test('public condition searches are not saved, while registered searches are', async () => {
    for (const status of ['signed-out', 'signed-in']) {
        const saved = [];
        const tree = renderComponent('Mainpage.tsx', {
            auth: { status, email: status === 'signed-in' ? 'user@example.com' : null },
            firstState: 'flu',
            search: async () => ({ name: 'Flu' }),
            save: async (account, entry) => { saved.push({ account, entry }); },
        });
        await find(tree, (node) => node.type === 'TextInput').props.onSubmitEditing();
        assert.equal(saved.length, status === 'signed-in' ? 1 : 0);
        if (saved.length) {
            assert.equal(saved[0].account, 'user@example.com');
            assert.equal(saved[0].entry.query, 'flu');
        }
    }
});

test('the history screen does not load records for signed-out users', () => {
    let loads = 0;
    const tree = renderComponent('SearchHistory.tsx', {
        auth: { status: 'signed-out', email: null },
        load: () => { loads++; },
    });
    assert.ok(find(tree, (node) => node.type === 'Text'
        && node.props.children === 'Log in or create an account to see search history.'));
    assert.equal(loads, 0);
});
