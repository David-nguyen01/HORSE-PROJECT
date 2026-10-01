import React, { useEffect, useState } from 'react';
import { CreditCard, History, Plus, Wallet, ShieldAlert, CheckCircle2, Clock } from 'lucide-react';
import { api, pageContent } from './api';

const formatCurrency = (val) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);

function getSubscriptionStatus(horse) {
  if (!horse.paidUntil) {
     return horse.paymentStatus === 'UNPAID' ? { label: 'Chưa kích hoạt', color: 'unknown' } : { label: 'Không có dữ liệu', color: 'unknown' };
  }
  const end = new Date(horse.paidUntil);
  const now = new Date();
  end.setHours(0,0,0,0);
  now.setHours(0,0,0,0);
  const diffDays = Math.ceil((end - now) / (1000 * 60 * 60 * 24)); 

  if (diffDays >= 0) return { label: 'Đang sử dụng', color: 'green', days: diffDays };
  if (diffDays === -1) return { label: 'Ngày ân hạn', color: 'yellow', days: diffDays };
  if (diffDays >= -3) return { label: 'Quá hạn - Hạn chế', color: 'red', days: diffDays };
  return { label: 'Quá hạn - Tạm ngừng', color: 'red', days: diffDays };
}

export default function WalletPage() {
  const [balance, setBalance] = useState({ balance: 0 });
  const [transactions, setTransactions] = useState([]);
  const [horses, setHorses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      api('/api/wallet/balance').catch(() => ({ balance: 0 })),
      api('/api/wallet/transactions?size=50').catch(() => ({ content: [] })),
      api('/api/horses/mine?size=100').catch(() => ({ content: [] }))
    ]).then(([bal, txs, hrs]) => {
      setBalance(bal);
      setTransactions(pageContent(txs));
      setHorses(pageContent(hrs));
    }).catch(e => setError(e.message)).finally(() => setLoading(false));
  }, []);

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">TÀI CHÍNH</p>
          <h1>Ví thanh toán</h1>
          <p>Quản lý số dư, gói dịch vụ và lịch sử giao dịch.</p>
        </div>
        <button className="button primary">
          <Plus size={18} /> Nạp tiền
        </button>
      </div>
      
      {error && <div className="notice error">{error}</div>}

      <div className="stats-grid">
        <div className="stat card gold">
          <span className="stat-icon"><Wallet size={21} /></span>
          <div>
            <strong>{formatCurrency(balance?.balance || 0)}</strong>
            <span>Số dư hiện tại</span>
          </div>
        </div>
      </div>

      <section className="card source-table" style={{ marginTop: '24px' }}>
        <div className="card-head">
          <h3>Trạng thái gói dịch vụ</h3>
          <span>Quy định ân hạn/quá hạn theo từng ngựa</span>
        </div>
        {loading ? <div className="empty">Đang tải...</div> : horses.length === 0 ? <div className="empty">Bạn chưa có ngựa nào.</div> : (
          <div className="source-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Mã ngựa</th>
                  <th>Tên ngựa</th>
                  <th>Ngày hết hạn</th>
                  <th>Trạng thái gói</th>
                  <th>Số ngày</th>
                </tr>
              </thead>
              <tbody>
                {horses.map(horse => {
                  const status = getSubscriptionStatus(horse);
                  return (
                    <tr key={horse.id}>
                      <td><code>{horse.code}</code></td>
                      <td><b>{horse.name}</b></td>
                      <td>{horse.paidUntil ? new Date(horse.paidUntil).toLocaleDateString('vi-VN') : '-'}</td>
                      <td><span className={`fitness-badge ${status.color}`}>{status.label}</span></td>
                      <td>{status.days !== undefined ? (status.days >= 0 ? `Còn ${status.days} ngày` : `Quá hạn ${Math.abs(status.days)} ngày`) : '-'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card source-table" style={{ marginTop: '24px' }}>
        <div className="card-head">
          <h3>Lịch sử giao dịch</h3>
        </div>
        {loading ? <div className="empty">Đang tải...</div> : transactions.length === 0 ? (
          <div className="empty">
            <History size={26} />
            <b>Chưa có giao dịch nào</b>
            <span>Các giao dịch nạp tiền và thanh toán sẽ hiển thị tại đây.</span>
          </div>
        ) : (
          <div className="source-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Mã GD</th>
                  <th>Ngày</th>
                  <th>Loại</th>
                  <th>Số tiền</th>
                  <th>Nội dung</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map(tx => (
                  <tr key={tx.id}>
                    <td><code>{tx.transactionCode || tx.id.split('-')[0].toUpperCase()}</code></td>
                    <td>{new Date(tx.createdAt).toLocaleString('vi-VN')}</td>
                    <td><span className={`fitness-badge ${tx.amount > 0 ? 'green' : 'yellow'}`}>{tx.amount > 0 ? 'NẠP' : 'TRỪ'}</span></td>
                    <td style={{ color: tx.amount > 0 ? 'var(--green-600)' : 'var(--text-main)', fontWeight: 600 }}>
                      {tx.amount > 0 ? '+' : ''}{formatCurrency(tx.amount)}
                    </td>
                    <td>{tx.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
