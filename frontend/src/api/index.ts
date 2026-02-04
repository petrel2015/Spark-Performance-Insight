import axios from 'axios';
import router from '../router';

const request = axios.create({
    baseURL: '/api'
});

request.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 503) {
      const msg = error.response.data && error.response.data.message ? error.response.data.message : 'Application is processing...';
      router.push({ path: '/', query: { processingMsg: msg } });
    }
    return Promise.reject(error);
  }
);

export const getApps = (page = 1, size = 20, sort = '', search = '') => 
    request.get(`/apps?page=${page}&size=${size}&sort=${sort}&search=${search}`);
export const getAppStages = (appId: string, page = 1, size = 20, sort = '', jobId = null, stageId = null) => {
    let url = `/apps/${appId}/stages?page=${page}&size=${size}&sort=${sort}`;
    if (jobId !== null) url += `&jobId=${jobId}`;
    if (stageId !== null && stageId !== '') url += `&stageId=${stageId}`;
    return request.get(url);
};
export const getStage = (appId: string, stageId: number, attemptId?: number) => {
    let url = `/apps/${appId}/stages/${stageId}`;
    if (attemptId !== undefined && attemptId !== null) url += `?attemptId=${attemptId}`;
    return request.get(url);
};
export const getAppJobs = (appId: string, page = 1, size = 20, sort = '', jobId = null, jobGroup = '') => {
    let url = `/apps/${appId}/jobs?page=${page}&size=${size}&sort=${sort}`;
    if (jobId !== null && jobId !== '') url += `&jobId=${jobId}`;
    if (jobGroup && jobGroup !== '') url += `&jobGroup=${encodeURIComponent(jobGroup)}`;
    return request.get(url);
};
export const getJob = (appId: string, jobId: number) => request.get(`/apps/${appId}/jobs/${jobId}`);
export const getAppExecutors = (appId: string) => request.get(`/apps/${appId}/executors`);
export const getAppEnvironment = (appId: string) => request.get(`/apps/${appId}/environment`);
export const getStageTasks = (appId: string, stageId: number, page = 1, size = 20, sort = '', attemptId?: number) => {
    let url = `/apps/${appId}/stages/${stageId}/tasks?page=${page}&size=${size}&sort=${sort}`;
    if (attemptId !== undefined && attemptId !== null) url += `&attemptId=${attemptId}`;
    return request.get(url);
};
export const getStageStats = (appId: string, stageId: number, attemptId: number) => 
  request.get(`/apps/${appId}/stages/${stageId}/${attemptId}/stats`);

export const getExecutorSummary = (appId: string, stageId: number, attemptId?: number) => {
  let url = `/apps/${appId}/stages/${stageId}/executor-summary`;
  if (attemptId !== undefined && attemptId !== null) url += `?attemptId=${attemptId}`;
  return request.get(url);
};

export const getJobExecutorSummary = (appId: string, jobId: number) =>
  request.get(`/apps/${appId}/jobs/${jobId}/executor-summary`);

export const getStageTimeline = (appId: string, stageId: number, attemptId?: number) => {
  let url = `/apps/${appId}/stages/${stageId}/timeline`;
  if (attemptId !== undefined && attemptId !== null) url += `?attemptId=${attemptId}`;
  return request.get(url);
};

export const getJobStages = (appId: string, jobId: number) =>
  request.get(`/apps/${appId}/jobs/${jobId}/stages`);

export const getApp = (appId: string) => request.get(`/apps/${appId}`);
export const getDiagnosisReport = (appId: string) => request.get(`/apps/${appId}/report`);
export const compareApps = (appId1: string, appId2: string) => 
    request.get(`/compare?appId1=${appId1}&appId2=${appId2}`);
