import React, { useState } from 'react';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { Header } from './components/Header';
import { EmpresasView } from './components/EmpresasView';
import { PesquisasView } from './components/PesquisasView';
import { LoginScreen } from './components/LoginScreen';
import { AuthModal } from './components/AuthModal';

function AppContent() {
  const { isAuthenticated } = useAuth();
  const [currentView, setCurrentView] = useState('login');
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authMode, setAuthMode] = useState('login');
  const [authEmail, setAuthEmail] = useState('');

  React.useEffect(() => {
    if (!isAuthenticated) {
      setCurrentView('login');
    }
  }, [isAuthenticated]);

  const handleOpenAuth = (mode = 'login', email = '') => {
    setAuthMode(mode);
    setAuthEmail(email);
    setAuthModalOpen(true);
  };

  if (currentView === 'login') {
    return (
      <>
        <LoginScreen
          onLoginSuccess={() => setCurrentView('pesquisas')}
          onSwitchToRegister={() => handleOpenAuth('register')}
          onSwitchToVerify={(email) => handleOpenAuth('verify', email)}
        />
        <AuthModal
          key={`auth-${authMode}-${authModalOpen}-${authEmail}`}
          isOpen={authModalOpen}
          onClose={() => setAuthModalOpen(false)}
          initialMode={authMode}
          initialEmail={authEmail}
        />
      </>
    );
  }

  return (
    <div className="min-h-screen bg-vertex-bg dark:bg-vertex-dark-bg text-vertex-body dark:text-vertex-dark-text flex flex-col font-sans selection:bg-vertex-orange selection:text-white transition-colors duration-700 ease-in-out">
      <Header
        currentView={currentView}
        onViewChange={(view) => setCurrentView(view)}
        onOpenAuth={handleOpenAuth}
        onOpenLoginScreen={() => setCurrentView('login')}
      />

      <main className="flex-1 max-w-[1600px] w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-in fade-in duration-150">
        {currentView === 'empresas' && (
          <EmpresasView />
        )}
        {currentView === 'pesquisas' && (
          <PesquisasView />
        )}
      </main>

      <AuthModal
        key={`auth-main-${authMode}-${authModalOpen}`}
        isOpen={authModalOpen}
        onClose={() => setAuthModalOpen(false)}
        initialMode={authMode}
      />
    </div>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <AppContent />
        </AuthProvider>
      </ToastProvider>
    </ThemeProvider>
  );
}
