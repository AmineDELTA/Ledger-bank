import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API_URL = 'http://localhost:8080';

function App() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sourceId, setSourceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [amount, setAmount] = useState('');
  const [transferStatus, setTransferStatus] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [demoRunning, setDemoRunning] = useState(false);
  const [demoState, setDemoState] = useState(null);
  const [notification, setNotification] = useState(null);
  const [historyAccountId, setHistoryAccountId] = useState('');
  const [historyEntries, setHistoryEntries] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const fetchAccounts = async () => {
    try {
      const response = await axios.get(`${API_URL}/accounts`);
      setAccounts(response.data);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch accounts:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const addActivity = (title, detail, tone = 'info') => {
    setNotification({ title, detail, tone });
  };

  const loadHistory = async (accountId) => {
    setHistoryAccountId(accountId);
    setHistoryLoading(true);

    try {
      const response = await axios.get(`${API_URL}/accounts/${accountId}/transactions`);
      setHistoryEntries(response.data);
    } catch (error) {
      setHistoryEntries([]);
      addActivity('History unavailable', error.response?.data?.message || 'Could not load this account history.', 'error');
    } finally {
      setHistoryLoading(false);
    }
  };

  const runTransferRequest = async (request, idempotencyKey, requestNumber, demoType) => {
    const startedAt = performance.now();
    setDemoState((currentState) => currentState ? {
      ...currentState,
      requests: currentState.requests.map((item) => item.number === requestNumber
        ? { ...item, status: 'sending' }
        : item)
    } : currentState);

    try {
      const response = await axios.post(`${API_URL}/transfers`, request, {
        headers: {
          'Idempotency-Key': idempotencyKey,
          'Content-Type': 'application/json'
        }
      });

      const result = {
        number: requestNumber,
        status: response.status,
        transactionId: response.data?.transactionId,
        duration: Math.round(performance.now() - startedAt),
        result: response.status === 200 ? (demoType === 'idempotency' ? 'Executed or cached' : 'Committed') : 'Unexpected response'
      };
      setDemoState((currentState) => currentState ? {
        ...currentState,
        completed: currentState.completed + 1,
        requests: currentState.requests.map((item) => item.number === requestNumber ? { ...item, ...result } : item)
      } : currentState);
      return result;
    } catch (error) {
      const result = {
        number: requestNumber,
        status: error.response?.status || 500,
        message: error.response?.data?.message || error.response?.data || error.message,
        duration: Math.round(performance.now() - startedAt),
        result: error.response?.status === 409 ? 'Blocked while pending' : 'Rejected'
      };
      setDemoState((currentState) => currentState ? {
        ...currentState,
        completed: currentState.completed + 1,
        requests: currentState.requests.map((item) => item.number === requestNumber ? { ...item, ...result } : item)
      } : currentState);
      return result;
    }
  };

  const canRunDemo = () => {
    if (!sourceId || !targetId || sourceId === targetId) {
      addActivity('Demo needs two accounts', 'Choose different source and destination accounts first.', 'error');
      return false;
    }

    return true;
  };

  const getRequestTone = (request) => {
    if (request.status === 'sending') return 'text-white';
    if (request.status === 'waiting') return 'text-slate-500';
    if (request.status === 200) return 'text-emerald-300';
    if (request.status === 409) return 'text-amber-300';
    return 'text-rose-300';
  };

  const notificationTone = notification?.tone === 'success'
    ? 'border-emerald-300 bg-emerald-50 text-emerald-900'
    : notification?.tone === 'error'
      ? 'border-rose-300 bg-rose-50 text-rose-900'
      : notification?.tone === 'warning'
        ? 'border-amber-300 bg-amber-50 text-amber-900'
        : 'border-slate-300 bg-white text-slate-900';

  const runIdempotencyDemo = async () => {
    if (!canRunDemo()) return;

    const demoAmount = Number(amount) > 0 ? Number(amount) : 1;
    const sharedKey = `frontend-demo-${crypto.randomUUID()}`;
    const request = {
      fromAccountId: sourceId,
      toAccountId: targetId,
      amount: demoAmount,
      description: 'Frontend idempotency demonstration'
    };

    setDemoRunning(true);
    setDemoState({
      name: 'Idempotency test',
      phase: 'Sending five requests with one shared key',
      completed: 0,
      before: accounts,
      after: null,
      requests: Array.from({ length: 5 }, (_, index) => ({ number: index + 1, status: 'waiting' }))
    });
    addActivity('Idempotency demo started', 'Five requests will share one Idempotency-Key.', 'info');

    const results = await Promise.all(
      Array.from({ length: 5 }, (_, index) => runTransferRequest(request, sharedKey, index + 1, 'idempotency'))
    );
    const successfulResults = results.filter((result) => result.status === 200);
    const conflictResults = results.filter((result) => result.status === 409);
    const transactionIds = new Set(successfulResults.map((result) => result.transactionId).filter(Boolean));

    const refreshedAccounts = await fetchAccounts();
    setDemoState((currentState) => currentState ? { ...currentState, phase: 'Verification complete', after: refreshedAccounts } : currentState);
    addActivity(
      'Idempotency demo finished',
      `${successfulResults.length} successful response(s), ${conflictResults.length} conflict(s), ${transactionIds.size} transaction ID(s). Money should move once.`,
      transactionIds.size <= 1 ? 'success' : 'error'
    );
    setDemoRunning(false);
  };

  const runConcurrencyDemo = async () => {
    if (!canRunDemo()) return;

    const demoAmount = Number(amount) > 0 ? Number(amount) : 1;
    const request = {
      fromAccountId: sourceId,
      toAccountId: targetId,
      amount: demoAmount,
      description: 'Frontend concurrency demonstration'
    };

    setDemoRunning(true);
    setDemoState({
      name: 'Concurrency test',
      phase: 'Sending five unique transfers',
      completed: 0,
      before: accounts,
      after: null,
      requests: Array.from({ length: 5 }, (_, index) => ({ number: index + 1, status: 'waiting' }))
    });
    addActivity('Concurrency demo started', 'Five unique transfers will compete for the same account locks.', 'info');

    const results = await Promise.all(
      Array.from({ length: 5 }, (_, index) => runTransferRequest(request, `frontend-concurrency-${crypto.randomUUID()}`, index + 1, 'concurrency'))
    );
    const successfulResults = results.filter((result) => result.status === 200);
    const rejectedResults = results.length - successfulResults.length;

    const refreshedAccounts = await fetchAccounts();
    setDemoState((currentState) => currentState ? { ...currentState, phase: 'Verification complete', after: refreshedAccounts } : currentState);
    addActivity(
      'Concurrency demo finished',
      `${successfulResults.length}/5 committed and ${rejectedResults} rejected. Refresh the balances to inspect the final state.`,
      rejectedResults === 0 ? 'success' : 'warning'
    );
    setDemoRunning(false);
  };

  const handleTransfer = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setTransferStatus(null);

    const previousAccounts = accounts;
    const transferAmount = Number(amount);

    try {
      const payload = {
        fromAccountId: sourceId,
        toAccountId: targetId,
        amount: transferAmount,
        description: 'Transfer initiated from Admin Console'
      };

      setAccounts((currentAccounts) => currentAccounts.map((account) => {
        if (account.accountId === sourceId) {
          return { ...account, balance: Number(account.balance) - transferAmount };
        }

        if (account.accountId === targetId) {
          return { ...account, balance: Number(account.balance) + transferAmount };
        }

        return account;
      }));

      const requestConfig = {
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
          'Content-Type': 'application/json'
        }
      };

      const response = await axios.post(`${API_URL}/transfers`, payload, requestConfig);

      setTransferStatus({
        success: true,
        message: `Transfer executed successfully. Ref: ${response.data.transactionId || 'OK'}`
      });

      setAmount('');
      fetchAccounts();
    } catch (error) {
      setTransferStatus({
        success: false,
        message: error.response?.data?.message || error.response?.data || error.message || 'Transfer failed'
      });
      setAccounts(previousAccounts);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-stone-100 text-slate-700 p-6 md:p-10">
      <div className="mx-auto max-w-6xl">
        {notification && (
          <div
            className={`fixed right-4 top-4 z-50 w-[min(24rem,calc(100vw-2rem))] rounded-xl border px-4 py-3 shadow-lg ${notificationTone}`}
            role="status"
            aria-live="polite"
          >
            <p className="text-sm font-semibold">{notification.title}</p>
            <p className="mt-1 text-xs leading-5 opacity-80">{notification.detail}</p>
          </div>
        )}

        <header className="mb-8 flex items-end justify-between gap-4 border-b border-slate-200 pb-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-500">Admin Console</p>
            <h1 className="mt-2 text-3xl font-semibold text-slate-900">Core Banking</h1>
          </div>
          <div className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
            System online
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-2">
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">Accounts</h2>
              <span className="text-xs text-slate-500">Live balances</span>
            </div>

            {loading ? (
              <p className="text-sm text-slate-500">Loading accounts...</p>
            ) : (
              <div className="space-y-3">
                {accounts.map((acc) => (
                  <div key={acc.accountId} className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
                    <div>
                      <p className="text-sm font-medium text-slate-900">{acc.holderName}</p>
                      <p className="mt-1 text-xs text-slate-500">{acc.accountNumber}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Balance</p>
                      <p className="mt-1 text-lg font-semibold text-slate-900">${Number(acc.balance).toFixed(2)}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => loadHistory(acc.accountId)}
                      className="shrink-0 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition hover:border-slate-400 hover:text-slate-900"
                    >
                      History
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-lg font-semibold text-slate-900">Transfer funds</h2>

            <form onSubmit={handleTransfer} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-slate-500">From account</label>
                <select
                  required
                  value={sourceId}
                  onChange={(e) => setSourceId(e.target.value)}
                  disabled={loading || accounts.length === 0}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <option value="">Select source account</option>
                  {accounts.map((account) => (
                    <option key={account.accountId} value={account.accountId}>
                      {account.accountNumber} - {account.holderName} (${Number(account.balance).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-slate-500">To account</label>
                <select
                  required
                  value={targetId}
                  onChange={(e) => setTargetId(e.target.value)}
                  disabled={loading || accounts.length === 0}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <option value="">Select destination account</option>
                  {accounts.map((account) => (
                    <option key={account.accountId} value={account.accountId}>
                      {account.accountNumber} - {account.holderName} (${Number(account.balance).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Amount</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? 'Processing transfer...' : 'Submit transfer'}
              </button>
            </form>

            {transferStatus && (
              <div className={`mt-4 rounded-xl border px-3 py-2.5 text-sm ${
                transferStatus.success
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                  : 'border-rose-200 bg-rose-50 text-rose-700'
              }`}>
                {transferStatus.message}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-2">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-slate-900">Transaction history</h2>
                <p className="mt-1 text-sm text-slate-500">Ledger entries recorded for the selected account.</p>
              </div>
              <span className="shrink-0 text-xs uppercase tracking-[0.16em] text-slate-400">
                {historyEntries.length} entries
              </span>
            </div>

            <div className="mt-4 h-[15rem] overflow-y-auto rounded-xl border border-slate-200 bg-slate-50">
              {!historyAccountId ? (
                <div className="flex h-full items-center justify-center px-6 text-center text-sm text-slate-500">
                  Select History on an account to inspect its ledger.
                </div>
              ) : historyLoading ? (
                <div className="flex h-full items-center justify-center text-sm text-slate-500">Loading ledger...</div>
              ) : historyEntries.length === 0 ? (
                <div className="flex h-full items-center justify-center px-6 text-center text-sm text-slate-500">
                  No ledger entries exist for this account yet.
                </div>
              ) : (
                <div className="divide-y divide-slate-200">
                  {historyEntries.map((entry) => (
                    <div key={`${entry.transactionId}-${entry.type}-${entry.amount}`} className="grid grid-cols-[auto_1fr_auto] items-center gap-3 px-4 py-3 text-sm">
                      <span className={`rounded-full px-2 py-1 text-xs font-medium ${
                        entry.type === 'CREDIT' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                      }`}>
                        {entry.type}
                      </span>
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-800">{entry.description || 'Account transaction'}</p>
                        <p className="mt-1 truncate text-xs text-slate-500">
                          {new Date(entry.timestamp).toLocaleString()} · {entry.transactionId}
                        </p>
                      </div>
                      <span className={`whitespace-nowrap font-semibold ${
                        entry.type === 'CREDIT' ? 'text-emerald-700' : 'text-rose-700'
                      }`}>
                        {entry.type === 'CREDIT' ? '+' : '-'}${Math.abs(Number(entry.amount)).toFixed(2)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <aside className="order-first min-h-[28rem] rounded-2xl border border-slate-200 bg-slate-900 p-5 text-slate-200 shadow-sm xl:col-span-2">
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-400">Backend lab</p>
            <h2 className="mt-3 text-xl font-semibold text-white">See the safeguards work</h2>
            <p className="mt-2 text-sm leading-6 text-slate-400">Run small real-request demos against Redis, PostgreSQL, and the transfer locks.</p>

            <div className="mt-5 grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={runIdempotencyDemo}
                disabled={demoRunning || loading}
                className="w-full rounded-xl border border-slate-700 bg-slate-800 px-3 py-2.5 text-left text-sm font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Duplicate request demo
                <span className="mt-1 block text-xs font-normal text-slate-400">5 requests, 1 shared key</span>
              </button>
              <button
                type="button"
                onClick={runConcurrencyDemo}
                disabled={demoRunning || loading}
                className="w-full rounded-xl border border-slate-700 bg-slate-800 px-3 py-2.5 text-left text-sm font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Locking demo
                <span className="mt-1 block text-xs font-normal text-slate-400">5 requests, unique keys</span>
              </button>
            </div>

            <div className="mt-5 h-[18rem] overflow-y-auto border-t border-slate-800 pt-4">
              {demoState ? (
                <>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-white">{demoState.name}</p>
                    <p className="mt-1 text-xs text-slate-400">{demoState.phase}</p>
                  </div>
                  <span className="shrink-0 text-xs text-slate-400">{demoState.completed}/5</span>
                </div>

                <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-800">
                  <div
                    className="h-full rounded-full bg-emerald-400 transition-all duration-300"
                    style={{ width: `${(demoState.completed / 5) * 100}%` }}
                  />
                </div>

                <div className="mt-3 space-y-1.5">
                  {demoState.requests.map((request) => (
                    <div key={request.number} className="flex items-center justify-between rounded-lg bg-slate-800 px-3 py-2 text-xs">
                      <span className="text-slate-300">Request {request.number}</span>
                      <span className={getRequestTone(request)}>
                        {request.status === 'waiting' && 'Waiting'}
                        {request.status === 'sending' && 'Sending...'}
                        {request.status === 200 && `${request.status} ${request.result}`}
                        {request.status === 409 && `${request.status} ${request.result}`}
                        {request.status !== 'waiting' && request.status !== 'sending' && request.status !== 200 && request.status !== 409 && `${request.status} ${request.result}`}
                      </span>
                    </div>
                  ))}
                </div>

                {demoState.after && (
                  <div className="mt-3 rounded-lg bg-slate-800 px-3 py-2 text-xs">
                    <p className="uppercase tracking-[0.16em] text-slate-500">Balance verification</p>
                    {demoState.after.filter((account) => [sourceId, targetId].includes(account.accountId)).map((account) => {
                      const before = demoState.before.find((item) => item.accountId === account.accountId);
                      const change = Number(account.balance) - Number(before?.balance || 0);
                      return (
                        <div key={account.accountId} className="mt-2 flex justify-between gap-2 text-slate-300">
                          <span>{account.accountNumber}</span>
                          <span className={change < 0 ? 'text-rose-300' : 'text-emerald-300'}>
                            ${Number(account.balance).toFixed(2)} ({change >= 0 ? '+' : ''}{change.toFixed(2)})
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}
                </>
              ) : (
                <div className="flex h-full items-center justify-center rounded-lg border border-dashed border-slate-700 px-6 text-center">
                  <p className="max-w-sm text-sm leading-6 text-slate-500">Choose two different accounts, then run a demo to see each request and the verified balance movement here.</p>
                </div>
              )}
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

export default App;