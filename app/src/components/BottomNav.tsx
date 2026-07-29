// src/components/BottomNav.tsx
import { Home, BarChart3, User } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';

const tabs = [
  { path: '/dashboard', icon: Home, label: '仪表盘' },
  { path: '/stats', icon: BarChart3, label: '统计' },
  { path: '/profile', icon: User, label: '我的' },
];

export default function BottomNav() {
  const location = useLocation();
  const navigate = useNavigate();

  if (!tabs.some(tab => tab.path === location.pathname)) return null;

  return (
    <nav aria-label="主导航" className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-300/40 z-50" style={{ paddingBottom: 'env(safe-area-inset-bottom, 8px)' }}>
      <div className="flex items-center justify-around h-14 max-w-lg mx-auto">
        {tabs.map(tab => {
          const isActive = location.pathname === tab.path;
          const Icon = tab.icon;
          return (
            <button
              key={tab.path}
              onClick={() => navigate(tab.path)}
              aria-current={isActive ? 'page' : undefined}
              aria-label={tab.label}
              className="relative flex flex-col items-center justify-center flex-1 h-full"
            >
              {isActive && (
                <motion.div
                  layoutId="tab-indicator"
                  className="absolute top-0 left-1/2 -translate-x-1/2 w-12 h-[3px] rounded-full bg-primary"
                  transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                />
              )}
              <Icon
                size={22}
                className={`transition-colors ${isActive ? 'text-primary' : 'text-gray-500'}`}
                strokeWidth={isActive ? 2.5 : 1.8}
              />
              <span className={`text-[10px] mt-0.5 ${isActive ? 'text-primary font-medium' : 'text-gray-500'}`}>
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
