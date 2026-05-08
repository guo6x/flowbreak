// src/App.tsx
import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { isOnboardingDone } from './backend/storage';
import BottomNav from './components/BottomNav';
import './App.css';

const Onboarding = lazy(() => import('./pages/Onboarding'));
const Login = lazy(() => import('./pages/Login'));
const Permissions = lazy(() => import('./pages/Permissions'));
const Personalize = lazy(() => import('./pages/Personalize'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Statistics = lazy(() => import('./pages/Statistics'));
const Achievements = lazy(() => import('./pages/Achievements'));
const Profile = lazy(() => import('./pages/Profile'));
const RestMode = lazy(() => import('./pages/RestMode'));

function LoadingPage() {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-gray-100">
      <div className="text-[13px] text-gray-500">正在加载...</div>
    </div>
  );
}

function MainLayout() {
  return (
    <div className="relative min-h-dvh bg-gray-100">
      <Outlet />
      <BottomNav />
    </div>
  );
}

function AppRouter() {
  const done = isOnboardingDone();

  return (
    <Suspense fallback={<LoadingPage />}>
      <Routes>
        <Route path="/onboarding" element={<Onboarding />} />
        <Route path="/login" element={<Login />} />
        <Route path="/permissions" element={<Permissions />} />
        <Route path="/personalize" element={<Personalize />} />

        <Route element={<MainLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/stats" element={<Statistics />} />
          <Route path="/achievements" element={<Achievements />} />
          <Route path="/profile" element={<Profile />} />
        </Route>

        <Route path="/rest" element={<RestMode />} />
        <Route path="*" element={<Navigate to={done ? '/dashboard' : '/onboarding'} replace />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRouter />
    </BrowserRouter>
  );
}
