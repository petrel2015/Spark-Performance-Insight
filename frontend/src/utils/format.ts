export const formatTime = (ms: number | null | undefined) => {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1) return '0 ms';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)} s`;
  const m = Math.floor(s / 60);
  const rs = Math.round(s % 60);
  return `${m} m ${rs} s`;
};

export const formatBytes = (bytes: number | null | undefined) => {
  if (bytes === null || bytes === undefined) return '-';
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

export const formatCompactNum = (num: number | null | undefined) => {
  if (num === null || num === undefined) return '';
  if (num >= 1000000) return (num / 1000000).toFixed(1) + ' M';
  if (num >= 1000) return (num / 1000).toFixed(1) + ' K';
  return num.toString();
};

export const formatNum = (num: number | null | undefined) => {
  if (num === null || num === undefined) return '-';
  if (num === 0) return '0';
  const exact = num.toLocaleString();
  const compact = formatCompactNum(num);
  return compact && num >= 1000 ? `${exact} (${compact})` : exact;
};
