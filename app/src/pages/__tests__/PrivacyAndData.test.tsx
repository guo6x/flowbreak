import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import PrivacyAndData from '../PrivacyAndData';

const mocks = vi.hoisted(() => ({
  native: vi.fn(() => true),
  navigate: vi.fn(),
  build: vi.fn(),
  logs: vi.fn(),
  clearLogs: vi.fn(),
  clearNative: vi.fn(),
  share: vi.fn(),
  payload: vi.fn(),
  clearCache: vi.fn(),
}));

vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: mocks.native } }));
vi.mock('react-router', () => ({ useNavigate: () => mocks.navigate }));
vi.mock('../../backend/nativeFlow', () => ({ NativeFlow: {
  getBuildInfo: mocks.build, getCrashLogs: mocks.logs, clearCrashLogs: mocks.clearLogs,
  clearLocalData: mocks.clearNative, shareLocalData: mocks.share,
} }));
vi.mock('../../backend/storage', () => ({ exportLegacyPayload: mocks.payload, clearAllLocalData: mocks.clearCache }));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.native.mockReturnValue(true);
  mocks.build.mockResolvedValue({ versionName: '1.1.0', channel: 'domestic', packageName: 'com.flowbreak.app.cn' });
  mocks.logs.mockResolvedValue({ logs: [] });
  mocks.clearNative.mockResolvedValue(undefined);
  mocks.share.mockResolvedValue(undefined);
  mocks.payload.mockReturnValue({ profile: {} });
});

describe('PrivacyAndData', () => {
  it('discloses Accessibility event fields and local WeChat signal before describing the feature', async () => {
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    expect(screen.getByText(/窗口事件中的应用包名及必要的窗口类名/)).toBeInTheDocument();
    expect(screen.getByText(/保存“是否为视频号页面”的判断值和检测时间/)).toBeInTheDocument();
    expect(screen.getByText(/最终安装包的运行时网络行为尚待核验/)).toBeInTheDocument();
  });

  it('explains the identifying environment fields in the shared diagnostics allowlist', async () => {
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    expect(screen.getByText(/设备厂商\/支持状态、权限与保护状态/)).toBeInTheDocument();
    expect(screen.getByText(/设备环境信息/)).toBeInTheDocument();
  });

  it('does not equate an empty crash log with verified healthy operation', async () => {
    render(<PrivacyAndData />);
    expect(await screen.findByText('本机暂无已保存的崩溃记录。')).toBeInTheDocument();
    expect(screen.queryByText(/应用运行正常/)).not.toBeInTheDocument();
  });

  it('shows a failed crash read honestly and allows a retry', async () => {
    mocks.logs.mockRejectedValueOnce(new Error('unavailable'));
    render(<PrivacyAndData />);
    expect(await screen.findByText(/暂时无法读取崩溃日志/)).toBeInTheDocument();
    expect(screen.queryByText('本机暂无已保存的崩溃记录。')).not.toBeInTheDocument();
    mocks.logs.mockResolvedValue({ logs: [{ filename: 'crash-1.txt', timestamp: 1, content: 'local stack trace' }] });
    fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
    const toggle = await screen.findByRole('button', { name: 'crash-1.txt' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('local stack trace')).toBeInTheDocument();
  });

  it('requires an explicit confirmation and offers a safe cancellation', async () => {
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    fireEvent.click(screen.getByRole('button', { name: /清除全部本地数据/ }));
    expect(screen.getByRole('group', { name: '确定要清除全部数据吗？' })).toBeInTheDocument();
    expect(mocks.clearNative).not.toHaveBeenCalled();
    expect(mocks.clearCache).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '保留数据' }));
    expect(screen.queryByRole('button', { name: '永久清除并重新设置' })).not.toBeInTheDocument();
    expect(mocks.clearNative).not.toHaveBeenCalled();
  });

  it('moves a focused clear trigger to the safe choice and restores focus after cancellation', async () => {
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    const trigger = screen.getByRole('button', { name: /清除全部本地数据/ });
    trigger.focus();
    expect(trigger).toHaveFocus();
    fireEvent.click(trigger);
    const confirmation = screen.getByRole('group', { name: '确定要清除全部数据吗？' });
    expect(confirmation).toHaveAccessibleDescription(/无法撤销/);
    expect(confirmation).not.toHaveAttribute('aria-modal');
    const keepData = screen.getByRole('button', { name: '保留数据' });
    expect(keepData).toHaveFocus();
    expect(mocks.clearNative).not.toHaveBeenCalled();
    fireEvent.click(keepData);
    expect(screen.getByRole('button', { name: /清除全部本地数据/ })).toHaveFocus();
    expect(mocks.clearCache).not.toHaveBeenCalled();
  });

  it('does not clear interface data when native data clearing fails', async () => {
    mocks.clearNative.mockRejectedValueOnce(new Error('failed'));
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    fireEvent.click(screen.getByRole('button', { name: /清除全部本地数据/ }));
    const confirm = screen.getByRole('button', { name: '永久清除并重新设置' });
    confirm.focus();
    fireEvent.click(confirm);
    expect(await screen.findByRole('alert')).toHaveTextContent('界面缓存未清除');
    await waitFor(() => expect(screen.getByRole('button', { name: /清除全部本地数据/ })).toHaveFocus());
    expect(mocks.clearNative).toHaveBeenCalledTimes(1);
    expect(mocks.clearCache).not.toHaveBeenCalled();
  });

  it('prevents duplicate export requests and exposes failures', async () => {
    let rejectShare!: (reason: Error) => void;
    mocks.share.mockReturnValueOnce(new Promise((_, reject) => { rejectShare = reject; }));
    render(<PrivacyAndData />);
    await screen.findByText('本机暂无已保存的崩溃记录。');
    const exportButton = screen.getByRole('button', { name: /导出 JSON 数据/ });
    fireEvent.click(exportButton);
    fireEvent.click(exportButton);
    expect(mocks.share).toHaveBeenCalledTimes(1);
    rejectShare(new Error('cancelled'));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('数据导出失败'));
    expect(screen.getByRole('button', { name: /导出 JSON 数据/ })).toBeEnabled();
  });

  it('labels the browser experience as a preview rather than a native build', () => {
    mocks.native.mockReturnValue(false);
    render(<PrivacyAndData />);
    expect(screen.getByText(/Web 界面预览/)).toBeInTheDocument();
    expect(mocks.logs).not.toHaveBeenCalled();
    expect(mocks.build).not.toHaveBeenCalled();
  });
});
