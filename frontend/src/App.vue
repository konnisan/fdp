<script setup>
import axios from 'axios'

async function deploy(){
 const r=await axios.post('http://localhost:8080/deploy/run')
 alert(r.data)
}
</script>

<template>
<div>
<h1>Financial Delivery Platform</h1>

<button @click="deploy">
一键部署
</button>

</div>
</template>