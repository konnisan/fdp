import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

export const listProjects = () => http.get('/poc-projects').then(r => r.data)
export const createProject = data => http.post('/poc-projects', data).then(r => r.data)
export const updateProject = (id, data) => http.put(`/poc-projects/${id}`, data).then(r => r.data)
export const deleteProject = id => http.delete(`/poc-projects/${id}`)
export const deployProject = id => http.post(`/poc-projects/${id}/deploy`).then(r => r.data)
export const restartProject = id => http.post(`/poc-projects/${id}/restart`).then(r => r.data)
export const stopProject = id => http.post(`/poc-projects/${id}/stop`).then(r => r.data)
export const listDeployments = projectId => http.get('/deployments', { params: projectId ? { projectId } : {} }).then(r => r.data)
export const getDeploymentLogs = id => http.get(`/deployments/${id}/logs`).then(r => r.data)
