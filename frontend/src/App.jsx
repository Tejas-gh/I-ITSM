import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { USE_MOCK } from './api/apiClient';
import Navbar from './components/Navbar';
import DashboardPage from './pages/DashboardPage';
import IncidentListPage from './pages/IncidentListPage';
import CreateIncidentPage from './pages/CreateIncidentPage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import TechniciansPage from './pages/TechniciansPage';
import LoginPage from './pages/LoginPage';

function AppShell() {
  const { isAuthenticated, isAdmin } = useAuth();

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  // The technician list endpoint is administrator-only on the backend.
  const canSeeTechnicians = USE_MOCK || isAdmin;

  return (
    <BrowserRouter>
      <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f8fafc' }}>
        <Navbar />
        <main style={{ flex: 1 }}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/incidents" element={<IncidentListPage />} />
            <Route path="/incidents/new" element={<CreateIncidentPage />} />
            <Route path="/incidents/:id" element={<IncidentDetailPage />} />
            <Route
              path="/technicians"
              element={canSeeTechnicians ? <TechniciansPage /> : <Navigate to="/" replace />}
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer
          style={{
            padding: '20px',
            textAlign: 'center',
            fontSize: '12px',
            color: '#94a3b8',
            borderTop: '1px solid #e2e8f0',
            backgroundColor: '#ffffff',
          }}
        >
          I-ITSM Intelligent IT Service Management System • Phase 2 Frontend Architecture
        </footer>
      </div>
    </BrowserRouter>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}

export default App;
