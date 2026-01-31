import axios from 'axios';

const request = axios.create({
    baseURL: '/api'
});

export const getApps = () => request.get('/apps');
export const getAppStages = (appId: string) => request.get(`/apps/${appId}/stages`);
export const getAppJobs = (appId: string) => request.get(`/apps/${appId}/jobs`);
export const getAppExecutors = (appId: string) => request.get(`/apps/${appId}/executors`);
export const getAppEnvironment = (appId: string) => request.get(`/apps/${appId}/environment`);
export const getStageTasks = (appId: string, stageId: number, page = 1, size = 100) => 
    request.get(`/apps/${appId}/stages/${stageId}/tasks?page=${page}&size=${size}`);
export const getStageStats = (appId: string, stageId: number, attemptId = 0) =>
    request.get(`/apps/${appId}/stages/${stageId}/${attemptId}/stats`);
export const getDiagnosisReport = (appId: string) => request.get(`/apps/${appId}/report`);
export const compareApps = (appId1: string, appId2: string) => 
    request.get(`/compare?appId1=${appId1}&appId2=${appId2}`);
