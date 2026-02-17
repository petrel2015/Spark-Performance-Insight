export const parseStorageLevelObject = (levelStr: any) => {
  if (!levelStr) return null;
  if (levelStr === 'NONE') return { useDisk: false, useMemory: false, useOffHeap: false, deserialized: false, replication: 0 };
  try {
    return typeof levelStr === 'string' ? JSON.parse(levelStr) : levelStr;
  } catch (e) {
    return null;
  }
};

export const formatStorageLevel = (levelStr: any) => {
  if (!levelStr) return [];
  if (levelStr === 'NONE') return ['None'];
  
  try {
    // If it's already a JSON object, use it directly
    const level = typeof levelStr === 'string' ? JSON.parse(levelStr) : levelStr;
    const tags = [];
    
    if (level.useDisk) tags.push('Disk');
    if (level.useMemory) tags.push('Memory');
    if (level.useOffHeap) tags.push('OffHeap');
    if (level.deserialized) tags.push('Deserialized');
    if (level.replication > 1) tags.push(`${level.replication}x Replicated`);
    
    return tags.length > 0 ? tags : [levelStr];
  } catch (e) {
    // Fallback for non-JSON strings
    return [levelStr];
  }
};
