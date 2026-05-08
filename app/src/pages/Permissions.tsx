// src/pages/Permissions.tsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bell, Clock, Heart, Shield, Check } from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { NativeFlow } from '../backend/nativeFlow';

interface PermItem {
  icon: typeof Bell;
  title: string;
  desc: string;
  required: boolean;
}

const permissions: PermItem[] = [
  { icon: Clock, title: '屏幕使用时间', desc: '用于检测应用使用统计数据', required: true },
  { icon: Bell, title: '通知权限', desc: '在需要休息时温和地提醒你', required: false },
  { icon: Heart, title: '健康数据', desc: '获取心率等数据提升检测准确率', required: false },
];

export default function Permissions() {
  const navigate = useNavigate();
  const [granted, setGranted] = useState<Record<number, boolean>>({});

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      NativeFlow.checkPermissions().then(res => {
        setGranted(prev => ({
          ...prev,
          0: res.hasUsageStats,
          // You could map res.hasOverlay to another index if needed
        }));
      });

      let listener: { remove: () => Promise<void> } | null = null;
      NativeFlow.addListener('permissionsChanged', info => {
        setGranted(prev => ({ ...prev, 0: info.hasUsageStats }));
      }).then(handle => {
        listener = handle;
      });

      return () => {
        if (listener) listener.remove();
      };
    }

    // Web 模式: 屏幕使用时间权限自动授予（使用模拟数据）
    setGranted(prev => ({ ...prev, 0: true }));

    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      setGranted(prev => ({ ...prev, 1: true }));
    }
  }, []);

  const toggle = async (i: number) => {
    if (!Capacitor.isNativePlatform() && i === 0) return; // Web 模式下自动授予
    if (Capacitor.isNativePlatform()) {
      if (i === 0 && !granted[0]) {
        await NativeFlow.requestUsageStatsPermission();
      }
      // Assuming i===1 is notifications, can also request overlay here if we had it in the list
    } else if (i === 1 && typeof Notification !== 'undefined' && !granted[1]) {
      const permission = await Notification.requestPermission();
      setGranted(prev => ({ ...prev, [i]: permission === 'granted' }));
      return;
    }
    setGranted(prev => ({ ...prev, [i]: !prev[i] }));
  };

  const proceed = () => navigate('/personalize');
  const canProceed = permissions.every((p, i) => !p.required || granted[i]);

  return (
    <div className="flex flex-col min-h-dvh bg-white px-8 pt-16 pb-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Header */}
        <div className="w-14 h-14 rounded-2xl bg-secondary/10 flex items-center justify-center mb-6">
          <Shield size={32} className="text-secondary" />
        </div>
        <h1 className="text-[24px] font-bold text-gray-900 mb-2">权限申请</h1>
        <p className="text-[14px] text-gray-500 mb-8 leading-relaxed">
          FlowBreak 需要以下权限来保护你的数字健康。你的数据仅在本地存储，我们尊重你的隐私。
        </p>

        {/* Permission Cards */}
        <div className="flex flex-col gap-3 mb-8">
          {permissions.map((p, i) => {
            const Icon = p.icon;
            const isGranted = granted[i];
            return (
              <motion.button
                key={i}
                whileTap={{ scale: 0.98 }}
                onClick={() => toggle(i)}
                className={`flex items-center gap-4 p-4 rounded-2xl border-2 transition-colors text-left ${
                  isGranted ? 'border-primary bg-primary/5' : 'border-gray-300/60 bg-white'
                }`}
              >
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${
                  isGranted ? 'bg-primary/15' : 'bg-gray-100'
                }`}>
                  <Icon size={22} className={isGranted ? 'text-primary' : 'text-gray-400'} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[14px] font-medium text-gray-900">{p.title}</span>
                    {p.required && (
                      <span className="text-[10px] bg-error/10 text-error px-1.5 py-0.5 rounded-full font-medium">必需</span>
                    )}
                  </div>
                  <p className="text-[12px] text-gray-500 mt-0.5">{p.desc}</p>
                </div>
                <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center shrink-0 ${
                  isGranted ? 'border-primary bg-primary' : 'border-gray-300'
                }`}>
                  {isGranted && <Check size={14} className="text-white" />}
                </div>
              </motion.button>
            );
          })}
        </div>

        {/* Footer */}
        <div className="mt-auto flex flex-col gap-3">
          <button
            onClick={proceed}
            disabled={!canProceed}
            className={`btn-primary w-full ${!canProceed ? 'opacity-50 pointer-events-none' : ''}`}
          >
            继续
          </button>
          <p className="text-[12px] text-gray-400 text-center">
            你可以稍后在设置中更改这些权限
          </p>
        </div>
      </motion.div>
    </div>
  );
}
