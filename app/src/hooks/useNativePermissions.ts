import { useCallback, useEffect, useRef, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { NativeFlow, PermissionState } from '../backend/nativeFlow';

const EMPTY_PERMISSIONS: PermissionState = {
  hasUsageStats: false,
  hasOverlay: false,
  isIgnoringBattery: false,
  hasNotification: false,
  hasAccessibility: false,
  isDomestic: false,
  channel: 'base',
  manufacturer: '',
};

export function useNativePermissions(poll = false) {
  const isNative = Capacitor.isNativePlatform();
  const [permissions, setPermissions] = useState<PermissionState>(EMPTY_PERMISSIONS);
  const [checking, setChecking] = useState(isNative);
  const [error, setError] = useState('');
  const checkingRef = useRef(false);

  const refresh = useCallback(async () => {
    if (!isNative || checkingRef.current) return;
    checkingRef.current = true;
    setChecking(true);
    try {
      setPermissions(await NativeFlow.checkPermissions());
      setError('');
    } catch {
      setError('无法读取系统权限状态，请重试。');
    } finally {
      checkingRef.current = false;
      setChecking(false);
    }
  }, [isNative]);

  useEffect(() => {
    if (!isNative) return;
    refresh();

    const onResume = () => refresh();
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    const interval = poll ? window.setInterval(refresh, 1000) : undefined;
    window.addEventListener('focus', onResume);
    window.addEventListener('pageshow', onResume);
    window.addEventListener('flow-permissions-changed', onResume);
    document.addEventListener('visibilitychange', onVisible);

    let listener: { remove: () => Promise<void> } | undefined;
    NativeFlow.addListener('permissionsChanged', next => {
      setPermissions(next);
      setError('');
      setChecking(false);
    }).then(handle => {
      listener = handle;
    }).catch(() => {});

    return () => {
      if (interval !== undefined) window.clearInterval(interval);
      window.removeEventListener('focus', onResume);
      window.removeEventListener('pageshow', onResume);
      window.removeEventListener('flow-permissions-changed', onResume);
      document.removeEventListener('visibilitychange', onVisible);
      listener?.remove().catch(() => {});
    };
  }, [isNative, poll, refresh]);

  return { isNative, permissions, checking, error, refresh };
}
