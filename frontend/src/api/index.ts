import axios from 'axios';

const request = axios.create({
    baseURL: '/api'
});

export const getApps = () => request.get('/apps');
export const getAppStages = (appId: string, page = 1, size = 20, sort = '', jobId = null) => {
    let url = `/apps/${appId}/stages?page=${page}&size=${size}&sort=${sort}`;
    if (jobId !== null) url += `&jobId=${jobId}`;
    return request.get(url);
};
export const getStage = (appId: string, stageId: number) => request.get(`/apps/${appId}/stages/${stageId}`);
export const getAppJobs = (appId: string, page = 1, size = 20, sort = '') => 
    request.get(`/apps/${appId}/jobs?page=${page}&size=${size}&sort=${sort}`);
export const getJob = (appId: string, jobId: number) => request.get(`/apps/${appId}/jobs/${jobId}`);
export const getAppExecutors = (appId: string) => request.get(`/apps/${appId}/executors`);
export const getAppEnvironment = (appId: string) => request.get(`/apps/${appId}/environment`);
export const getStageTasks = (appId: string, stageId: number, page = 1, size = 20, sort = '') => 
    request.get(`/apps/${appId}/stages/${stageId}/tasks?page=${page}&size=${size}&sort=${sort}`);
export const getStageStats = (appId: string, stageId: number, attemptId: number) => 
  request.get(`/apps/${appId}/stages/${stageId}/${attemptId}/stats`);

export const getExecutorSummary = (appId: string, stageId: number) =>
  request.get(`/apps/${appId}/stages/${stageId}/executor-summary`);

export const getJobExecutorSummary = (appId: string, jobId: number) =>
  request.get(`/apps/${appId}/jobs/${jobId}/executor-summary`);

export const getStageTimeline = (appId: string, stageId: number) => 
  request.get(`/apps/${appId}/stages/${stageId}/timeline`);

export const getJobStages = (appId: string, jobId: number) =>
  request.get(`/apps/${appId}/jobs/${jobId}/stages`);

export const getDiagnosisReport = (appId: string) => request.get(`/apps/${appId}/report`);
export const compareApps = (appId1: string, appId2: string) => 
    request.get(`/compare?appId1=${appId1}&appId2=${appId2}`);
