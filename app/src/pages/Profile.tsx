// src/pages/Profile.tsx

import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router';
import {
  User, Bell, Shield, Info, ChevronRight,
  Leaf, LogOut, Palette, Target, AppWindow, SlidersHorizontal, Database, Activity
} from 'lucide-react';
import { NativeFlow } from '../backend/nativeFlow';
import { useStore } from '../hooks/useStore';
import { useNativePermissions } from '../hooks/useNativePermissions';

interface MenuItem {
  icon: typeof Bell;
  label: string;
  desc?: string;
  color: string;
  action?: () => void;
}

function formatGoal(minutes: number) {
  if (minutes < 60) return `${minutes}分钟`;
  const hours = minutes / 60;
  if (Number.isInteger(hours)) return `${hours}小时`;
  return `${hours.toFixed(1)}小时`;
}

export default function Profile() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const points = useStore(s => s.points);
  const streak = useStore(s => s.streak);
  const updateProfile = useStore(s => s.updateProfile);
  const { isNative, permissions, refresh: refreshPermissions } = useNativePermissions();
  const [actionError, setActionError] = useState('');
  const [notifGranted, setNotifGranted] = useState(
    typeof Notification !== 'undefined' && Notification.permission === 'granted',
  );
  const bgName = ['森林', '海洋', '山脉', '花园', '日落'][profile.selectedBackground] || '森林';
  const bgPreviews = [
    'bg-gradient-to-br from-green-900 via-green-700 to-green-400',
    'bg-gradient-to-br from-blue-900 via-blue-700 to-blue-400',
    'bg-gradient-to-br from-gray-800 via-gray-600 to-gray-400',
    'bg-gradient-to-br from-pink-900 via-pink-600 to-pink-300',
    'bg-gradient-to-br from-orange-800 via-orange-600 to-orange-300',
  ];

  const requestNotifications = async () => {
    setActionError('');
    try {
      if (isNative) {
        await NativeFlow.requestNotificationPermission();
        window.setTimeout(refreshPermissions, 300);
      } else if (typeof Notification !== 'undefined') {
        const result = await Notification.requestPermission();
        setNotifGranted(result === 'granted');
      }
    } catch {
      setActionError('无法打开通知权限设置，请稍后重试。');
    }
  };
  const sections: { title: string; items: MenuItem[] }[] = [
    {
      title: '健康目标',
      items: [
        { icon: AppWindow, label: '受限应用', desc: `${profile.targetApps.length} 个`, color: '#4CAF50', action: () => navigate('/target-apps') },
        { icon: SlidersHorizontal, label: '阻断设置', desc: `${profile.sessionLimit} 分钟`, color: '#2196F3', action: () => navigate('/blocking-settings') },
        { 
          icon: Target, 
          label: '健康目标与提醒', 
          desc: `${formatGoal(profile.dailyGoal)}/天 · ${profile.sessionLimit}分钟提醒 · 休息${profile.restDuration >= 60 ? `${Math.round(profile.restDuration / 60)}分钟` : `${profile.restDuration}秒`}`, 
          color: '#4CAF50', 
          action: () => navigate('/profile-preferences') 
        },
      ],
    },
    {
      title: '个性化',
      items: [
        { icon: Palette, label: '休息背景', desc: bgName, color: '#9C27B0', action: () => navigate('/profile-preferences') },
        { icon: Bell, label: '通知权限', desc: isNative ? (permissions.hasNotification ? '已开启' : '未开启') : (notifGranted ? '已开启' : '未开启'), color: '#F44336', action: requestNotifications },
      ],
    },
    {
      title: '其他',
      items: [
        { icon: Activity, label: '效果验证与运行诊断', desc: '近 7 天', color: '#7C3AED', action: () => navigate('/validation') },
        { icon: Shield, label: '隐私说明', color: '#4CAF50', action: () => navigate('/privacy') },
        { icon: Database, label: '数据导出与清除', color: '#607D8B', action: () => navigate('/privacy') },
        { icon: Info, label: '关于与开源许可', color: '#2196F3', action: () => navigate('/privacy') },
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
        className="card-lg p-5 flex items-center gap-4 mb-6 bg-gradient-to-br from-primary/5 to-secondary/5 border border-primary/10"
      >
        <div className="w-16 h-16 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center shrink-0">
          <User size={30} className="text-white" />
        </div>
        <div className="flex-1 min-w-0">
          <h2 className="text-[18px] font-bold text-gray-900 truncate">{profile.name || '未设置名称'}</h2>
          <p className="text-[12px] text-gray-500 mt-0.5">
            {profile.type === 'student' ? '学生' : profile.type === 'worker' ? '上班族' : '用户'} · 目标 {formatGoal(profile.dailyGoal)}/天
          </p>
          <div className="flex items-center gap-3 mt-2">
            <span className="text-[11px] bg-primary/10 text-primary px-2 py-0.5 rounded-full font-medium">
              {points} 积分
            </span>
            <span className="text-[11px] bg-accent/10 text-accent px-2 py-0.5 rounded-full font-medium">
              连续 {streak} 天
            </span>
          </div>
        </div>
      </motion.div>

      {/* Background Preview */}
      <div className="card p-3 mb-5">
        <div className="flex items-center gap-2 mb-2">
          <Palette size={14} className="text-gray-400" />
          <span className="text-[12px] text-gray-500">当前休息背景：{bgName}</span>
        </div>
        <div
          className={`w-full h-10 rounded-xl ${bgPreviews[profile.selectedBackground] || bgPreviews[0]}`}
          onClick={() => navigate('/profile-preferences')}
        />
        <p className="text-[11px] text-gray-400 mt-1.5">点击预览或前往个性化设置切换</p>
      </div>

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
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" style={{ backgroundColor: item.color + '15' }}>
                    <Icon size={17} style={{ color: item.color }} />
                  </div>
                  <span className="text-[14px] text-gray-900 flex-1">{item.label}</span>
                  {item.desc && <span className="text-[12px] text-gray-500 mr-1">{item.desc}</span>}
                  <ChevronRight size={16} className="text-gray-300 shrink-0" />
                </button>
              );
            })}
          </div>
        </div>
      ))}

      {actionError && <p className="text-[12px] text-error text-center mb-3">{actionError}</p>}

      {/* Logout */}
      <button
        onClick={() => {
          updateProfile({ onboardingDone: false });
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
        <span className="text-[11px] text-gray-400">FlowBreak v1.1.0 · 帮你从刷视频里醒过来</span>
      </div>

    </div>
  );
}
