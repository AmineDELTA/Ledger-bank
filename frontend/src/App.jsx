import React, { useState, useEffect } from 'react';
import axios from 'axios';

function App() {
  // 1. Account State (Pillar A)
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);

  // 2. Transfer Form State (Pillar B)
  const [sourceId, setSourceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [amount, setAmount] = useState('');

  // 3. Execution Feedback State
  const [transferStatus, setTransferStatus] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Helper function to fetch account data
  const fetchAccounts = async () => {
    try {
      // Replace with your actual ACC-001 UUID
      const response = await axios.get(`http://localhost:8080/accounts`);
      setAccounts(response.data);

    } catch (error) {
      console.error("Failed to fetch accounts:", error);
    } finally {
      setLoading(false);
    }
  };

  // Run on startup
  useEffect(() => {
    fetchAccounts();
  }, []);

  // Form Submit Handler
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
        message: `Transfer executed successfully! Ref: ${response.data.transactionId || 'OK'}` 
      });

      // Clear input and re-fetch balances automatically
      setAmount('');
      fetchAccounts();
    } catch (error) {
      setTransferStatus({ 
        success: false, 
        message: error.response?.data?.message || error.message || 'Transfer failed' 
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-300 p-8 font-sans">
      
      {/* Header */}
      <header className="mb-8 border-b border-slate-800 pb-4">
        <h1 className="text-2xl font-bold text-white tracking-tight">
          Core Banking <span className="text-emerald-500">Engine</span>
        </h1>
        <p className="text-sm text-slate-500 mt-1">Internal Administration Console</p>
      </header>

      {/* The 3-Pillar Dashboard Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Pillar A: Accounts & Ledger */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-2xl">
          <h2 className="text-lg font-semibold text-white mb-4 border-b border-slate-800 pb-2">
            Active Accounts
          </h2>
          
          {loading ? (
            <p className="text-sm italic text-slate-500">Fetching accounts from PostgreSQL...</p>
          ) : (
            <div className="space-y-3">
              {accounts.map((acc) => (
                <div key={acc.accountId} className="p-3 bg-slate-800 rounded border border-slate-700 flex justify-between items-center">
                  <div>
                    <p className="text-white font-mono text-sm">{acc.accountNumber}</p>
                    <p className="text-xs text-slate-500 mt-1 truncate w-32" title={acc.accountId}>{acc.accountId}</p>
                  </div>
                  <p className="text-emerald-400 font-bold font-mono">${acc.balance.toFixed(2)}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Pillar B: Transfer Engine */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-500 to-cyan-500"></div>
          <h2 className="text-lg font-semibold text-white mb-4 border-b border-slate-800 pb-2">
            Execute Transfer
          </h2>
          
          <form onSubmit={handleTransfer} className="space-y-4">
            <div>
              <label className="block text-xs font-mono text-slate-400 mb-1">Source Account UUID</label>
              <input
                type="text"
                required
                value={sourceId}
                onChange={(e) => setSourceId(e.target.value)}
                placeholder="e.g. 852b4a13-..."
                className="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-slate-400 mb-1">Target Account UUID</label>
              <input
                type="text"
                required
                value={targetId}
                onChange={(e) => setTargetId(e.target.value)}
                placeholder="e.g. 4a2c1b89-..."
                className="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-slate-400 mb-1">Amount ($)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                required
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                className="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 font-mono"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-medium py-2 rounded text-sm transition-colors disabled:opacity-50"
            >
              {submitting ? 'Executing via Ledger...' : 'Dispatch Transfer'}
            </button>
          </form>

          {/* Status Box */}
          {transferStatus && (
            <div className={`mt-4 p-3 rounded text-xs font-mono border ${
              transferStatus.success 
                ? 'bg-emerald-950/50 border-emerald-800 text-emerald-300' 
                : 'bg-rose-950/50 border-rose-800 text-rose-300'
            }`}>
              {transferStatus.message}
            </div>
          )}
        </div>

        {/* Pillar C: Diagnostics & Security */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-2xl border-t-4 border-t-rose-900">
          <h2 className="text-lg font-semibold text-white mb-4 border-b border-slate-800 pb-2">
            Stress Test & Security
          </h2>
          <div className="text-sm text-slate-500 space-y-4">
            <p className="italic">Ready for attack vector simulations.</p>
          </div>
        </div>

      </div>
    </div>
  );
}

export default App;