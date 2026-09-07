import React, { useState, useEffect } from 'react';
import axios from 'axios';

function App() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sourceId, setSourceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [amount, setAmount] = useState('');
  const [transferStatus, setTransferStatus] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchAccounts = async () => {
    try {
      const response = await axios.get('http://localhost:8080/accounts');
      setAccounts(response.data);
    } catch (error) {
      console.error('Failed to fetch accounts:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const handleTransfer = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setTransferStatus(null);

    try {
      const payload = {
        fromAccountId: sourceId,
        toAccountId: targetId,
        amount: parseFloat(amount),
        description: 'Transfer initiated from Admin Console'
      };

      const requestConfig = {
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
          'Content-Type': 'application/json'
        }
      };

      const response = await axios.post('http://localhost:8080/transfers', payload, requestConfig);

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
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-stone-100 text-slate-700 p-6 md:p-10">
      <div className="mx-auto max-w-6xl">
        <header className="mb-8 flex items-end justify-between gap-4 border-b border-slate-200 pb-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-500">Admin Console</p>
            <h1 className="mt-2 text-3xl font-semibold text-slate-900">Core Banking</h1>
          </div>
          <div className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
            System online
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-[1.1fr_1.2fr_0.9fr]">
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
                  <div key={acc.accountId} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                    <div>
                      <p className="text-sm font-medium text-slate-900">{acc.holderName}</p>
                      <p className="mt-1 text-xs text-slate-500">{acc.accountNumber}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Balance</p>
                      <p className="mt-1 text-lg font-semibold text-slate-900">${Number(acc.balance).toFixed(2)}</p>
                    </div>
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
                <input
                  type="text"
                  required
                  value={sourceId}
                  onChange={(e) => setSourceId(e.target.value)}
                  placeholder="UUID"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-slate-500">To account</label>
                <input
                  type="text"
                  required
                  value={targetId}
                  onChange={(e) => setTargetId(e.target.value)}
                  placeholder="UUID"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
                />
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

          <aside className="rounded-2xl border border-slate-200 bg-slate-900 p-5 text-slate-200 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-400">Operations</p>
            <h2 className="mt-3 text-xl font-semibold text-white">Daily overview</h2>

            <div className="mt-5 space-y-3">
              <div className="rounded-xl bg-slate-800 p-3">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Transfers</p>
                <p className="mt-2 text-2xl font-semibold text-white">24</p>
              </div>
              <div className="rounded-xl bg-slate-800 p-3">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Accounts</p>
                <p className="mt-2 text-2xl font-semibold text-white">{accounts.length}</p>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

export default App;