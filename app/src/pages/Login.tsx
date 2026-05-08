// src/pages/Login.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Leaf, User, ArrowRight } from 'lucide-react';

export default function Login() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');

  const handleLogin = () => {
    if (nickname.trim()) {
      import('../backend/storage').then(({ saveProfile }) => saveProfile({ name: nickname.trim() }));
    }
    navigate('/permissions');
  };

  const handleSkip = () => {
    navigate('/permissions');
  };

  return (
    <div className="flex flex-col min-h-dvh bg-white px-8 pt-16 pb-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Logo */}
        <div className="flex items-center gap-3 mb-12">
          <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center">
            <Leaf size={28} className="text-primary" />
          </div>
          <div>
            <h1 className="text-[20px] font-bold text-gray-900">FlowBreak</h1>
            <p className="text-[12px] text-gray-500">自然唤醒系统</p>
          </div>
        </div>

        {/* Form */}
        <h2 className="text-[24px] font-bold text-gray-900 mb-2">欢迎使用</h2>
        <p className="text-[14px] text-gray-500 mb-8">开始管理你的数字健康</p>

        <div className="flex flex-col gap-4 mb-6">
          <div className="flex items-center gap-3 bg-gray-100 rounded-2xl px-4 py-3.5">
            <User size={18} className="text-gray-400" />
            <input
              type="text"
              placeholder="昵称（可选）"
              value={nickname}
              onChange={e => setNickname(e.target.value)}
              className="flex-1 bg-transparent outline-none text-[14px] text-gray-900 placeholder:text-gray-400"
            />
          </div>
        </div>

        <button onClick={handleLogin} className="btn-primary flex items-center justify-center gap-2 w-full mb-4">
          <span>进入应用</span>
          <ArrowRight size={18} />
        </button>

        {/* Divider */}
        <div className="flex items-center gap-4 my-4">
          <div className="flex-1 h-px bg-gray-300" />
          <span className="text-[12px] text-gray-500">或</span>
          <div className="flex-1 h-px bg-gray-300" />
        </div>

        {/* Social Login */}
        <div className="flex gap-3 mb-6">
          <button className="flex-1 flex items-center justify-center gap-2 bg-gray-100 rounded-2xl py-3 text-[14px] text-gray-400 font-medium opacity-50 cursor-default">
            <span className="text-lg">G</span> Google (即将上线)
          </button>
          <button className="flex-1 flex items-center justify-center gap-2 bg-gray-100 rounded-2xl py-3 text-[14px] text-gray-400 font-medium opacity-50 cursor-default">
            <span className="text-lg">🍎</span> Apple (即将上线)
          </button>
        </div>

        {/* Skip */}
        <div className="mt-auto flex justify-center">
          <button onClick={handleSkip} className="btn-text text-gray-500 text-[14px]">
            跳过登录，使用本地模式 →
          </button>
        </div>
      </motion.div>
    </div>
  );
}
