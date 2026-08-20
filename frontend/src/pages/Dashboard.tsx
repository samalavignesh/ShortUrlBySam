import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/AuthContext';
import api from '../api/api';
import { Link, Copy, Check, QrCode, LogOut, ExternalLink } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';

interface Url {
  id: number;
  originalUrl: string;
  shortCode: string;
  clickCount: number;
  createdAt: string;
}

const Dashboard: React.FC = () => {
  const { logout, user } = useAuth();
  const [urls, setUrls] = useState<Url[]>([]);
  const [originalUrl, setOriginalUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  
  // UI states
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [showQrCode, setShowQrCode] = useState<string | null>(null);

  const fetchUrls = async () => {
    try {
      const response = await api.get('/urls');
      setUrls(response.data.data || []);
    } catch (err: any) {
      console.error('Failed to fetch URLs', err);
      // If unauthorized, logout
      if (err.response?.status === 401 || err.response?.status === 403) {
        logout();
      }
    }
  };

  useEffect(() => {
    fetchUrls();
    
    // Set up polling for real-time click count updates
    const intervalId = setInterval(() => {
      fetchUrls();
    }, 5000);
    
    // Clean up the interval when the component unmounts
    return () => clearInterval(intervalId);
  }, []);

  const handleCreateShortUrl = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!originalUrl) return;

    setIsLoading(true);
    setError('');

    try {
      await api.post('/urls/shorten', { originalUrl });
      setOriginalUrl('');
      fetchUrls(); // Refresh the list
    } catch (err: any) {
      console.error(err);
      if (err.response?.status === 429) {
        setError('Rate limit exceeded. Please wait a moment before creating more short links.');
      } else {
        setError(err.response?.data?.message || 'Failed to create short URL.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopy = (shortUrl: string, code: string) => {
    navigator.clipboard.writeText(shortUrl);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  // Base URL for the redirect service (for the UI display)
  const BASE_URL = import.meta.env.DEV ? 'http://localhost:8080' : window.location.origin;

  return (
    <div className="container" style={{ paddingTop: '2rem', paddingBottom: '2rem' }}>
      {/* Header */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '3rem' }}>
        <div>
          <h1 className="heading-md text-gradient" style={{ margin: 0 }}>URL Shortener</h1>
          <p className="text-muted" style={{ fontSize: '0.875rem' }}>Welcome back, {user?.username || 'User'}</p>
        </div>
        <button onClick={logout} className="btn btn-secondary" style={{ padding: '0.5rem 1rem' }}>
          <LogOut size={16} />
          Sign Out
        </button>
      </header>

      {/* Main Content Area */}
      <div style={{ display: 'grid', gap: '2rem', gridTemplateColumns: 'minmax(0, 1fr)' }}>
        
        {/* Create URL Form */}
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem' }}>Create New Short Link</h2>
          
          {error && (
            <div style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid var(--danger)', color: 'var(--danger)', padding: '0.75rem', borderRadius: 'var(--border-radius-sm)', marginBottom: '1.5rem', fontSize: '0.875rem' }}>
              {error}
            </div>
          )}

          <form onSubmit={handleCreateShortUrl} style={{ display: 'flex', gap: '1rem', alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <input
                type="url"
                className="input-field"
                placeholder="Paste a long URL here (e.g., https://example.com/very/long/path)"
                value={originalUrl}
                onChange={(e) => setOriginalUrl(e.target.value)}
                required
                style={{ margin: 0 }}
              />
            </div>
            <button type="submit" className="btn btn-primary" disabled={isLoading} style={{ height: '46px' }}>
              {isLoading ? 'Shortening...' : 'Shorten URL'}
            </button>
          </form>
        </div>

        {/* URLs List */}
        <div className="glass-panel" style={{ padding: '2rem', overflowX: 'auto' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem' }}>Your Short Links</h2>
          
          {urls.length === 0 ? (
            <div className="flex-center text-muted" style={{ padding: '3rem 0', flexDirection: 'column', gap: '1rem' }}>
              <Link size={48} style={{ opacity: 0.2 }} />
              <p>You haven't created any short links yet.</p>
            </div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.1)' }}>
                  <th style={{ padding: '1rem 0.5rem', color: 'var(--text-secondary)', fontWeight: 500 }}>Short Link</th>
                  <th style={{ padding: '1rem 0.5rem', color: 'var(--text-secondary)', fontWeight: 500 }}>Original URL</th>
                  <th style={{ padding: '1rem 0.5rem', color: 'var(--text-secondary)', fontWeight: 500 }}>Clicks</th>
                  <th style={{ padding: '1rem 0.5rem', color: 'var(--text-secondary)', fontWeight: 500, textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {urls.map((url) => {
                  const fullShortUrl = `${BASE_URL}/${url.shortCode}`;
                  return (
                    <tr key={url.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)', transition: 'background 0.2s' }}>
                      <td style={{ padding: '1rem 0.5rem' }}>
                        <a href={fullShortUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--accent-secondary)', textDecoration: 'none', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                          /{url.shortCode}
                          <ExternalLink size={12} />
                        </a>
                      </td>
                      <td style={{ padding: '1rem 0.5rem', maxWidth: '300px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        <span title={url.originalUrl} className="text-muted">{url.originalUrl}</span>
                      </td>
                      <td style={{ padding: '1rem 0.5rem' }}>
                        <span style={{ background: 'rgba(255, 255, 255, 0.1)', padding: '0.25rem 0.75rem', borderRadius: '1rem', fontSize: '0.875rem' }}>
                          {url.clickCount}
                        </span>
                      </td>
                      <td style={{ padding: '1rem 0.5rem', textAlign: 'right' }}>
                        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                          <button 
                            className="btn btn-secondary" 
                            style={{ padding: '0.5rem' }} 
                            onClick={() => handleCopy(fullShortUrl, url.shortCode)}
                            title="Copy to clipboard"
                          >
                            {copiedCode === url.shortCode ? <Check size={16} color="var(--success)" /> : <Copy size={16} />}
                          </button>
                          <button 
                            className="btn btn-secondary" 
                            style={{ padding: '0.5rem' }}
                            onClick={() => setShowQrCode(showQrCode === url.shortCode ? null : url.shortCode)}
                            title="Show QR Code"
                          >
                            <QrCode size={16} color={showQrCode === url.shortCode ? 'var(--accent-primary)' : 'currentColor'} />
                          </button>
                        </div>

                        {/* Inline QR Code Display */}
                        {showQrCode === url.shortCode && (
                          <div style={{ marginTop: '1rem', padding: '1rem', background: 'white', borderRadius: 'var(--border-radius-md)', display: 'inline-block' }}>
                            <QRCodeSVG value={fullShortUrl} size={150} />
                            <div style={{ textAlign: 'center', color: '#000', marginTop: '0.5rem', fontSize: '0.875rem', fontWeight: 500 }}>
                              Scan Me!
                            </div>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
