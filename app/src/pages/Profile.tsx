// src/pages/Profile.tsx

import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  User, Bell, Shield, Moon, CreditCard, Info, ChevronRight,
  Leaf, LogOut, Clock, Palette
} from 'lucide-react';
import { getProfile, getPoints, getStreak, saveProfile } from '../backend/storage';

interface MenuItem {
  icon: typeof Bell;
  label: string;
  desc?: string;
  color: string;
  action?: () => void;
}

export default function Profile() {
  const navigate = useNavigate();
  const profile = getProfile();
  const points = getPoints();
  const streak = getStreak();
  const bgName = ['森林', '海洋', '山脉', '花园', '日落'][profile.selectedBackground] || '森林';
  const [toast, setToast] = useState('');

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 2000);
  };

  const sections: { title: string; items: MenuItem[] }[] = [
    {
      title: '偏好设置',
      items: [
        { icon: Clock, label: '提醒时间', desc: `${profile.sessionLimit}分钟`, color: '#4CAF50', action: () => navigate('/personalize') },
        { icon: Moon, label: '休息时长', desc: `${profile.restDuration / 60}分钟`, color: '#2196F3', action: () => navigate('/personalize') },
        { icon: Bell, label: '通知设置', desc: '已开启', color: '#FF9800', action: () => { if (typeof Notification !== 'undefined') Notification.requestPermission(); } },
        { icon: Palette, label: '休息背景', desc: bgName, color: '#9C27B0', action: () => navigate('/personalize') },
      ],
    },
    {
      title: '账号与安全',
      items: [
        { icon: CreditCard, label: '升级高级版', desc: '¥12/月', color: '#FF9800', action: () => showToast('即将上线，敬请期待') },
        { icon: Shield, label: '隐私设置', color: '#4CAF50', action: () => showToast('即将上线，敬请期待') },
        { icon: Info, label: '关于 FlowBreak', desc: 'v1.0.0', color: '#2196F3', action: () => showToast('FlowBreak v1.0.0 — 守护你的数字健康') },
      ],
    },
  ];

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      {/* Header */}
      <h1 className="text-[24px] font-bold text-gray-900 mb-6">我的</h1>

      {/* Profile Card */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        onClick={() => navigate('/personalize')}
        className="card-lg p-5 flex items-center gap-4 mb-6 cursor-pointer active:bg-gray-50 transition-colors"
      >
        <div className="w-16 h-16 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
          <User size={30} className="text-white" />
        </div>
        <div className="flex-1">
          <h2 className="text-[18px] font-bold text-gray-900">{profile.name || '未设置名称'}</h2>
          <p className="text-[12px] text-gray-500 mt-0.5">
            {profile.type === 'student' ? '学生' : profile.type === 'worker' ? '上班族' : '用户'} · 目标 {profile.dailyGoal / 60}h/天
          </p>
          <div className="flex items-center gap-3 mt-2">
            <span className="text-[11px] bg-primary/10 text-primary px-2 py-0.5 rounded-full font-medium">
              {points} 积分
            </span>
            <span className="text-[11px] bg-accent/10 text-accent px-2 py-0.5 rounded-full font-medium">
              🔥 {streak} 天连续
            </span>
          </div>
        </div>
        <ChevronRight size={18} className="text-gray-400" />
      </motion.div>


      {/* Menu Sections */}
      {sections.map((section, si) => (
        <div key={si} className="mb-5">
          <h3 className="text-[14px] font-bold text-gray-500 mb-2 px-1">{section.title}</h3>
          <div className="card overflow-hidden">
            {section.items.map((item, i) => {
              const Icon = item.icon;
              return (
                <button
                  key={i}
                  onClick={item.action}
                  className="flex items-center gap-3 w-full px-4 py-3.5 border-b border-gray-300/20 last:border-b-0 active:bg-gray-50 transition-colors text-left"
                >
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: item.color + '15' }}>
                    <Icon size={17} style={{ color: item.color }} />
                  </div>
              <span className="text-[14px] text-gray-900 flex-1">{item.label}</span>
                  {item.desc && <span className="text-[12px] text-gray-500 mr-1">{item.desc}</span>}
                  <ChevronRight size={16} className="text-gray-300" />
                </button>
              );
            })}
          </div>
        </div>
      ))}

      <button
        onClick={() => navigate('/personalize')}
        className="btn-secondary w-full"
      >
        编辑个性化设置
      </button>

      {/* Logout */}
      <button
        onClick={() => {
          saveProfile({ onboardingDone: false });
          navigate('/onboarding');
        }}
        className="flex items-center justify-center gap-2 py-3 text-error text-[14px] font-medium mt-2"
      >
        <LogOut size={16} />
        <span>重新体验引导</span>
      </button>

      {/* Footer */}
      <div className="flex items-center justify-center gap-1.5 mt-4 mb-4">
        <Leaf size={14} className="text-primary" />
        <span className="text-[11px] text-gray-400">FlowBreak v1.0.0 · 守护你的数字健康</span>
      </div>

      {toast && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="fixed bottom-20 left-1/2 -translate-x-1/2 bg-gray-900 text-white text-[13px] px-5 py-2.5 rounded-full shadow-lg z-50"
        >
          {toast}
        </motion.div>
      )}
    </div>
  );
}
