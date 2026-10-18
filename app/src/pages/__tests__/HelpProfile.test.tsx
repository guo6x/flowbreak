import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import Help from '../Help';
import Profile from '../Profile';
import { useStore } from '../../hooks/useStore';

const navigate = vi.fn();
vi.mock('react-router', async () => ({ ...await vi.importActual('react-router'), useNavigate: () => navigate }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => false }, registerPlugin: () => ({}) }));
vi.mock('../../hooks/useNativePermissions', () => ({ useNativePermissions: () => ({ isNative: false, permissions: {}, refresh: vi.fn() }) }));

beforeEach(() => {
  navigate.mockClear();
  useStore.setState({ profile: { ...useStore.getState().profile, onboardingDone: true, name: '测试用户', selectedBackground: 0 } });
});

describe('帮助与我的', () => {
  it('网页指南明确预览不能识别或阻断其他应用', () => {
    render(<MemoryRouter><Help /></MemoryRouter>);
    expect(screen.getByRole('note')).toHaveTextContent('不能识别或阻断手机上其他应用');
    expect(screen.getByText(/当前没有导入或云同步/)).toBeInTheDocument();
    expect(screen.getByText(/不做医疗诊断/)).toBeInTheDocument();
  });

  it('使用指南提供支持范围与准确阈值，并可返回我的或打开诊断', () => {
    render(<MemoryRouter><Help /></MemoryRouter>);
    expect(screen.getByText(/80%.*100%.*120%/)).toBeInTheDocument();
    expect(screen.getByText(/vivo.*iQOO.*不支持/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '检查权限与运行状态' }));
    expect(navigate).toHaveBeenLastCalledWith('/validation');
    fireEvent.click(screen.getByRole('button', { name: '返回我的' }));
    expect(navigate).toHaveBeenLastCalledWith('/profile');
  });

  it('休息背景预览是键盘可操作且有名称的按钮', () => {
    render(<MemoryRouter><Profile /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: '更换休息背景，当前森林' }));
    expect(navigate).toHaveBeenLastCalledWith('/profile-preferences');
  });

  it('再次查看引导不会清除已经完成的设置状态', () => {
    render(<MemoryRouter><Profile /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: '重新体验引导' }));
    expect(navigate).toHaveBeenLastCalledWith('/onboarding');
    expect(useStore.getState().profile.onboardingDone).toBe(true);
  });

  it('我的页面有清晰的使用指南入口', () => {
    render(<MemoryRouter><Profile /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: /使用指南/ }));
    expect(navigate).toHaveBeenLastCalledWith('/help');
  });
});
