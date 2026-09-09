import axios from 'axios'

const http=axios.create({baseURL:'/api'})

export const materializeManagedArtifacts=(projectId,artifacts)=>
  http.post(`/managed-projects/${projectId}/materialize`,{artifacts}).then(r=>r.data)
