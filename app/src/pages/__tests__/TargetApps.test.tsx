import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import TargetApps from '../TargetApps';
import { useStore } from '../../hooks/useStore';

const mockNavigate = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ state: null }),
  };
});

vi.mock('../../backend/appNames', () => ({
  DEFAULT_TARGET_APPS: ['com.test.a', 'com.test.b', 'com.test.c'],
  APP_NAMES: {
    'com.test.a': '应用A',
    'com.test.b': '应用B',
    'com.test.c': '应用C',
  },
  getAppName: (pkg: string) => {
    const names: Record<string, string> = {
      'com.test.a': '应用A',
      'com.test.b': '应用B',
      'com.test.c': '应用C',
    };
    return names[pkg] || '未知';
  },
}));

vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    getLaunchableApps: vi.fn().mockResolvedValue({
      apps: [],
    }),
    loadSettings: vi.fn().mockResolvedValue({
      limitMinutes: 15,
      restDuration: 120,
      targetApps: ['com.test.a'],
      allowEmergencyUnlock: false,
      strongBlockingEnabled: false,
      channel: 'play',
    }),
    saveTargetApps: vi.fn().mockResolvedValue(undefined),
  },
  LaunchableApp: {},
}));

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false },
}));

function renderTargetApps() {
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      targetApps: ['com.test.a'],
    },
  });
  return render(
    <BrowserRouter>
      <TargetApps />
    </BrowserRouter>
  );
}

describe('TargetApps', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it('加载完成后不显示未保存弹窗', async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.queryByText('有未保存的修改')).toBeNull();
    });
  });

  it('选中一个新应用后dirty为true', async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.getByText('应用B')).toBeDefined();
    });
    fireEvent.click(screen.getByText('应用B'));
    fireEvent.click(screen.getByLabelText('返回'));
    expect(screen.getByText('有未保存的修改')).toBeDefined();
  });

  it('已选置顶', async () => {
    renderTargetApps();
    await waitFor(() => {
      const appTexts = screen.getAllByText('应用A');
      expect(appTexts.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('只看已选过滤', async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.getByText('应用A')).toBeDefined();
    });
    fireEvent.click(screen.getByText('只看已选'));
    await waitFor(() => {
      expect(screen.queryByText('应用B')).toBeNull();
      expect(screen.getByText('应用A')).toBeDefined();
    });
  });

  it('搜索过滤', async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索应用')).toBeDefined();
    });
    fireEvent.change(screen.getByPlaceholderText('搜索应用'), { target: { value: '应用B' } });
    await waitFor(() => {
      expect(screen.getByText('应用B')).toBeDefined();
      expect(screen.queryByText('应用A')).toBeNull();
    });
  });

  it('清空搜索', async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索应用')).toBeDefined();
    });
    fireEvent.change(screen.getByPlaceholderText('搜索应用'), { target: { value: '应用B' } });
    await waitFor(() => {
      expect(screen.getByText('清空搜索')).toBeDefined();
    });
    fireEvent.click(screen.getByText('清空搜索'));
    await waitFor(() => {
      expect(screen.getByText('应用A')).toBeDefined();
      expect(screen.getByText('应用B')).toBeDefined();
    });
  });
});
