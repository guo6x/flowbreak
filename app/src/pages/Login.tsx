import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, ArrowRight, LockKeyhole, User } from 'lucide-react';
import { useStore } from '../hooks/useStore';

export default function Login() {
  const navigate = useNavigate();
  const profileName = useStore(s => s.profile.name);
  const updateProfile = useStore(s => s.updateProfile);
  const [name, setName] = useState(profileName);

  const continueLocally = () => {
    const normalizedName = name.trim() || 'FlowBreak 用户';
    updateProfile({ name: normalizedName });
    navigate('/permissions');
  };

  return (
    <div className="min-h-dvh bg-gray-50 flex flex-col px-8 pt-20 pb-12 overflow-y-auto no-scrollbar">
      <button onClick={() => navigate('/onboarding')} aria-label="返回引导页" className="w-10 h-10 rounded-full bg-white card flex items-center justify-center mb-4">
        <ArrowLeft size={20} />
      </button>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        <div className="mb-12">
          <div className="w-16 h-16 rounded-3xl bg-primary/10 flex items-center justify-center mb-6">
            <User size={32} className="text-primary" />
          </div>
          <h1 className="text-[28px] font-bold text-gray-900 mb-2">先认识一下你</h1>
          <p className="text-[14px] text-gray-500 leading-relaxed">
            FlowBreak 当前采用本地模式，不需要注册账号，数据只保存在这台设备上。
          </p>
        </div>

        <div className="card-lg p-5 mb-5">
          <label htmlFor="profile-name" className="block text-[14px] font-bold text-gray-800 mb-3">
            你的昵称
          </label>
          <input
            id="profile-name"
            autoFocus
            value={name}
            onChange={event => setName(event.target.value)}
            onKeyDown={event => {
              if (event.key === 'Enter') continueLocally();
            }}
            maxLength={24}
            placeholder="例如：自律的小明"
            className="w-full h-14 bg-gray-100 border border-transparent rounded-2xl px-5 text-[15px] outline-none focus:border-primary focus:bg-white transition-colors"
          />
          <p className="text-[11px] text-gray-400 mt-2 text-right">{name.length}/24</p>
        </div>

        <div className="flex items-start gap-3 p-4 rounded-2xl bg-secondary/5 border border-secondary/10 mb-8">
          <div className="w-9 h-9 rounded-xl bg-secondary/10 flex items-center justify-center shrink-0">
            <LockKeyhole size={18} className="text-secondary" />
          </div>
          <div>
            <p className="text-[13px] font-bold text-gray-800 mb-1">本地隐私模式</p>
            <p className="text-[12px] text-gray-500 leading-relaxed">
              无账号、无云端上传。卸载应用或清除应用数据会删除本地记录。
            </p>
          </div>
        </div>

        <button
          onClick={continueLocally}
          className="btn-primary w-full h-14 flex items-center justify-center gap-2 mt-auto"
        >
          继续设置
          <ArrowRight size={18} />
        </button>
      </motion.div>
    </div>
  );
}
