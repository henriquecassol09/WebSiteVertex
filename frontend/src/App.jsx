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
  const [currentView, setCurrentView] = useState('login'); // 'empresas' | 'pesquisas' | 'login'
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authMode, setAuthMode] = useState('login');

  // Se o usuário não estiver autenticado, garante que a tela ativa seja o login
  React.useEffect(() => {
    if (!isAuthenticated) {
      setCurrentView('login');
    }
  }, [isAuthenticated]);

  const handleOpenAuth = (mode = 'login') => {
    setAuthMode(mode);
    setAuthModalOpen(true);
  };

  // Se o usuário selecionou a Tela de Login dedicada
  if (currentView === 'login') {
    return (
      <>
        <LoginScreen
          onLoginSuccess={() => setCurrentView('pesquisas')}
          onSwitchToRegister={() => handleOpenAuth('register')}
        />
        <AuthModal
          key={`auth-${authMode}-${authModalOpen}`}
          isOpen={authModalOpen}
          onClose={() => setAuthModalOpen(false)}
          initialMode={authMode}
        />
      </>
    );
  }

  return (
    <div className="min-h-screen bg-vertex-bg dark:bg-vertex-dark-bg text-vertex-body dark:text-vertex-dark-text flex flex-col font-sans selection:bg-vertex-orange selection:text-white transition-colors duration-700 ease-in-out">
      {/* Header Fiel com Logo VERTEX, Navegação e Switch de Tema */}
      <Header
        currentView={currentView}
        onViewChange={(view) => setCurrentView(view)}
        onOpenAuth={handleOpenAuth}
        onOpenLoginScreen={() => setCurrentView('login')}
      />

      {/* Conteúdo Principal com Respiro e Proporção */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-in fade-in duration-150">
        {currentView === 'empresas' && (
          <EmpresasView />
        )}
        {currentView === 'pesquisas' && (
          <PesquisasView />
        )}
      </main>

      {/* Modal de Autenticação */}
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
