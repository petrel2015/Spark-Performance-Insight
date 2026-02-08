export const formatTime = (ms: number | null | undefined) => {
    if (ms === null || ms === undefined) return '-';
    if (ms < 1) return '0 ms';
    if (ms < 1000) return `${Math.round(ms)} ms`;
    const s = ms / 1000;
    if (s < 60) return `${s.toFixed(2)} s`;
    if (s < 3600) {
        const m = Math.floor(s / 60);
        const rs = (s % 60).toFixed(2);
        return `${m} min ${rs} s`;
    }
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const rs = (s % 60).toFixed(1);
    return `${h} h ${m} min ${rs} s`;
};

export const formatBytes = (bytes: number | null | undefined) => {
    if (bytes === null || bytes === undefined || bytes === 0) return '-';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

export const formatCompactNum = (num: number | null | undefined) => {
    if (num === null || num === undefined || num === 0) return '';
    if (num >= 1000000) return (num / 1000000).toFixed(1) + ' M';
    if (num >= 1000) return (num / 1000).toFixed(1) + ' K';
    return num.toString();
};

export const formatNum = (num: number | null | undefined) => {
    if (num === null || num === undefined || num === 0) return '-';
    const exact = num.toLocaleString();
    const compact = formatCompactNum(num);
    return compact && num >= 1000 ? `${exact} (${compact})` : exact;
};

export const formatDateTime = (t: string | number | null | undefined) => {
    if (!t) return '-';
    return new Date(t).toLocaleString();
};