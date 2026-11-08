import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';

const rawApiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const API_URL = rawApiUrl.replace(/\/+$/, '');

const money = (value) => `$${Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const shortId = (value) => value ? `${value.slice(0, 8)}...${value.slice(-4)}` : '—';

function StatusMark({ status }) {
  const active = status === 'UP';
  return <span className={`status-mark ${active ? 'status-mark--active' : ''}`} aria-hidden="true" />;
}

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
  const [health, setHealth] = useState({ status: 'CHECKING', components: null });
  const [healthLoading, setHealthLoading] = useState(false);
  const notificationTimerRef = useRef(null);

  const addActivity = (title, detail, tone = 'info') => {
    if (notificationTimerRef.current) clearTimeout(notificationTimerRef.current);
    setNotification({ title, detail, tone });
    notificationTimerRef.current = setTimeout(() => setNotification(null), 4500);
  };

  const fetchHealth = async () => {
    setHealthLoading(true);
    try {
      const response = await axios.get(`${API_URL}/actuator/health`, { timeout: 8000 });
      setHealth({ status: response.data?.status || 'UP', components: response.data?.components || null });
    } catch (error) {
      setHealth({ status: error.response?.data?.status || 'DOWN', components: error.response?.data?.components || null });
    } finally {
      setHealthLoading(false);
    }
  };

  const fetchAccounts = async () => {
    try {
      const response = await axios.get(`${API_URL}/accounts`);
      setAccounts(response.data);
      return response.data;
    } catch (error) {
      addActivity('Account feed unavailable', error.response?.data?.message || 'The ledger API did not respond.', 'error');
      return [];
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
    fetchHealth();
    const interval = setInterval(fetchHealth, 15000);
    return () => {
      clearInterval(interval);
      if (notificationTimerRef.current) clearTimeout(notificationTimerRef.current);
    };
  }, []);

  const loadHistory = async (accountId) => {
    if (!accountId) return;
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

  const formatTimestamp = (timestamp) => {
    const parsed = new Date(timestamp);
    return Number.isNaN(parsed.getTime()) ? 'UNKNOWN TIME' : parsed.toLocaleString();
  };

  const getRequestTone = (request) => {
    if (request.status === 'sending') return 'request--sending';
    if (request.status === 'waiting') return 'request--waiting';
    if (request.status === 200) return 'request--success';
    if (request.status === 409) return 'request--warning';
    return 'request--error';
  };

  const runTransferRequest = async (request, idempotencyKey, requestNumber, demoType) => {
    const startedAt = performance.now();
    setDemoState((current) => current ? { ...current, requests: current.requests.map((item) => item.number === requestNumber ? { ...item, status: 'sending' } : item) } : current);
    try {
      const response = await axios.post(`${API_URL}/transfers`, request, { headers: { 'Idempotency-Key': idempotencyKey, 'Content-Type': 'application/json' } });
      const result = {
        number: requestNumber,
        status: response.status,
        transactionId: response.data?.transactionId,
        duration: Math.round(performance.now() - startedAt),
        result: response.status === 200 ? (demoType === 'idempotency' ? 'EXECUTED / CACHED' : 'COMMITTED') : 'UNEXPECTED'
      };
      setDemoState((current) => current ? { ...current, completed: current.completed + 1, requests: current.requests.map((item) => item.number === requestNumber ? { ...item, ...result } : item) } : current);
      return result;
    } catch (error) {
      const result = {
        number: requestNumber,
        status: error.response?.status || 500,
        message: error.response?.data?.message || error.response?.data || error.message,
        duration: Math.round(performance.now() - startedAt),
        result: error.response?.status === 409 ? 'BLOCKED / PENDING' : 'REJECTED'
      };
      setDemoState((current) => current ? { ...current, completed: current.completed + 1, requests: current.requests.map((item) => item.number === requestNumber ? { ...item, ...result } : item) } : current);
      return result;
    }
  };

  const canRunDemo = () => {
    if (!sourceId || !targetId || sourceId === targetId) {
      addActivity('Two accounts required', 'Choose different source and destination accounts first.', 'error');
      return false;
    }
    return true;
  };

  const runDemo = async (type) => {
    if (!canRunDemo()) return;
    const demoAmount = Number(amount) > 0 ? Number(amount) : 1;
    const idempotency = type === 'idempotency';
    const request = { fromAccountId: sourceId, toAccountId: targetId, amount: demoAmount, description: `Frontend ${idempotency ? 'idempotency' : 'concurrency'} demonstration` };
    const sharedKey = idempotency ? `frontend-demo-${crypto.randomUUID()}` : null;
    const requests = Array.from({ length: 5 }, (_, index) => ({ number: index + 1, status: 'waiting' }));
    setDemoRunning(true);
    setDemoState({ name: idempotency ? 'IDEMPOTENCY TEST' : 'CONCURRENCY TEST', phase: idempotency ? 'SENDING FIVE REQUESTS / ONE SHARED KEY' : 'SENDING FIVE REQUESTS / UNIQUE KEYS', completed: 0, before: accounts, after: null, requests });
    addActivity(`${idempotency ? 'Idempotency' : 'Concurrency'} demo started`, idempotency ? 'Five requests will share one Idempotency-Key.' : 'Five unique transfers will compete for the same locks.');
    const results = await Promise.all(requests.map((_, index) => runTransferRequest(request, sharedKey || `frontend-concurrency-${crypto.randomUUID()}`, index + 1, type)));
    const successful = results.filter((result) => result.status === 200).length;
    const refreshed = await fetchAccounts();
    setDemoState((current) => current ? { ...current, phase: 'VERIFICATION COMPLETE', after: refreshed } : current);
    if (historyAccountId === sourceId || historyAccountId === targetId) await loadHistory(historyAccountId);
    addActivity(`${idempotency ? 'Idempotency' : 'Concurrency'} demo finished`, `${successful}/5 requests returned 200. Inspect the ledger for the durable result.`, successful > 0 ? 'success' : 'warning');
    setDemoRunning(false);
  };

  const handleTransfer = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setTransferStatus(null);
    const previousAccounts = accounts;
    const transferAmount = Number(amount);
    try {
      if (!sourceId || !targetId || sourceId === targetId || transferAmount <= 0) throw new Error('Choose two different accounts and enter an amount greater than zero.');
      setAccounts((current) => current.map((account) => account.accountId === sourceId ? { ...account, balance: Number(account.balance) - transferAmount } : account.accountId === targetId ? { ...account, balance: Number(account.balance) + transferAmount } : account));
      const response = await axios.post(`${API_URL}/transfers`, { fromAccountId: sourceId, toAccountId: targetId, amount: transferAmount, description: 'Transfer initiated from Admin Console' }, { headers: { 'Idempotency-Key': crypto.randomUUID(), 'Content-Type': 'application/json' } });
      setTransferStatus({ success: true, message: `TRANSFER COMMITTED // REF ${response.data.transactionId || 'OK'}` });
      setAmount('');
      await fetchAccounts();
      if (historyAccountId === sourceId || historyAccountId === targetId) await loadHistory(historyAccountId);
    } catch (error) {
      setTransferStatus({ success: false, message: error.response?.data?.message || error.response?.data || error.message || 'TRANSFER FAILED' });
      setAccounts(previousAccounts);
    } finally {
      setSubmitting(false);
    }
  };

  const selectedHistoryAccount = accounts.find((account) => account.accountId === historyAccountId);
  const totalBalance = accounts.reduce((sum, account) => sum + Number(account.balance || 0), 0);
  const healthLabel = healthLoading ? 'CHECKING' : health.status === 'UP' ? 'SYSTEM ONLINE' : health.components?.db?.status === 'UP' ? 'DB ONLINE / REDIS OFFLINE' : 'SYSTEM OFFLINE';

  return (
    <main className="app-shell">
      {notification && <div className={`toast toast--${notification.tone}`} role="status"><div><strong>{notification.title}</strong><span>{notification.detail}</span></div><button type="button" onClick={() => setNotification(null)} aria-label="Close notification">×</button></div>}

      <div className="topline"><span>LEDGER-BANK / CORE OPERATIONS</span><span>ENV: PRODUCTION SIMULATION</span><span>UTC // 2026-11-08</span></div>
      <header className="masthead">
        <div><p className="eyebrow">ADMIN CONSOLE / CONTROL SURFACE</p><h1>LEDGER<span>-</span>BANK</h1></div>
        <button type="button" className={`health-button ${health.status === 'UP' ? 'health-button--up' : ''}`} onClick={fetchHealth} disabled={healthLoading} title="Refresh system health"><StatusMark status={health.status} />{healthLabel}<b>↻</b></button>
      </header>

      <section className="notice"><span className="notice-code">INFO</span><span>BACKEND WARM-UP MAY TAKE 20–60 SECONDS AFTER IDLE. SUBSEQUENT TRANSACTIONS PROCESS IN REAL TIME.</span></section>

      <div className="workspace-grid">
        <section className="panel accounts-panel">
          <div className="panel-heading"><div><span className="panel-index">01</span><div><h2>ACCOUNTS</h2><p className="panel-subtitle">{loading ? 'LOADING...' : `${accounts.length} LIVE ACCOUNTS / ${money(totalBalance)} TOTAL`}</p></div></div><span className="panel-meta">BALANCES</span></div>
          <div className="account-list">
            {loading ? <div className="empty-state">FETCHING ACCOUNT REGISTER...</div> : accounts.length === 0 ? <div className="empty-state">NO ACCOUNTS RETURNED BY API.</div> : accounts.map((account) => (
              <article className={`account-row ${historyAccountId === account.accountId ? 'account-row--selected' : ''}`} key={account.accountId}>
                <div className="account-number"><span>ACCT</span><strong>{account.accountNumber || shortId(account.accountId)}</strong></div>
                <div className="account-owner"><strong>{account.holderName}</strong><span>{shortId(account.accountId)}</span></div>
                <div className="account-balance"><span>AVAILABLE</span><strong>{money(account.balance)}</strong></div>
                <button type="button" className="text-button" onClick={() => loadHistory(account.accountId)}>VIEW HISTORY →</button>
              </article>
            ))}
          </div>
        </section>

        <section className="panel transfer-panel">
          <div className="panel-heading"><div><span className="panel-index">02</span><div><h2>MAKE A TRANSFER</h2><p className="panel-subtitle">ATOMIC LEDGER WRITE</p></div></div><span className="panel-meta">SECURE</span></div>
          <form onSubmit={handleTransfer} className="terminal-form">
            <label>FROM / SENDER<select value={sourceId} onChange={(event) => setSourceId(event.target.value)} required><option value="">CHOOSE SENDER</option>{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.holderName} / {account.accountNumber || shortId(account.accountId)}</option>)}</select></label>
            <label>TO / RECIPIENT<select value={targetId} onChange={(event) => setTargetId(event.target.value)} required><option value="">CHOOSE RECIPIENT</option>{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.holderName} / {account.accountNumber || shortId(account.accountId)}</option>)}</select></label>
            <label>HOW MUCH (USD)<input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0.00" required /></label>
            <button type="submit" className="primary-button" disabled={submitting}>{submitting ? 'PROCESSING...' : 'SEND MONEY →'}</button>
          </form>
          {transferStatus && <div className={`result-box ${transferStatus.success ? 'result-box--success' : 'result-box--error'}`}>{transferStatus.message}</div>}
          <div className="terminal-foot">IDEMPOTENCY KEY: AUTO-GENERATED / LOCK ORDER: DETERMINISTIC / AUDIT: ENABLED</div>
        </section>

        <section className="panel history-panel">
          <div className="panel-heading"><div><span className="panel-index">03</span><div><h2>TRANSACTION HISTORY</h2><p className="panel-subtitle">IMMUTABLE LEDGER ENTRIES</p></div></div><div className="panel-actions">{historyAccountId && <button type="button" className="text-button" onClick={() => loadHistory(historyAccountId)} disabled={historyLoading}>REFRESH ↻</button>}<span className="panel-meta">{historyEntries.length} ENTRIES</span></div></div>
          <div className="history-caption">{selectedHistoryAccount ? `SHOWING TRANSACTIONS FOR ${selectedHistoryAccount.holderName} / ${selectedHistoryAccount.accountNumber || shortId(selectedHistoryAccount.accountId)}` : 'CLICK “VIEW HISTORY” ON ANY ACCOUNT TO SEE ITS TRANSACTIONS.'}</div>
          <div className="history-list">
            {!historyAccountId ? <div className="empty-state">NO ACCOUNT SELECTED.</div> : historyLoading ? <div className="empty-state">READING LEDGER...</div> : historyEntries.length === 0 ? <div className="empty-state">NO LEDGER ENTRIES EXIST FOR THIS ACCOUNT.</div> : historyEntries.map((entry) => <div className="ledger-row" key={entry.ledgerEntryId || `${entry.transactionId}-${entry.type}-${entry.amount}`}><span className={`ledger-type ${entry.type === 'CREDIT' ? 'ledger-type--credit' : 'ledger-type--debit'}`}>{entry.type}</span><div><strong>{entry.description || 'ACCOUNT TRANSACTION'}</strong><span>{formatTimestamp(entry.timestamp)} / TX {shortId(entry.transactionId)}</span></div><b className={entry.type === 'CREDIT' ? 'amount-credit' : 'amount-debit'}>{entry.type === 'CREDIT' ? '+' : '-'}{money(Math.abs(Number(entry.amount)))}</b></div>)}
          </div>
        </section>

        <aside className="panel lab-panel">
          <div className="panel-heading"><div><span className="panel-index">04</span><div><h2>TEST SAFEGUARDS</h2><p className="panel-subtitle">OPTIONAL TOOLS FOR IDEMPOTENCY AND LOCKING</p></div></div><span className="panel-meta">OPTIONAL</span></div>
          <div className="lab-actions"><button type="button" className="lab-button" onClick={() => runDemo('idempotency')} disabled={demoRunning || loading}>DUPLICATE REQUEST DEMO<span>5 REQUESTS / 1 SHARED KEY</span></button><button type="button" className="lab-button" onClick={() => runDemo('concurrency')} disabled={demoRunning || loading}>LOCKING DEMO<span>5 REQUESTS / UNIQUE KEYS</span></button></div>
          {demoState ? <div className="demo-output"><div className="demo-header"><div><strong>{demoState.name}</strong><span>{demoState.phase}</span></div><b>{demoState.completed}/5</b></div><div className="progress-track"><span style={{ width: `${(demoState.completed / 5) * 100}%` }} /></div>{demoState.requests.map((request) => <div className="request-row" key={request.number}><span>REQUEST {String(request.number).padStart(2, '0')}</span><strong className={getRequestTone(request)}>{request.status === 'waiting' ? 'WAITING' : request.status === 'sending' ? 'SENDING...' : `${request.status} ${request.result}`} {request.duration ? `// ${request.duration}MS` : ''}</strong></div>)}</div> : <div className="demo-placeholder">AWAITING TEST VECTOR<span>RUN A DEMO TO STREAM REQUEST-LEVEL RESULTS.</span></div>}
        </aside>
      </div>
      <footer className="footer-line"><span>LEDGER-BANK / ADMIN CONSOLE</span><span>POSTGRESQL + REDIS / SPRING BOOT API</span><span>BUILD 2026.11</span></footer>
    </main>
  );
}

export default App;
