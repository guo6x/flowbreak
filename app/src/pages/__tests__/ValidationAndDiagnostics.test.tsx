import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ValidationAndDiagnostics from '../ValidationAndDiagnostics';

const mocks = vi.hoisted(() => ({ native: vi.fn(() => true), navigate: vi.fn(), summary: vi.fn(), diagnostics: vi.fn(), share: vi.fn(), week: vi.fn(), reflections: vi.fn() }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: mocks.native } }));
vi.mock('react-router', () => ({ useNavigate: () => mocks.navigate }));
vi.mock('../../backend/nativeFlow', () => ({ NativeFlow: { getValidationSummary: mocks.summary, getDiagnostics: mocks.diagnostics, shareDiagnostics: mocks.share } }));
vi.mock('../../backend/storage', () => ({ getWeekStats: mocks.week, getReflectionCounts: mocks.reflections }));

const summary = { days: 7, restCount: 3, outcomeCount: 2, successfulPullbackCount: 1, postRestReturnCount: 1, postRestTargetSeconds: 60, helpedDays: 2, neutralDays: 1, notHelpedDays: 0 };
const diagnostics = {
  versionName: '1.1.0', versionCode: 1, channel: 'domestic', packageName: 'com.flowbreak.app.cn',
  databaseVersion: 4, databaseSchemaCurrent: true, serviceAlive: true, serviceHeartbeatAt: 100, state: 'IDLE',
  monitoringConfigured: true, monitoringEnabled: true, protectionRuntimeAvailable: true, targetCount: 2,
  permissions: { hasUsageStats: true, hasOverlay: true },
  runtimeTracking: { monitorThreadAlive: true, monitorLooperIsMain: false },
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.native.mockReturnValue(true);
  mocks.summary.mockResolvedValue(summary);
  mocks.diagnostics.mockResolvedValue(diagnostics);
  mocks.share.mockResolvedValue(undefined);
  mocks.week.mockReturnValue([{ restCount: 2 }]);
  mocks.reflections.mockReturnValue({ helpedDays: 1, neutralDays: 0, notHelpedDays: 0 });
});

describe('ValidationAndDiagnostics', () => {
  it('never presents fake Android service health in the browser preview', async () => {
    mocks.native.mockReturnValue(false);
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByText('原生诊断仅在 Android 提供')).toBeInTheDocument();
    expect(screen.getByTestId('pullback-rate')).toHaveTextContent('设备端查看');
    expect(screen.queryByText('检测到服务心跳')).not.toBeInTheDocument();
    expect(screen.queryByText('Room v0')).not.toBeInTheDocument();
    expect(screen.getByTestId('export-diagnostics')).toBeDisabled();
    expect(mocks.diagnostics).not.toHaveBeenCalled();
  });

  it('reports Room v4 as healthy when the native schema is current', async () => {
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByText('Room v4 · 正常')).toBeInTheDocument();
  });

  it('flags a stale Room schema without hard-coding v3 as healthy', async () => {
    mocks.diagnostics.mockResolvedValueOnce({
      ...diagnostics,
      databaseVersion: 3,
      databaseSchemaCurrent: false,
    });
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByText('Room v3 · 需检查')).toBeInTheDocument();
  });

  it('keeps the real summary when diagnostics cannot be read', async () => {
    mocks.diagnostics.mockRejectedValueOnce(new Error('unavailable'));
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByRole('alert')).toHaveTextContent('部分数据暂时无法读取');
    expect(screen.getByTestId('pullback-rate')).toHaveTextContent('50%');
    expect(screen.getByText('运行诊断暂不可用')).toBeInTheDocument();
    expect(screen.queryByText('未检测到心跳')).not.toBeInTheDocument();
    expect(screen.getByTestId('export-diagnostics')).toBeDisabled();
  });

  it('keeps real runtime data without making failed summary reads look empty', async () => {
    mocks.summary.mockRejectedValueOnce(new Error('unavailable'));
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByText('检测到服务心跳')).toBeInTheDocument();
    expect(screen.getByTestId('pullback-rate')).toHaveTextContent('暂不可用');
    expect(screen.getByTestId('export-diagnostics')).toBeEnabled();
    expect(screen.queryByText('待积累')).not.toBeInTheDocument();
  });

  it('shows the unsupported-device boundary and lets the user export actual diagnostics', async () => {
    mocks.diagnostics.mockResolvedValueOnce({ ...diagnostics, protectionRuntimeAvailable: false });
    render(<ValidationAndDiagnostics />);
    expect(await screen.findByText('设备暂不支持')).toBeInTheDocument();
    expect(screen.getByText('当前版本暂不支持在此设备上开启保护')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('export-diagnostics'));
    await waitFor(() => expect(mocks.share).toHaveBeenCalledTimes(1));
  });

  it('shows an actionable export failure and allows another export', async () => {
    mocks.share.mockRejectedValueOnce(new Error('failed'));
    render(<ValidationAndDiagnostics />);
    await screen.findByText('检测到服务心跳');
    fireEvent.click(screen.getByTestId('export-diagnostics'));
    expect(await screen.findByRole('alert')).toHaveTextContent('诊断导出失败');
    expect(screen.getByTestId('export-diagnostics')).toBeEnabled();
  });

  it('refreshes from unavailable to actual native data', async () => {
    mocks.diagnostics.mockRejectedValueOnce(new Error('unavailable'));
    render(<ValidationAndDiagnostics />);
    await screen.findByText('运行诊断暂不可用');
    fireEvent.click(screen.getByRole('button', { name: '刷新诊断' }));
    expect(await screen.findByText('检测到服务心跳')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
