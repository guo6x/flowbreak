// src/pages/Permissions.tsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bell, Clock, Heart, Shield, Check, Battery, Eye } from 'lucide-react';
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
  { icon: Eye, title: '悬浮窗权限', desc: '在其他应用上层显示休息提醒', required: true },
  { icon: Battery, title: '电池优化豁免', desc: '确保后台服务持续运行', required: true },
  { icon: Bell, title: '通知权限', desc: '在需要休息时温和地提醒你', required: false },
  { icon: Heart, title: '健康数据', desc: '获取心率等数据提升检测准确率', required: false },
];

export default function Permissions() {
  const navigate = useNavigate();
  const [granted, setGranted] = useState<Record<number, boolean>>({});
  const [notificationHandled, setNotificationHandled] = useState(false);

  const isNative = Capacitor.isNativePlatform();

  useEffect(() => {
    if (isNative) {
      NativeFlow.checkPermissions().then(res => {
        setGranted({
          0: res.hasUsageStats,
          1: res.hasOverlay,
          2: res.isIgnoringBattery,
          3: res.hasNotification,
        });
      }).catch(err => {
        console.error('checkPermissions failed:', err);
      });

      let listener: { remove: () => Promise<void> } | null = null;
      NativeFlow.addListener('permissionsChanged', info => {
        setGranted({
          0: info.hasUsageStats,
          1: info.hasOverlay,
          2: info.isIgnoringBattery,
          3: info.hasNotification,
        });
      }).then(handle => {
        listener = handle;
      }).catch(err => {
        console.error('addListener failed:', err);
      });

      return () => {
        if (listener) listener.remove().catch(() => {});
      };
    } else {
      // Web 模式
      if (typeof Notification !== 'undefined') {
        setNotificationHandled(Notification.permission !== 'default');
        setGranted(prev => ({ ...prev, 3: Notification.permission === 'granted' }));
      } else {
        setNotificationHandled(true); // Notifications not supported, treat as handled
      }
    }
  }, [isNative]);

  const toggle = async (i: number) => {
    if (!isNative) {
      // Index 0 (usage stats), 1 (overlay), 2 (battery) — non-functional on web, skip
      if (i <= 2) return;
      if (i === 3 && typeof Notification !== 'undefined') {
        const permission = await Notification.requestPermission();
        setNotificationHandled(true);
        setGranted(prev => ({ ...prev, 3: permission === 'granted' }));
        return;
      }
      if (i === 4) {
        setGranted(prev => ({ ...prev, 4: !prev[4] }));
        return;
      }
      return;
    }

    try {
      if (i === 0) {
        await NativeFlow.requestUsageStatsPermission();
      } else if (i === 1) {
        await NativeFlow.requestOverlayPermission();
      } else if (i === 2) {
        await NativeFlow.requestIgnoreBatteryOptimizations();
      } else if (i === 3) {
        await NativeFlow.requestNotificationPermission();
      } else {
        setGranted(prev => ({ ...prev, 4: !prev[4] }));
        return;
      }
    } catch (err) {
      console.error('Permission request failed:', err);
    }
  };

  const proceed = () => navigate('/personalize');
  
  const canProceed = isNative
    ? permissions.every((p, i) => !p.required || granted[i])
    : notificationHandled; // Web 模式下只要通知权限处理过即可继续

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-16 pb-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Web Preview Hint */}
        {!isNative && (
          <div className="mb-6 p-4 bg-secondary/5 border border-secondary/10 rounded-2xl flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-secondary flex items-center justify-center text-white text-lg shrink-0">
              <Shield size={18} />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-[14px] font-bold text-gray-900">Web 预览模式</p>
              <p className="text-[12px] text-gray-500">你正在浏览器中预览，完整功能请使用 Android 应用。</p>
            </div>
          </div>
        )}

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
            const isAndroidOnly = i <= 2;
            const isDisabled = !isNative && isAndroidOnly;
            
            return (
              <motion.button
                key={i}
                whileTap={isDisabled ? {} : { scale: 0.98 }}
                onClick={() => toggle(i)}
                disabled={isDisabled}
                className={`flex items-center gap-4 p-4 rounded-2xl border-2 transition-colors text-left ${
                  isGranted ? 'border-primary bg-primary/5' : 'border-gray-300/60 bg-white'
                } ${isDisabled ? 'opacity-60 grayscale-[0.5]' : ''}`}
              >
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${
                  isGranted ? 'bg-primary/15' : 'bg-gray-100'
                }`}>
                  <Icon size={22} className={isGranted ? 'text-primary' : 'text-gray-400'} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[14px] font-medium text-gray-900">{p.title}</span>
                    {p.required && isNative && (
                      <span className="text-[10px] bg-error/10 text-error px-1.5 py-0.5 rounded-full font-medium">必需</span>
                    )}
                    {!isNative && isAndroidOnly && (
                      <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded-full font-medium">仅限 Android</span>
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
