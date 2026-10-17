// src/pages/Profile.tsx

import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router';
import {
  User, Bell, Shield, Info, ChevronRight,
  RotateCcw, Palette, Target, AppWindow, SlidersHorizontal, Activity, BookOpen
} from 'lucide-react';
import Brand from '../components/Brand';
import { version } from '../../package.json';
import { themeGlows } from './rest-mode/themeData';
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
  const { isNative, permissions, refresh: refreshPermissions } = useNativePermissions();
  const [actionError, setActionError] = useState('');
  const [notifGranted, setNotifGranted] = useState(
    typeof Notification !== 'undefined' && Notification.permission === 'granted',
  );
  const bgName = ['森林', '海洋', '山脉', '花园', '日落'][profile.selectedBackground] || '森林';

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
        { icon: AppWindow, label: '受限应用', desc: `${profile.targetApps.length} 个`, color: '#247353', action: () => navigate('/target-apps') },
        { icon: SlidersHorizontal, label: '阻断设置', desc: `${profile.sessionLimit} 分钟`, color: '#367C98', action: () => navigate('/blocking-settings') },
        { 
          icon: Target, 
          label: '健康目标与提醒', 
          desc: `${formatGoal(profile.dailyGoal)}/天 · ${profile.sessionLimit}分钟提醒 · 休息${profile.restDuration >= 60 ? `${Math.round(profile.restDuration / 60)}分钟` : `${profile.restDuration}秒`}`, 
          color: '#247353',
          action: () => navigate('/profile-preferences') 
        },
      ],
    },
    {
      title: '个性化',
      items: [
        { icon: Palette, label: '休息背景', desc: bgName, color: '#7C7893', action: () => navigate('/profile-preferences') },
        { icon: Bell, label: '通知权限', desc: isNative ? (permissions.hasNotification ? '已开启' : '未开启') : (notifGranted ? '已开启' : '未开启'), color: '#A36519', action: requestNotifications },
      ],
    },
    {
      title: '帮助与数据',
      items: [
        { icon: BookOpen, label: '使用指南', desc: '从这里开始', color: '#247353', action: () => navigate('/help') },
        { icon: Activity, label: '效果验证与运行诊断', desc: '近 7 天', color: '#7C7893', action: () => navigate('/validation') },
        { icon: Shield, label: '隐私与数据', desc: '导出 · 清除 · 许可', color: '#367C98', action: () => navigate('/privacy') },
      ],
    },
  ];

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <p className="text-[12px] tracking-widest text-primary font-semibold mb-1">YOUR OWN PACE</p>
          <h1 className="page-heading">我的</h1>
        </div>
        <Info size={20} aria-hidden="true" className="text-primary/40" />
      </div>

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
          <h2 className="text-[18px] font-bold text-gray-900 truncate">{(profile.name || '').trim() || 'FlowBreak 用户'}</h2>
          <p className="text-[13px] text-gray-500 mt-1">
            {profile.type === 'student' ? '学生' : profile.type === 'worker' ? '上班族' : '用户'} · 目标 {formatGoal(profile.dailyGoal)}/天
          </p>
          <div className="flex items-center gap-3 mt-2">
            <span className="text-[12px] bg-primary/10 text-primary px-2.5 py-1 rounded-full font-medium">
              {points} 积分
            </span>
            <span className="text-[12px] bg-accent/10 text-accent px-2.5 py-1 rounded-full font-medium">
              连续 {streak} 天
            </span>
          </div>
        </div>
      </motion.div>

      {/* Background Preview */}
      <button
        type="button"
        aria-label={`更换休息背景，当前${bgName}`}
        onClick={() => navigate('/profile-preferences')}
        style={{ background: (themeGlows[profile.selectedBackground] || themeGlows[0]).baseBg }}
        className="relative w-full min-h-28 rounded-3xl p-5 mb-6 text-left text-white overflow-hidden"
      >
        <div className="absolute inset-0 bg-black/25" aria-hidden="true" />
        <div className="relative flex items-center justify-between gap-3">
          <div>
            <p className="text-[12px] text-white/90 mb-1">你的休息空间</p>
            <p className="text-[20px] font-semibold">{bgName}</p>
            <p className="text-[12px] text-white/90 mt-2">换个风景，也换个心情</p>
          </div>
          <Palette size={28} aria-hidden="true" className="text-white/80" />
        </div>
      </button>

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
                  <span className="flex-1 min-w-0">
                    <span className="block text-[14px] font-medium text-gray-900">{item.label}</span>
                    {item.desc && <span className="block text-[12px] text-gray-500 mt-1 leading-relaxed">{item.desc}</span>}
                  </span>
                  <ChevronRight size={16} className="text-gray-300 shrink-0" />
                </button>
              );
            })}
          </div>
        </div>
      ))}

      {actionError && <p role="alert" className="text-[13px] text-error text-center mb-3">{actionError}</p>}

      {/* Logout */}
      <button
        onClick={() => {
          navigate('/onboarding');
        }}
        className="flex items-center justify-center gap-2 min-h-12 py-3 text-gray-500 text-[14px] font-medium mt-2"
      >
        <RotateCcw size={16} aria-hidden="true" />
        <span>重新体验引导</span>
      </button>

      {/* Footer */}
      <div className="flex flex-col items-center justify-center gap-3 mt-6 mb-4">
        <Brand compact />
        <span className="text-[12px] text-gray-500">v{version} · 数据留在本机，节奏由你决定</span>
      </div>

    </div>
  );
}
