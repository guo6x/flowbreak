// src/pages/ReminderSettings.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft } from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { getSchedule, saveSchedule, startReminderLoop, ReminderSchedule } from '../backend/reminderScheduler';
import { NativeFlow } from '../backend/nativeFlow';
import { useStore } from '../hooks/useStore';
import { DEFAULT_TARGET_APPS } from '../backend/appNames';

const intervalOptions = [15, 30, 45, 60];

export default function ReminderSettings() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const [schedule, setSchedule] = useState<ReminderSchedule>(getSchedule());

  const toggle = (key: keyof ReminderSchedule) => {
    if (key === 'enabled' || key === 'quietWeekends') {
      setSchedule(prev => ({ ...prev, [key]: !prev[key] }));
    }
  };

  const save = async () => {
    saveSchedule(schedule);
    
    if (Capacitor.isNativePlatform()) {
      try {
        // Sync with native truth source (SharedPreferences)
        // We need to pass the existing limits/apps to avoid overwriting them with defaults
        await NativeFlow.saveSettings({
          limitMinutes: profile.sessionLimit,
          targetApps: DEFAULT_TARGET_APPS, // Or read from current native settings if needed
          reminderEnabled: schedule.enabled,
          reminderIntervalMinutes: schedule.intervalMinutes,
          reminderStartHour: schedule.startHour,
          reminderEndHour: schedule.endHour,
        });
        
        // Restart service to reload SharedPreferences
        await NativeFlow.startService({
          limitMinutes: profile.sessionLimit,
          apps: DEFAULT_TARGET_APPS
        });
      } catch (err) {
        console.error('Failed to sync reminder settings to native:', err);
      }
    }

    startReminderLoop(() => {
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        new Notification('FlowBreak 提醒', { body: '该休息一下了', icon: '/favicon.png' });
      }
      navigate('/rest');
    });
    navigate(-1);
  };

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-12 pb-12 no-scrollbar overflow-y-auto">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col"
      >
        {/* Header */}
        <button onClick={() => navigate(-1)} className="w-10 h-10 rounded-xl bg-gray-100 flex items-center justify-center mb-6">
          <ArrowLeft size={20} className="text-gray-700" />
        </button>
        <h1 className="text-[24px] font-bold text-gray-900 mb-1">休息提醒设置</h1>
        <p className="text-[14px] text-gray-500 mb-8">即使不打开应用，也能定时提醒你休息</p>

        {/* Enable Toggle */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h3 className="text-[16px] font-bold text-gray-900">启用定时提醒</h3>
            <p className="text-[12px] text-gray-400 mt-0.5">在指定时间段内定期提醒</p>
          </div>
          <button
            onClick={() => toggle('enabled')}
            className={`w-12 h-7 rounded-full transition-colors ${schedule.enabled ? 'bg-primary' : 'bg-gray-300'}`}
          >
            <div className={`w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${schedule.enabled ? 'translate-x-6' : 'translate-x-1'}`} />
          </button>
        </div>

        {/* Interval */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">提醒间隔</h3>
        <div className="flex flex-wrap gap-2 mb-8">
          {intervalOptions.map(i => (
            <button
              key={i}
              onClick={() => setSchedule(prev => ({ ...prev, intervalMinutes: i }))}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                schedule.intervalMinutes === i ? 'bg-primary text-white' : 'bg-gray-100 text-gray-700'
              }`}
            >
              {i}分钟
            </button>
          ))}
        </div>

        {/* Time Range */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">提醒时间段</h3>
        <div className="flex items-center gap-3 mb-8">
          <div className="flex items-center gap-2">
            <span className="text-[13px] text-gray-500">开始</span>
            <select
              value={schedule.startHour}
              onChange={e => setSchedule(prev => ({ ...prev, startHour: Number(e.target.value) }))}
              className="bg-gray-100 rounded-xl px-3 py-2.5 text-[14px] text-gray-900 outline-none"
            >
              {Array.from({ length: 24 }, (_, i) => (
                <option key={i} value={i}>{i}:00</option>
              ))}
            </select>
          </div>
          <span className="text-gray-400">至</span>
          <div className="flex items-center gap-2">
            <span className="text-[13px] text-gray-500">结束</span>
            <select
              value={schedule.endHour}
              onChange={e => setSchedule(prev => ({ ...prev, endHour: Number(e.target.value) }))}
              className="bg-gray-100 rounded-xl px-3 py-2.5 text-[14px] text-gray-900 outline-none"
            >
              {Array.from({ length: 24 }, (_, i) => (
                <option key={i} value={i}>{i}:00</option>
              ))}
            </select>
          </div>
        </div>

        {/* Quiet Weekends */}
        <div className="flex items-center justify-between mb-10">
          <div>
            <h3 className="text-[16px] font-bold text-gray-900">周末免打扰</h3>
            <p className="text-[12px] text-gray-400 mt-0.5">周六日不发送提醒</p>
          </div>
          <button
            onClick={() => toggle('quietWeekends')}
            className={`w-12 h-7 rounded-full transition-colors ${schedule.quietWeekends ? 'bg-primary' : 'bg-gray-300'}`}
          >
            <div className={`w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${schedule.quietWeekends ? 'translate-x-6' : 'translate-x-1'}`} />
          </button>
        </div>

        {/* Save */}
        <button onClick={save} className="btn-primary w-full">
          保存设置
        </button>
      </motion.div>
    </div>
  );
}
