import axios from 'axios'

const http=axios.create({baseURL:'/api'})

export const updateManagedArtifactBindings=(projectId,artifacts)=>
  http.put(`/managed-projects/${projectId}/artifact-bindings`,{artifacts}).then(r=>r.data)
