// src/pages/Login.tsx
// PRD 5.1 登录注册模块
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Mail, Phone, Globe, Globe as Google, Apple, MessageCircle as WeChat, ArrowRight, User } from 'lucide-react';
import { useStore } from '../hooks/useStore';

type LoginTab = 'email' | 'phone' | 'third-party';

export default function Login() {
  const navigate = useNavigate();
  const updateProfile = useStore(s => s.updateProfile);
  const [activeTab, setActiveTab] = useState<LoginTab>('email');
  
  // States for different tabs
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phoneNumber, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [localName, setLocalName] = useState('');
  const [showLocalMode, setShowLocalMode] = useState(false);

  // Phone countdown logic
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleGetCode = () => {
    if (phoneNumber.length < 11) return;
    setCountdown(60);
    // TODO: Integrated with SMS service
  };

  const handleLogin = (method: string) => {
    console.log(`Login via ${method}`);
    // TODO: Connect to backend authentication service
    navigate('/permissions');
  };

  const handleSkip = () => {
    if (!localName.trim()) return;
    updateProfile({ name: localName.trim() });
    navigate('/permissions');
  };

  const toast = (msg: string) => {
    // Simple mock toast
    alert(msg);
  };

  const tabItems = [
    { id: 'email', label: '邮箱登录', icon: Mail },
    { id: 'phone', label: '手机号', icon: Phone },
    { id: 'third-party', label: '第三方', icon: Globe },
  ];

  return (
    <div className="min-h-dvh bg-gray-50 flex flex-col px-8 pt-20 pb-12 overflow-y-auto no-scrollbar">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Header */}
        <div className="mb-10">
          <div className="w-16 h-16 rounded-3xl bg-primary/10 flex items-center justify-center mb-6">
            <User size={32} className="text-primary" />
          </div>
          <h1 className="text-[28px] font-bold text-gray-900 mb-2">欢迎回来</h1>
          <p className="text-[14px] text-gray-500">登录以同步你的专注数据与成就</p>
        </div>

        {/* Tabs */}
        <div className="flex bg-gray-100 p-1.5 rounded-2xl mb-8">
          {tabItems.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => { setActiveTab(tab.id as LoginTab); setShowLocalMode(false); }}
                className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-[14px] font-medium transition-all ${
                  isActive ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 active:scale-95'
                }`}
              >
                <Icon size={16} />
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Content Area */}
        <div className="flex-1">
          <AnimatePresence mode="wait">
            {!showLocalMode ? (
              <motion.div
                key={activeTab}
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                className="space-y-4"
              >
                {activeTab === 'email' && (
                  <>
                    <input
                      type="email"
                      placeholder="邮箱地址"
                      value={email}
                      onChange={e => setEmail(e.target.value)}
                      className="w-full h-14 bg-white border border-gray-200 rounded-2xl px-5 text-[15px] outline-none focus:border-primary transition-colors"
                    />
                    <input
                      type="password"
                      placeholder="登录密码"
                      value={password}
                      onChange={e => setPassword(e.target.value)}
                      className="w-full h-14 bg-white border border-gray-200 rounded-2xl px-5 text-[15px] outline-none focus:border-primary transition-colors"
                    />
                    <button
                      onClick={() => handleLogin('email')}
                      className="btn-primary w-full h-14 shadow-lg shadow-primary/20"
                    >
                      登录
                    </button>
                  </>
                )}

                {activeTab === 'phone' && (
                  <>
                    <div className="relative">
                      <input
                        type="tel"
                        placeholder="手机号码"
                        value={phoneNumber}
                        onChange={e => setPhone(e.target.value)}
                        className="w-full h-14 bg-white border border-gray-200 rounded-2xl px-5 text-[15px] outline-none focus:border-primary transition-colors"
                      />
                      <button
                        disabled={countdown > 0 || phoneNumber.length < 11}
                        onClick={handleGetCode}
                        className={`absolute right-3 top-2.5 h-8 px-3 rounded-lg text-[12px] font-medium transition-colors ${
                          countdown > 0 || phoneNumber.length < 11
                            ? 'text-gray-300 bg-gray-50'
                            : 'text-primary bg-primary/5 active:bg-primary/10'
                        }`}
                      >
                        {countdown > 0 ? `${countdown}s` : '获取验证码'}
                      </button>
                    </div>
                    <input
                      type="text"
                      placeholder="6 位验证码"
                      value={code}
                      onChange={e => setCode(e.target.value)}
                      className="w-full h-14 bg-white border border-gray-200 rounded-2xl px-5 text-[15px] outline-none focus:border-primary transition-colors"
                    />
                    <button
                      onClick={() => handleLogin('phone')}
                      className="btn-primary w-full h-14 shadow-lg shadow-primary/20"
                    >
                      验证并登录
                    </button>
                  </>
                )}

                {activeTab === 'third-party' && (
                  <div className="grid grid-cols-1 gap-3 pt-2">
                    {[
                      { name: 'Google', icon: Google, color: 'text-gray-700' },
                      { name: 'Apple', icon: Apple, color: 'text-black' },
                      { name: 'WeChat', icon: WeChat, color: 'text-green-500', label: '微信' },
                    ].map(item => (
                      <button
                        key={item.name}
                        onClick={() => toast('即将上线')}
                        className="w-full h-14 bg-white border border-gray-200 rounded-2xl flex items-center justify-center gap-3 active:bg-gray-50 transition-colors"
                      >
                        <item.icon size={20} className={item.color} />
                        <span className="text-[15px] font-medium text-gray-900">
                          使用 {item.label || item.name} 登录
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </motion.div>
            ) : (
              <motion.div
                key="local-mode"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-6"
              >
                <div className="p-5 bg-orange-50 border border-orange-100 rounded-2xl">
                  <p className="text-[13px] text-orange-700 leading-relaxed">
                    💡 本地模式下，你的数据将仅存储在此设备上。卸载应用或清除缓存将导致数据丢失。
                  </p>
                </div>
                <div className="space-y-4">
                  <label className="text-[14px] font-bold text-gray-700 px-1">请输入你的昵称</label>
                  <input
                    autoFocus
                    placeholder="例如：自律的小明"
                    value={localName}
                    onChange={e => setLocalName(e.target.value)}
                    className="w-full h-14 bg-white border border-gray-200 rounded-2xl px-5 text-[15px] outline-none focus:border-primary transition-colors"
                  />
                  <button
                    onClick={handleSkip}
                    disabled={!localName.trim()}
                    className={`w-full h-14 rounded-2xl font-bold flex items-center justify-center gap-2 transition-all ${
                      localName.trim()
                        ? 'bg-gray-900 text-white active:scale-95 shadow-lg shadow-gray-200'
                        : 'bg-gray-200 text-gray-400'
                    }`}
                  >
                    开始使用 <ArrowRight size={18} />
                  </button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer actions */}
        <div className="mt-12 text-center">
          {!showLocalMode ? (
            <button
              onClick={() => setShowLocalMode(true)}
              className="text-[14px] text-gray-500 font-medium hover:text-primary transition-colors py-2 px-4"
            >
              跳过登录，使用本地模式
            </button>
          ) : (
            <button
              onClick={() => setShowLocalMode(false)}
              className="text-[14px] text-primary font-medium py-2 px-4"
            >
              返回登录
            </button>
          )}
          <p className="text-[11px] text-gray-400 mt-6 px-4">
            登录即代表你同意我们的 <span className="underline">服务协议</span> 和 <span className="underline">隐私政策</span>
          </p>
        </div>
      </motion.div>
    </div>
  );
}
